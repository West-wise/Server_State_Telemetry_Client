package com.SST.server_state_telemetry_client.domain.model

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private val page = "https://github.com/West-wise/Server_State_Telemetry_Client/releases/tag/v2"
    private val metadata = """{"schemaVersion":1,"versionCode":2,"versionName":"2.0","minSdkVersion":26,"apkAssetName":"sstc.apk","sha256":"${"a".repeat(64)}"}"""

    @Test fun validatesNumericMetadataAndPreservesHash() {
        val update = requireNotNull(parseAppUpdate(metadata, page))
        assertEquals(2L, update.versionCode)
        assertEquals("a".repeat(64), update.sha256)
        for (invalid in listOf("0", "-1", "2.5", "\"2\"", "2100000001", "9223372036854775808")) {
            assertNull(parseAppUpdate(metadata.replace("\"versionCode\":2", "\"versionCode\":$invalid"), page))
        }
        assertNull(parseAppUpdate(metadata.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), page))
        assertNull(parseAppUpdate(metadata.replace("\"minSdkVersion\":26", "\"minSdkVersion\":0"), page))
        assertNull(parseAppUpdate(metadata.replace("a".repeat(64), "xyz"), page))
        assertNull(parseAppUpdate("{}", page))
        assertNull(parseAppUpdate(metadata.dropLast(1), page))
    }

    @Test fun comparesVersionCodesAndChecksAndroidApi() {
        val update = requireNotNull(parseAppUpdate(metadata, page))
        assertTrue(update.isNewSupportedVersion(1, 26))
        assertFalse(update.isNewSupportedVersion(2, 26))
        assertFalse(update.isNewSupportedVersion(3, 26))
        assertFalse(update.isNewSupportedVersion(1, 25))
        assertTrue(update.copy(versionName = "0.1").isNewSupportedVersion(1, 35))
    }

    @Test fun restrictsBrowserAndAssetLinksToRepository() {
        assertTrue(validReleaseUrl(page))
        assertTrue(validAssetUrl(page.replace("/tag/", "/download/") + "/sstc.apk"))
        for (url in listOf(page.replace("https:", "http:"), page.replace("github.com", "github.com.evil"),
            page.replace("West-wise", "Other"), page.replace("github.com", "user@github.com"),
            "$page?next=https://evil.example", "$page#fragment", "$page/extra")) {
            assertFalse(url, validReleaseUrl(url))
            assertNull(parseAppUpdate(metadata, url))
        }
    }

    @Test fun cacheAndPostponementExpireAtTwentyFourHours() {
        assertTrue(withinUpdateDay(1001L, 1000L))
        assertTrue(withinUpdateDay(86_400_999L, 1000L))
        assertFalse(withinUpdateDay(86_401_000L, 1000L))
        assertFalse(withinUpdateDay(1000L, 0L))
        assertTrue(withinUpdateDay(999L, 1000L))
    }
}
