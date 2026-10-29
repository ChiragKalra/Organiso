package com.bruhascended.organiso.backup

import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class DriveBackupException(val status: Int) : IOException("Drive request failed ($status)")

data class DriveResponse(val status: Int, val body: String, val location: String? = null)

interface DriveTransport {
    fun request(url: String, method: String, token: String, contentType: String,
                length: Long, write: (OutputStream) -> Unit): DriveResponse
}

class HttpDriveTransport : DriveTransport {
    override fun request(url: String, method: String, token: String, contentType: String,
                         length: Long, write: (OutputStream) -> Unit): DriveResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(length)
            connection.outputStream.use(write)
            val status = connection.responseCode
            if (status !in 200..299) throw DriveBackupException(status)
            return DriveResponse(status, connection.inputStream.bufferedReader().use { it.readText() },
                connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }
}

/** Creates a new private app-data backup each time; never replaces an older backup. */
class DriveBackupClient(private val transport: DriveTransport = HttpDriveTransport()) {
    fun upload(backup: SmsBackup, accessToken: String): String {
        val metadata = JSONObject()
            .put("name", SmsBackupRepository.filename())
            .put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put("smsCount", backup.messageCount.toString())
                .put("sha256", backup.sha256))
            .toString().toByteArray(Charsets.UTF_8)
        val session = transport.request(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,size,md5Checksum",
            "POST", accessToken, "application/json; charset=UTF-8", metadata.size.toLong()
        ) { it.write(metadata) }
        if (session.status !in 200..299) throw DriveBackupException(session.status)
        val location = session.location ?: throw IOException("Missing upload session")
        // Do not send the access token to a redirect or a non-Google host.
        val sessionUrl = URL(location)
        if (sessionUrl.protocol != "https" || sessionUrl.host != "www.googleapis.com") {
            throw IOException("Invalid upload session")
        }
        val response = transport.request(location, "PUT", accessToken, "application/json", backup.file.length()) {
            output -> backup.file.inputStream().use { it.copyTo(output) }
        }
        if (response.status !in 200..299) throw DriveBackupException(response.status)
        val result = JSONObject(response.body)
        if (result.optString("md5Checksum") != backup.md5 ||
            result.optString("size") != backup.file.length().toString() || result.optString("id").isEmpty()) {
            throw IOException("Drive backup verification failed")
        }
        return result.getString("id")
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
