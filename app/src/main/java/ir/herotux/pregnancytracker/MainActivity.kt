package ir.herotux.pregnancytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val Fa = FontFamily.SansSerif
private val months = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")

private fun fa(s:String)=s.map{if(it in '0'..'9') ('۰'.code+it.code-'0'.code).toChar() else it}.joinToString("")
private fun fa(n:Int)=fa(n.toString())

private fun jalali(g:LocalDate):String{
    val md=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
    val gy=g.year-1600; val gm=g.monthValue-1; val gd=g.dayOfMonth-1
    var days=365*gy+(gy+3)/4-(gy+99)/100+(gy+399)/400+gd+md[gm]
    if(gm>1 && ((g.year%4==0&&g.year%100!=0)||g.year%400==0)) days++
    var j=days-79; val n=j/12053; j%=12053
    var jy=979+33*n+4*(j/1461); j%=1461
    if(j>=366){jy+=(j-1)/365;j=(j-1)%365}
    val jm=if(j<186)1+j/31 else 7+(j-186)/30
    val jd=1+if(j<186)j%31 else(j-186)%30
    return "${fa(jd)} ${months[jm-1]} ${fa(jy)}"
}

data class Pregnancy(val due:LocalDate){
    val lmp get()=due.minusDays(280)
    fun age(now:LocalDate=LocalDate.now()):Pair<Int,Int>{
        val days=ChronoUnit.DAYS.between(lmp,now).coerceIn(0,280)
        return (days/7).toInt() to (days%7).toInt()
    }
    fun percent(now:LocalDate=LocalDate.now())=
        ChronoUnit.DAYS.between(lmp,now).coerceIn(0,280)/280f
}

private val sizes=listOf("دانهٔ خشخاش","دانهٔ کنجد","دانهٔ سیب","عدس","بلوبری","تمشک","انگور","توت‌فرنگی","انجیر","لیمو","هلو","لیموترش","سیب","آووکادو","گلابی","فلفل دلمه‌ای","انبه","موز","هویج","نارگیل کوچک","پاپایا","طالبی کوچک","روتابگا","کدوحلوایی کوچک","گل‌کلم","بادمجان","کدوحلوایی","کلم","نارگیل","آناناس","هندوانه کوچک","طالبی","خربزه","رومین","هندوانه","کدوحلوایی","نوزاد کامل‌قد","نوزاد کامل‌قد","نوزاد کامل‌قد","نوزاد کامل‌قد")

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{PregnancyApp()}}
}

@Composable
fun PregnancyApp(){
    var due by remember{mutableStateOf(LocalDate.of(2027,2,4))}
    var screen by remember{mutableIntStateOf(0)}
    var note by remember{mutableStateOf("")}
    var notes by remember{mutableStateOf(emptyList<String>())}
    var dateDialog by remember{mutableStateOf(false)}
    val p=Pregnancy(due); val now=LocalDate.now(); val age=p.age(now)
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF8B4667),secondary=Color(0xFF765467))){
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl){
            Scaffold(
                topBar={TopAppBar(title={Text("ردیاب بارداری",fontFamily=Fa,fontWeight=FontWeight.Bold)},actions={
                    IconButton({dateDialog=true}){Icon(Icons.Default.Event,null)}
                })},
                bottomBar={NavigationBar{
                    listOf("خانه" to Icons.Default.Home,"هفته‌ها" to Icons.Default.CalendarMonth,"یادداشت‌ها" to Icons.Default.Note,"تنظیمات" to Icons.Default.Settings)
                        .forEachIndexed{i,(label,icon)->NavigationBarItem(selected=screen==i,onClick={screen=i},icon={Icon(icon,null)},label={Text(label,fontFamily=Fa)})}
                }}
            ){pad->
                when(screen){
                    0->Home(p,age,now,pad)
                    1->Weeks(age.first,pad)
                    2->Notes(notes,note,{note=it},{if(note.isNotBlank()){notes=notes+note;note=""}},pad)
                    else->Settings(due,{due=it},pad)
                }
            }
        }
    }
    if(dateDialog) DateDialog(due,{due=it;dateDialog=false},{dateDialog=false})
}

