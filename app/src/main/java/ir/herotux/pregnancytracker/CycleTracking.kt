package ir.herotux.pregnancytracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
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


private fun fullFlowDates(logs: List<CycleDayLog>): List<LocalDate> =
    logs.filter { it.bleeding in setOf("light", "medium", "heavy") }.mapNotNull { it.localDate }.distinct().sorted()

internal fun detectedPeriodStarts(logs: List<CycleDayLog>): List<LocalDate> {
    val dates = fullFlowDates(logs)
    if (dates.isEmpty()) return emptyList()
    val episodes = mutableListOf<MutableList<LocalDate>>()
    dates.forEach { date ->
        val episode = episodes.lastOrNull()
        if (episode == null || java.time.temporal.ChronoUnit.DAYS.between(episode.last(), date) > 2L) episodes.add(mutableListOf(date))
        else episode.add(date)
    }
    return episodes.map { it.first() }
}

internal fun observedCycleLengths(logs: List<CycleDayLog>): List<Int> =
    detectedPeriodStarts(logs).zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt() }.filter { it in 15..90 }

internal fun calculatedCycleLength(logs: List<CycleDayLog>): Int? {
    val values = observedCycleLengths(logs).takeLast(6).sorted()
    if (values.isEmpty()) return null
    val mid = values.size / 2
    return (if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2).coerceIn(15, 90)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CycleHistoryEditor(logs: List<CycleDayLog>, onSave: (CycleDayLog) -> Unit, onDelete: (String) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var showDate by remember { mutableStateOf(false) }
    var bleeding by remember { mutableStateOf("none") }
    var lh by remember { mutableStateOf("none") }
    var bbt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val existing = logs.firstOrNull { it.date == date.toString() }
    LaunchedEffect(date, existing) {
        bleeding = existing?.bleeding ?: "none"; lh = existing?.lhResult ?: "none"
        bbt = existing?.basalTemperature ?: ""; note = existing?.note ?: ""
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("افزودن یا ویرایش روزهای گذشته", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(jalali(date), fontFamily = Fa)
            }
            Text("شدت خون‌ریزی", fontFamily = Fa, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("none" to "ندارد", "spotting" to "لکه‌بینی", "light" to "کم", "medium" to "متوسط", "heavy" to "زیاد").forEach { (key, label) ->
                    FilterChip(selected = bleeding == key, onClick = { bleeding = key }, label = { Text(label, fontFamily = Fa, fontSize = 10.sp) })
                }
            }
            Text("تست LH", fontFamily = Fa, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("none" to "ندادم", "negative" to "منفی", "positive" to "مثبت", "peak" to "اوج").forEach { (key, label) ->
                    FilterChip(selected = lh == key, onClick = { lh = key }, label = { Text(label, fontFamily = Fa, fontSize = 10.sp) })
                }
            }
            OutlinedTextField(value = bbt, onValueChange = { bbt = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("دمای پایه بدن °C", fontFamily = Fa) })
            OutlinedTextField(value = note, onValueChange = { note = it.take(300) }, modifier = Modifier.fillMaxWidth(), label = { Text("یادداشت", fontFamily = Fa) })
            Button(onClick = { onSave(CycleDayLog(date.toString(), bleeding, existing?.symptoms ?: emptyList(), existing?.cervicalMucus ?: "unknown", lh, bbt, note)) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (existing == null) "ذخیره روز گذشته" else "به‌روزرسانی روز گذشته", fontFamily = Fa)
            }
            if (existing != null) TextButton(onClick = { onDelete(date.toString()) }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error); Text("حذف این روز", fontFamily = Fa, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showDate) DateDialogModern(date, { date = it; showDate = false }, { showDate = false })
}

@Composable
internal fun CycleAnalyticsSection(logs: List<CycleDayLog>) {
    val starts = detectedPeriodStarts(logs)
    val lengths = observedCycleLengths(logs)
    val median = calculatedCycleLength(logs)
    val temperatures = logs.mapNotNull { log ->
        val day = log.localDate ?: return@mapNotNull null
        val value = log.basalTemperature.toFloatOrNull() ?: return@mapNotNull null
        if (value in 34f..42f) day to value else null
    }.sortedBy { it.first }.takeLast(14)
    val lhLogs = logs.filter { it.lhResult != "none" }.sortedByDescending { it.date }.take(8)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تاریخچه و روند چرخه", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Blush) {
                    Column(Modifier.padding(10.dp)) { Text("شروع قاعدگی", fontFamily = Fa, fontSize = 10.sp, color = RoseDark); Text(fa(starts.size.toString()), fontFamily = Fa, fontWeight = FontWeight.Bold, color = RoseDark) }
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Blush) {
                    Column(Modifier.padding(10.dp)) { Text("میانه طول چرخه", fontFamily = Fa, fontSize = 10.sp, color = RoseDark); Text(median?.let { fa(it.toString()) + " روز" } ?: "داده کافی نیست", fontFamily = Fa, fontWeight = FontWeight.Bold, color = RoseDark) }
                }
            }
            Text("شروع چرخه از اولین روز خون‌ریزی کامل محاسبه می‌شود؛ لکه‌بینی شروع چرخه نیست. برای محاسبه فاصله چرخه حداقل دو شروع ثبت‌شده لازم است.", fontFamily = Fa, fontSize = 10.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lengths.isNotEmpty()) {
                Text("فاصله چرخه‌های ثبت‌شده (روز)", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                CycleTrendBars(lengths.takeLast(8).map { it.toFloat() }, 15f, 90f)
                Text(lengths.takeLast(6).joinToString("، ") { fa(it.toString()) + " روز" }, fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (temperatures.isNotEmpty()) {
                HorizontalDivider()
                Text("دمای پایه بدن؛ ۱۴ ثبت اخیر", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                CycleTrendBars(temperatures.map { it.second }, 35f, 38f)
                Text("کمینه ${fa("%.2f".format(java.util.Locale.US, temperatures.minOf { it.second }))}° و بیشینه ${fa("%.2f".format(java.util.Locale.US, temperatures.maxOf { it.second }))}°", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lhLogs.isNotEmpty()) {
                HorizontalDivider(); Text("تست‌های اخیر LH", fontFamily = Fa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                lhLogs.forEach { log ->
                    val label = when (log.lhResult) { "negative" -> "منفی"; "positive" -> "مثبت"; "peak" -> "اوج"; else -> log.lhResult }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.localDate?.let(::jalali) ?: log.date, fontFamily = Fa, fontSize = 11.sp)
                        Text(label, fontFamily = Fa, fontWeight = FontWeight.SemiBold, color = RoseDark, fontSize = 11.sp)
                    }
                }
            }
            Text("این داده‌ها الگوهای ثبت‌شده را نمایش می‌دهند و به‌تنهایی تخمک‌گذاری را تشخیص نمی‌دهند.", fontFamily = Fa, fontSize = 10.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CycleTrendBars(values: List<Float>, minValue: Float, maxValue: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(86.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(2f)
        values.forEachIndexed { index, value ->
            val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            val barHeight = fraction * (size.height - 8.dp.toPx())
            drawRoundRect(Rose.copy(alpha = 0.85f), topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - barHeight), size = androidx.compose.ui.geometry.Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
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
