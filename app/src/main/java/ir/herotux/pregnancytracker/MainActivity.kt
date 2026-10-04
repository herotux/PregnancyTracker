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
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        enableEdgeToEdge()
        setContent{ PregnancyApp() }
    }
}

private val Rose = Color(0xFFB85C7A)
private val RoseDark = Color(0xFF8F3F5D)
private val Blush = Color(0xFFFFE8EF)
private val Cream = Color(0xFFFFF9FA)
private val Sage = Color(0xFFE5F2EA)
private val AmberSoft = Color(0xFFFFF1D8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyApp(){
    var due by remember{mutableStateOf(LocalDate.of(2027,2,4))}
    var screen by remember{mutableIntStateOf(0)}
    var note by remember{mutableStateOf("")}
    var notes by remember{mutableStateOf(emptyList<String>())}
    var dateDialog by remember{mutableStateOf(false)}
    var selectedWeek by remember{mutableStateOf<Int?>(null)}

    val p=Pregnancy(due)
    val now=LocalDate.now()
    val age=p.age(now)
    val colors=lightColorScheme(
        primary=RoseDark,
        onPrimary=Color.White,
        primaryContainer=Blush,
        onPrimaryContainer=RoseDark,
        secondary=Color(0xFF6D5A63),
        background=Cream,
        surface=Cream,
        surfaceVariant=Color(0xFFF5ECEF)
    )

    MaterialTheme(colorScheme=colors){
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl){
            Scaffold(
                containerColor=MaterialTheme.colorScheme.background,
                topBar={
                    CenterAlignedTopAppBar(
                        title={
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                Text(
                                    when(screen){0->"خانه";1->"هفته‌های بارداری";2->"یادداشت‌ها";else->"تنظیمات"},
                                    fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=19.sp
                                )
                            }
                        },
                        navigationIcon={
                            IconButton({dateDialog=true}){
                                Icon(Icons.Default.Event,null,tint=MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions={
                            IconButton({screen=3}){
                                Icon(Icons.Default.Settings,null)
                            }
                        },
                        colors=TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor=MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar={
                    NavigationBar(
                        containerColor=Color.White,
                        tonalElevation=3.dp
                    ){
                        listOf(
                            "خانه" to Icons.Default.Home,
                            "هفته‌ها" to Icons.Default.CalendarMonth,
                            "یادداشت‌ها" to Icons.Default.NoteAlt,
                            "تنظیمات" to Icons.Default.Settings
                        ).forEachIndexed{i,(label,icon)->
                            NavigationBarItem(
                                selected=screen==i,
                                onClick={screen=i},
                                icon={Icon(icon,null)},
                                label={Text(label,fontFamily=Fa,fontSize=11.sp)},
                                alwaysShowLabel=true
                            )
                        }
                    }
                }
            ){pad->
                when(screen){
                    0->HomeModern(p,age,now,pad){selectedWeek=it}
                    1->WeeksModern(age.first,pad){selectedWeek=it}
                    2->NotesModern(notes,note,{note=it},{if(note.isNotBlank()){notes=notes+note;note=""}},pad)
                    else->SettingsModern(due,{due=it},pad)
                }
            }
        }
    }
    if(dateDialog) DateDialogModern(due,{due=it;dateDialog=false},{dateDialog=false})
    selectedWeek?.let{WeekDialogModern(weeklyPlan(it)){selectedWeek=null}}
}

@Composable
fun HomeModern(p:Pregnancy,age:Pair<Int,Int>,now:LocalDate,pad:PaddingValues,onWeek:(Int)->Unit){
    val week=age.first.coerceIn(1,40)
    val plan=weeklyPlan(week)
    val daysLeft=ChronoUnit.DAYS.between(now,p.due).coerceAtLeast(0).toInt()

    LazyColumn(
        Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp),
        contentPadding=PaddingValues(top=8.dp,bottom=28.dp)
    ){
        item{
            HeroPregnancyCard(age,p,now)
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                MetricCard("تاریخ زایمان",jalali(p.due),Icons.Default.Event,Modifier.weight(1f))
                MetricCard("باقی‌مانده",fa(daysLeft)+" روز",Icons.Default.HourglassBottom,Modifier.weight(1f))
            }
        }
        item{
            SectionHeader("این هفته", "هفته "+fa(week), Icons.Default.AutoAwesome)
        }
        item{
            Card(
                modifier=Modifier.fillMaxWidth().clickable{onWeek(week)},
                shape=RoundedCornerShape(24.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White),
                elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
            ){
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Surface(
                            shape=RoundedCornerShape(16.dp),
                            color=Blush,
                            modifier=Modifier.size(52.dp)
                        ){
                            Box(contentAlignment=Alignment.Center){
                                Icon(Icons.Default.ChildCare,null,tint=RoseDark)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text(plan.title,fontFamily=Fa,fontSize=18.sp,fontWeight=FontWeight.Bold)
                            Text("مهم‌ترین کار این هفته",fontFamily=Fa,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                        }
                        Icon(Icons.Default.ChevronLeft,null,tint=MaterialTheme.colorScheme.primary)
                    }
                    Text(plan.actions.first(),fontFamily=Fa,fontSize=15.sp,lineHeight=23.sp)
                    Text("مشاهده برنامه کامل هفته",fontFamily=Fa,color=RoseDark,fontWeight=FontWeight.Bold,fontSize=14.sp)
                }
            }
        }
        item{
            SectionHeader("رشد کوچولو", "هفته "+fa(week), Icons.Default.Favorite)
        }
        item{
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Sage)){
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.size(68.dp)){
                        Box(contentAlignment=Alignment.Center){
                            Icon(Icons.Default.ChildCare,null,tint=Color(0xFF4E8062),modifier=Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column{
                        Text("اندازه تقریبی جنین",fontFamily=Fa,fontSize=14.sp,color=Color(0xFF4D6657))
                        Text(sizes[(week-1).coerceIn(0,39)],fontFamily=Fa,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color(0xFF31523F))
                        Text("اطلاعات آموزشی و تقریبی است.",fontFamily=Fa,fontSize=12.sp,color=Color(0xFF587364))
                    }
                }
            }
        }
        item{
            SectionHeader("هشدارهای مهم", "در صورت نیاز سریع اقدام کنید", Icons.Default.Warning)
        }
        item{
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF0F1))){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    plan.warnings.take(3).forEach{
                        Row(verticalAlignment=Alignment.Top){
                            Icon(Icons.Default.ErrorOutline,null,tint=Color(0xFFB04455),modifier=Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it,fontFamily=Fa,fontSize=13.sp,lineHeight=21.sp,color=Color(0xFF71313B))
                        }
                    }
                    Text("در وضعیت شدید یا اورژانسی با اورژانس محل زندگی تماس بگیرید.",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=13.sp,color=Color(0xFF71313B))
                }
            }
        }
        item{
            SectionHeader("مسیر بارداری", "پیشرفت تا هفته ۴۰", Icons.Default.Timeline)
        }
        item{ModernProgressChart(week)}
    }
}

