package ir.herotux.pregnancytracker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Context.postpartumStore by preferencesDataStore(name = "nono_postpartum")
private val PostpartumRecordsKey = stringPreferencesKey("daily_records")
private val PostpartumDeliveryDateKey = stringPreferencesKey("delivery_date")

private data class PostpartumEntry(
    val date: LocalDate, val bleeding: String, val pain: Int, val mood: String,
    val feeding: String, val symptoms: Set<String>, val note: String
)

private fun encodePostpartum(entries: List<PostpartumEntry>): String {
    val array = JSONArray()
    entries.sortedByDescending { it.date }.forEach { entry ->
        val item = JSONObject()
        item.put("date", entry.date.toString())
        item.put("bleeding", entry.bleeding)
        item.put("pain", entry.pain)
        item.put("mood", entry.mood)
        item.put("feeding", entry.feeding)
        item.put("symptoms", JSONArray(entry.symptoms.toList()))
        item.put("note", entry.note)
        array.put(item)
    }
    return array.toString()
}

private fun decodePostpartum(raw: String?): List<PostpartumEntry> = runCatching {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.getJSONObject(index)
        val symptomArray = item.optJSONArray("symptoms") ?: JSONArray()
        PostpartumEntry(
            LocalDate.parse(item.getString("date")),
            item.optString("bleeding", "نامشخص"),
            item.optInt("pain", 0).coerceIn(0, 10),
            item.optString("mood", "معمولی"),
            item.optString("feeding", "ترکیبی"),
            (0 until symptomArray.length()).map { symptomArray.optString(it) }.toSet(),
            item.optString("note", "")
        )
    }.sortedByDescending { it.date }
}.getOrDefault(emptyList())

