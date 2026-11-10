package ir.herotux.pregnancytracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class CycleDayLog(
    val date: String,
    val bleeding: String = "none",
    val symptoms: List<String> = emptyList(),
    val cervicalMucus: String = "unknown",
    val lhResult: String = "none",
    val basalTemperature: String = "",
    val note: String = ""
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

internal fun encodeCycleLogs(logs: List<CycleDayLog>): String = JSONArray().apply {
    logs.sortedByDescending { it.date }.forEach { log ->
        put(JSONObject().apply {
            put("date", log.date)
            put("bleeding", log.bleeding)
            put("symptoms", JSONArray(log.symptoms))
            put("mucus", log.cervicalMucus)
            put("lh", log.lhResult)
            put("bbt", log.basalTemperature)
            put("note", log.note)
        })
    }
}.toString()

internal fun decodeCycleLogs(raw: String?): List<CycleDayLog> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val symptomsJson = item.optJSONArray("symptoms") ?: JSONArray()
                val symptoms = buildList {
                    for (j in 0 until symptomsJson.length()) {
                        symptomsJson.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                val date = item.optString("date")
                if (runCatching { LocalDate.parse(date) }.isSuccess) add(
                    CycleDayLog(
                        date = date,
                        bleeding = item.optString("bleeding", "none"),
                        symptoms = symptoms,
                        cervicalMucus = item.optString("mucus", "unknown"),
                        lhResult = item.optString("lh", "none"),
                        basalTemperature = item.optString("bbt"),
                        note = item.optString("note")
                    )
                )
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CycleLogSection(
    logs: List<CycleDayLog>,
    onSave: (CycleDayLog) -> Unit,
    onDelete: (String) -> Unit
) {
    var bleeding by remember { mutableStateOf("none") }
    var mucus by remember { mutableStateOf("unknown") }
    var lh by remember { mutableStateOf("none") }
    var bbt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf(setOf<String>()) }
    val today = LocalDate.now()
    val todayLog = logs.firstOrNull { it.date == today.toString() }
    LaunchedEffect(todayLog) {
        bleeding = todayLog?.bleeding ?: "none"
        mucus = todayLog?.cervicalMucus ?: "unknown"
        lh = todayLog?.lhResult ?: "none"
        bbt = todayLog?.basalTemperature ?: ""
        note = todayLog?.note ?: ""
        symptoms = todayLog?.symptoms?.toSet() ?: emptySet()
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = RoseDark)
                Spacer(Modifier.width(8.dp))
                Text("ثبت روزانه چرخه", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text("امروز؛ ${jalali(today)}", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("شدت خون‌ریزی", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("none" to "ندارد", "spotting" to "لکه‌بینی", "light" to "کم", "medium" to "متوسط", "heavy" to "زیاد").forEach { (value, label) ->
                    FilterChip(selected = bleeding == value, onClick = { bleeding = value }, label = { Text(label, fontFamily = Fa, fontSize = 10.sp) })
                }
            }
            Text("علائم امروز", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            val symptomOptions = listOf("درد شکم", "گرفتگی", "سردرد", "حساسیت سینه", "نفخ", "خستگی", "تغییر خلق", "تهوع")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                symptomOptions.forEach { symptom ->
                    FilterChip(
                        selected = symptom in symptoms,
                        onClick = { symptoms = if (symptom in symptoms) symptoms - symptom else symptoms + symptom },
                        label = { Text(symptom, fontFamily = Fa, fontSize = 10.sp) }
                    )
                }
            }
            Text("ترشحات دهانه رحم", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("unknown" to "نامشخص", "dry" to "خشک", "sticky" to "چسبنده", "creamy" to "کِرِمی", "eggwhite" to "شفاف و کش‌دار").forEach { (value, label) ->
                    FilterChip(selected = mucus == value, onClick = { mucus = value }, label = { Text(label, fontFamily = Fa, fontSize = 9.sp) })
                }
            }
            Text("نتیجه تست LH", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("none" to "ندادم", "negative" to "منفی", "positive" to "مثبت", "peak" to "اوج").forEach { (value, label) ->
                    FilterChip(selected = lh == value, onClick = { lh = value }, label = { Text(label, fontFamily = Fa, fontSize = 10.sp) })
                }
            }
            OutlinedTextField(
                value = bbt, onValueChange = { bbt = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("دمای پایه بدن (°C)؛ اختیاری", fontFamily = Fa) },
                placeholder = { Text("مثلاً 36.45", fontFamily = Fa) },
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(300) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("یادداشت امروز", fontFamily = Fa) },
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    val entry = CycleDayLog(today.toString(), bleeding, symptoms.toList(), mucus, lh, bbt, note)
                    onSave(entry)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (todayLog == null) "ذخیره ثبت امروز" else "به‌روزرسانی ثبت امروز", fontFamily = Fa)
            }
            if (logs.isNotEmpty()) {
                HorizontalDivider()
                Text("سوابق ثبت‌شده (${fa(logs.size)})", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                logs.sortedByDescending { it.date }.take(12).forEach { log ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(log.localDate?.let(::jalali) ?: log.date, fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            val bleedLabel = when (log.bleeding) {
                                "spotting" -> "لکه‌بینی"; "light" -> "خون‌ریزی کم"; "medium" -> "خون‌ریزی متوسط"; "heavy" -> "خون‌ریزی زیاد"; else -> "بدون خون‌ریزی"
                            }
                            Text(
                                listOfNotNull(bleedLabel, log.symptoms.takeIf { it.isNotEmpty() }?.joinToString("، "), log.lhResult.takeIf { it != "none" }?.let { "LH: $it" }, log.basalTemperature.takeIf { it.isNotBlank() }?.let { "BBT: $it°C" }).joinToString(" • "),
                                fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (log.note.isNotBlank()) Text(log.note, fontFamily = Fa, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onDelete(log.date) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف ثبت روز", tint = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
                if (logs.size > 12) Text("برای سبک ماندن صفحه، فقط ۱۲ ثبت اخیر نمایش داده می‌شود.", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal val preconceptionChecklist = listOf(
    "برای مشاوره پیش از بارداری وقت گرفته‌ام",
    "داروها و مکمل‌ها را با پزشک یا داروساز مرور کرده‌ام",
    "درباره اسیدفولیک و مقدار مناسب آن سؤال کرده‌ام",
    "وضعیت واکسیناسیون را بررسی کرده‌ام",
    "بیماری‌های زمینه‌ای و سابقه پزشکی را مطرح کرده‌ام",
    "درباره آزمایش‌های لازم پیش از بارداری سؤال کرده‌ام",
    "سلامت دهان و دندان را بررسی کرده‌ام",
    "درباره سبک زندگی، تغذیه و مصرف دخانیات مشورت کرده‌ام"
)

internal fun encodeChecklist(done: Set<Int>): String = JSONArray().apply {
    done.sorted().forEach { put(it) }
}.toString()

internal fun decodeChecklist(raw: String?): Set<Int> {
    if (raw.isNullOrBlank()) return emptySet()
    return runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (i in 0 until array.length()) {
                val index = array.optInt(i, -1)
                if (index in preconceptionChecklist.indices) add(index)
            }
        }
    }.getOrDefault(emptySet())
}

@Composable
internal fun PreconceptionSection(
    completed: Set<Int>,
    onToggle: (Int, Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("چک‌لیست آمادگی پیش از بارداری", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "این فهرست برای آماده‌سازی گفت‌وگو با پزشک است، نه تشخیص یا تجویز. موارد را متناسب با شرایط خودت بررسی کن.",
                fontFamily = Fa, fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { completed.size.toFloat() / preconceptionChecklist.size },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${fa(completed.size)} از ${fa(preconceptionChecklist.size)} مورد انجام شده", fontFamily = Fa, fontSize = 11.sp, color = RoseDark)
            preconceptionChecklist.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = index in completed, onCheckedChange = { onToggle(index, it) })
                    Text(label, fontFamily = Fa, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}
