package com.SST.server_state_telemetry_client.data.repository

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.SST.server_state_telemetry_client.domain.model.AppUpdate
import com.SST.server_state_telemetry_client.domain.model.UpdateApkIdentity
import com.SST.server_state_telemetry_client.domain.model.downloadUrl
import com.SST.server_state_telemetry_client.domain.model.validateUpdateApk
import com.SST.server_state_telemetry_client.data.remote.APP_UPDATE_DOWNLOAD_TIMEOUT_MILLIS
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
internal class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("appUpdateDownload") private val client: HttpClient
) {
    private val downloadMutex = Mutex()

    suspend fun download(update: AppUpdate, progress: suspend (Long, Long?) -> Unit): File =
        withContext(Dispatchers.IO) {
            downloadMutex.withLock {
                val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
                // Recover partial files left by process death; this singleton serializes downloads.
                directory.listFiles().orEmpty().filter { it.name.startsWith("download-") && it.extension == "part" }
                    .forEach { check(it.delete()) }
                val partial = File.createTempFile("download-", ".part", directory)
                try {
                    withTimeout(APP_UPDATE_DOWNLOAD_TIMEOUT_MILLIS) {
                        client.prepareGet(update.downloadUrl()).execute { response ->
                            require(response.status.value == 200)
                            val total = response.headers["Content-Length"]?.toLong()?.also {
                                require(it in 1..MAX_APK_BYTES)
                            }
                            val channel = response.bodyAsChannel()
                            try {
                                partial.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var received = 0L
                                    var reported = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = channel.readAvailable(buffer)
                                        if (count < 0) break
                                        received += count
                                        require(received <= MAX_APK_BYTES)
                                        output.write(buffer, 0, count)
                                        if (received - reported >= 256 * 1024) {
                                            progress(received, total)
                                            reported = received
                                        }
                                    }
                                    require(received > 0 && (total == null || received == total))
                                    progress(received, total)
                                }
                            } finally { channel.cancel(null) }
                        }
                        verify(partial, update)
                        currentCoroutineContext().ensureActive()
                        val apk = File(directory, "update.apk")
                        context.revokeUriPermission(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        check(!apk.exists() || apk.delete())
                        check(partial.renameTo(apk))
                        apk
                    }
                } finally { partial.delete() }
            }
        }

    suspend fun installIntent(file: File, update: AppUpdate): Intent = withContext(Dispatchers.IO) {
        require(file.canonicalFile == File(context.cacheDir, "app-updates/update.apk").canonicalFile)
        verify(file, update)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("SSTC update", uri) }
        val activity = intent.resolveActivity(context.packageManager)
            ?: throw android.content.ActivityNotFoundException()
        context.grantUriPermission(activity.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent
    }

    private fun verify(file: File, update: AppUpdate) {
        require(file.length() in 1..MAX_APK_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val installed = manager.getPackageInfo(context.packageName, flags)
        val archive = requireNotNull(manager.getPackageArchiveInfo(file.path, flags))
        validateUpdateApk(update, identity(installed), identity(archive), hex(digest.digest()), Build.VERSION.SDK_INT)
    }

    private fun identity(info: PackageInfo): UpdateApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return UpdateApkIdentity(
            info.packageName, PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty(),
            requireNotNull(info.applicationInfo).minSdkVersion,
            signatures.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
        )
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_APK_BYTES = 512L * 1024 * 1024
    }
}
