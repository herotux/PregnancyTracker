package ir.herotux.pregnancytracker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private val Context.calendarStore by preferencesDataStore(name = "pregnancy_calendar")
private val CalendarEntriesKey = stringPreferencesKey("entries")
private const val CALENDAR_CHANNEL = "nono_calendar_events"
private const val CALENDAR_EXTRA_ID = "calendar_entry_id"
private const val CALENDAR_EXTRA_SNOOZE = "calendar_snooze"
private const val SNOOZE_REQUEST_OFFSET = 1_000_000_000

data class CalendarEntry(
    val id: Long,
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: String,
    val category: String,
    val type: String,
    val leadMinutes: Int,
    val repeat: String,
    val allDay: Boolean = false,
    val completed: Boolean = false
)

private fun CalendarEntry.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("description", description)
    put("date", date.toString()); put("time", time); put("category", category)
    put("type", type); put("lead", leadMinutes); put("repeat", repeat)
    put("allDay", allDay); put("completed", completed)
}

private fun calendarEntryFromJson(o: JSONObject): CalendarEntry? = runCatching {
    CalendarEntry(
        id = o.getLong("id"),
        title = o.getString("title"),
        description = o.optString("description"),
        date = LocalDate.parse(o.getString("date")),
        time = o.optString("time", "09:00"),
        category = o.optString("category", "عمومی"),
        type = o.optString("type", "event"),
        leadMinutes = o.optInt("lead", 0).coerceIn(0, 10080),
        repeat = o.optString("repeat", "هیچ‌وقت"),
        allDay = o.optBoolean("allDay", false),
        completed = o.optBoolean("completed", false)
    )
}.getOrNull()?.takeIf { it.id > 0 && it.title.isNotBlank() }

internal suspend fun loadCalendarEntries(context: Context): List<CalendarEntry> {
    val raw = context.calendarStore.data.first()[CalendarEntriesKey] ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                calendarEntryFromJson(array.getJSONObject(i))?.let(::add)
            }
        }.sortedWith(compareBy<CalendarEntry> { it.date }.thenBy { it.time })
    }.getOrDefault(emptyList())
}

private fun encodeCalendarEntries(entries: List<CalendarEntry>) =
    JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()

internal suspend fun saveCalendarEntries(context: Context, entries: List<CalendarEntry>) {
    context.calendarStore.edit { it[CalendarEntriesKey] = encodeCalendarEntries(entries) }
}

private fun calendarRequestCode(id: Long) = (id xor (id ushr 32)).toInt() and 0x3fffffff

private fun ensureCalendarChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CALENDAR_CHANNEL, "رویدادها و یادآورهای نونو", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "اعلان قرارها و یادآورهای تقویم"
            }
        )
    }
}

internal fun cancelCalendarAlarm(context: Context, id: Long) {
    val intent = Intent(context, CalendarEventReceiver::class.java).putExtra(CALENDAR_EXTRA_ID, id)
    val pending = PendingIntent.getBroadcast(
        context, calendarRequestCode(id), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    context.getSystemService(AlarmManager::class.java).cancel(pending)
    pending.cancel()
}

internal fun scheduleCalendarEntry(context: Context, entry: CalendarEntry) {
    cancelCalendarAlarm(context, entry.id)
    if (entry.completed || entry.allDay) return
    val time = if (entry.allDay) LocalTime.of(9, 0) else runCatching { LocalTime.parse(entry.time) }.getOrNull() ?: return
    var target = LocalDateTime.of(entry.date, time).minusMinutes(entry.leadMinutes.toLong())
    val now = LocalDateTime.now()
    if (!target.isAfter(now)) {
        target = when (entry.repeat) {
            "هر روز" -> target.plusDays(((java.time.Duration.between(target, now).toDays() / 1) + 1))
            "هر هفته" -> target.plusWeeks((java.time.Duration.between(target, now).toDays() / 7) + 1)
            "هر ماه" -> {
                var next = target
                while (!next.isAfter(now)) next = next.plusMonths(1)
                next
            }
            else -> return
        }
    }
    val intent = Intent(context, CalendarEventReceiver::class.java).putExtra(CALENDAR_EXTRA_ID, entry.id)
    val pending = PendingIntent.getBroadcast(
        context, calendarRequestCode(entry.id), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val trigger = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val manager = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    } else if (Build.VERSION.SDK_INT >= 23) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    } else {
        manager.set(AlarmManager.RTC_WAKEUP, trigger, pending)
    }
}

