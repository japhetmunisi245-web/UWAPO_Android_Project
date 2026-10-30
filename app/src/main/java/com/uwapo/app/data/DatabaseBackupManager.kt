package com.uwapo.app.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.FileProvider
import com.uwapo.app.UWAPOApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class RestoreResult {
    data object Success : RestoreResult()
    data object PasswordRequired : RestoreResult()
    data object InvalidPassword : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

object DatabaseBackupManager {

    private const val HEADER_RAW = "UWAPO_RAW_V1"
    private const val HEADER_ENC = "UWAPO_ENC_V1"
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    suspend fun exportDatabase(
        context: Context,
        outputStream: OutputStream,
        password: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as UWAPOApplication
            app.checkpointDatabase()

            val dbFile = context.getDatabasePath("uwapo.db")
            if (!dbFile.exists()) return@withContext false

            val dbBytes = dbFile.readBytes()

            if (!password.isNullOrBlank()) {
                val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }

                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val encryptedBytes = cipher.doFinal(dbBytes)

                outputStream.use { out ->
                    out.write(HEADER_ENC.toByteArray(Charsets.UTF_8))
                    out.write(salt)
                    out.write(iv)
                    out.write(encryptedBytes)
                    out.flush()
                }
            } else {
                outputStream.use { out ->
                    out.write(HEADER_RAW.toByteArray(Charsets.UTF_8))
                    out.write(dbBytes)
                    out.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreDatabase(
        context: Context,
        inputStream: InputStream,
        password: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.db")
        try {
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size < 12) {
                return@withContext RestoreResult.Error("Invalid backup file format.")
            }

            val header = String(bytes, 0, 12, Charsets.UTF_8)
            val dbBytes: ByteArray = when (header) {
                HEADER_ENC -> {
                    if (password.isNullOrBlank()) {
                        return@withContext RestoreResult.PasswordRequired
                    }
                    if (bytes.size < 12 + SALT_SIZE + IV_SIZE) {
                        return@withContext RestoreResult.Error("Corrupted encrypted backup file.")
                    }
                    val salt = bytes.copyOfRange(12, 12 + SALT_SIZE)
                    val iv = bytes.copyOfRange(12 + SALT_SIZE, 12 + SALT_SIZE + IV_SIZE)
                    val ciphertext = bytes.copyOfRange(12 + SALT_SIZE + IV_SIZE, bytes.size)

                    try {
                        val key = deriveKey(password, salt)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                        cipher.doFinal(ciphertext)
                    } catch (_: Exception) {
                        return@withContext RestoreResult.InvalidPassword
                    }
                }
                HEADER_RAW -> {
                    bytes.copyOfRange(12, bytes.size)
                }
                else -> {
                    // Check if it's a raw SQLite file
                    val sqliteHeader = String(bytes.take(15).toByteArray(), Charsets.UTF_8)
                    if (sqliteHeader.startsWith("SQLite format 3")) {
                        bytes
                    } else {
                        return@withContext RestoreResult.Error("Unrecognized backup file format.")
                    }
                }
            }

            // Write decrypted/raw DB to temp file for validation
            tempFile.writeBytes(dbBytes)

            // Validate database schema
            val isValid = validateDatabaseFile(tempFile)
            if (!isValid) {
                tempFile.delete()
                return@withContext RestoreResult.Error("Selected file is not a valid UWAPO database.")
            }

            // Close application database
            val app = context.applicationContext as UWAPOApplication
            app.closeDatabase()

            // Replace current database file
            val targetDb = context.getDatabasePath("uwapo.db")
            if (targetDb.exists()) {
                targetDb.delete()
            }
            tempFile.copyTo(targetDb, overwrite = true)

            // Remove existing WAL and SHM files to avoid stale logs
            val walFile = File(targetDb.path + "-wal")
            val shmFile = File(targetDb.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Re-open database
            app.reopenDatabase()

            tempFile.delete()
            RestoreResult.Success
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            RestoreResult.Error("Restore failed: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun createShareBackupIntent(
        context: Context,
        password: String? = null
    ): Intent? = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "UWAPO_Backup_$timestamp.uwapo")

            val success = backupFile.outputStream().use { out ->
                exportDatabase(context, out, password)
            }

            if (!success || !backupFile.exists()) return@withContext null

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "UWAPO Database Backup")
                putExtra(Intent.EXTRA_TEXT, "Here is your UWAPO local database backup.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun validateDatabaseFile(file: File): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            val tables = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    tables.add(it.getString(0))
                }
            }
            db.close()
            tables.contains("members") && tables.contains("payments") && tables.contains("expenses")
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    suspend fun getBackupCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        val backupDir = File(context.cacheDir, "backups")
        if (backupDir.exists()) {
            backupDir.listFiles()?.forEach { file ->
                if (file.isFile) totalSize += file.length()
            }
        }
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("temp_restore_")) {
                totalSize += file.length()
            }
        }
        totalSize
    }

    suspend fun clearOldBackups(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.cacheDir, "backups")
            if (backupDir.exists()) {
                backupDir.listFiles()?.forEach { it.delete() }
            }
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("temp_restore_")) {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
