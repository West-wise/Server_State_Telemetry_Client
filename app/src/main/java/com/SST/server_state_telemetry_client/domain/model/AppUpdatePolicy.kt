package com.SST.server_state_telemetry_client.domain.model

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

internal data class UpdateCacheState(
    val conditions: String?,
    val checkedAt: Long,
    val candidate: AppUpdate?,
    val dismissedCode: Long,
    val dismissedAt: Long
)

internal data class UpdateCacheDecision(val shouldFetch: Boolean, val candidate: AppUpdate?)

internal fun updateConditions(installedCode: Long, sdk: Int): String = "$installedCode:$sdk:1"

internal fun visibleUpdate(
    candidate: AppUpdate?, installedCode: Long, sdk: Int, now: Long,
    dismissedCode: Long, dismissedAt: Long
): AppUpdate? = candidate?.takeIf {
    it.isNewSupportedVersion(installedCode, sdk) &&
        !(it.versionCode == dismissedCode && withinUpdateDay(now, dismissedAt))
}

internal fun decideUpdateCache(
    state: UpdateCacheState, installedCode: Long, sdk: Int, now: Long
): UpdateCacheDecision {
    val shouldFetch = state.conditions != updateConditions(installedCode, sdk) ||
        !withinUpdateDay(now, state.checkedAt)
    return UpdateCacheDecision(shouldFetch, if (shouldFetch) null else visibleUpdate(
        state.candidate, installedCode, sdk, now, state.dismissedCode, state.dismissedAt
    ))
}

// Only this scope's timeout becomes a failed request; parent cancellation always propagates.
internal suspend fun <T> updateRequest(timeoutMs: Long = 5_000L, block: suspend () -> T): T? =
    withTimeoutOrNull(timeoutMs) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

internal fun decodeBoundedUpdateBytes(bytes: ByteArray, limit: Int): String {
    require(bytes.size <= limit)
    return bytes.toString(Charsets.UTF_8)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun pairedAsset(url: String, page: String, name: String): Boolean {
    if (!validAssetUrl(url)) return false
    val asset = URI(url)
    return asset.rawPath.substringBeforeLast('/').substringAfter("/releases/download/") ==
        URI(page).rawPath.substringAfter("/releases/tag/") &&
        asset.path.substringAfterLast('/') == name
}

// Select the highest supported numeric version among the recent official releases.
// A later failed request or our overall budget expiry never discards an earlier candidate.
internal suspend fun selectAppUpdate(
    releases: JsonArray, installedCode: Long, sdk: Int,
    requestTimeoutMs: Long = 5_000L, selectionTimeoutMs: Long = 15_000L,
    loadMetadata: suspend (String, Int) -> String
): AppUpdate? {
    var best: AppUpdate? = null
    withTimeoutOrNull(selectionTimeoutMs) {
        for (element in releases) {
            val release = element as? JsonObject ?: continue
            if ((release["draft"] as? JsonPrimitive)?.booleanOrNull != false ||
                (release["prerelease"] as? JsonPrimitive)?.booleanOrNull != false) continue
            val page = release.string("html_url") ?: continue
            if (!validReleaseUrl(page)) continue
            val assets = (release["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: continue
            val metadata = assets.singleOrNull { it.string("name") == "sstc-update.json" } ?: continue
            val metadataUrl = metadata.string("browser_download_url") ?: continue
            if (!pairedAsset(metadataUrl, page, "sstc-update.json")) continue
            val text = updateRequest(requestTimeoutMs) { loadMetadata(metadataUrl, 16_384) } ?: continue
            val update = parseAppUpdate(text, page) ?: continue
            val apk = assets.singleOrNull { it.string("name") == update.apkAssetName } ?: continue
            val apkUrl = apk.string("browser_download_url") ?: continue
            if (!pairedAsset(apkUrl, page, update.apkAssetName)) continue
            if (update.isNewSupportedVersion(installedCode, sdk) &&
                update.versionCode > (best?.versionCode ?: 0)) best = update
        }
    }
    return best
}
