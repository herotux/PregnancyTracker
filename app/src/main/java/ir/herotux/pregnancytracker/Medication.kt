package ir.herotux.pregnancytracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val Context.medicationStore by preferencesDataStore(name = "pregnancy_medications")
private val MedicationsKey = stringPreferencesKey("medications")
private const val MED_CHANNEL = "pregnancy_medications"
private const val EXTRA_ID = "medication_id"
private const val EXTRA_NAME = "medication_name"
private const val EXTRA_DOSAGE = "medication_dosage"
private const val EXTRA_TIME = "medication_time"
private const val EXTRA_SLOT = "medication_slot"

data class MedicationRecord(val id:Long,val name:String,val dosage:String,val times:List<String>,val enabled:Boolean=true)

private fun normalizeMedicationTime(value:String):String?{
    val s=value.trim().replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9').replace('：',':').replace('.',':')
    val m=Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s)?:return null
    val h=m.groupValues[1].toIntOrNull()?:return null;val min=m.groupValues[2].toIntOrNull()?:return null
    return if(h in 0..23&&min in 0..59)"%02d:%02d".format(h,min) else null
}
private fun MedicationRecord.toJson()=JSONObject().apply{put("id",id);put("name",name);put("dosage",dosage);put("enabled",enabled);put("times",JSONArray(times))}
private fun fromMedicationJson(o:JSONObject):MedicationRecord?=runCatching{
    val a=o.optJSONArray("times")?:JSONArray();val ts=buildList{for(i in 0 until a.length())normalizeMedicationTime(a.optString(i))?.let(::add)}.distinct().sorted()
    MedicationRecord(o.optLong("id"),o.optString("name"),o.optString("dosage"),ts,o.optBoolean("enabled",true))
}.getOrNull()?.takeIf{it.id!=0L&&it.name.isNotBlank()&&it.times.isNotEmpty()}
internal suspend fun loadMedications(context:Context):List<MedicationRecord>{
    val raw=context.medicationStore.data.first()[MedicationsKey]?:return emptyList()
    return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length())fromMedicationJson(a.getJSONObject(i))?.let(::add)}.sortedBy{it.name}}.getOrDefault(emptyList())
}
private fun encodeMedications(items:List<MedicationRecord>)=JSONArray().apply{items.forEach{put(it.toJson())}}.toString()
internal suspend fun saveMedications(context:Context,items:List<MedicationRecord>){context.medicationStore.edit{it[MedicationsKey]=encodeMedications(items)}}

