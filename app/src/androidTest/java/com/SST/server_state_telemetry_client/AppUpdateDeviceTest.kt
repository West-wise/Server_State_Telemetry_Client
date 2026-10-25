package com.SST.server_state_telemetry_client

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.SST.server_state_telemetry_client.data.repository.AppUpdateInstaller
import com.SST.server_state_telemetry_client.data.repository.AppUpdateRepository
import com.SST.server_state_telemetry_client.domain.model.AppUpdate
import com.SST.server_state_telemetry_client.domain.model.downloadUrl
import com.SST.server_state_telemetry_client.presentation.viewmodel.AppUpdateState
import com.SST.server_state_telemetry_client.presentation.viewmodel.AppUpdateViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val page = "https://github.com/West-wise/Server_State_Telemetry_Client/releases/tag/device-test"

    private fun metadata(file: File): AppUpdate {
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(file.path, 0))
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return AppUpdate(PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty(),
            requireNotNull(info.applicationInfo).minSdkVersion, "sstc-release.apk", sha, page)
    }

    // Only transport is substituted. Real APKs, PackageManager, digest checks, cache and FileProvider run on Android.
    private fun client(apk: ByteArray, update: AppUpdate, status: HttpStatusCode = HttpStatusCode.OK,
                       stallAsset: Boolean = false): HttpClient =
        HttpClient(object : HttpClientEngineBase("update-device-fixture") {
            override val dispatcher = Dispatchers.IO
            override val config = HttpClientEngineConfig()
            @OptIn(io.ktor.util.InternalAPI::class)
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                val url = data.url.toString()
                if (stallAsset && url == update.downloadUrl()) awaitCancellation()
                val bytes = when {
                    url.startsWith("https://api.github.com/repos/West-wise/Server_State_Telemetry_Client/releases?") ->
                        """[{"draft":false,"prerelease":false,"html_url":"$page","assets":[{"name":"sstc-update.json","browser_download_url":"${page.replace("/tag/", "/download/")}/sstc-update.json"},{"name":"sstc-release.apk","browser_download_url":"${update.downloadUrl()}"}]}]""".toByteArray()
                    url.endsWith("/sstc-update.json") ->
                        """{"schemaVersion":1,"versionCode":${update.versionCode},"versionName":"${update.versionName}","minSdkVersion":${update.minSdkVersion},"apkAssetName":"${update.apkAssetName}","sha256":"${update.sha256}"}""".toByteArray()
                    url == update.downloadUrl() -> apk
                    else -> error("Unexpected update request")
                }
                return HttpResponseData(status, GMTDate(), Headers.build { append("Content-Length", bytes.size.toString()) },
                    HttpProtocolVersion.HTTP_1_1, ByteReadChannel(bytes), callContext())
            }
        })

    @Test fun rejectsInstalledVersionAndRemovesPartialDownload() = runBlocking {
        val apk = File(context.applicationInfo.sourceDir)
        val update = metadata(apk)
        client(apk.readBytes(), update).use { client ->
            val result = runCatching { AppUpdateInstaller(context, client).download(update) { _, _ -> } }
            assertTrue(result.isFailure)
            assertTrue(File(context.cacheDir, "app-updates").listFiles().orEmpty().none { it.extension == "part" })
        }
    }

    @Test fun rejectsTamperedApkAndHttpFailure() = runBlocking {
        val update = metadata(File(context.applicationInfo.sourceDir)).let { it.copy(versionCode = it.versionCode + 1) }
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)) {
            client("corrupt apk".toByteArray(), update, status).use { client ->
                assertTrue(runCatching { AppUpdateInstaller(context, client).download(update) { _, _ -> } }.isFailure)
            }
        }
        assertTrue(File(context.cacheDir, "app-updates").listFiles().orEmpty().none { it.extension == "part" })
    }

    @Test fun cancelledDownloadDoesNotReopenDialogAndRemovesPartialFile() = runBlocking {
        val update = metadata(File(context.applicationInfo.sourceDir)).let { it.copy(versionCode = it.versionCode + 1) }
        context.getSharedPreferences("sstc_app_update", Context.MODE_PRIVATE).edit().clear().commit()
        client(byteArrayOf(), update, stallAsset = true).use { client ->
            val model = AppUpdateViewModel(AppUpdateRepository(context, client), AppUpdateInstaller(context, client))
            withContext(Dispatchers.Main) { model.check(update.versionCode - 1, android.os.Build.VERSION.SDK_INT) }
            withTimeout(5_000) {
                while (withContext(Dispatchers.Main) { model.state !is AppUpdateState.Available }) delay(10)
            }
            withContext(Dispatchers.Main) { model.download() }
            withTimeout(5_000) {
                while (File(context.cacheDir, "app-updates").listFiles().orEmpty().none { it.extension == "part" }) delay(10)
            }
            withContext(Dispatchers.Main) { model.dismiss() }
            withTimeout(5_000) {
                while (File(context.cacheDir, "app-updates").listFiles().orEmpty().any { it.extension == "part" }) delay(10)
            }
            assertNull(withContext(Dispatchers.Main) { model.state })
        }
    }

    @Test fun detectsDownloadsAndPreparesNewVersionForInstallation() = runBlocking {
        val fixtureName = InstrumentationRegistry.getArguments().getString("updateFixture")
        assumeTrue("Supply a newer APK using the documented device verification procedure", fixtureName != null)
        val fixture = File(context.getExternalFilesDir(null), requireNotNull(fixtureName))
        assertTrue("Newer APK fixture must exist", fixture.isFile)
        val expected = metadata(fixture)
        val installed = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        assertTrue(expected.versionCode > installed)
        context.getSharedPreferences("sstc_app_update", Context.MODE_PRIVATE).edit().clear().commit()
        client(fixture.readBytes(), expected).use { client ->
            val update = requireNotNull(AppUpdateRepository(context, client).check(installed, android.os.Build.VERSION.SDK_INT))
            assertEquals(expected, update)
            val installer = AppUpdateInstaller(context, client)
            val file = installer.download(update) { _, _ -> }
            val intent = installer.installIntent(file, update)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(intent.data, intent.clipData?.getItemAt(0)?.uri)
            context.contentResolver.openInputStream(requireNotNull(intent.data)).use { stream ->
                assertArrayEquals(fixture.readBytes(), requireNotNull(stream).readBytes())
            }
            context.getSharedPreferences("update_flow_test", Context.MODE_PRIVATE).edit()
                .putString("data_retention", "sstc-update-sentinel").commit()
            if (InstrumentationRegistry.getArguments().getString("launchInstaller") == "true") {
                // Start from the real foreground Activity, as the app does; background launches may be blocked.
                val scenario = ActivityScenario.launch(MainActivity::class.java)
                scenario.onActivity { it.startActivity(intent) }
                val installerPackage = requireNotNull(intent.resolveActivity(context.packageManager)).packageName
                val installerUid = context.packageManager.getApplicationInfo(installerPackage, 0).uid
                assertEquals("Installer must receive read access", android.content.pm.PackageManager.PERMISSION_GRANTED,
                    context.checkUriPermission(intent.data, -1, installerUid, Intent.FLAG_GRANT_READ_URI_PERMISSION))
                try {
                    withTimeout(15_000) {
                        while (true) {
                            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                            if (listOf("Settings", "Update", "Install").any {
                                root?.findAccessibilityNodeInfosByText(it).orEmpty().isNotEmpty()
                            }) break
                            delay(50)
                        }
                    }
                } finally { scenario.close() }
            }
        }
    }
}