@Composable
fun HeroPregnancyCard(age:Pair<Int,Int>,p:Pregnancy,now:LocalDate){
    val progress=p.percent(now)
    Card(
        shape=RoundedCornerShape(30.dp),
        colors=CardDefaults.cardColors(containerColor=RoseDark),
        elevation=CardDefaults.cardElevation(defaultElevation=4.dp)
    ){
        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Box(Modifier.size(112.dp),contentAlignment=Alignment.Center){
                Canvas(Modifier.fillMaxSize()){
                    drawCircle(Color.White.copy(alpha=.16f),size.minDimension/2)
                    drawArc(
                        Color.White,
                        -90f,
                        360f*progress,
                        false,
                        style=androidx.compose.ui.graphics.drawscope.Stroke(width=9.dp.toPx(),cap=StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text(fa(age.first),fontFamily=Fa,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White)
                    Text("هفته",fontFamily=Fa,fontSize=13.sp,color=Color.White.copy(alpha=.9f))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("سلام مامان! 🌷",fontFamily=Fa,fontSize=14.sp,color=Color.White.copy(alpha=.85f))
                Text("رشد بارداری",fontFamily=Fa,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text(
                    "هفته "+fa(age.first)+" و "+fa(age.second)+" روز",
                    fontFamily=Fa,fontSize=16.sp,color=Color.White
                )
                Text(
                    fa((progress*100).roundToInt())+"٪ از مسیر ۴۰ هفته",
                    fontFamily=Fa,fontSize=13.sp,color=Color.White.copy(alpha=.9f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Surface(shape=RoundedCornerShape(12.dp),color=Blush,modifier=Modifier.size(38.dp)){
            Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=RoseDark,modifier=Modifier.size(20.dp))}
        }
        Spacer(Modifier.width(10.dp))
        Column{
            Text(title,fontFamily=Fa,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text(subtitle,fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricCard(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier){
    Card(modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Icon(icon,null,tint=RoseDark,modifier=Modifier.size(21.dp))
            Text(title,fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=15.sp)
        }
    }
}

@Composable
fun ModernProgressChart(week:Int){
    val grid=MaterialTheme.colorScheme.outlineVariant
    val line=Rose
    Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(18.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Column{
                    Text("پیشرفت بارداری",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Text("هفته "+fa(week)+" از ۴۰",fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape=RoundedCornerShape(50.dp),color=Blush){
                    Text(fa((week*100/40).coerceIn(0,100))+"٪",fontFamily=Fa,color=RoseDark,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)){
                val w=size.width; val h=size.height
                for(i in 0..4){
                    val y=h*i/4
                    drawLine(grid,Offset(0f,y),Offset(w,y),1f)
                }
                var last:Offset?=null
                for(x in 1..40){
                    val pt=Offset(w*(x-1)/39f,h-h*x/40f)
                    if(last!=null) drawLine(line,last!!,pt,5f,StrokeCap.Round)
                    last=pt
                }
                val x=w*(week.coerceIn(1,40)-1)/39f
                val y=h-h*week.coerceIn(1,40)/40f
                drawCircle(Color.White,11f,Offset(x,y))
                drawCircle(line,8f,Offset(x,y))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("۱",fontFamily=Fa,fontSize=11.sp)
                Text("۲۰",fontFamily=Fa,fontSize=11.sp)
                Text("۴۰",fontFamily=Fa,fontSize=11.sp)
            }
        }
    }
}

@Composable
fun WeeksModern(current:Int,pad:PaddingValues,onWeek:(Int)->Unit){
    val trimesterColors=listOf(Blush,Color(0xFFE9F0FF),Sage)
    LazyColumn(
        Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp),
        contentPadding=PaddingValues(top=8.dp,bottom=28.dp)
    ){
        item{
            Text("تقویم ۴۰ هفته‌ای",fontFamily=Fa,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("هفته موردنظر را انتخاب کنید تا اقدامات و هشدارهای همان هفته را ببینید.",fontFamily=Fa,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp,modifier=Modifier.padding(top=4.dp))
        }
        items((1..40).toList()){
            val w=it
            val trimester=when{w<=13->"سه‌ماهه اول";w<=27->"سه‌ماهه دوم";else->"سه‌ماهه سوم"}
            Card(
                modifier=Modifier.fillMaxWidth().clickable{onWeek(w)},
                shape=RoundedCornerShape(20.dp),
                colors=CardDefaults.cardColors(containerColor=if(w==current)Blush else Color.White)
            ){
                Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=RoundedCornerShape(16.dp),color=trimesterColors[(w-1)/14.coerceAtMost(2)],modifier=Modifier.size(52.dp)){
                        Box(contentAlignment=Alignment.Center){Text(fa(w),fontFamily=Fa,fontSize=21.sp,fontWeight=FontWeight.Bold,color=RoseDark)}
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)){
                        Text(trimester+" • هفته "+fa(w),fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=15.sp)
                        Text(weeklyPlan(w).actions.first(),fontFamily=Fa,fontSize=12.sp,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=18.sp)
                    }
                    Icon(Icons.Default.ChevronLeft,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WeekDialogModern(plan:WeekPlan,onClose:()->Unit){
    BasicAlertDialog(onDismissRequest=onClose){
        Surface(shape=RoundedCornerShape(28.dp),color=Color.White,tonalElevation=6.dp,modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(20.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=RoundedCornerShape(16.dp),color=Blush,modifier=Modifier.size(48.dp)){
                        Box(contentAlignment=Alignment.Center){Icon(Icons.Default.ChildCare,null,tint=RoseDark)}
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(plan.title,fontFamily=Fa,fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Text("راهنمای این هفته",fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClose){Icon(Icons.Default.Close,null)}
                }
                HorizontalDivider(Modifier.padding(vertical=8.dp))
                LazyColumn(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    item{ModernSection("اقدامات",plan.actions,Icons.Default.CheckCircle)}
                    item{ModernSection("بررسی‌ها و ویزیت",plan.checks,Icons.Default.MedicalServices)}
                    item{ModernSection("توصیه‌ها",plan.tips,Icons.Default.FavoriteBorder)}
                    item{ModernSection("علائم هشدار",plan.warnings,Icons.Default.Warning)}
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick=onClose,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
                    Text("متوجه شدم",fontFamily=Fa)
                }
            }
        }
    }
}

@Composable
fun ModernSection(title:String,items:List<String>,icon:androidx.compose.ui.graphics.vector.ImageVector){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=RoseDark,modifier=Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text(title,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=16.sp)
        }
        items.forEach{
            Row(verticalAlignment=Alignment.Top){
                Text("•",fontFamily=Fa,color=RoseDark,fontWeight=FontWeight.Bold,fontSize=18.sp)
                Spacer(Modifier.width(7.dp))
                Text(it,fontFamily=Fa,fontSize=13.sp,lineHeight=21.sp)
            }
        }
    }
}

@Composable
fun NotesModern(notes:List<String>,text:String,onText:(String)->Unit,onAdd:()->Unit,pad:PaddingValues){
    LazyColumn(
        Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),
        contentPadding=PaddingValues(top=8.dp,bottom=28.dp)
    ){
        item{
            Text("یادداشت‌های من",fontFamily=Fa,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("قرارها، علائم، سؤال‌ها و نکات مهم را اینجا ثبت کن.",fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
        }
        item{
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                Column(Modifier.padding(16.dp)){
                    OutlinedTextField(
                        value=text,
                        onValueChange=onText,
                        label={Text("یادداشت جدید",fontFamily=Fa)},
                        placeholder={Text("مثلاً: سؤال برای ویزیت بعدی...",fontFamily=Fa)},
                        modifier=Modifier.fillMaxWidth(),
                        minLines=3,
                        shape=RoundedCornerShape(16.dp)
                    )
                    Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().padding(top=10.dp),shape=RoundedCornerShape(15.dp)){
                        Icon(Icons.Default.Add,null)
                        Spacer(Modifier.width(7.dp))
                        Text("ذخیره یادداشت",fontFamily=Fa)
                    }
                }
            }
        }
        if(notes.isEmpty()){
            item{
                Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Blush)){
                    Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(Icons.Default.EditNote,null,tint=RoseDark,modifier=Modifier.size(46.dp))
                        Text("هنوز یادداشتی ثبت نشده",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=10.dp))
                        Text("یادداشت‌های بارداری را همین‌جا نگه دار.",fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }else{
            items(notes.reversed()){InfoCardModern("یادداشت",it)}
        }
    }
}

@Composable
fun InfoCardModern(title:String,body:String){
    Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top){
            Surface(shape=RoundedCornerShape(12.dp),color=Blush,modifier=Modifier.size(38.dp)){
                Box(contentAlignment=Alignment.Center){Icon(Icons.Default.NoteAlt,null,tint=RoseDark,modifier=Modifier.size(19.dp))}
            }
            Spacer(Modifier.width(11.dp))
            Column{
                Text(title,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=13.sp,color=RoseDark)
                Text(body,fontFamily=Fa,fontSize=14.sp,lineHeight=22.sp,modifier=Modifier.padding(top=4.dp))
            }
        }
    }
}

@Composable
fun SettingsModern(due:LocalDate,onDue:(LocalDate)->Unit,pad:PaddingValues){
    LazyColumn(
        Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),
        contentPadding=PaddingValues(top=8.dp,bottom=28.dp)
    ){
        item{
            Text("تنظیمات",fontFamily=Fa,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("اطلاعات بارداری و ظاهر برنامه را مدیریت کن.",fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
        }
        item{
            SettingTile(Icons.Default.Event,"تاریخ زایمان","تاریخ فعلی: "+jalali(due)){onDue(due)}
        }
        item{SettingTile(Icons.Default.Language,"تقویم","نمایش همزمان تاریخ شمسی و میلادی"){}}
        item{SettingTile(Icons.Default.NotificationsNone,"یادآوری‌ها","در نسخه بعدی: یادآوری ویزیت، آزمایش و شروع هر هفته"){}}
        item{SettingTile(Icons.Default.Palette,"ظاهر برنامه","طراحی فارسی، RTL و Material 3"){}}
        item{
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Blush)){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.Top){
                    Icon(Icons.Default.Info,null,tint=RoseDark)
                    Spacer(Modifier.width(10.dp))
                    Text("این برنامه آموزشی است و جایگزین تشخیص یا توصیه شخصی پزشک نیست. در علائم شدید یا اورژانسی از خدمات درمانی محل زندگی کمک بگیر.",fontFamily=Fa,fontSize=13.sp,lineHeight=21.sp)
                }
            }
        }
    }
}

@Composable
fun SettingTile(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit){
    Card(modifier=Modifier.fillMaxWidth().clickable{onClick()},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){
            Surface(shape=RoundedCornerShape(14.dp),color=Blush,modifier=Modifier.size(46.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=RoseDark)}
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)){
                Text(title,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=15.sp)
                Text(subtitle,fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
            }
            Icon(Icons.Default.ChevronLeft,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DateDialogModern(current:LocalDate,onOk:(LocalDate)->Unit,onCancel:()->Unit){
    AlertDialog(
        onDismissRequest=onCancel,
        icon={Icon(Icons.Default.Event,null,tint=RoseDark)},
        title={Text("تاریخ زایمان",fontFamily=Fa,fontWeight=FontWeight.Bold)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("تاریخ فعلی",fontFamily=Fa,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(jalali(current),fontFamily=Fa,fontSize=21.sp,fontWeight=FontWeight.Bold)
                Text("نسخه فعلی با تاریخ نمونه شروع می‌شود؛ انتخاب‌گر واقعی تاریخ را در مرحله بعد اضافه می‌کنیم.",fontFamily=Fa,fontSize=13.sp,lineHeight=20.sp)
            }
        },
        confirmButton={TextButton({onOk(current)}){Text("تأیید",fontFamily=Fa)}},
        dismissButton={TextButton(onCancel){Text("لغو",fontFamily=Fa)}}
    )
}
