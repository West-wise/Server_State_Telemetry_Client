package com.SST.server_state_telemetry_client.domain.model

internal data class UpdateApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdkVersion: Int,
    val signers: Set<String>
)

internal fun validateUpdateApk(
    update: AppUpdate,
    installed: UpdateApkIdentity,
    archive: UpdateApkIdentity,
    sha256: String,
    sdk: Int
) {
    require(update.isNewSupportedVersion(installed.versionCode, sdk))
    require(sha256.equals(update.sha256, ignoreCase = true))
    require(archive.packageName == installed.packageName)
    require(archive.versionCode == update.versionCode && archive.versionName == update.versionName)
    require(archive.minSdkVersion == update.minSdkVersion && sdk >= archive.minSdkVersion)
    // Release packaging also rejects signer rotation and multiple signers.
    require(installed.signers.size == 1 && archive.signers == installed.signers)
}