@Composable fun Home(p:Pregnancy,age:Pair<Int,Int>,now:LocalDate,pad:PaddingValues){
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(0.dp,14.dp,0.dp,24.dp)){
        item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("سن بارداری",fontFamily=Fa)
            Text("هفته ${fa(age.first)} + ${fa(age.second)} روز",fontFamily=Fa,fontSize=32.sp,fontWeight=FontWeight.Bold)
            Text("${jalali(now)}  •  $now",fontFamily=Fa)
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator({p.percent(now)},Modifier.fillMaxWidth().height(10.dp),strokeCap=StrokeCap.Round)
            Text("${fa((p.percent(now)*100).roundToInt())}٪ از ۴۰ هفته",fontFamily=Fa,modifier=Modifier.padding(top=8.dp))
        }}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
            Stat("تاریخ زایمان",jalali(p.due),Modifier.weight(1f)); Stat("روز باقی‌مانده",fa(ChronoUnit.DAYS.between(now,p.due).coerceAtLeast(0).toInt()),Modifier.weight(1f))
        }}
        item{Text("نمودار پیشرفت",fontFamily=Fa,fontSize=20.sp,fontWeight=FontWeight.Bold)}
        item{Chart(age.first)}
        item{InfoCard("این هفته","هفته ${fa(age.first)}","اندازه تقریبی: ${sizes[(age.first-1).coerceIn(0,39)]}. اطلاعات برنامه آموزشی است و جایگزین نظر پزشک نیست.")}
    }
}

@Composable fun Stat(title:String,value:String,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){Text(title,fontFamily=Fa,fontSize=13.sp);Text(value,fontFamily=Fa,fontWeight=FontWeight.Bold)}}}

@Composable fun Chart(week:Int){Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Canvas(Modifier.fillMaxWidth().height(160.dp)){
    val w=size.width; val h=size.height
    for(i in 0..4){val y=h*i/4;drawLine(MaterialTheme.colorScheme.outlineVariant,Offset(0f,y),Offset(w,y))}
    var last:Offset?=null
    for(x in 1..40){val pt=Offset(w*(x-1)/39f,h-h*x/40f);if(last!=null)drawLine(MaterialTheme.colorScheme.primary,last!!,pt,5f,StrokeCap.Round);last=pt}
    val x=w*(week.coerceIn(1,40)-1)/39f; val y=h-h*week.coerceIn(1,40)/40f
    drawCircle(MaterialTheme.colorScheme.primary,8f,Offset(x,y))
};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("هفته ۱",fontFamily=Fa,fontSize=12.sp);Text("هفته ۲۰",fontFamily=Fa,fontSize=12.sp);Text("هفته ۴۰",fontFamily=Fa,fontSize=12.sp)}}}}

@Composable fun InfoCard(title:String,subtitle:String,body:String){Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(title,fontFamily=Fa,fontSize=18.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,fontFamily=Fa,modifier=Modifier.padding(top=4.dp));Text(body,fontFamily=Fa,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=9.dp))}}}

@Composable fun Weeks(current:Int,pad:PaddingValues){LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(top=14.dp,bottom=24.dp)){items((1..40).toList()){w->
    Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=if(w==current)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Text(fa(w),fontFamily=Fa,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(60.dp))
            Column{Text(if(w<=13)"سه‌ماهه اول" else if(w<=27)"سه‌ماهه دوم" else "سه‌ماهه سوم",fontFamily=Fa,fontWeight=FontWeight.Bold);Text("اندازه تقریبی: ${sizes[w-1]}",fontFamily=Fa)}
        }
    }
}}}

@Composable fun Notes(notes:List<String>,text:String,onText:(String)->Unit,onAdd:()->Unit,pad:PaddingValues){Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)){
    Text("یادداشت‌ها",fontFamily=Fa,fontSize=26.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp))
    OutlinedTextField(text,onText,label={Text("یادداشت جدید",fontFamily=Fa)},modifier=Modifier.fillMaxWidth(),minLines=3)
    Button(onAdd,Modifier.fillMaxWidth().padding(vertical=10.dp)){Text("افزودن یادداشت",fontFamily=Fa)}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(notes){InfoCard("یادداشت","",it)}}
}}

@Composable fun Settings(due:LocalDate,onDue:(LocalDate)->Unit,pad:PaddingValues){LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Text("تنظیمات",fontFamily=Fa,fontSize=26.sp,fontWeight=FontWeight.Bold)}
    item{InfoCard("تاریخ زایمان","محاسبه خودکار سن بارداری","تاریخ زایمان فعلی: ${jalali(due)}")}
    item{Text("رابط کاربری کاملاً راست‌به‌چپ است و برنامه برای کار بدون اینترنت طراحی شده است. تاریخ شمسی و میلادی همزمان نمایش داده می‌شوند.",fontFamily=Fa,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}}

@Composable fun DateDialog(current:LocalDate,onOk:(LocalDate)->Unit,onCancel:()->Unit){AlertDialog(onDismissRequest=onCancel,title={Text("تاریخ زایمان",fontFamily=Fa)},text={Text("تاریخ فعلی: ${jalali(current)}\nنسخه اول با تاریخ نمونهٔ ۴ فوریهٔ ۲۰۲۷ شروع می‌شود.",fontFamily=Fa)},confirmButton={TextButton({onOk(current)}){Text("تأیید",fontFamily=Fa)}},dismissButton={TextButton(onCancel){Text("لغو",fontFamily=Fa)}})}
