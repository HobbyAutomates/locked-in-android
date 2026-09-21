package com.sohum.bandlog.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * One-tap self-update. Reads a small `version.json` from the public Supabase Storage bucket;
 * if it advertises a newer versionCode than this build, downloads the APK and hands it to the
 * system installer. The app carries no secret — it only reads a public URL.
 */
object AppUpdater {

    data class Update(val versionCode: Int, val versionName: String, val url: String, val notes: String = "")

    // Generous read timeout for the ~4 MB APK; no overall callTimeout so a slow link isn't cut off.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val versionUrl: String
        get() = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/app/bandlog-version.json"

    /** Returns an Update if a newer build is published, else null (also null on any error/offline). */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url("$versionUrl?t=${System.currentTimeMillis()}").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val o = JSONObject(res.body?.string().orEmpty())
                val code = o.getInt("versionCode")
                if (code > BuildConfig.VERSION_CODE) {
                    Update(code, o.optString("versionName", "$code"), o.getString("url"), o.optString("notes", ""))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Download the APK to cache, reporting 0f..1f progress. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "LockedIn.apk")
        val req = Request.Builder().url(update.url).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("Download failed (${res.code})")
            val body = res.body ?: throw RuntimeException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0) onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    /** Launch the system package installer for the downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
