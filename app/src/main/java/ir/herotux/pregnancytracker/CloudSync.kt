package ir.herotux.pregnancytracker

import android.accounts.Account
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val Context.cloudStore by preferencesDataStore(name = "pregnancy_cloud")
private val CloudFileIdKey = stringPreferencesKey("drive_file_id")
private val CloudEmailKey = stringPreferencesKey("drive_account_email")
private val CloudNotesKey = stringPreferencesKey("sync_notes")
private val CloudUpdatedKey = longPreferencesKey("sync_updated_at")
private val LocalDueKey = stringPreferencesKey("local_due_date")

data class CloudSyncResult(
    val dueDate: LocalDate,
    val notes: List<String>,
    val updatedAt: Long
)

private data class CloudPayload(
    val dueDate: String,
    val notes: List<String>,
    val updatedAt: Long
)

object DriveSync {
    const val DRIVE_SCOPE = Scopes.DRIVE_FILE
    private const val FILE_NAME = "PregnancyTracker-Shared-Data.json"
    private const val MIME = "application/json"
    private const val BASE = "https://www.googleapis.com"
    private const val UNIQUE_WORK = "pregnancy-drive-sync"

    suspend fun sync(context: Context, accessToken: String, dueDate: LocalDate, notes: List<String>, localUpdatedAt: Long): CloudSyncResult =
        withContext(Dispatchers.IO) {
            val existingId = context.cloudStore.data.first()[CloudFileIdKey]
            val fileId = existingId ?: findFile(accessToken) ?: createFile(
                accessToken,
                CloudPayload(dueDate.toString(), notes.distinct(), localUpdatedAt)
            ).also { id -> context.cloudStore.edit { it[CloudFileIdKey] = id } }

            val remote = runCatching { download(accessToken, fileId) }.getOrNull()
            if (remote == null) {
                upload(accessToken, fileId, CloudPayload(dueDate.toString(), notes.distinct(), localUpdatedAt))
                return@withContext CloudSyncResult(dueDate, notes.distinct(), localUpdatedAt)
            }

            val mergedNotes = (notes + remote.notes).filter { it.isNotBlank() }.distinct()
            val mergedDue = if (remote.updatedAt > localUpdatedAt) LocalDate.parse(remote.dueDate) else dueDate
            val mergedUpdated = maxOf(remote.updatedAt, localUpdatedAt, System.currentTimeMillis())

            upload(accessToken, fileId, CloudPayload(mergedDue.toString(), mergedNotes, mergedUpdated))
            context.cloudStore.edit {
                it[CloudUpdatedKey] = mergedUpdated
                it[CloudNotesKey] = JSONArray(mergedNotes).toString()
                it[LocalDueKey] = mergedDue.toString()
            }
            CloudSyncResult(mergedDue, mergedNotes, mergedUpdated)
        }

    suspend fun shareWith(context: Context, accessToken: String, email: String) = withContext(Dispatchers.IO) {
        val fileId = context.cloudStore.data.first()[CloudFileIdKey]
            ?: findFile(accessToken)
            ?: throw IllegalStateException("ابتدا همگام‌سازی را فعال کنید.")
        share(accessToken, fileId, email)
    }

    suspend fun connectAndSync(context: Context, accountEmail: String, accessToken: String, dueDate: LocalDate, notes: List<String>, localUpdatedAt: Long): CloudSyncResult {
        context.cloudStore.edit {
            it[CloudEmailKey] = accountEmail
            it[LocalDueKey] = dueDate.toString()
        }
        scheduleBackgroundSync(context)
        return sync(context, accessToken, dueDate, notes, localUpdatedAt)
    }

    suspend fun saveLocalSnapshot(context: Context, dueDate: LocalDate, notes: List<String>, updatedAt: Long) {
        context.cloudStore.edit {
            it[CloudNotesKey] = JSONArray(notes.distinct()).toString()
            it[CloudUpdatedKey] = updatedAt
            it[LocalDueKey] = dueDate.toString()
        }
    }

    suspend fun readLocalSnapshot(context: Context): Pair<List<String>, Long> {
        val notes = context.cloudStore.data.first()[CloudNotesKey]?.let { raw ->
            runCatching {
                val a = JSONArray(raw)
                buildList { for (i in 0 until a.length()) add(a.optString(i)) }
            }.getOrNull()
        } ?: emptyList()
        return notes to (context.cloudStore.data.first()[CloudUpdatedKey] ?: 0L)
    }

