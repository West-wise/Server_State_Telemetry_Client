package com.SST.server_state_telemetry_client.domain.model

import org.junit.Assert.*
import org.junit.Test

class UpdateApkValidationTest {
    private val hash = "ab".repeat(32)
    private val page = "https://github.com/West-wise/Server_State_Telemetry_Client/releases/tag/v1.1.0-2"
    private val update = AppUpdate(2, "1.1.0", 26, "sstc-release.apk", hash, page)
    private val installed = UpdateApkIdentity("com.SST.server_state_telemetry_client", 1, "1.0", 26, setOf("trusted"))
    private val archive = installed.copy(versionCode = 2, versionName = "1.1.0")

    @Test fun acceptsMatchingNewApkAndCaseInsensitiveDigest() {
        validateUpdateApk(update, installed, archive, hash.uppercase(), 26)
    }

    @Test fun rejectsTamperedDigestWrongPackageVersionNameSdkAndSigner() {
        for (wrong in listOf(
            archive.copy(packageName = "other.app"), archive.copy(versionCode = 3),
            archive.copy(versionName = "other"), archive.copy(minSdkVersion = 27),
            archive.copy(signers = setOf("other")), archive.copy(signers = emptySet()),
            archive.copy(signers = setOf("trusted", "other"))
        )) {
            assertThrows(IllegalArgumentException::class.java) { validateUpdateApk(update, installed, wrong, hash, 35) }
        }
        assertThrows(IllegalArgumentException::class.java) { validateUpdateApk(update, installed, archive, "00".repeat(32), 35) }
    }

    @Test fun rejectsSameVersionDowngradeUnsupportedSdkAndMissingInstalledSigner() {
        for (current in listOf(installed.copy(versionCode = 2), installed.copy(versionCode = 3),
            installed.copy(signers = emptySet()))) {
            assertThrows(IllegalArgumentException::class.java) { validateUpdateApk(update, current, archive, hash, 35) }
        }
        assertThrows(IllegalArgumentException::class.java) { validateUpdateApk(update, installed, archive, hash, 25) }
    }

    @Test fun derivesAssetFromTheSameOfficialReleaseAndEncodesFilename() {
        assertEquals(page.replace("/tag/", "/download/") + "/sstc-release.apk", update.downloadUrl())
        assertEquals(page.replace("/tag/", "/download/") + "/sstc%20release.apk",
            update.copy(apkAssetName = "sstc release.apk").downloadUrl())
        assertThrows(IllegalArgumentException::class.java) { update.copy(releaseUrl = "https://evil.example/tag/v2").downloadUrl() }
    }
}
