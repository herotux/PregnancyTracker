package ir.herotux.pregnancytracker

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NonoIssueReport(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val status: String,
    val createdAt: Long
)

object NonoIssueReports {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun submit(
        uid: String,
        title: String,
        category: String,
        description: String,
        appVersion: String,
        versionCode: Long,
        device: String
    ) = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        require(cleanTitle.length in 4..100) { "عنوان گزارش باید بین ۴ تا ۱۰۰ نویسه باشد." }
        require(cleanDescription.length in 10..3000) { "توضیحات باید بین ۱۰ تا ۳۰۰۰ نویسه باشد." }
        require(category in listOf("bug", "suggestion", "other")) { "نوع گزارش معتبر نیست." }
        Tasks.await(db.collection("issueReports").add(mapOf(
            "uid" to uid,
            "title" to cleanTitle,
            "category" to category,
            "description" to cleanDescription,
            "appVersion" to appVersion,
            "versionCode" to versionCode,
            "androidVersion" to Build.VERSION.RELEASE.orEmpty(),
            "device" to device.take(120),
            "status" to "new",
            "createdAt" to FieldValue.serverTimestamp()
        )))
    }

    suspend fun listMine(uid: String): List<NonoIssueReport> = withContext(Dispatchers.IO) {
        Tasks.await(db.collection("issueReports").whereEqualTo("uid", uid).get())
            .documents.map { doc ->
                NonoIssueReport(
                    doc.id,
                    doc.getString("title").orEmpty(),
                    doc.getString("category").orEmpty(),
                    doc.getString("description").orEmpty(),
                    doc.getString("status") ?: "new",
                    doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                )
            }.sortedByDescending { it.createdAt }.take(30)
    }
}

@Composable
fun IssueReportScreen(uid: String?, onBack: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("bug") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reports by remember { mutableStateOf(emptyList<NonoIssueReport>()) }
    var loading by remember { mutableStateOf(false) }

    fun loadReports() {
        if (uid == null) return
        loading = true
        scope.launch {
            runCatching { NonoIssueReports.listMine(uid) }
                .onSuccess { reports = it }
                .onFailure { error = it.message ?: "دریافت گزارش‌ها ناموفق بود." }
            loading = false
        }
    }

    LaunchedEffect(uid) { loadReports() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت") }
            Text("گزارش مشکل و پیشنهاد", fontFamily = Fa, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (uid == null) {
            Text("برای ثبت و پیگیری گزارش، ابتدا با حساب Google وارد شو.", fontFamily = Fa)
            Button(onClick = onSignIn) { Text("ورود با Google", fontFamily = Fa) }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, null, tint = RoseDark)
                            Spacer(Modifier.width(8.dp))
                            Text("ارسال گزارش جدید", fontFamily = Fa, fontWeight = FontWeight.Bold)
                        }
                        Text("لطفاً اطلاعات لازم برای بازتولید مشکل را بنویس. اطلاعات بارداری و پرونده پزشکی به گزارش پیوست نمی‌شود.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = category == "bug", onClick = { category = "bug" }, label = { Text("گزارش خطا", fontFamily = Fa) })
                            FilterChip(selected = category == "suggestion", onClick = { category = "suggestion" }, label = { Text("پیشنهاد", fontFamily = Fa) })
                            FilterChip(selected = category == "other", onClick = { category = "other" }, label = { Text("سایر", fontFamily = Fa) })
                        }
                        OutlinedTextField(title, { title = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان", fontFamily = Fa) }, singleLine = true)
                        OutlinedTextField(description, { description = it.take(3000) }, modifier = Modifier.fillMaxWidth(), label = { Text("شرح مشکل یا پیشنهاد", fontFamily = Fa) }, minLines = 4, maxLines = 7)
                        Text("نسخه برنامه: ${BuildConfig.VERSION_NAME}  •  اندروید ${Build.VERSION.RELEASE.orEmpty()}", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        message?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                        Button(
                            onClick = {
                                busy = true; error = null; message = null
                                val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                                val versionCode = (info?.versionCode ?: 1).toLong()
                                scope.launch {
                                    runCatching {
                                        NonoIssueReports.submit(uid, title, category, description, BuildConfig.VERSION_NAME, versionCode, "${Build.MANUFACTURER} ${Build.MODEL}")
                                    }.onSuccess {
                                        title = ""; description = ""; message = "گزارش با موفقیت ثبت شد. ممنون از اینکه به بهتر شدن نونو کمک می‌کنی."; loadReports()
                                    }.onFailure { error = it.message ?: "ارسال گزارش ناموفق بود." }
                                    busy = false
                                }
                            },
                            enabled = !busy && title.trim().length >= 4 && description.trim().length >= 10,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("ارسال گزارش", fontFamily = Fa) }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("گزارش‌های قبلی", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { loadReports() }, enabled = !loading) { Text("بروزرسانی", fontFamily = Fa) }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!loading && reports.isEmpty()) item { Text("هنوز گزارشی ثبت نکرده‌ای.", fontFamily = Fa, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            items(reports, key = { it.id }) { report ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(report.title, fontFamily = Fa, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            AssistChip(onClick = {}, label = { Text(when(report.status) { "reviewing" -> "در حال بررسی"; "resolved" -> "بررسی شد"; "closed" -> "بسته شد"; else -> "ثبت‌شده" }, fontFamily = Fa, fontSize = 10.sp) })
                        }
                        Text(when(report.category) { "suggestion" -> "پیشنهاد"; "other" -> "سایر"; else -> "گزارش خطا" }, fontFamily = Fa, fontSize = 11.sp, color = RoseDark)
                        Text(report.description, fontFamily = Fa, fontSize = 12.sp, maxLines = 3)
                        if (report.createdAt > 0) Text(SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date(report.createdAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
