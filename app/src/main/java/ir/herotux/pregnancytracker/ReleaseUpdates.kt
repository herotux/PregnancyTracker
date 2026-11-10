package ir.herotux.pregnancytracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class NonoReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val url: String,
    val publishedAt: String
)

object NonoUpdateService {
    private const val OWNER = "herotux"
    private const val REPO = "PregnancyTracker"
    private const val CHANNEL = "nono_updates"
    private const val PREFS = "nono_release_updates"
    private const val LAST_NOTIFIED = "last_notified_tag"
    private const val PERIODIC_WORK = "nono-release-update-check"
    private const val IMMEDIATE_WORK = "nono-release-update-check-now"

    suspend fun latestRelease(): NonoReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Nono-Android-App")
            if (connection.responseCode !in 200..299) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            NonoReleaseInfo(
                json.optString("tag_name"),
                json.optString("name").ifBlank { json.optString("tag_name") },
                json.optString("body"),
                json.optString("html_url"),
                json.optString("published_at")
            ).takeIf { it.tag.isNotBlank() && it.url.startsWith("https://github.com/") }
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        fun parts(value: String) = value.trim().removePrefix("v").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val latest = parts(latestTag)
        val current = parts(currentVersion)
        for (i in 0 until maxOf(latest.size, current.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork(IMMEDIATE_WORK, androidx.work.ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReleaseUpdateWorker>().build())
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReleaseUpdateWorker>(12, TimeUnit.HOURS).build()
        )
    }

    suspend fun checkAndNotify(context: Context): NonoReleaseInfo? {
        val release = latestRelease() ?: return null
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        if (!isNewer(release.tag, current)) return release
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOTIFIED, null) == release.tag) return release
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return release
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(context, 731, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("نسخه جدید نونو منتشر شد")
            .setContentText("نسخه ${release.name} آماده دریافت است.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("نسخه ${release.name} منتشر شده است. برای دریافت و مشاهده تغییرات، صفحه انتشار را باز کن."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.stat_sys_download, "دریافت بروزرسانی", PendingIntent.getActivity(
                context, 732, Intent(Intent.ACTION_VIEW, Uri.parse(release.url)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        NotificationManagerCompat.from(context).notify(731, notification)
        prefs.edit().putString(LAST_NOTIFIED, release.tag).apply()
        return release
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "بروزرسانی‌های نونو", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "اطلاع از انتشار نسخه‌های جدید نونو"
            })
        }
    }
}

class ReleaseUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        NonoUpdateService.checkAndNotify(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

@Composable
fun UpdateCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<NonoReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "نامشخص" }.getOrDefault("نامشخص")
    }

    fun check() {
        checking = true; error = null; checked = false
        scope.launch {
            runCatching { NonoUpdateService.latestRelease() }
                .onSuccess { release = it; checked = true; if (it == null) error = "هنوز نسخه عمومی‌ای در GitHub منتشر نشده یا اتصال برقرار نشد." }
                .onFailure { error = "بررسی بروزرسانی ناموفق بود. اتصال اینترنت را بررسی کن." }
            checking = false
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("بازگشت", fontFamily = Fa) }
            Text("بروزرسانی نونو", fontFamily = Fa, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SystemUpdate, null, tint = RoseDark, modifier = Modifier.size(34.dp))
                Text("نسخه نصب‌شده: $currentVersion", fontFamily = Fa, fontWeight = FontWeight.Bold)
                Text("نونو به‌صورت دوره‌ای انتشارهای رسمی GitHub را بررسی می‌کند. اگر نسخه جدیدی منتشر شود، اعلان دریافت می‌کنی.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { check() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("بررسی دستی بروزرسانی", fontFamily = Fa)
                }
                error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (checked && release != null) {
                    val newer = NonoUpdateService.isNewer(release!!.tag, currentVersion)
                    HorizontalDivider()
                    Text(if (newer) "نسخه جدید موجود است: ${release!!.name}" else "برنامه به‌روز است.", fontFamily = Fa, fontWeight = FontWeight.Bold, color = if (newer) RoseDark else MaterialTheme.colorScheme.primary)
                    if (release!!.notes.isNotBlank()) Text(release!!.notes.take(1800), fontFamily = Fa, fontSize = 12.sp, lineHeight = 20.sp)
                    if (newer) OutlinedButton(onClick = { ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(release!!.url)), null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("دریافت نسخه جدید از GitHub", fontFamily = Fa)
                    }
                }
            }
        }
    }
}