private fun alarmRequestCode(id:Long,slot:Int)=(((id xor(id ushr 32)).toInt()and 0x1fffffff)*8)+slot
private fun ensureMedicationChannel(context:Context){if(Build.VERSION.SDK_INT>=26)context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(MED_CHANNEL,"یادآوری مصرف دارو",NotificationManager.IMPORTANCE_HIGH))}
internal fun cancelMedicationAlarms(context:Context,id:Long){
    val am=context.getSystemService(AlarmManager::class.java)
    repeat(8){slot->val pi=PendingIntent.getBroadcast(context,alarmRequestCode(id,slot),Intent(context,MedicationAlarmReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);am.cancel(pi);pi.cancel()}
}
internal fun scheduleMedicationAlarms(context:Context,m:MedicationRecord){
    cancelMedicationAlarms(context,m.id);if(!m.enabled)return;ensureMedicationChannel(context);val am=context.getSystemService(AlarmManager::class.java)
    m.times.take(8).forEachIndexed{slot,raw->
        val t=normalizeMedicationTime(raw)?:return@forEachIndexed;var next=LocalDateTime.now().toLocalDate().atTime(LocalTime.parse(t));if(!next.isAfter(LocalDateTime.now()))next=next.plusDays(1)
        val pi=PendingIntent.getBroadcast(context,alarmRequestCode(m.id,slot),Intent(context,MedicationAlarmReceiver::class.java).apply{putExtra(EXTRA_ID,m.id);putExtra(EXTRA_NAME,m.name);putExtra(EXTRA_DOSAGE,m.dosage);putExtra(EXTRA_TIME,t);putExtra(EXTRA_SLOT,slot)},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)else am.set(AlarmManager.RTC_WAKEUP,trigger,pi)
    }
}
internal fun rescheduleAllMedicationAlarms(context:Context,items:List<MedicationRecord>)=items.forEach{scheduleMedicationAlarms(context,it)}

class MedicationAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val id=intent.getLongExtra(EXTRA_ID,0);if(id==0L)return
        val name=intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank{"دارو"};val dosage=intent.getStringExtra(EXTRA_DOSAGE).orEmpty();val time=intent.getStringExtra(EXTRA_TIME).orEmpty();val slot=intent.getIntExtra(EXTRA_SLOT,0)
        ensureMedicationChannel(context)
        if(Build.VERSION.SDK_INT<33||androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){
            val body=if(dosage.isBlank())"ساعت "+fa(time) else dosage+" — ساعت "+fa(time)
            androidx.core.app.NotificationManagerCompat.from(context).notify((id xor slot.toLong()).toInt(),androidx.core.app.NotificationCompat.Builder(context,MED_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("زمان مصرف "+name).setContentText(body).setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        }
        kotlinx.coroutines.runBlocking{loadMedications(context).firstOrNull{it.id==id&&it.enabled&&slot<it.times.size}?.let{m->
            val t=normalizeMedicationTime(m.times[slot])?:return@runBlocking;val next=LocalDateTime.now().toLocalDate().plusDays(1).atTime(LocalTime.parse(t));val am=context.getSystemService(AlarmManager::class.java)
            val pi=PendingIntent.getBroadcast(context,alarmRequestCode(m.id,slot),Intent(context,MedicationAlarmReceiver::class.java).apply{putExtra(EXTRA_ID,m.id);putExtra(EXTRA_NAME,m.name);putExtra(EXTRA_DOSAGE,m.dosage);putExtra(EXTRA_TIME,t);putExtra(EXTRA_SLOT,slot)},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val trigger=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)else am.set(AlarmManager.RTC_WAKEUP,trigger,pi)
        }}
    }
}
class MedicationBootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==Intent.ACTION_BOOT_COMPLETED||intent.action=="android.intent.action.TIME_SET"||intent.action=="android.intent.action.TIMEZONE_CHANGED"||intent.action==AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)kotlinx.coroutines.runBlocking{rescheduleAllMedicationAlarms(context,loadMedications(context))}}}