private fun scheduleCalendarSnooze(context: Context, entry: CalendarEntry) {
    val intent = Intent(context, CalendarEventReceiver::class.java)
        .putExtra(CALENDAR_EXTRA_ID, entry.id)
        .putExtra(CALENDAR_EXTRA_SNOOZE, true)
    val pending = PendingIntent.getBroadcast(
        context, SNOOZE_REQUEST_OFFSET + calendarRequestCode(entry.id), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val trigger = System.currentTimeMillis() + 10 * 60 * 1000L
    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}

class CalendarEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(CALENDAR_EXTRA_ID, 0L)
        if (id == 0L) return
        kotlinx.coroutines.runBlocking {
            val entry = loadCalendarEntries(context).firstOrNull { it.id == id } ?: return@runBlocking
            if (entry.completed) return@runBlocking
            ensureCalendarChannel(context)
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                val isSnooze = intent.getBooleanExtra(CALENDAR_EXTRA_SNOOZE, false)
                val title = if (isSnooze) "یادآوری دوباره: ${entry.title}" else entry.title
                val body = entry.description.ifBlank {
                    if (entry.type == "reminder") "زمان انجام یادآور رسیده است." else "زمان رویداد ثبت‌شده در تقویم رسیده است."
                }
                val snoozeIntent = Intent(context, CalendarEventReceiver::class.java)
                    .putExtra(CALENDAR_EXTRA_ID, entry.id)
                    .putExtra(CALENDAR_EXTRA_SNOOZE, true)
                val snooze = PendingIntent.getBroadcast(
                    context, SNOOZE_REQUEST_OFFSET + calendarRequestCode(entry.id), snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CALENDAR_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .addAction(android.R.drawable.ic_popup_reminder, "۱۰ دقیقه بعد", snooze)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(context).notify(calendarRequestCode(entry.id), notification)
            }
            if (!intent.getBooleanExtra(CALENDAR_EXTRA_SNOOZE, false) && entry.repeat != "هیچ‌وقت") {
                scheduleCalendarEntry(context, entry.copy(date = when (entry.repeat) {
                    "هر روز" -> entry.date.plusDays(1)
                    "هر هفته" -> entry.date.plusWeeks(1)
                    else -> entry.date.plusMonths(1)
                }))
            }
        }
    }
}

class CalendarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            kotlinx.coroutines.runBlocking {
                loadCalendarEntries(context).filterNot { it.completed }.forEach { scheduleCalendarEntry(context, it) }
            }
        }
    }
}

private fun parseCalendarDigits(value: String) = value.trim()
    .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
    .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')

private fun parseJalaliCalendarDate(value: String): LocalDate? {
    val parts = parseCalendarDigits(value).replace('-', '/').split('/')
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    val maxDay = if (m <= 6) 31 else if (m <= 11) 30 else if (isJalaliLeap(y)) 30 else 29
    if (m !in 1..12 || d !in 1..maxDay) return null
    return runCatching { jalaliToGregorian(y, m, d) }.getOrNull()
}

