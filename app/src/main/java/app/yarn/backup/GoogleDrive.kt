package app.yarn.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/** A backup stored in the user's Google Drive. */
data class DriveBackup(val id: String, val modifiedAt: Long, val sizeBytes: Long, val messageCount: Int)

/** Thrown when Google needs the user to pick an account or grant access on screen. */
class NeedsConsentException(val pendingIntent: PendingIntent) : Exception("Google sign-in needed")

/**
 * Backs up to the hidden, app-only folder of the user's own Google Drive (the `drive.appdata`
 * scope). Files there count against the user's free Drive storage, are invisible in the Drive UI,
 * and can only be read by Yarn. The Drive API is free, so this costs neither the user nor us.
 */
class GoogleDrive(private val context: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_APPDATA), Scope(SCOPE_EMAIL)))
        .build()

    /**
     * Returns an access token. When the user hasn't granted access yet (or must choose an account)
     * this throws [NeedsConsentException]; the UI launches its intent and calls [tokenFrom].
     */
    suspend fun token(): String {
        val result = try {
            Identity.getAuthorizationClient(context).authorize(request).await()
        } catch (e: com.google.android.gms.common.api.ApiException) {
            throw IOException(
                explain(e.statusCode),
            )
        }
        if (result.hasResolution()) throw NeedsConsentException(requireNotNull(result.pendingIntent))
        return result.accessToken ?: throw IOException("Google didn't return access")
    }

    fun tokenFrom(data: Intent?): String {
        val result: AuthorizationResult = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            throw IOException(explain(e.statusCode), e)
        }
        return result.accessToken ?: throw IOException("Google didn't return access")
    }

    /** Plain-language reason for a failed Google sign-in, with the code for troubleshooting. */
    fun explain(statusCode: Int): String = when (statusCode) {
        com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR ->
            "Google doesn't recognise this copy of Yarn (code 10). In Google Cloud → Clients, add an Android client for " +
                "package ${context.packageName} with SHA-1 ${signingSha1() ?: "(unknown)"}"
        com.google.android.gms.common.api.CommonStatusCodes.CANCELED, 12501 -> "Sign-in was cancelled."
        com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "No internet connection."
        com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> "Please choose a Google account."
        else -> "Google sign-in failed (code $statusCode)."
    }

    suspend fun accountEmail(token: String): String = withContext(Dispatchers.IO) {
        val body = request("GET", "$API/about?fields=user(emailAddress)", token)
        JSONObject(body).getJSONObject("user").getString("emailAddress")
    }

    /** The random key that encrypts this account's backups, created on first use. */
    suspend fun backupKey(token: String): CharArray = withContext(Dispatchers.IO) {
        val existing = find(token, KEY_NAME).firstOrNull()
        val key = if (existing != null) {
            request("GET", "$API/files/${existing.getString("id")}?alt=media", token)
        } else {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val generated = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            uploadBytes(token, KEY_NAME, generated.toByteArray(), "text/plain", emptyMap())
            generated
        }
        key.trim().toCharArray()
    }

    suspend fun latest(token: String): DriveBackup? = withContext(Dispatchers.IO) {
        find(token, BACKUP_NAME).firstOrNull()?.let { f ->
            DriveBackup(
                id = f.getString("id"),
                modifiedAt = runCatching { java.time.Instant.parse(f.getString("modifiedTime")).toEpochMilli() }.getOrDefault(0L),
                sizeBytes = f.optString("size").toLongOrNull() ?: 0L,
                messageCount = f.optJSONObject("appProperties")?.optString("messages")?.toIntOrNull() ?: 0,
            )
        }
    }

    /** Uploads [file] as the new backup, then removes older copies so only the latest is kept. */
    suspend fun upload(token: String, file: File, messageCount: Int) = withContext(Dispatchers.IO) {
        val old = find(token, BACKUP_NAME).map { it.getString("id") }
        val meta = JSONObject()
            .put("name", BACKUP_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put("messages", messageCount.toString()))
        // Resumable upload streams the file instead of holding it in memory.
        val start = open("POST", "$UPLOAD?uploadType=resumable", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            setRequestProperty("X-Upload-Content-Length", file.length().toString())
            outputStream.use { it.write(meta.toString().toByteArray()) }
        }
        check(start)
        val session = start.getHeaderField("Location") ?: throw IOException("Google Drive refused the upload")
        val put = (URL(session).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setFixedLengthStreamingMode(file.length())
            setRequestProperty("Content-Type", "application/octet-stream")
            connectTimeout = 30_000
            readTimeout = 120_000
            file.inputStream().use { input -> outputStream.use { input.copyTo(it, 64 * 1024) } }
        }
        check(put)
        for (id in old) runCatching { request("DELETE", "$API/files/$id", token) }
    }

    suspend fun download(token: String, backup: DriveBackup, target: File) = withContext(Dispatchers.IO) {
        val conn = open("GET", "$API/files/${backup.id}?alt=media", token)
        check(conn)
        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
    }

    private fun find(token: String, name: String): List<JSONObject> {
        val q = URLEncoder.encode("name = '$name' and trashed = false", "UTF-8")
        val body = request(
            "GET",
            "$API/files?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&fields=files(id,size,modifiedTime,appProperties)",
            token,
        )
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { files.getJSONObject(it) }
    }

    private fun uploadBytes(token: String, name: String, bytes: ByteArray, mime: String, props: Map<String, String>) {
        val boundary = "yarn" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
        if (props.isNotEmpty()) meta.put("appProperties", JSONObject(props))
        val conn = open("POST", "$UPLOAD?uploadType=multipart", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            outputStream.use { out ->
                out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
        }
        check(conn)
    }

    private fun request(method: String, url: String, token: String): String {
        val conn = open(method, url, token)
        check(conn)
        return conn.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun open(method: String, url: String, token: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 60_000
        setRequestProperty("Authorization", "Bearer $token")
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) {
            val detail = runCatching { conn.errorStream?.use { it.readBytes().decodeToString() } }.getOrNull().orEmpty()
            throw IOException(
                when (code) {
                    401 -> "Google sign-in expired. Please sign in again."
                    403 -> if ("storageQuota" in detail) "Your Google storage is full." else "Google Drive refused access."
                    else -> "Google Drive error ($code)"
                },
            )
        }
    }

    /** SHA-1 of the certificate this installed copy is signed with (Play's app signing key for Play installs). */
    fun signingSha1(): String? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        java.security.MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val SCOPE_EMAIL = "email"
        private const val BACKUP_NAME = "yarn-backup.yarnbak"
        private const val KEY_NAME = "yarn-backup-key"
    }
}