@Composable
fun MedicationEditorDialog(initial:MedicationRecord?,onSave:(MedicationRecord)->Unit,onDelete:((MedicationRecord)->Unit)?,onCancel:()->Unit){
    var name by remember{mutableStateOf(initial?.name?:"")};var dosage by remember{mutableStateOf(initial?.dosage?:"")};var times by remember{mutableStateOf(initial?.times?:listOf("08:00"))};var newTime by remember{mutableStateOf("")};var enabled by remember{mutableStateOf(initial?.enabled?:true)};var error by remember{mutableStateOf<String?>(null)}
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxWidth().padding(horizontal=14.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(20.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Blush,modifier=Modifier.size(46.dp)){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Medication,null,tint=RoseDark)}};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(if(initial==null)"افزودن دارو" else "ویرایش دارو",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=19.sp);Text("داروی تجویز شده توسط پزشک را وارد کن",fontFamily=Fa,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=onCancel){Icon(Icons.Default.Close,"بستن")}}
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
                OutlinedTextField(name,{name=it},label={Text("نام دارو",fontFamily=Fa)},leadingIcon={Icon(Icons.Default.Medication,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(15.dp))
                OutlinedTextField(dosage,{dosage=it},label={Text("دوز / مقدار مصرف",fontFamily=Fa)},leadingIcon={Icon(Icons.Default.MedicationLiquid,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(15.dp))
                Text("ساعت‌های مصرف روزانه",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=14.sp)
                times.forEachIndexed{index,time->Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(time,{v->times=times.toMutableList().also{it[index]=v}},label={Text("ساعت "+fa(index+1),fontFamily=Fa)},leadingIcon={Icon(Icons.Default.Schedule,null)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true,shape=RoundedCornerShape(15.dp));IconButton(onClick={if(times.size>1)times=times.toMutableList().also{it.removeAt(index)}}){Icon(Icons.Default.RemoveCircleOutline,"حذف ساعت",tint=MaterialTheme.colorScheme.error)}}}
                Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(newTime,{newTime=it},label={Text("ساعت جدید، مثلاً ۱۴:۳۰",fontFamily=Fa)},leadingIcon={Icon(Icons.Default.AddAlarm,null)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true,shape=RoundedCornerShape(15.dp));IconButton(onClick={val t=normalizeMedicationTime(newTime);if(t!=null&&t !in times){times=(times+t).sorted();newTime="";error=null}else error="ساعت را به شکل ۰۸:۰۰ وارد کن."}){Icon(Icons.Default.AddCircle,"افزودن ساعت",tint=RoseDark)}}
                Text("هشدار هر روز در این ساعت‌ها فعال می‌شود.",fontFamily=Fa,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clickable{enabled=!enabled},verticalAlignment=Alignment.CenterVertically){Switch(enabled,{enabled=it});Spacer(Modifier.width(8.dp));Text("یادآوری این دارو فعال باشد",fontFamily=Fa,fontSize=13.sp)}
                error?.let{Text(it,fontFamily=Fa,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            }
            Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(initial!=null&&onDelete!=null)TextButton(onClick={onDelete(initial)}){Text("حذف دارو",fontFamily=Fa,color=MaterialTheme.colorScheme.error)};Spacer(Modifier.weight(1f));TextButton(onClick=onCancel){Text("لغو",fontFamily=Fa)};Button(onClick={val ts=times.mapNotNull(::normalizeMedicationTime).distinct().sorted();if(name.isBlank()||ts.isEmpty())error="نام دارو و حداقل یک ساعت مصرف را وارد کن."else onSave(MedicationRecord(initial?.id?:System.currentTimeMillis(),name.trim(),dosage.trim(),ts,enabled))},shape=RoundedCornerShape(14.dp)){Text("ذخیره دارو",fontFamily=Fa,fontWeight=FontWeight.Bold)}}
        }
    }}}
}

@Composable
fun MedicationsSection(medications:List<MedicationRecord>,onAdd:()->Unit,onEdit:(MedicationRecord)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Medication,null,tint=RoseDark,modifier=Modifier.size(19.dp));Spacer(Modifier.width(7.dp));Text("داروهای تجویز شده",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.weight(1f));TextButton(onClick=onAdd){Icon(Icons.Default.Add,null);Spacer(Modifier.width(3.dp));Text("افزودن",fontFamily=Fa)}}
        if(medications.isEmpty())Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Blush),modifier=Modifier.fillMaxWidth().clickable(onClick=onAdd)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AddAlarm,null,tint=RoseDark);Spacer(Modifier.width(9.dp));Text("هنوز دارویی ثبت نشده؛ داروهای پزشک و ساعت مصرفشان را اضافه کن.",fontFamily=Fa,fontSize=12.sp,lineHeight=19.sp)}}else medications.forEach{m->Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),modifier=Modifier.fillMaxWidth().clickable{onEdit(m)}){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Blush,modifier=Modifier.size(40.dp)){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Medication,null,tint=RoseDark,modifier=Modifier.size(21.dp))}};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(m.name,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=14.sp);if(m.dosage.isNotBlank())Text(m.dosage,fontFamily=Fa,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("هر روز: "+m.times.joinToString("، "){fa(it)},fontFamily=Fa,fontSize=11.sp,color=RoseDark)};Icon(if(m.enabled)Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,null,tint=if(m.enabled)RoseDark else MaterialTheme.colorScheme.outline)}}}
    }
}