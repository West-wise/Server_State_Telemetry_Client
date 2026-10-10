package com.SST.server_state_telemetry_client.domain.model

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private const val REPOSITORY = "/West-wise/Server_State_Telemetry_Client"

@Serializable
internal data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val minSdkVersion: Int,
    val apkAssetName: String,
    val sha256: String,
    val releaseUrl: String
)

internal fun validReleaseUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.rawPath.startsWith("$REPOSITORY/releases/tag/") &&
        uri.rawPath.removePrefix("$REPOSITORY/releases/tag/").let {
            it.isNotEmpty() && !it.contains('/') && it != "." && it != ".."
        }
} catch (_: Exception) { false }

internal fun validAssetUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.rawPath.startsWith("$REPOSITORY/releases/download/") &&
        uri.rawPath.removePrefix("$REPOSITORY/releases/download/").split('/').let {
            it.size == 2 && it.all { part -> part.isNotEmpty() && part != "." && part != ".." }
        }
} catch (_: Exception) { false }

internal fun AppUpdate.isValid(): Boolean = versionCode in 1..2_100_000_000L &&
    versionName.isNotBlank() && versionName.length <= 128 && versionName.none { it.isISOControl() } &&
    minSdkVersion > 0 && apkAssetName.endsWith(".apk") && apkAssetName.length <= 255 &&
    apkAssetName.none { it == '/' || it == '\\' || it.isISOControl() } &&
    sha256.matches(Regex("[0-9a-fA-F]{64}")) && validReleaseUrl(releaseUrl)

internal fun parseAppUpdate(text: String, releaseUrl: String): AppUpdate? = try {
    val json = Json.parseToJsonElement(text).jsonObject
    fun number(key: String): Long? = (json[key] as? JsonPrimitive)?.let {
        if (it.isString || !it.content.matches(Regex("[0-9]+"))) null else it.longOrNull
    }
    fun string(key: String): String? = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (number("schemaVersion") != 1L) null else {
        val sdk = number("minSdkVersion")
        if (sdk == null || sdk !in 1..Int.MAX_VALUE.toLong()) null else AppUpdate(
            number("versionCode") ?: 0, string("versionName") ?: "", sdk.toInt(),
            string("apkAssetName") ?: "", string("sha256") ?: "", releaseUrl
        ).takeIf { it.isValid() }
    }
} catch (_: Exception) { null }

internal fun withinUpdateDay(now: Long, previous: Long): Boolean =
    previous > 0 && (now < previous || now - previous < 86_400_000L)

internal fun AppUpdate.isNewSupportedVersion(installedCode: Long, sdk: Int): Boolean =
    isValid() && versionCode > installedCode && sdk >= minSdkVersion
