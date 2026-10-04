package ir.herotux.pregnancytracker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class ExamRecord(
    val id: Long,
    val date: LocalDate,
    val type: String,
    val title: String,
    val result: String,
    val notes: String,
    val attachmentUri: String? = null
)

private val examTypes = listOf("سونوگرافی", "آزمایش خون", "آزمایش ادرار", "غربالگری", "آنومالی", "سایر")
private val examFilters = listOf("همه", "سونوگرافی", "آزمایش", "غربالگری", "آنومالی")

private fun ExamRecord.toJson() = JSONObject().apply {
    put("id", id); put("date", date.toString()); put("type", type)
    put("title", title); put("result", result); put("notes", notes)
    put("attachmentUri", attachmentUri ?: "")
}

private fun examFromJson(o: JSONObject): ExamRecord? = runCatching {
    ExamRecord(
        o.optLong("id"), LocalDate.parse(o.getString("date")),
        o.optString("type", "سایر"), o.optString("title"),
        o.optString("result"), o.optString("notes"),
        o.optString("attachmentUri").takeIf { it.isNotBlank() }
    )
}.getOrNull()

internal fun loadExamRecords(raw: String?): List<ExamRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        buildList {
            for (i in 0 until a.length()) examFromJson(a.getJSONObject(i))?.let(::add)
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}

internal fun encodeExamRecords(items: List<ExamRecord>): String =
    JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

private fun examIcon(type: String) = when (type) {
    "سونوگرافی", "آنومالی" -> Icons.Default.ImageSearch
    "آزمایش خون", "آزمایش ادرار" -> Icons.Default.Science
    "غربالگری" -> Icons.Default.FactCheck
    else -> Icons.Default.Assignment
}

private fun examTypeShort(type: String) = when (type) {
    "آزمایش خون", "آزمایش ادرار" -> "آزمایش"
    else -> type
}

@Composable
fun ExamsModern(
    records: List<ExamRecord>, pregnancy: Pregnancy,
    onAdd: () -> Unit, onEdit: (ExamRecord) -> Unit,
    onDelete: (ExamRecord) -> Unit, onOpen: (ExamRecord) -> Unit,
    pad: PaddingValues
) {
    var filter by remember { mutableStateOf("همه") }
    var query by remember { mutableStateOf("") }

    val shown = records
        .filter {
            filter == "همه" ||
                (filter == "آزمایش" && it.type.startsWith("آزمایش")) ||
                it.type == filter
        }
        .filter {
            query.isBlank() ||
                it.title.contains(query, true) ||
                it.result.contains(query, true) ||
                it.type.contains(query, true)
        }
        .sortedByDescending { it.date }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "پرونده پزشکی",
                            fontFamily = Fa, fontSize = 26.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "آزمایش‌ها و سونوگرافی‌های بارداری",
                            fontFamily = Fa, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Blush,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Assignment, null, tint = RoseDark)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = Fa, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                            )
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    examFilters.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item, fontFamily = Fa, fontSize = 12.sp) }
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (shown.isEmpty()) "گزارشی وجود ندارد" else "گزارش‌های ثبت‌شده",
                        fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (shown.isNotEmpty()) {
                        Text(
                            fa(shown.size) + " مورد",
                            fontFamily = Fa, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    EmptyExamsCard(query.isNotBlank(), onAdd)
                }
            } else {
                items(shown, key = { it.id }) { record ->
                    ExamRecordCard(record, pregnancy) { onOpen(record) }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("ثبت گزارش", fontFamily = Fa, fontWeight = FontWeight.Bold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun EmptyExamsCard(filtered: Boolean, onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Blush),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(68.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assignment, null, tint = RoseDark, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                if (filtered) "نتیجه‌ای پیدا نشد" else "پرونده هنوز خالی است",
                fontFamily = Fa, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                if (filtered) "فیلتر یا عبارت جستجو را تغییر بده."
                else "اولین آزمایش یا سونوگرافی را ثبت کن تا سابقه بارداری اینجا مرتب بماند.",
                fontFamily = Fa, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (!filtered) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.padding(top = 16.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ثبت اولین گزارش", fontFamily = Fa)
                }
            }
        }
    }
}

