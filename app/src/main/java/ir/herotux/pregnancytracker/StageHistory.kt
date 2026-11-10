package ir.herotux.pregnancytracker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val StageHistoryRecordsKey = stringPreferencesKey("transitions")

internal data class StageTransition(
    val from: String,
    val to: String,
    val dateTime: LocalDateTime
)

private fun stageLabel(stage: String): String = when (stage) {
    STAGE_PLANNING -> "برنامه‌ریزی برای بارداری"
    STAGE_CYCLE -> "ثبت چرخه قاعدگی"
    STAGE_TTC -> "تلاش برای بارداری"
    STAGE_POSSIBLE -> "احتمال بارداری"
    STAGE_POSITIVE -> "تست مثبت"
    STAGE_PREGNANT -> "بارداری تأییدشده"
    STAGE_POSTPARTUM -> "پس از زایمان"
    else -> "مرحله نامشخص"
}

private fun decodeStageHistory(raw: String?): List<StageTransition> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.getJSONObject(i)
        StageTransition(
            item.getString("from"),
            item.getString("to"),
            LocalDateTime.parse(item.getString("at"))
        )
    }.sortedByDescending { it.dateTime }
}.getOrDefault(emptyList())

private fun encodeStageHistory(items: List<StageTransition>): String {
    val array = JSONArray()
    items.sortedByDescending { it.dateTime }.take(100).forEach { entry ->
        array.put(JSONObject().put("from", entry.from).put("to", entry.to).put("at", entry.dateTime.toString()))
    }
    return array.toString()
}

/** Call inside the same DataStore edit transaction that persists the new active stage. */
internal fun MutablePreferences.recordStageTransition(newStage: String) {
    val oldStage = this[stringPreferencesKey("reproductive_stage")]
    if (oldStage == newStage) return
    val history = decodeStageHistory(this[StageHistoryRecordsKey])
    val next = history + StageTransition(oldStage ?: "not_set", newStage, LocalDateTime.now())
    this[StageHistoryRecordsKey] = encodeStageHistory(next)
}

@Composable
internal fun StageHistorySection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var history by remember { mutableStateOf<List<StageTransition>>(emptyList()) }
    LaunchedEffect(Unit) {
        history = decodeStageHistory(context.pregnancyStore.data.first()[StageHistoryRecordsKey])
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تاریخچه مراحل مسیر", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "تغییر مرحله، سوابق قبلی چرخه و بارداری را پاک نمی‌کند. هر انتقال ثبت‌شده در این فهرست باقی می‌ماند.",
                fontFamily = Fa, fontSize = 11.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (history.isEmpty()) {
                Text("هنوز انتقالی ثبت نشده است. پس از تأیید تغییر مرحله، تاریخچه اینجا نمایش داده می‌شود.", fontFamily = Fa, fontSize = 11.sp)
            } else {
                history.take(12).forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stageLabel(entry.from) + "  ←  " + stageLabel(entry.to),
                            fontFamily = Fa, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            entry.dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm")),
                            fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry != history.take(12).last()) HorizontalDivider()
                }
            }
        }
    }
}
