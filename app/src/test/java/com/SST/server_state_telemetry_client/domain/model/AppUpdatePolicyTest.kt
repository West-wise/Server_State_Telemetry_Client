package com.SST.server_state_telemetry_client.domain.model

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AppUpdatePolicyTest {
    private val base = "https://github.com/West-wise/Server_State_Telemetry_Client/releases"
    private fun metadata(code: Long = 2) = """{"schemaVersion":1,"versionCode":$code,"versionName":"v$code","minSdkVersion":26,"apkAssetName":"sstc.apk","sha256":"${"a".repeat(64)}"}"""
    private fun asset(name: String, tag: String) = buildJsonObject {
        put("name", name)
        put("browser_download_url", "$base/download/$tag/$name")
    }
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false,
        assets: List<JsonObject> = listOf(asset("sstc-update.json", tag), asset("sstc.apk", tag))
    ) = buildJsonObject {
        put("draft", draft); put("prerelease", prerelease)
        put("html_url", "$base/tag/$tag"); put("assets", JsonArray(assets))
    }

    @Test fun excludesDraftPrereleaseAndRequiresExactUniqueAssetPairing() = runBlocking {
        val invalid = listOf(
            release("draft", draft = true), release("pre", prerelease = true),
            release("missing", assets = listOf(asset("sstc-update.json", "missing"))),
            release("missingMeta", assets = listOf(asset("sstc.apk", "missingMeta"))),
            release("wrongName", assets = listOf(asset("sstc-update.json", "wrongName"), asset("other.apk", "wrongName"))),
            release("duplicate", assets = listOf(asset("sstc-update.json", "duplicate"),
                asset("sstc.apk", "duplicate"), asset("sstc.apk", "duplicate"))),
            release("duplicateMeta", assets = listOf(asset("sstc-update.json", "duplicateMeta"),
                asset("sstc-update.json", "duplicateMeta"), asset("sstc.apk", "duplicateMeta")))
        )
        assertNull(selectAppUpdate(JsonArray(invalid), 1, 26) { _, _ -> metadata() })
        assertEquals(2L, selectAppUpdate(JsonArray(invalid + release("valid")), 1, 26) { _, _ -> metadata() }?.versionCode)
    }

    @Test fun rejectsWrongTagRepositoryAndAssetUrlFilename() = runBlocking {
        val wrongMeta = release("v2", assets = listOf(asset("sstc-update.json", "v1"), asset("sstc.apk", "v2")))
        val wrongApk = release("v2", assets = listOf(asset("sstc-update.json", "v2"), asset("sstc.apk", "v1")))
        val wrongRepository = Json.parseToJsonElement(release("v2").toString().replace("West-wise", "Other")).jsonObject
        val wrongFilename = Json.parseToJsonElement(release("v2").toString().replace("/sstc.apk", "/other.apk")).jsonObject
        assertNull(selectAppUpdate(JsonArray(listOf(wrongMeta, wrongApk, wrongRepository, wrongFilename)), 1, 26) { _, _ -> metadata() })
    }

    @Test fun networkAndMalformedMetadataFallBackAndHighestNumericVersionWins() = runBlocking {
        val selected = selectAppUpdate(JsonArray(listOf(release("network"), release("malformed"),
            release("v3"), release("v2"))), 1, 26) { url, limit ->
            assertEquals(16_384, limit)
            when {
                "/network/" in url -> throw IOException("injected network failure")
                "/malformed/" in url -> "{"
                "/v3/" in url -> metadata(3)
                else -> metadata(2)
            }
        }
        assertEquals(3L, selected?.versionCode)
        assertNull(updateRequest<String> { throw IOException("API failure") })
    }

    @Test fun preservesCandidateAfterLaterFailureAndIndividualTimeout() = runBlocking {
        val selected = selectAppUpdate(JsonArray(listOf(release("v3"), release("error"), release("slow"))),
            1, 26, requestTimeoutMs = 20) { url, _ ->
            when {
                "/error/" in url -> throw IOException("later failure")
                "/slow/" in url -> { delay(100); metadata(2) }
                else -> metadata(3)
            }
        }
        assertEquals(3L, selected?.versionCode)
    }

    @Test fun preservesCandidateWhenOverallSelectionBudgetExpires() = runBlocking {
        val selected = selectAppUpdate(JsonArray(listOf(release("v3"), release("slow"))),
            1, 26, requestTimeoutMs = 1_000, selectionTimeoutMs = 50) { url, _ ->
            if ("/slow/" in url) delay(200)
            metadata(if ("/slow/" in url) 2 else 3)
        }
        assertEquals(3L, selected?.versionCode)
    }

    @Test fun propagatesLifecycleCancellation() = runBlocking {
        try {
            selectAppUpdate(JsonArray(listOf(release("v2"))), 1, 26) { _, _ ->
                throw CancellationException("lifecycle cancelled")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun cacheDecisionsUseConditionsAttemptTimeAndExactDayBoundary() {
        val candidate = requireNotNull(parseAppUpdate(metadata(), "$base/tag/v2"))
        val state = UpdateCacheState(updateConditions(1, 26), 1_000, candidate, 0, 0)
        assertFalse(decideUpdateCache(state, 1, 26, 86_400_999).shouldFetch)
        assertEquals(candidate, decideUpdateCache(state, 1, 26, 86_400_999).candidate)
        assertTrue(decideUpdateCache(state, 1, 26, 86_401_000).shouldFetch)
        assertNull(decideUpdateCache(state, 1, 26, 86_401_000).candidate)
        assertTrue(decideUpdateCache(state, 2, 26, 1_001).shouldFetch)
        assertTrue(decideUpdateCache(state, 1, 27, 1_001).shouldFetch)
        assertTrue(decideUpdateCache(state.copy(checkedAt = 0), 1, 26, 1_001).shouldFetch)
        val failedAttempt = decideUpdateCache(state.copy(candidate = null), 1, 26, 1_001)
        assertFalse(failedAttempt.shouldFetch)
        assertNull(failedAttempt.candidate)
    }

    @Test fun postponementSuppressesOnlySameVersionUntilExactBoundary() {
        val candidate = requireNotNull(parseAppUpdate(metadata(), "$base/tag/v2"))
        val state = UpdateCacheState(updateConditions(1, 26), 2_000, candidate, 2, 1_000)
        assertNull(decideUpdateCache(state, 1, 26, 86_400_999).candidate)
        assertEquals(candidate, decideUpdateCache(state, 1, 26, 86_401_000).candidate)
        val newer = candidate.copy(versionCode = 3)
        assertEquals(newer, decideUpdateCache(state.copy(candidate = newer), 1, 26, 2_001).candidate)
        assertNull(visibleUpdate(candidate, 2, 26, 2_001, 0, 0))
        assertNull(visibleUpdate(candidate, 1, 25, 2_001, 0, 0))
    }

    @Test fun rejectsByteExcessBeforeUtf8DecodingEvenForMultibyteText() {
        val bytes = "가".toByteArray(Charsets.UTF_8)
        assertEquals("가", decodeBoundedUpdateBytes(bytes, 3))
        try {
            decodeBoundedUpdateBytes(bytes, 2)
            fail("Three bytes exceed the two-byte limit despite decoding to one character")
        } catch (_: IllegalArgumentException) { }
    }
}