@Composable
internal fun PostpartumCareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<PostpartumEntry>()) }
    var deliveryDate by remember { mutableStateOf<LocalDate?>(null) }
    var bleeding by remember { mutableStateOf("کم") }
    var pain by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf("معمولی") }
    var feeding by remember { mutableStateOf("ترکیبی") }
    var symptoms by remember { mutableStateOf(emptySet<String>()) }
    var note by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editorDate by remember { mutableStateOf(LocalDate.now()) }
    var deliveryPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.postpartumStore.data.first()
        entries = decodePostpartum(prefs[PostpartumRecordsKey])
        deliveryDate = prefs[PostpartumDeliveryDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        loaded = true
    }
    fun persist(next: List<PostpartumEntry>) {
        val saved = next.sortedByDescending { it.date }.take(730)
        entries = saved
        scope.launch { context.postpartumStore.edit { it[PostpartumRecordsKey] = encodePostpartum(saved) } }
    }
    val today = LocalDate.now()
    val todayEntry = entries.firstOrNull { it.date == today }
    val postpartumDay = deliveryDate?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 }?.coerceAtLeast(1)

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 30.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ChildCare, null, tint = RoseDark, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("مراقبت پس از زایمان", fontFamily = Fa, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ثبت روند بهبودی مادر، خلق‌وخو و تغذیه نوزاد", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تاریخ زایمان", fontFamily = Fa, fontWeight = FontWeight.Bold)
                    Text(deliveryDate?.let(::jalali) ?: "هنوز ثبت نشده", fontFamily = Fa, color = RoseDark, fontSize = 16.sp)
                    OutlinedButton(onClick = { deliveryPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("ثبت یا ویرایش تاریخ زایمان", fontFamily = Fa)
                    }
                    if (postpartumDay != null) Text("امروز روز " + fa(postpartumDay) + " پس از زایمان است", fontFamily = Fa, fontWeight = FontWeight.Bold, color = RoseDark)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت وضعیت امروز", fontFamily = Fa, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (todayEntry != null) "برای امروز ثبت داری؛ می‌توانی آن را ویرایش کنی." else "ثبت کوتاه روزانه به نگهداری روند علائم برای گفت‌وگو با پزشک کمک می‌کند.", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        val old = todayEntry
                        editorDate = today
                        bleeding = old?.bleeding ?: "کم"
                        pain = old?.pain ?: 0
                        mood = old?.mood ?: "معمولی"
                        feeding = old?.feeding ?: "ترکیبی"
                        symptoms = old?.symptoms ?: emptySet()
                        note = old?.note ?: ""
                        editorOpen = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (todayEntry == null) "ثبت وضعیت امروز" else "ویرایش ثبت امروز", fontFamily = Fa)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("علائم هشدار پس از زایمان", fontFamily = Fa, fontWeight = FontWeight.Bold, color = RoseDark)
                    Text("در صورت درد قفسه سینه، تنگی نفس، تشنج یا غش، خونریزی شدید (مانند خیس شدن یک یا چند پد در یک ساعت)، تب ۳۸ درجه یا بیشتر، سردرد شدید یا اختلال دید، درد و تورم یک‌طرفه پا، یا افکار آسیب به خود یا نوزاد، فوراً کمک پزشکی بگیر. منتظر ثبت در برنامه نمان.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 21.sp)
                    Text("این فهرست کامل نیست و جایگزین ارزیابی پزشکی نیست.", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("تاریخچه ثبت‌ها", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (entries.isEmpty()) Text("هنوز گزارشی ثبت نشده است.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries, key = { it.date.toString() }) { entry ->
            Card(shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(jalali(entry.date), fontFamily = Fa, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { persist(entries.filterNot { it.date == entry.date }) }) {
                            Icon(Icons.Default.DeleteOutline, "حذف ثبت", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("خونریزی: " + entry.bleeding + " • درد: " + fa(entry.pain) + "/۱۰ • خلق: " + entry.mood, fontFamily = Fa, fontSize = 11.sp)
                    Text("تغذیه نوزاد: " + entry.feeding, fontFamily = Fa, fontSize = 11.sp)
                    if (entry.symptoms.isNotEmpty()) Text("علائم: " + entry.symptoms.joinToString("، "), fontFamily = Fa, fontSize = 11.sp)
                    if (entry.note.isNotBlank()) Text(entry.note, fontFamily = Fa, fontSize = 11.sp)
                    TextButton(onClick = {
                        editorDate = entry.date; bleeding = entry.bleeding; pain = entry.pain
                        mood = entry.mood; feeding = entry.feeding; symptoms = entry.symptoms
                        note = entry.note; editorOpen = true
                    }) { Text("ویرایش", fontFamily = Fa) }
                }
            }
        }
        item {
            Text("اطلاعات این بخش فقط روی همین دستگاه ذخیره می‌شود و فعلاً با Google یا پرونده خانواده همگام‌سازی نمی‌شود.", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (editorOpen) AlertDialog(
        onDismissRequest = { editorOpen = false },
        title = { Text("ثبت روزانه پس از زایمان", fontFamily = Fa, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تاریخ: " + jalali(editorDate), fontFamily = Fa, fontSize = 12.sp)
                Text("میزان خونریزی", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("ندارد", "کم", "متوسط", "زیاد").forEach { value ->
                        FilterChip(selected = bleeding == value, onClick = { bleeding = value }, label = { Text(value, fontFamily = Fa, fontSize = 10.sp) })
                    }
                }
                Text("شدت درد: " + fa(pain) + "/۱۰", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = pain.toFloat(), onValueChange = { pain = it.toInt() }, valueRange = 0f..10f, steps = 9)
                Text("خلق‌وخو", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("خوب", "معمولی", "غمگین", "مضطرب").forEach { value ->
                        FilterChip(selected = mood == value, onClick = { mood = value }, label = { Text(value, fontFamily = Fa, fontSize = 10.sp) })
                    }
                }
                Text("تغذیه نوزاد", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("شیر مادر", "شیر خشک", "ترکیبی").forEach { value ->
                        FilterChip(selected = feeding == value, onClick = { feeding = value }, label = { Text(value, fontFamily = Fa, fontSize = 10.sp) })
                    }
                }
                Text("علائم یا موارد قابل پیگیری", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                listOf("تب یا لرز", "سردرد", "درد لگن/شکم", "مشکل زخم یا بخیه", "درد پستان/نوک پستان", "مشکل ادرار", "یبوست").forEach { value ->
                    FilterChip(selected = value in symptoms, onClick = { symptoms = if (value in symptoms) symptoms - value else symptoms + value }, label = { Text(value, fontFamily = Fa, fontSize = 10.sp) })
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("یادداشت", fontFamily = Fa) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val entry = PostpartumEntry(editorDate, bleeding, pain, mood, feeding, symptoms, note.trim())
                persist(entries.filterNot { it.date == editorDate } + entry)
                editorOpen = false
            }) { Text("ذخیره", fontFamily = Fa) }
        },
        dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("انصراف", fontFamily = Fa) } }
    )

    if (deliveryPickerOpen) DateDialogModern(
        deliveryDate ?: LocalDate.now(),
        { date ->
            deliveryDate = date
            deliveryPickerOpen = false
            scope.launch { context.postpartumStore.edit { it[PostpartumDeliveryDateKey] = date.toString() } }
        },
        { deliveryPickerOpen = false }
    )
}
