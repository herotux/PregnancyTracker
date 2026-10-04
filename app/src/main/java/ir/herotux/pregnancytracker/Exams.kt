
package ir.herotux.pregnancytracker

import android.app.DatePickerDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@Composable
fun ExamsModern(
    records: List<ExamRecord>, pregnancy: Pregnancy,
    onAdd: () -> Unit, onEdit: (ExamRecord) -> Unit,
    onDelete: (ExamRecord) -> Unit, onOpen: (ExamRecord) -> Unit,
    pad: PaddingValues
) {
    var filter by remember { mutableStateOf("همه") }
    val filters = listOf("همه", "سونوگرافی", "آزمایش", "غربالگری")
    val shown = records.filter {
        filter == "همه" ||
        (filter == "آزمایش" && (it.type == "آزمایش خون" || it.type == "آزمایش ادرار")) ||
        it.type == filter
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp)
        ) {
            item {
                Text("آزمایش‌ها و سونوگرافی‌ها", fontFamily=Fa, fontSize=25.sp, fontWeight=FontWeight.Bold)
                Text("نتایج و گزارش‌های بارداری را یکجا و آفلاین نگه دار.", fontFamily=Fa, fontSize=13.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(top=4.dp))
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    filters.forEach {
                        FilterChip(selected=filter==it, onClick={filter=it},
                            label={Text(it,fontFamily=Fa,fontSize=12.sp)})
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Card(shape=RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=Blush)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Assignment,null,tint=RoseDark,modifier=Modifier.size(48.dp))
                            Text("هنوز گزارشی ثبت نشده",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=10.dp))
                            Text("نتیجه آزمایش یا سونوگرافی بعدی را اینجا ثبت کن.",fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(shown,key={it.id}) { record ->
                    val age=pregnancy.age(record.date)
                    Card(Modifier.fillMaxWidth().clickable{onOpen(record)},shape=RoundedCornerShape(20.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.Top) {
                            Surface(shape=RoundedCornerShape(14.dp),color=Blush,modifier=Modifier.size(46.dp)) {
                                Box(contentAlignment=Alignment.Center) {
                                    Icon(if(record.type=="سونوگرافی"||record.type=="آنومالی") Icons.Default.ImageSearch
                                        else if(record.type.startsWith("آزمایش")) Icons.Default.Science else Icons.Default.Assignment,
                                        null,tint=RoseDark)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.title.ifBlank{record.type},fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=15.sp)
                                Text(record.type+" • "+jalali(record.date),fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("هفته "+fa(age.first)+"، روز "+fa(age.second),fontFamily=Fa,fontSize=12.sp,color=RoseDark,modifier=Modifier.padding(top=4.dp))
                                if(record.result.isNotBlank()) Text(record.result,fontFamily=Fa,fontSize=13.sp,maxLines=2,modifier=Modifier.padding(top=5.dp))
                            }
                            Icon(Icons.Default.ChevronLeft,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick=onAdd,modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=18.dp)) {
            Icon(Icons.Default.Add,contentDescription="افزودن")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorDialog(
    initial: ExamRecord?, onSave:(ExamRecord)->Unit,
    onDelete:((ExamRecord)->Unit)?, onCancel:()->Unit
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var date by remember{mutableStateOf(initial?.date ?: LocalDate.now())}
    var type by remember{mutableStateOf(initial?.type ?: "سونوگرافی")}
    var title by remember{mutableStateOf(initial?.title ?: "")}
    var result by remember{mutableStateOf(initial?.result ?: "")}
    var notes by remember{mutableStateOf(initial?.notes ?: "")}
    var attachment by remember{mutableStateOf(initial?.attachmentUri)}
    var typeExpanded by remember{mutableStateOf(false)}

    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        uri?.let{
            runCatching{context.contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            attachment=it.toString()
        }
    }

    AlertDialog(
        onDismissRequest=onCancel,
        title={Text(if(initial==null)"ثبت گزارش جدید" else "ویرایش گزارش",fontFamily=Fa,fontWeight=FontWeight.Bold)},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                ExposedDropdownMenuBox(expanded=typeExpanded,onExpandedChange={typeExpanded=!typeExpanded}){
                    OutlinedTextField(value=type,onValueChange={},readOnly=true,label={Text("نوع گزارش",fontFamily=Fa)},
                        trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded)},modifier=Modifier.fillMaxWidth().menuAnchor(),
                        shape=RoundedCornerShape(14.dp))
                    ExposedDropdownMenu(expanded=typeExpanded,onDismissRequest={typeExpanded=false}){
                        examTypes.forEach{label->DropdownMenuItem(text={Text(label,fontFamily=Fa)},onClick={type=label;typeExpanded=false})}
                    }
                }
                OutlinedButton(onClick={
                    DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).show()
                },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.Event,null);Spacer(Modifier.width(7.dp));Text(jalali(date)+"  •  "+date,fontFamily=Fa)
                }
                OutlinedTextField(title,{title=it},label={Text("عنوان / نام آزمایش",fontFamily=Fa)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp))
                OutlinedTextField(result,{result=it},label={Text("نتیجه",fontFamily=Fa)},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5,shape=RoundedCornerShape(14.dp))
                OutlinedTextField(notes,{notes=it},label={Text("یادداشت پزشک",fontFamily=Fa)},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,shape=RoundedCornerShape(14.dp))
                OutlinedButton(onClick={imagePicker.launch(arrayOf("image/*","application/pdf"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Icon(if(attachment==null)Icons.Default.AttachFile else Icons.Default.Attachment,null);Spacer(Modifier.width(7.dp))
                    Text(if(attachment==null)"پیوست گزارش / تصویر" else "پیوست انتخاب شد",fontFamily=Fa)
                }
                if(attachment!=null) Text("پیوست ذخیره شده است.",fontFamily=Fa,fontSize=11.sp,color=RoseDark)
            }
        },
        confirmButton={
            Button(onClick={
                onSave(ExamRecord(initial?.id ?: System.currentTimeMillis(),date,type,title.trim(),result.trim(),notes.trim(),attachment))
            },enabled=title.isNotBlank()||result.isNotBlank(),shape=RoundedCornerShape(14.dp)){Text("ذخیره",fontFamily=Fa)}
        },
        dismissButton={
            Row{
                if(initial!=null&&onDelete!=null) TextButton(onClick={onDelete(initial)}){Text("حذف",fontFamily=Fa,color=MaterialTheme.colorScheme.error)}
                TextButton(onClick=onCancel){Text("لغو",fontFamily=Fa)}
            }
        }
    )
}

@Composable
fun ExamDetailDialog(record:ExamRecord,pregnancy:Pregnancy,onEdit:()->Unit,onClose:()->Unit){
    val age=pregnancy.age(record.date)
    AlertDialog(
        onDismissRequest=onClose,
        icon={Icon(Icons.Default.Assignment,null,tint=RoseDark)},
        title={Text(record.title.ifBlank{record.type},fontFamily=Fa,fontWeight=FontWeight.Bold)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(record.type+" • "+jalali(record.date),fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("هفته "+fa(age.first)+"، روز "+fa(age.second)+" بارداری",fontFamily=Fa,fontWeight=FontWeight.Bold,color=RoseDark)
                if(record.result.isNotBlank()){Text("نتیجه",fontFamily=Fa,fontWeight=FontWeight.Bold);Text(record.result,fontFamily=Fa,fontSize=13.sp,lineHeight=20.sp)}
                if(record.notes.isNotBlank()){Text("یادداشت پزشک",fontFamily=Fa,fontWeight=FontWeight.Bold);Text(record.notes,fontFamily=Fa,fontSize=13.sp,lineHeight=20.sp)}
                if(record.attachmentUri!=null) Text("پیوست گزارش ثبت شده است.",fontFamily=Fa,fontSize=12.sp,color=RoseDark)
            }
        },
        confirmButton={Button(onClick=onEdit,shape=RoundedCornerShape(14.dp)){Text("ویرایش",fontFamily=Fa)}},
        dismissButton={TextButton(onClick=onClose){Text("بستن",fontFamily=Fa)}}
    )
}