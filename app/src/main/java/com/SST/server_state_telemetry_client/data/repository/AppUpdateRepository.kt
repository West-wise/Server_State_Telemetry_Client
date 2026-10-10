package com.SST.server_state_telemetry_client.data.repository

import android.content.Context
import com.SST.server_state_telemetry_client.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

@Singleton
internal class AppUpdateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: HttpClient
) {
    private val preferences = context.getSharedPreferences("sstc_app_update", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(installedCode: Long, sdk: Int): AppUpdate? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val storedCandidate = try {
                preferences.getString("candidate", null)?.let { json.decodeFromString<AppUpdate>(it) }
            } catch (_: Exception) { null }
            val state = UpdateCacheState(
                preferences.getString("conditions", null), preferences.getLong("checked_at", 0),
                storedCandidate, preferences.getLong("dismissed_code", 0),
                preferences.getLong("dismissed_at", 0)
            )
            val decision = decideUpdateCache(state, installedCode, sdk, now)
            if (!decision.shouldFetch) return@withLock decision.candidate
            // Record failed attempts too, so foreground events cannot hammer the API.
            preferences.edit().putString("conditions", updateConditions(installedCode, sdk))
                .putLong("checked_at", now).remove("candidate").apply()
            val candidate = fetch(installedCode, sdk)
            preferences.edit().putString("candidate", candidate?.let { json.encodeToString(it) }).apply()
            visibleUpdate(candidate, installedCode, sdk, System.currentTimeMillis(),
                preferences.getLong("dismissed_code", 0), preferences.getLong("dismissed_at", 0))
        }
    }

    fun dismiss(update: AppUpdate) {
        preferences.edit().putLong("dismissed_code", update.versionCode)
            .putLong("dismissed_at", System.currentTimeMillis()).apply()
    }

    private suspend fun getText(url: String, limit: Int): String =
        client.prepareGet(url) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "SSTC-update-check")
        }.execute { response ->
            val channel = response.bodyAsChannel()
            try {
                require(response.status.value == 200)
                val packet = channel.readRemaining(limit.toLong() + 1)
                val bytes = try { packet.readBytes() } finally { packet.close() }
                decodeBoundedUpdateBytes(bytes, limit)
            } finally { channel.cancel(null) }
        }

    private suspend fun fetch(installedCode: Long, sdk: Int): AppUpdate? {
        val releases = updateRequest {
            json.parseToJsonElement(getText(
                "https://api.github.com/repos/West-wise/Server_State_Telemetry_Client/releases?per_page=100",
                2_000_000
            )).jsonArray
        } ?: return null
        return selectAppUpdate(releases, installedCode, sdk, loadMetadata = ::getText)
    }
}
