package com.whisperorb.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ModelDownloader? = null

        fun getInstance(context: Context): ModelDownloader {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloader(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<WhisperModelTier, Job>()
    val downloadProgress = mutableMapOf<WhisperModelTier, Float>()
    var errorMessage: String? = null
    var onStateChanged: (() -> Unit)? = null

    /**
     * Répertoire persistant dans /sdcard/Android/media/com.whisperorb.app/Models/
     * Survit aux mises à jour d'APK et même aux désinstallations/réinstallations.
     */
    @Suppress("DEPRECATION")
    val persistentMediaModelsDir: File?
        get() {
            return try {
                val base = context.externalMediaDirs?.firstOrNull()
                    ?: File("/sdcard/Android/media/${context.packageName}")
                val dir = File(base, "Models")
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists() && dir.canWrite()) dir else null
            } catch (_: Exception) {
                null
            }
        }

    val internalModelsDir: File
        get() {
            val dir = File(context.filesDir, "Models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val modelsDir: File
        get() = persistentMediaModelsDir ?: internalModelsDir

    fun localModelFile(tier: WhisperModelTier): File {
        val minValidBytes = 20L * 1024L * 1024L
        persistentMediaModelsDir?.let { extDir ->
            val extFile = File(extDir, tier.fileName)
            if (extFile.exists() && extFile.length() > minValidBytes) {
                return extFile
            }
        }
        val intFile = File(internalModelsDir, tier.fileName)
        if (intFile.exists() && intFile.length() > minValidBytes) {
            return intFile
        }
        return File(modelsDir, tier.fileName)
    }

    fun isModelDownloaded(tier: WhisperModelTier): Boolean {
        val f = localModelFile(tier)
        return f.exists() && f.length() > 20L * 1024L * 1024L
    }

    fun isDownloading(tier: WhisperModelTier): Boolean {
        return activeJobs[tier]?.isActive == true
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun downloadModel(tier: WhisperModelTier, onComplete: ((WhisperModelTier) -> Unit)? = null) {
        if (isDownloading(tier)) return
        errorMessage = null
        downloadProgress[tier] = 0f
        notifyUpdate()

        val job = scope.launch {
            val targetDir = modelsDir
            val tempFile = File(targetDir, "${tier.fileName}.part")
            val destFile = File(targetDir, tier.fileName)
            var conn: HttpURLConnection? = null
            try {
                val url = URL(tier.downloadURL)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw RuntimeException("HTTP $code")
                }

                val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
                    ?: (tier.sizeMB.toLong() * 1024L * 1024L)

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastUiNotify = 0L
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiNotify > 120) {
                                lastUiNotify = now
                                val pct = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                withContext(Dispatchers.Main) {
                                    downloadProgress[tier] = pct
                                    onStateChanged?.invoke()
                                }
                            }
                        }
                    }
                }

                if (isActive && tempFile.length() > 20L * 1024L * 1024L) {
                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                    withContext(Dispatchers.Main) {
                        downloadProgress.remove(tier)
                        activeJobs.remove(tier)
                        onStateChanged?.invoke()
                        onComplete?.invoke(tier)
                    }
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                withContext(Dispatchers.Main) {
                    errorMessage = "Erreur téléchargement (${tier.shortBadgeName}) : ${e.message}"
                    downloadProgress.remove(tier)
                    activeJobs.remove(tier)
                    onStateChanged?.invoke()
                }
            } finally {
                conn?.disconnect()
            }
        }
        activeJobs[tier] = job
    }

    fun cancelDownload(tier: WhisperModelTier) {
        activeJobs[tier]?.cancel()
        activeJobs.remove(tier)
        downloadProgress.remove(tier)
        persistentMediaModelsDir?.let { File(it, "${tier.fileName}.part").delete() }
        File(internalModelsDir, "${tier.fileName}.part").delete()
        notifyUpdate()
    }

    fun deleteModel(tier: WhisperModelTier) {
        cancelDownload(tier)
        persistentMediaModelsDir?.let { File(it, tier.fileName).delete() }
        File(internalModelsDir, tier.fileName).delete()
        notifyUpdate()
    }

    private fun notifyUpdate() {
        CoroutineScope(Dispatchers.Main).launch {
            onStateChanged?.invoke()
        }
    }
}