@Composable
fun CalendarModern(
    due: LocalDate, month: LocalDate, onMonth: (LocalDate) -> Unit,
    selected: LocalDate, onSelected: (LocalDate) -> Unit, onChangeDue: () -> Unit,
    pad: PaddingValues
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<CalendarEntry>()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEntry?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        entries = loadCalendarEntries(context)
        entries.filterNot { it.completed }.forEach { scheduleCalendarEntry(context, it) }
    }
    fun saveEntry(entry: CalendarEntry) {
        entries = (entries.filterNot { it.id == entry.id } + entry).sortedWith(compareBy<CalendarEntry> { it.date }.thenBy { it.time })
        scope.launch { saveCalendarEntries(context, entries) }
        scheduleCalendarEntry(context, entry)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val today = LocalDate.now()
    val displayed = jalaliParts(month)
    val first = jalaliToGregorian(displayed.year, displayed.month, 1)
    val offset = ((first.dayOfWeek.value + 1) % 7)
    val maxDay = if (displayed.month <= 6) 31 else if (displayed.month <= 11) 30 else if (isJalaliLeap(displayed.year)) 30 else 29
    val pregnancy = Pregnancy(due)
    val selectedAge = pregnancy.age(selected)
    val inPregnancy = selected >= pregnancy.lmp && selected <= due
    val selectedEntries = entries.filter { it.date == selected }.sortedBy { it.time }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        item {
            Text("تقویم بارداری", fontFamily = Fa, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("تقویم کاملاً شمسی؛ تاریخ‌ها، ماه‌ها و انتخاب روز بدون نمایش تاریخ میلادی.", fontFamily = Fa, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChildFriendly, null, tint = RoseDark) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("موعد تقریبی زایمان", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(jalali(due), fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RoseDark)
                    }
                    TextButton(onClick = onChangeDue) { Text("تغییر", fontFamily = Fa) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { val p = jalaliPreviousMonth(displayed); onMonth(jalaliToGregorian(p.year, p.month, 1)) }) { Icon(Icons.Default.ChevronRight, null) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(months[displayed.month - 1], fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(fa(displayed.year), fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { val p = jalaliNextMonth(displayed); onMonth(jalaliToGregorian(p.year, p.month, 1)) }) { Icon(Icons.Default.ChevronLeft, null) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
                        listOf("ش","ی","د","س","چ","پ","ج").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, fontFamily = Fa, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                    val cells = List(offset) { null } + List(maxDay) { it + 1 }
                    cells.chunked(7).forEach { weekDays ->
                        Row(Modifier.fillMaxWidth()) {
                            weekDays.forEach { day ->
                                Box(Modifier.weight(1f).height(58.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                                    if (day != null) {
                                        val date = jalaliToGregorian(displayed.year, displayed.month, day)
                                        val isSelected = date == selected
                                        val isToday = date == today
                                        val isDue = date == due
                                        val active = date >= pregnancy.lmp && date <= due
                                        val hasEntries = entries.any { it.date == date }
                                        Surface(
                                            Modifier.fillMaxSize().clickable { onSelected(date) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = when { isDue -> Rose; isSelected -> Blush; active -> MaterialTheme.colorScheme.surfaceVariant; else -> Color.Transparent }
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(fa(day), fontFamily = Fa, fontSize = 15.sp, fontWeight = if (isToday || isSelected || isDue) FontWeight.Bold else FontWeight.Normal, color = if (isDue) Color.White else MaterialTheme.colorScheme.onSurface)
                                                if (isToday) Text("امروز", fontFamily = Fa, fontSize = 7.sp, color = if (isDue) Color.White else RoseDark)
                                                if (hasEntries) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    repeat(minOf(3, entries.count { it.date == date })) {
                                                        Box(Modifier.size(4.dp).background(if (isDue) Color.White else Rose, CircleShape))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(7 - weekDays.size) { Box(Modifier.weight(1f).height(58.dp)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
                        Surface(Modifier.size(6.dp), shape = CircleShape, color = Rose)
                        Text("دارای رویداد یا یادآور", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(17.dp)) {
                    Text("تاریخ انتخاب‌شده", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(jalali(selected), fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = RoseDark, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.height(10.dp))
                    if (inPregnancy) {
                        Text("هفته ${fa(selectedAge.first)}، روز ${fa(selectedAge.second)} بارداری", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (selected == due) "این تاریخ، موعد تقریبی زایمان است." else if (selected < today) "این تاریخ در گذشته است." else "این تاریخ در بازه بارداری قرار دارد.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                    } else {
                        Text(if (selected < pregnancy.lmp) "این تاریخ پیش از شروع محاسبات بارداری است." else "این تاریخ بعد از موعد تقریبی زایمان است.", fontFamily = Fa, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("برنامه‌های این روز", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (selectedEntries.isEmpty()) "برای این روز هنوز برنامه‌ای ثبت نشده." else "${fa(selectedEntries.size)} مورد ثبت شده", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { editing = null; editorOpen = true }, shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("افزودن", fontFamily = Fa)
                }
            }
        }
        if (selectedEntries.isNotEmpty()) {
            items(selectedEntries, key = { it.id }) { entry ->
                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(11.dp), color = if (entry.type == "reminder") Sage else Blush, modifier = Modifier.size(38.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (entry.type == "reminder") Icons.Default.NotificationsActive else Icons.Default.Event, null, tint = RoseDark) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    (if (entry.allDay) "تمام‌روز" else fa(entry.time)) + "  •  " + entry.category + if (entry.type == "reminder") "  •  یادآور" else "  •  رویداد",
                                    fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(checked = entry.completed, onCheckedChange = { done ->
                                val updated = entry.copy(completed = done)
                                if (done) cancelCalendarAlarm(context, entry.id)
                                saveEntry(updated)
                            })
                            IconButton(onClick = { editing = entry; editorOpen = true }) { Icon(Icons.Default.Edit, "ویرایش", tint = RoseDark) }
                        }
                        if (entry.description.isNotBlank()) Text(entry.description, fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp, top = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { scheduleCalendarSnooze(context, entry) }, enabled = !entry.completed) {
                                Icon(Icons.Default.Snooze, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("۱۰ دقیقه بعد", fontFamily = Fa, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                cancelCalendarAlarm(context, entry.id)
                                entries = entries.filterNot { it.id == entry.id }
                                scope.launch { saveCalendarEntries(context, entries) }
                            }) { Text("حذف", fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) CalendarEntryEditor(
        initial = editing,
        initialDate = selected,
        onDismiss = { editorOpen = false; editing = null },
        onSave = { entry -> saveEntry(entry); editorOpen = false; editing = null }
    )
}

@Composable
private fun CalendarEntryEditor(
    initial: CalendarEntry?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEntry) -> Unit
) {
    val initialParts = jalaliParts(initial?.date ?: initialDate)
    var title by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var dateText by remember(initial?.id) { mutableStateOf("${initialParts.year}/${initialParts.month.toString().padStart(2,'0')}/${initialParts.day.toString().padStart(2,'0')}") }
    var time by remember(initial?.id) { mutableStateOf(initial?.time ?: "09:00") }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "پزشکی") }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "event") }
    var lead by remember(initial?.id) { mutableStateOf((initial?.leadMinutes ?: 15).toString()) }
    var repeat by remember(initial?.id) { mutableStateOf(initial?.repeat ?: "هیچ‌وقت") }
    var allDay by remember(initial?.id) { mutableStateOf(initial?.allDay ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    val categories = listOf("پزشکی", "مراقبتی", "شخصی", "عمومی")
    val repeats = listOf("هیچ‌وقت", "هر روز", "هر هفته", "هر ماه")
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (initial == null) "افزودن به تقویم" else "ویرایش برنامه", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "بستن") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "event", onClick = { type = "event" }, label = { Text("رویداد", fontFamily = Fa) }, leadingIcon = { Icon(Icons.Default.Event, null) })
                        FilterChip(selected = type == "reminder", onClick = { type = "reminder" }, label = { Text("یادآور", fontFamily = Fa) }, leadingIcon = { Icon(Icons.Default.NotificationsActive, null) })
                    }
                    OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان", fontFamily = Fa) }, shape = RoundedCornerShape(13.dp))
                    OutlinedTextField(dateText, { dateText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("تاریخ شمسی (مثلاً ۱۴۰۵/۰۸/۲۰)", fontFamily = Fa) }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null) }, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(time, { time = it }, modifier = Modifier.weight(1f), singleLine = true, enabled = !allDay, label = { Text("ساعت (۲۴ ساعته)", fontFamily = Fa) }, leadingIcon = { Icon(Icons.Default.Schedule, null) }, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(allDay, { allDay = it })
                            Text("تمام‌روز", fontFamily = Fa, fontSize = 10.sp)
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("دسته‌بندی: $category", fontFamily = Fa); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { DropdownMenuItem(text = { Text(it, fontFamily = Fa) }, onClick = { category = it; categoryMenu = false }) }
                        }
                    }
                    OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, label = { Text("توضیحات (اختیاری)", fontFamily = Fa) }, shape = RoundedCornerShape(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(lead, { lead = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("اعلان چند دقیقه زودتر؟", fontFamily = Fa) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(repeat, fontFamily = Fa); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
                            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                                repeats.forEach { DropdownMenuItem(text = { Text(it, fontFamily = Fa) }, onClick = { repeat = it; repeatMenu = false }) }
                            }
                        }
                    }
                    Text("یادآورها حتی بدون اینترنت روی همین گوشی اعلان می‌دهند.", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("انصراف", fontFamily = Fa) }
                        Button(onClick = {
                            val date = parseJalaliCalendarDate(dateText)
                            val normalizedTime = parseCalendarDigits(time).let { runCatching { LocalTime.parse(it).toString().take(5) }.getOrNull() }
                            val leadMinutes = parseCalendarDigits(lead).toIntOrNull()
                            if (title.isBlank()) error = "عنوان را وارد کن."
                            else if (date == null) error = "تاریخ شمسی معتبر وارد کن."
                            else if (!allDay && normalizedTime == null) error = "ساعت را به شکل ۰۹:۳۰ وارد کن."
                            else if (leadMinutes == null || leadMinutes !in 0..10080) error = "زمان اعلان باید بین صفر تا ۱۰۰۸۰ دقیقه باشد."
                            else onSave(CalendarEntry(
                                id = initial?.id ?: System.currentTimeMillis(),
                                title = title.trim(), description = description.trim(), date = date,
                                time = normalizedTime ?: "09:00", category = category, type = type,
                                leadMinutes = leadMinutes, repeat = repeat, allDay = allDay,
                                completed = initial?.completed ?: false
                            ))
                        }, shape = RoundedCornerShape(13.dp)) { Text("ذخیره", fontFamily = Fa, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