@Composable
private fun ExamRecordCard(record: ExamRecord, pregnancy: Pregnancy, onClick: () -> Unit) {
    val age = pregnancy.age(record.date)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = Blush,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(examIcon(record.type), null, tint = RoseDark, modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.title.ifBlank { record.type },
                        fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    if (record.attachmentUri != null) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    examTypeShort(record.type) + "  •  " + jalali(record.date),
                    fontFamily = Fa, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Blush
                    ) {
                        Text(
                            "هفته " + fa(age.first) + "، روز " + fa(age.second),
                            fontFamily = Fa, fontSize = 11.sp, color = RoseDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (record.result.isNotBlank()) {
                        Text(
                            record.result.replace("\n", " "),
                            fontFamily = Fa, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronLeft, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 13.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorDialog(
    initial: ExamRecord?, onSave: (ExamRecord) -> Unit,
    onDelete: ((ExamRecord) -> Unit)?, onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var type by remember { mutableStateOf(initial?.type ?: "سونوگرافی") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var result by remember { mutableStateOf(initial?.result ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var attachment by remember { mutableStateOf(initial?.attachmentUri) }
    var typeExpanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            attachment = it.toString()
        }
    }

    if (datePickerOpen) {
        PersianDatePickerDialog(
            initialDate = date,
            onSelect = { date = it; datePickerOpen = false },
            onDismiss = { datePickerOpen = false }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Blush, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (initial == null) Icons.Default.Add else Icons.Default.Edit, null, tint = RoseDark)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (initial == null) "ثبت گزارش جدید" else "ویرایش گزارش",
                            fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 19.sp
                        )
                        Text("اطلاعات را برای نگهداری در پرونده وارد کن",
                            fontFamily = Fa, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "بستن") }
                }
                HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Text("نوع و تاریخ", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded }
                    ) {
                        OutlinedTextField(
                            value = type, onValueChange = {}, readOnly = true,
                            label = { Text("نوع گزارش", fontFamily = Fa) },
                            leadingIcon = { Icon(examIcon(type), null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(15.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            examTypes.forEach { label ->
                                DropdownMenuItem(
                                    leadingIcon = { Icon(examIcon(label), null) },
                                    text = { Text(label, fontFamily = Fa) },
                                    onClick = { type = label; typeExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { datePickerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("تاریخ گزارش", fontFamily = Fa, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(jalali(date), fontFamily = Fa, fontSize = 13.sp)
                        }
                    }

                    Text("جزئیات", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        title, { title = it }, label = { Text("عنوان / نام آزمایش", fontFamily = Fa) },
                        leadingIcon = { Icon(Icons.Default.Title, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                    OutlinedTextField(
                        result, { result = it }, label = { Text("نتیجه", fontFamily = Fa) },
                        leadingIcon = { Icon(Icons.Default.Notes, null) },
                        modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 7,
                        shape = RoundedCornerShape(15.dp)
                    )
                    OutlinedTextField(
                        notes, { notes = it }, label = { Text("یادداشت پزشک", fontFamily = Fa) },
                        leadingIcon = { Icon(Icons.Default.EditNote, null) },
                        modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                        shape = RoundedCornerShape(15.dp)
                    )
                    OutlinedButton(
                        onClick = { imagePicker.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(if (attachment == null) Icons.Default.AttachFile else Icons.Default.Attachment, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (attachment == null) "پیوست تصویر یا گزارش PDF" else "پیوست انتخاب شده",
                            fontFamily = Fa
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initial != null && onDelete != null) {
                        TextButton(onClick = { onDelete(initial) }) {
                            Text("حذف", fontFamily = Fa, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("لغو", fontFamily = Fa) }
                    Button(
                        onClick = {
                            onSave(
                                ExamRecord(
                                    initial?.id ?: System.currentTimeMillis(),
                                    date, type, title.trim(), result.trim(), notes.trim(), attachment
                                )
                            )
                        },
                        enabled = title.isNotBlank() || result.isNotBlank(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ذخیره گزارش", fontFamily = Fa, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    }
}

@Composable
fun ExamDetailDialog(
    record: ExamRecord, pregnancy: Pregnancy, onEdit: () -> Unit, onClose: () -> Unit
) {
    val age = pregnancy.age(record.date)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(15.dp), color = Blush, modifier = Modifier.size(50.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(examIcon(record.type), null, tint = RoseDark, modifier = Modifier.size(25.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title.ifBlank { record.type }, fontFamily = Fa,
                            fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(record.type + "  •  " + jalali(record.date), fontFamily = Fa, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "بستن") }
                }

                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Blush, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = RoseDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "هفته " + fa(age.first) + "، روز " + fa(age.second) + " بارداری",
                            fontFamily = Fa, fontWeight = FontWeight.Bold, color = RoseDark, fontSize = 13.sp
                        )
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    if (record.result.isNotBlank()) {
                        DetailSection("نتیجه", record.result)
                    }
                    if (record.notes.isNotBlank()) {
                        DetailSection("یادداشت پزشک", record.notes)
                    }
                    if (record.attachmentUri != null) {
                        Surface(
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null)
                                Spacer(Modifier.width(8.dp))
                                Text("گزارش یا تصویر پیوست شده است", fontFamily = Fa, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("بستن", fontFamily = Fa) }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ویرایش", fontFamily = Fa)
                    }
                }
            }
        }
    }
    }
}


@Composable
fun PersianDatePickerDialog(
    initialDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var displayed by remember(initialDate) { mutableStateOf(jalaliParts(initialDate)) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Blush,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CalendarMonth, null, tint = RoseDark)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("انتخاب تاریخ", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("تقویم شمسی", fontFamily = Fa, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "بستن")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            displayed = jalaliPreviousMonth(displayed)
                        }) {
                            Icon(Icons.Default.ChevronRight, "ماه قبل")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                months[displayed.month - 1],
                                fontFamily = Fa, fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                fa(displayed.year),
                                fontFamily = Fa, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            displayed = jalaliNextMonth(displayed)
                        }) {
                            Icon(Icons.Default.ChevronLeft, "ماه بعد")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth()) {
                        listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(it, fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    val first = jalaliToGregorian(displayed.year, displayed.month, 1)
                    val offset = ((first.dayOfWeek.value + 1) % 7)
                    val maxDay = if (displayed.month <= 6) 31 else if (displayed.month <= 11) 30 else if (isJalaliLeap(displayed.year)) 30 else 29
                    val cells = offset + maxDay

                    for (row in 0 until ((cells + 6) / 7)) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val index = row * 7 + col
                                if (index < offset || index >= offset + maxDay) {
                                    Box(Modifier.weight(1f).height(42.dp))
                                } else {
                                    val day = index - offset + 1
                                    val cellDate = jalaliToGregorian(displayed.year, displayed.month, day)
                                    val selected = cellDate == initialDate
                                    Box(
                                        Modifier.weight(1f).height(42.dp).padding(2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSelect(cellDate) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (selected) RoseDark else androidx.compose.ui.graphics.Color.Transparent,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    fa(day),
                                                    fontFamily = Fa,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selected) androidx.compose.ui.graphics.Color.White
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        jalali(initialDate),
                        fontFamily = Fa, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

private data class JalaliParts(val year: Int, val month: Int, val day: Int)

private fun jalaliParts(g: LocalDate): JalaliParts {
    val md = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
    val gy = g.year - 1600
    val gm = g.monthValue - 1
    val gd = g.dayOfMonth - 1
    var days = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400 + gd + md[gm]
    if (gm > 1 && ((g.year % 4 == 0 && g.year % 100 != 0) || g.year % 400 == 0)) days++
    var j = days - 79
    val n = j / 12053
    j %= 12053
    var jy = 979 + 33 * n + 4 * (j / 1461)
    j %= 1461
    if (j >= 366) {
        jy += (j - 1) / 365
        j = (j - 1) % 365
    }
    val jm = if (j < 186) 1 + j / 31 else 7 + (j - 186) / 30
    val jd = 1 + if (j < 186) j % 31 else (j - 186) % 30
    return JalaliParts(jy, jm, jd)
}

private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
    var jy0 = jy - 979
    var days = 365 * jy0 + (jy0 / 33) * 8 + ((jy0 % 33) + 3) / 4
    days += if (jm <= 6) (jm - 1) * 31 else 186 + (jm - 7) * 30
    days += jd - 1
    val gDays = days + 79
    var gy = 1600 + 400 * (gDays / 146097)
    var rem = gDays % 146097
    var leap = true
    if (rem >= 36525) {
        rem--
        gy += 100 * (rem / 36524)
        rem %= 36524
        if (rem >= 365) rem++
        else leap = false
    }
    gy += 4 * (rem / 1461)
    rem %= 1461
    if (rem >= 366) {
        leap = false
        rem--
        gy += rem / 365
        rem %= 365
    }
    val gd = rem + 1
    val monthLengths = intArrayOf(
        31, if (leap) 29 else 28, 31, 30, 31, 30,
        31, 31, 30, 31, 30, 31
    )
    var gm = 1
    var day = gd
    while (day > monthLengths[gm - 1]) {
        day -= monthLengths[gm - 1]
        gm++
    }
    return LocalDate.of(gy, gm, day)
}

private fun isJalaliLeap(year: Int): Boolean =
    jalaliToGregorian(year + 1, 1, 1).minusDays(1).let { jalaliParts(it).year == year && jalaliParts(it).month == 12 && jalaliParts(it).day == 30 }

private fun jalaliPreviousMonth(p: JalaliParts): JalaliParts =
    if (p.month == 1) JalaliParts(p.year - 1, 12, minOf(p.day, if (isJalaliLeap(p.year - 1)) 30 else 29))
    else JalaliParts(p.year, p.month - 1, minOf(p.day, if (p.month - 1 <= 6) 31 else 30))

private fun jalaliNextMonth(p: JalaliParts): JalaliParts {
    val year = if (p.month == 12) p.year + 1 else p.year
    val month = if (p.month == 12) 1 else p.month + 1
    val max = if (month <= 6) 31 else if (month <= 11) 30 else if (isJalaliLeap(year)) 30 else 29
    return JalaliParts(year, month, minOf(p.day, max))
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            body,
            fontFamily = Fa, fontSize = 13.sp, lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
