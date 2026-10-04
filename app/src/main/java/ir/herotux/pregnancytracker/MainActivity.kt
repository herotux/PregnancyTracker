package ir.herotux.pregnancytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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

data class WeekPlan(val week:Int,val title:String,val actions:List<String>,val checks:List<String>,val tips:List<String>,val warnings:List<String>)

private fun weeklyPlan(w:Int):WeekPlan{
    val trimester=when{w<=13->"سه‌ماهه اول";w<=27->"سه‌ماهه دوم";else->"سه‌ماهه سوم"}
    val actions=when(w){
        1->"ثبت تاریخ آخرین قاعدگی و شروع برنامه‌ریزی برای مراقبت بارداری."
        2->"در صورت احتمال بارداری، زمان مناسب تست و شروع مراقبت را مشخص کنید."
        3->"در صورت مثبت شدن تست، برای اولین ویزیت و بررسی داروها وقت بگیرید."
        4->"مکمل‌ها به‌ویژه اسیدفولیک را فقط طبق توصیه پزشک ادامه دهید."
        5->"فهرست داروها، مکمل‌ها، بیماری‌های زمینه‌ای و حساسیت‌ها را آماده کنید."
        6->"برای ویزیت اولیه و آزمایش‌های روتین بارداری برنامه‌ریزی کنید."
        7->"تغذیه منظم، آب کافی، استراحت و فعالیت مناسب را پیگیری کنید."
        8->"نتایج آزمایش‌ها و سونوگرافی‌ها را در پرونده بارداری ثبت کنید."
        9->"درباره غربالگری سه‌ماهه اول و زمان سونوگرافی با پزشک هماهنگ کنید."
        10->"برای سونوگرافی تعیین سن و غربالگری بازه ۱۱ تا ۱۴ هفته برنامه‌ریزی کنید."
        11->"اگر قرار است NT و غربالگری سه‌ماهه اول انجام شود، زمان آن را از دست ندهید."
        12->"نتایج غربالگری را با پزشک تفسیر کنید؛ نتیجه غربالگری تشخیص قطعی نیست."
        13->"نتایج سه‌ماهه اول و برنامه ویزیت‌های سه‌ماهه دوم را مرور کنید."
        14->"برنامه ویزیت‌ها و آزمایش‌های سه‌ماهه دوم را با پزشک مشخص کنید."
        15->"فعالیت بدنی مناسب بارداری، خواب کافی و تغذیه متعادل را ادامه دهید."
        16->"فشارخون، وزن و علائم جدید را طبق برنامه مراقبتی پیگیری کنید."
        17->"برای سونوگرافی آنومالی حوالی ۱۸ تا ۲۲ هفته برنامه‌ریزی کنید."
        18->"اگر سونوگرافی آنومالی انجام نشده، درباره زمان مناسب آن سؤال کنید."
        19->"نتایج سونوگرافی آنومالی و آزمایش‌های قبلی را با پزشک مرور کنید."
        20->"نیمه بارداری را با مرور حرکات جنین، تغذیه و ویزیت‌ها پیگیری کنید."
        21->"برای آزمایش دیابت بارداری در هفته‌های آینده برنامه‌ریزی کنید."
        22->"برای آزمایش قند بارداری در بازه معمول ۲۴ تا ۲۸ هفته آماده شوید."
        23->"وسایل و مسیر دسترسی به مرکز درمانی را برای ادامه بارداری بررسی کنید."
        24->"درباره آزمایش دیابت بارداری و آزمایش خون این دوره با پزشک هماهنگ کنید."
        25->"فشارخون، ادرار و رشد شکم/جنین را طبق مراقبت معمول پیگیری کنید."
        26->"اگر Rh منفی هستید، درباره برنامه پیشگیری از ناسازگاری Rh سؤال کنید."
        27->"درباره واکسن Tdap که معمولاً در ۲۷ تا ۳۶ هفته توصیه می‌شود سؤال کنید."
        28->"سه‌ماهه سوم را با ویزیت منظم‌تر و مرور حرکات جنین شروع کنید."
        29->"برای زایمان، مرکز زایمان و همراه مورد نظر برنامه اولیه داشته باشید."
        30->"وسایل ضروری مادر و نوزاد را کم‌کم آماده کنید."
        31->"درباره علائم شروع زایمان و زمان مراجعه آموزش بگیرید."
        32->"درباره واکسن RSV و زمان‌بندی محلی آن از پزشک سؤال کنید."
        33->"مدارک پزشکی و شماره‌های تماس ضروری را آماده و در دسترس نگه دارید."
        34->"کیف بیمارستان و مسیر رفت‌وآمد را آماده کنید."
        35->"الگوی معمول حرکات جنین را بشناسید و هر تغییر واضح را جدی بگیرید."
        36->"وضعیت جنین و آمادگی برای زایمان را با پزشک پیگیری کنید."
        37->"علائم زایمان و زمان مراجعه را دوباره مرور کنید."
        38->"استراحت، آب کافی، تغذیه مناسب و ویزیت‌ها را ادامه دهید."
        39->"برای زایمان در هر زمان آماده باشید و توصیه اختصاصی پزشک را در اولویت قرار دهید."
        else->"پیگیری ویزیت و برنامه زایمان؛ در صورت عبور از موعد، برنامه پایش را با پزشک مشخص کنید."
    }
    val checks=when{
        w<=8->listOf("ویزیت اولیه و آزمایش‌های بارداری طبق نظر پزشک","مرور داروها و مکمل‌ها")
        w<=13->listOf("غربالگری سه‌ماهه اول","سونوگرافی بازه ۱۱ تا ۱۴ هفته در صورت تجویز")
        w<=17->listOf("کنترل فشارخون و وزن در ویزیت‌ها","مرور نتایج غربالگری")
        w<=22->listOf("سونوگرافی آنومالی معمولاً حوالی ۱۸ تا ۲۲ هفته","بررسی برنامه آزمایش قند بارداری")
        w<=28->listOf("آزمایش دیابت بارداری معمولاً ۲۴ تا ۲۸ هفته","بررسی کم‌خونی و سایر آزمایش‌های لازم","کنترل فشارخون و ادرار")
        w<=36->listOf("ویزیت‌های منظم سه‌ماهه سوم","پیگیری رشد و وضعیت جنین طبق نظر پزشک","مرور واکسن‌های مورد نیاز")
        else->listOf("پیگیری ویزیت و وضعیت جنین","مرور برنامه زایمان و آمادگی بیمارستان")
    }
    val tips=mutableListOf("غذای متنوع و متعادل، آب کافی و فعالیت بدنی مناسب شرایط بارداری را حفظ کنید.","هیچ دارو، مکمل گیاهی یا مسکن را بدون بررسی با پزشک/داروساز شروع یا قطع نکنید.","سیگار، قلیان، الکل و مواد مخدر در بارداری توصیه نمی‌شوند.")
    if(w>=28) tips += "برای خواب در نیمه دوم بارداری، وضعیت راحت و ایمن را با توصیه پزشک انتخاب کنید."
    if(w>=20) tips += "بعد از شکل‌گیری الگوی حرکات جنین، کاهش یا تغییر واضح حرکات را جدی بگیرید."
    if(w in 27..36) tips += "درباره واکسن Tdap در هر بارداری با پزشک صحبت کنید؛ زمان ترجیحی ۲۷ تا ۳۶ هفته است."
    if(w in 32..36) tips += "درباره واکسن RSV و زمان‌بندی محلی آن از پزشک سؤال کنید؛ شرایط و فصل اهمیت دارد."
    val warnings=listOf("خونریزی واژینال، به‌خصوص خونریزی زیاد یا همراه درد شدید: مراجعه فوری.","سردرد شدید یا مداوم، تاری دید/جرقه‌های نوری، درد زیر دنده‌ها یا تورم ناگهانی صورت و دست‌ها: ارزیابی فوری.","نشت مایع از واژن، درد شدید یا مداوم شکم، یا انقباض‌های منظم و دردناک: تماس فوری با مرکز درمانی.","بعد از شکل‌گیری الگوی حرکات جنین، کاهش یا تغییر واضح حرکات: منتظر روز بعد نمانید و فوراً با مرکز درمانی تماس بگیرید.","تنگی نفس شدید، درد قفسه سینه، غش، تشنج یا احساس بیماری شدید: کمک اورژانسی.")
    return WeekPlan(w,trimester+" — هفته "+fa(w),listOf(actions),checks,tips,warnings)
}

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{PregnancyApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyApp(){
    var due by remember{mutableStateOf(LocalDate.of(2027,2,4))}
    var screen by remember{mutableIntStateOf(0)}
    var note by remember{mutableStateOf("")}
    var notes by remember{mutableStateOf(emptyList<String>())}
    var dateDialog by remember{mutableStateOf(false)}
    var selectedWeek by remember{mutableStateOf<Int?>(null)}
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
                    0->Home(p,age,now,pad){selectedWeek=it}
                    1->Weeks(age.first,pad){selectedWeek=it}
                    2->Notes(notes,note,{note=it},{if(note.isNotBlank()){notes=notes+note;note=""}},pad)
                    else->Settings(due,{due=it},pad)
                }
            }
        }
    }
    if(dateDialog) DateDialog(due,{due=it;dateDialog=false},{dateDialog=false})
    selectedWeek?.let{WeekDialog(weeklyPlan(it)){selectedWeek=null}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Home(p:Pregnancy,age:Pair<Int,Int>,now:LocalDate,pad:PaddingValues,onWeek:(Int)->Unit){
    val plan=weeklyPlan(age.first.coerceIn(1,40))
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(0.dp,14.dp,0.dp,24.dp)){
        item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("سن بارداری",fontFamily=Fa); Text("هفته "+fa(age.first)+" + "+fa(age.second)+" روز",fontFamily=Fa,fontSize=32.sp,fontWeight=FontWeight.Bold)
            Text(jalali(now)+"  •  "+now,fontFamily=Fa); Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress={p.percent(now)},Modifier.fillMaxWidth().height(10.dp),strokeCap=StrokeCap.Round)
            Text(fa((p.percent(now)*100).roundToInt())+"٪ از ۴۰ هفته",fontFamily=Fa,modifier=Modifier.padding(top=8.dp))
        }}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){Stat("تاریخ زایمان",jalali(p.due),Modifier.weight(1f));Stat("روز باقی‌مانده",fa(ChronoUnit.DAYS.between(now,p.due).coerceAtLeast(0).toInt()),Modifier.weight(1f))}}
        item{Text("اقدامات این هفته",fontFamily=Fa,fontSize=20.sp,fontWeight=FontWeight.Bold)}
        item{Card(shape=RoundedCornerShape(20.dp),modifier=Modifier.clickable{onWeek(plan.week)}){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(plan.title,fontFamily=Fa,fontSize=18.sp,fontWeight=FontWeight.Bold);plan.actions.forEach{Text("• "+it,fontFamily=Fa)};Text("مشاهده جزئیات هفته ›",fontFamily=Fa,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}}}
        item{Text("هشدارهای مهم",fontFamily=Fa,fontSize=20.sp,fontWeight=FontWeight.Bold)}
        item{Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){plan.warnings.forEach{Text("⚠ "+it,fontFamily=Fa,color=MaterialTheme.colorScheme.onErrorContainer)};Text("در وضعیت شدید یا اورژانسی با اورژانس محل زندگی تماس بگیرید.",fontFamily=Fa,fontWeight=FontWeight.Bold)}}}
        item{Text("نمودار پیشرفت",fontFamily=Fa,fontSize=20.sp,fontWeight=FontWeight.Bold)}; item{Chart(age.first)}
        item{InfoCard("این هفته","اندازه تقریبی: "+sizes[(age.first-1).coerceIn(0,39)],"اطلاعات آموزشی است و جایگزین تشخیص یا توصیه شخصی پزشک نیست.")}
    }
}

