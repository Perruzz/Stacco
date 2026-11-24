package io.github.perruzz.stacco.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.perruzz.stacco.BuildConfig
import io.github.perruzz.stacco.Links
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.localized
import io.github.perruzz.stacco.stacco
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A version newer than the installed one, published on GitHub. */
data class AppUpdate(val version: String, val tag: String, val pageUrl: String) {
    /** Direct link to the APK of this release. */
    val apkUrl: String get() = "${Links.GITHUB}/releases/download/$tag/Stacco.apk"
}

sealed interface UpdateResult {
    data class Available(val update: AppUpdate) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/**
 * Asks GitHub which is the latest published release. It is the app's only network access:
 * nothing about the user is sent, just an anonymous request for the public release list.
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/Perruzz/Stacco/releases/latest"
    private const val PREFS = "updates"
    private const val KEY_VERSION = "latest_version"
    private const val KEY_TAG = "latest_tag"
    private const val KEY_URL = "latest_url"
    private const val KEY_NOTIFIED = "notified_version"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val url: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
    )

    /** True when [candidate] (e.g. "1.2.0" or "v1.2.0") is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = Regex("\\d+").findAll(v.substringBefore('-')).map { it.value.toInt() }.toList()
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The last update found, as long as it is still newer than the installed version. */
    fun cached(context: Context): AppUpdate? {
        val p = prefs(context)
        val version = p.getString(KEY_VERSION, null) ?: return null
        val tag = p.getString(KEY_TAG, null) ?: return null
        val url = p.getString(KEY_URL, null) ?: return null
        return AppUpdate(version, tag, url).takeIf { isNewer(version, BuildConfig.VERSION_NAME) }
    }

    /** A release description is a few kilobytes: anything this large is not one (a captive portal…). */
    private const val MAX_RESPONSE_BYTES = 1 shl 20

    /**
     * The latest release, or null when it cannot be known now: offline, a timeout, GitHub's rate
     * limit, the page of a hotel Wi-Fi instead of GitHub's answer. Being stopped (the worker
     * cancelled) is not a failure: it goes up as such.
     */
    private suspend fun fetchLatest(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Stacco-Android")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.use { readAtMost(it, MAX_RESPONSE_BYTES) } ?: return@withContext null
                json.decodeFromString<GitHubRelease>(body).takeUnless { it.draft || it.prerelease }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The text of [input], or null if it is longer than [limit] bytes. */
    internal fun readAtMost(input: InputStream, limit: Int): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Checks now; with [notify] it also posts a notification, once per new version. */
    suspend fun check(context: Context, notify: Boolean): UpdateResult {
        val release = fetchLatest() ?: return UpdateResult.Failed
        val version = release.tag.removePrefix("v").removePrefix("V")
        prefs(context).edit {
            putString(KEY_VERSION, version)
            putString(KEY_TAG, release.tag)
            putString(KEY_URL, release.url)
        }
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return UpdateResult.UpToDate
        val update = AppUpdate(version, release.tag, release.url)
        if (notify && prefs(context).getString(KEY_NOTIFIED, null) != version) {
            post(context, update)
            prefs(context).edit { putString(KEY_NOTIFIED, version) }
        }
        return UpdateResult.Available(update)
    }

    private fun open(context: Context, url: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(base: Context, update: AppUpdate) {
        val context = base.localized()
        val builder = Notifications.base(context, Notifications.CHANNEL_UPDATES)
            .setContentTitle(context.getString(R.string.notif_update_title, update.version))
            .setContentText(context.getString(R.string.notif_update_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_update_text)))
            .setContentIntent(open(context, update.pageUrl, 20))
            .addAction(R.drawable.ic_stat_stacco, context.getString(R.string.notif_update_download), open(context, update.apkUrl, 21))
            .setAutoCancel(true)
        Notifications.post(context, Notifications.ID_UPDATE, builder)
    }
}

/** Once a day, when online, looks for a new version on GitHub. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.GITHUB_UPDATES) return Result.success()
        val data = applicationContext.stacco.repository.current()
        if (data.notifications.updateCheck) UpdateChecker.check(applicationContext, notify = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "stacco-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
