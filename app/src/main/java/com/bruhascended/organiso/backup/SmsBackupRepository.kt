package com.bruhascended.organiso.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.provider.DocumentsContract
import android.util.Base64
import android.util.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SmsBackup(val file: File, val messageCount: Int, val sha256: String, val md5: String)

/** Reads the system SMS provider only. Never writes, marks read, or deletes a message. */
class SmsBackupRepository(private val context: Context) {
    fun create(): SmsBackup {
        val directory = File(context.cacheDir, "sms-backups").apply { mkdirs() }
        val file = File.createTempFile("organiso-sms-", ".json", directory)
        try {
            val count = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, null, null, null, "_id ASC"
            )?.use { cursor ->
                FileOutputStream(file).use { output ->
                    val text = OutputStreamWriter(output, Charsets.UTF_8)
                    val writer = JsonWriter(text)
                    writer.beginObject()
                    writer.name("format").value("organiso-sms-backup")
                    writer.name("version").value(1)
                    writer.name("createdAt").value(utcTimestamp())
                    writer.name("sms").beginArray()
                    var count = 0
                    val columns = cursor.columnNames
                    while (cursor.moveToNext()) {
                        writer.beginObject()
                        columns.forEachIndexed { index, column ->
                            writer.name(column)
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                                Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> writer.value(cursor.getDouble(index))
                                Cursor.FIELD_TYPE_BLOB -> writer.value(Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                                else -> writer.value(cursor.getString(index))
                            }
                        }
                        writer.endObject()
                        count++
                    }
                    writer.endArray()
                    writer.name("messageCount").value(count)
                    writer.endObject()
                    writer.flush()
                    output.fd.sync()
                    count
                }
            } ?: throw IOException("SMS provider unavailable")
            return SmsBackup(file, count, file.inputStream().use { digest(it) },
                file.inputStream().use { digest(it, "MD5") })
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    /** Reopen and verify the destination before reporting success. */
    fun saveToDevice(backup: SmsBackup, uri: Uri) {
        val resolver = context.contentResolver
        (resolver.openOutputStream(uri, "wt") ?: throw IOException("Destination unavailable")).use { output ->
            backup.file.inputStream().use { it.copyTo(output) }
            output.flush()
        }
        val actual = (resolver.openInputStream(uri) ?: throw IOException("Cannot verify destination"))
            .use { digest(it) }
        if (actual != backup.sha256) throw IOException("Backup checksum mismatch")
    }

    fun saveToFolder(backup: SmsBackup, tree: Uri): Uri {
        if (context.contentResolver.persistedUriPermissions.none {
                it.uri == tree && it.isReadPermission && it.isWritePermission
            }) throw SecurityException("Backup folder access expired")
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(context.contentResolver, folder, "application/json", filename())
            ?: throw IOException("Cannot create backup in folder")
        try {
            saveToDevice(backup, file)
            return file
        } catch (e: Exception) {
            // Only clean up the incomplete file just created by this operation.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, file) }
            throw e
        }
    }

    companion object {
        fun filename(): String = "Organiso-SMS-${utcTimestamp().replace(":", "-")}.json"

        private fun utcTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        fun digest(input: InputStream, algorithm: String = "SHA-256"): String {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