@Composable fun WeekDialog(plan:WeekPlan,onClose:()->Unit){AlertDialog(onDismissRequest=onClose,title={Text(plan.title,fontFamily=Fa,fontWeight=FontWeight.Bold)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){item{Section("اقدامات",plan.actions)};item{Section("بررسی‌ها و ویزیت",plan.checks)};item{Section("توصیه‌ها",plan.tips)};item{Section("علائم هشدار",plan.warnings)}}},confirmButton={TextButton(onClose){Text("بستن",fontFamily=Fa)}})}

@Composable fun Section(title:String,items:List<String>){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=17.sp);items.forEach{Text("• "+it,fontFamily=Fa)}}}
@Composable fun Stat(title:String,value:String,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){Text(title,fontFamily=Fa,fontSize=13.sp);Text(value,fontFamily=Fa,fontWeight=FontWeight.Bold)}}}

@Composable fun Chart(week:Int){
    val gridColor=MaterialTheme.colorScheme.outlineVariant
    val lineColor=MaterialTheme.colorScheme.primary
    Card(shape=RoundedCornerShape(20.dp)){
        Column(Modifier.padding(16.dp)){
            Canvas(Modifier.fillMaxWidth().height(160.dp)){
                val w=size.width; val h=size.height
                for(i in 0..4){
                    val y=h*i/4
                    drawLine(gridColor,Offset(0f,y),Offset(w,y))
                }
                var last:Offset?=null
                for(x in 1..40){
                    val pt=Offset(w*(x-1)/39f,h-h*x/40f)
                    if(last!=null) drawLine(lineColor,last!!,pt,5f,StrokeCap.Round)
                    last=pt
                }
                val x=w*(week.coerceIn(1,40)-1)/39f
                val y=h-h*week.coerceIn(1,40)/40f
                drawCircle(lineColor,8f,Offset(x,y))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("هفته ۱",fontFamily=Fa,fontSize=12.sp)
                Text("هفته ۲۰",fontFamily=Fa,fontSize=12.sp)
                Text("هفته ۴۰",fontFamily=Fa,fontSize=12.sp)
            }
        }
    }
}

@Composable fun InfoCard(title:String,subtitle:String,body:String){Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(title,fontFamily=Fa,fontSize=18.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,fontFamily=Fa,modifier=Modifier.padding(top=4.dp));Text(body,fontFamily=Fa,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=9.dp))}}}

@Composable fun Weeks(current:Int,pad:PaddingValues,onWeek:(Int)->Unit){LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(top=14.dp,bottom=24.dp)){items((1..40).toList()){w->
    Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.clickable{onWeek(w)},colors=CardDefaults.cardColors(containerColor=if(w==current)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Text(fa(w),fontFamily=Fa,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(60.dp))
            Column(Modifier.weight(1f)){Text(weeklyPlan(w).title,fontFamily=Fa,fontWeight=FontWeight.Bold);Text("• "+weeklyPlan(w).actions.first(),fontFamily=Fa,maxLines=2)}; Icon(Icons.Default.ChevronLeft,null)
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