    suspend fun connectedEmail(context: Context): String? =
        context.cloudStore.data.first()[CloudEmailKey]

    fun scheduleBackgroundSync(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun findFile(token: String): String? {
        val q = "properties has { key='pregnancyTracker' and value='shared' } and trashed=false"
        val url = BASE + "/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") +
            "&spaces=drive&corpora=user&pageSize=10&fields=files(id,name,modifiedTime)"
        return request("GET", url, token).optJSONArray("files")?.optJSONObject(0)?.optString("id")
    }

    private fun createFile(token: String, payload: CloudPayload): String {
        val metadata = JSONObject().put("name", FILE_NAME).put("mimeType", MIME)
            .put("properties", JSONObject().put("pregnancyTracker", "shared"))
        val boundary = "----PregnancyTracker" + System.currentTimeMillis()
        val body = multipartBody(boundary, metadata.toString(), payloadJson(payload).toString().toByteArray(StandardCharsets.UTF_8))
        return requestBytes("POST", "$BASE/upload/drive/v3/files?uploadType=multipart&fields=id,name",
            token, body, "multipart/related; boundary=$boundary").let { JSONObject(String(it, StandardCharsets.UTF_8)).getString("id") }
    }

    private fun download(token: String, fileId: String): CloudPayload =
        payloadFromJson(request("GET", "$BASE/drive/v3/files/$fileId?alt=media", token))

    private fun upload(token: String, fileId: String, payload: CloudPayload) {
        requestBytes("PATCH", "$BASE/upload/drive/v3/files/$fileId?uploadType=media", token,
            payloadJson(payload).toString().toByteArray(StandardCharsets.UTF_8), MIME)
    }

    private fun share(token: String, fileId: String, email: String) {
        val permission = JSONObject().put("type", "user").put("role", "writer").put("emailAddress", email.trim())
        requestBytes("POST", "$BASE/drive/v3/files/$fileId/permissions?sendNotificationEmail=true&fields=id,emailAddress,role",
            token, permission.toString().toByteArray(StandardCharsets.UTF_8), "application/json")
    }

    private fun payloadJson(payload: CloudPayload) = JSONObject()
        .put("schema", 1).put("dueDate", payload.dueDate)
        .put("notes", JSONArray(payload.notes)).put("updatedAt", payload.updatedAt)

    private fun payloadFromJson(json: JSONObject): CloudPayload {
        val array = json.optJSONArray("notes") ?: JSONArray()
        val notes = buildList { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
        return CloudPayload(json.optString("dueDate", "2027-02-04"), notes, json.optLong("updatedAt", 0L))
    }

    private fun multipartBody(boundary: String, metadata: String, data: ByteArray): ByteArray {
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: $MIME\r\n\r\n"
        val suffix = "\r\n--$boundary--\r\n"
        return prefix.toByteArray(StandardCharsets.UTF_8) + data + suffix.toByteArray(StandardCharsets.UTF_8)
    }

    private fun request(method: String, url: String, token: String): JSONObject =
        JSONObject(String(requestBytes(method, url, token, null, null), StandardCharsets.UTF_8))

    private fun requestBytes(method: String, url: String, token: String, body: ByteArray?, contentType: String?): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: MIME)
                setFixedLengthStreamingMode(body.size)
            }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            if (code !in 200..299) throw IllegalStateException("Google Drive error $code: " + String(bytes, StandardCharsets.UTF_8))
            bytes
        } finally {
            connection.disconnect()
        }
    }
}

class DriveSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val email = applicationContext.cloudStore.data.first()[CloudEmailKey] ?: return@withContext Result.success()
        val due = applicationContext.cloudStore.data.first()[LocalDueKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(2027, 2, 4)
        val (notes, updated) = DriveSync.readLocalSnapshot(applicationContext)
        val account = Account(email, "com.google")
        val request = AuthorizationRequest.builder().setAccount(account)
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope(DriveSync.DRIVE_SCOPE))).build()
        val result = runCatching {
            Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(request))
        }.getOrNull() ?: return@withContext Result.retry()
        if (result.hasResolution()) return@withContext Result.success()
        val token = result.accessToken ?: return@withContext Result.success()
        runCatching { DriveSync.sync(applicationContext, token, due, notes, updated) }
            .fold({ Result.success() }, { Result.retry() })
    }
}
