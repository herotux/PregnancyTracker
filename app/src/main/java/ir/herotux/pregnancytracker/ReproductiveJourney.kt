package ir.herotux.pregnancytracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

internal const val STAGE_PLANNING = "planning"
internal const val STAGE_CYCLE = "cycle"
internal const val STAGE_TTC = "trying"
internal const val STAGE_POSSIBLE = "possible_pregnancy"
internal const val STAGE_POSITIVE = "positive_test"
internal const val STAGE_PREGNANT = "confirmed_pregnancy"
internal const val STAGE_POSTPARTUM = "postpartum"

internal const val DATING_LMP = "lmp"
internal const val DATING_OVULATION = "ovulation"
internal const val DATING_ULTRASOUND = "ultrasound"
internal const val DATING_IVF = "ivf"
internal const val DATING_CLINICIAN = "clinician_edd"
internal const val DATING_UNKNOWN = "unknown"

internal fun estimatePregnancyDueDate(
    method: String,
    referenceDate: LocalDate,
    cycleLength: Int = 28,
    gestationalWeeks: Int = 0,
    gestationalDays: Int = 0,
    embryoAgeDays: Int = 5
): LocalDate? = when (method) {
    DATING_LMP -> referenceDate.plusDays((280 + cycleLength.coerceIn(21, 45) - 28).toLong())
    DATING_OVULATION -> referenceDate.plusDays(266)
    DATING_ULTRASOUND -> {
        if (gestationalWeeks !in 4..42 || gestationalDays !in 0..6) null
        else {
            val ageDays = gestationalWeeks * 7 + gestationalDays
            if (ageDays > 294) null else referenceDate.plusDays((280 - ageDays).toLong())
        }
    }
    DATING_IVF -> referenceDate.plusDays((266 - embryoAgeDays.coerceIn(3, 6)).toLong())
    DATING_CLINICIAN -> referenceDate
    else -> null
}

@Composable
internal fun ReproductiveJourneyScreen(
    stage: String,
    datingMethod: String,
    referenceDate: LocalDate,
    dateSelected: Boolean,
    gestationalWeeks: String,
    gestationalDays: String,
    cycleLength: String,
    embryoAgeDays: Int,
    onStage: (String) -> Unit,
    onMethod: (String) -> Unit,
    onPickDate: () -> Unit,
    onWeeks: (String) -> Unit,
    onDays: (String) -> Unit,
    onCycleLength: (String) -> Unit,
    onEmbryoAge: (Int) -> Unit,
    onContinueCycle: () -> Unit,
    onContinuePregnancy: () -> Unit,
    onBack: () -> Unit
) {
    val possible = stage == STAGE_POSSIBLE
    val pregnant = stage in listOf(STAGE_POSITIVE, STAGE_PREGNANT)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 30.dp)
    ) {
        item {
            Text("نونو؛ همراه مسیر باروری و سلامت", fontFamily = Fa, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (!pregnant && !possible) "از برنامه‌ریزی و شناخت چرخه تا بارداری، نونو همراهت است. هر زمان خواستی می‌توانی مرحله را تغییر بدهی."
                else if (possible) "اگر احتمال بارداری می‌دهی، ابتدا باید آن را تأیید کنی؛ نونو بدون تأیید، هفته بارداری محاسبه نمی‌کند."
                else "برای برآورد سن بارداری، روشی را انتخاب کن که اطلاعات دقیق‌تری از آن داری. لازم نیست همه اطلاعات را بدانی.",
                fontFamily = Fa, fontSize = 13.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
            )
        }
        if (!pregnant && !possible) {
            item { Text("الان در کدام مرحله هستی؟", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            item { JourneyOption("در حال برنامه‌ریزی برای بارداری", "آمادگی پیش از بارداری و نکات مهم", Icons.Default.Favorite, stage == STAGE_PLANNING) { onStage(STAGE_PLANNING) } }
            item { JourneyOption("فقط می‌خواهم چرخه قاعدگی را ثبت کنم", "ثبت قاعدگی و مشاهده پیش‌بینی چرخه", Icons.Default.CalendarMonth, stage == STAGE_CYCLE) { onStage(STAGE_CYCLE) } }
            item { JourneyOption("برای بارداری تلاش می‌کنم", "پنجره باروری و تخمین زمان تخمک‌گذاری", Icons.Default.Spa, stage == STAGE_TTC) { onStage(STAGE_TTC) } }
            item { JourneyOption("احتمال می‌دهم باردار باشم", "هنوز تست نداده‌ام یا نتیجه مشخص نیست", Icons.Default.HelpOutline, stage == STAGE_POSSIBLE) { onStage(STAGE_POSSIBLE) } }
            item { JourneyOption("تست بارداری‌ام مثبت شده", "برای ثبت اطلاعات و تعیین سن بارداری", Icons.Default.HealthAndSafety, stage == STAGE_POSITIVE) { onStage(STAGE_POSITIVE) } }
            item { JourneyOption("بارداری با پزشک یا سونوگرافی تأیید شده", "ثبت سن بارداری بر اساس اطلاعات پزشکی", Icons.Default.MedicalServices, stage == STAGE_PREGNANT) { onStage(STAGE_PREGNANT) } }
            item { JourneyOption("دوران پس از زایمان", "پیگیری سلامت مادر و بازگشت چرخه", Icons.Default.ChildCare, stage == STAGE_POSTPARTUM) { onStage(STAGE_POSTPARTUM) } }
            item {
                Button(
                    onClick = { if (stage in listOf(STAGE_PLANNING, STAGE_CYCLE, STAGE_TTC, STAGE_POSTPARTUM)) onContinueCycle() else onContinuePregnancy() },
                    enabled = stage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("ادامه", fontFamily = Fa, fontWeight = FontWeight.Bold) }
            }
        } else if (possible) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("احتمال بارداری هنوز به معنی بارداری تأییدشده نیست", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RoseDark)
                        Text("تاریخ قاعدگی یا علائم به‌تنهایی بارداری را ثابت نمی‌کنند. می‌توانی تاریخ تست را ثبت کنی و پس از نتیجه مثبت، سن بارداری را با روش معتبر تعیین کنی.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
            item {
                Button(onClick = onContinuePregnancy, modifier = Modifier.fillMaxWidth()) {
                    Text("ادامه بدون تاریخ‌گذاری تا زمان تأیید", fontFamily = Fa)
                }
            }
        } else {
            item {
                Text("سن بارداری را بر چه اساسی مشخص کنیم؟", fontFamily = Fa, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("تاریخ آخرین قاعدگی یک تخمین اولیه است؛ سونوگرافی سه‌ماهه اول یا تاریخ‌گذاری پزشک می‌تواند آن را دقیق‌تر کند.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { JourneyOption("سونوگرافی و سن بارداری گزارش‌شده", "سونوگرافی سه‌ماهه اول با اندازه CRL معمولاً دقیق‌تر است", Icons.Default.MonitorHeart, datingMethod == DATING_ULTRASOUND) { onMethod(DATING_ULTRASOUND) } }
            item { JourneyOption("تاریخ اولین روز آخرین قاعدگی", "مناسب وقتی تاریخ دقیق و چرخه نسبتاً منظم است", Icons.Default.CalendarMonth, datingMethod == DATING_LMP) { onMethod(DATING_LMP) } }
            item { JourneyOption("تاریخ تخمک‌گذاری مشخص است", "مثلاً با تست LH یا پایش پزشکی", Icons.Default.Spa, datingMethod == DATING_OVULATION) { onMethod(DATING_OVULATION) } }
            item { JourneyOption("بارداری با IVF یا انتقال جنین", "تاریخ انتقال و سن جنین هنگام انتقال", Icons.Default.Science, datingMethod == DATING_IVF) { onMethod(DATING_IVF) } }
            item { JourneyOption("موعد زایمان را پزشک تعیین کرده", "ثبت موعد نهایی اعلام‌شده توسط پزشک", Icons.Default.Verified, datingMethod == DATING_CLINICIAN) { onMethod(DATING_CLINICIAN) } }
            item { JourneyOption("فعلاً تاریخ‌ها را نمی‌دانم", "اطلاعات را بعداً تکمیل می‌کنم؛ سن بارداری فعلاً نامشخص می‌ماند", Icons.Default.HelpOutline, datingMethod == DATING_UNKNOWN) { onMethod(DATING_UNKNOWN) } }
            if (datingMethod != DATING_UNKNOWN) {
                item {
                    OutlinedCard(
                        Modifier.fillMaxWidth().clickable(onClick = onPickDate),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = RoseDark)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (datingMethod) {
                                        DATING_LMP -> "اولین روز آخرین قاعدگی"
                                        DATING_OVULATION -> "تاریخ تخمک‌گذاری"
                                        DATING_ULTRASOUND -> "تاریخ انجام سونوگرافی"
                                        DATING_IVF -> "تاریخ انتقال جنین"
                                        else -> "موعد زایمان اعلام‌شده"
                                    },
                                    fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                Text(if (dateSelected) jalali(referenceDate) else "برای انتخاب تاریخ لمس کن", fontFamily = Fa, fontSize = 14.sp, color = RoseDark)
                            }
                            Icon(Icons.Default.EditCalendar, null)
                        }
                    }
                }
            }
            if (datingMethod == DATING_ULTRASOUND) {
                item {
                    OutlinedTextField(
                        value = gestationalWeeks, onValueChange = { onWeeks(it.filter(Char::isDigit).take(2)) },
                        label = { Text("سن بارداری در روز سونوگرافی؛ هفته", fontFamily = Fa) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = gestationalDays, onValueChange = { onDays(it.filter(Char::isDigit).take(1)) },
                        label = { Text("روز اضافه (۰ تا ۶)", fontFamily = Fa) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (datingMethod == DATING_LMP) {
                item {
                    OutlinedTextField(
                        value = cycleLength, onValueChange = { onCycleLength(it.filter(Char::isDigit).take(2)) },
                        label = { Text("طول معمول چرخه قاعدگی (روز)", fontFamily = Fa) },
                        supportingText = { Text("اگر نمی‌دانی، ۲۸ روز را نگه دار. چرخه نامنظم می‌تواند دقت این تخمین را کم کند.", fontFamily = Fa) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (datingMethod == DATING_IVF) {
                item {
                    Text("سن جنین هنگام انتقال", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = embryoAgeDays == 3, onClick = { onEmbryoAge(3) }, label = { Text("روز ۳", fontFamily = Fa) })
                        FilterChip(selected = embryoAgeDays == 5, onClick = { onEmbryoAge(5) }, label = { Text("روز ۵", fontFamily = Fa) })
                        FilterChip(selected = embryoAgeDays == 6, onClick = { onEmbryoAge(6) }, label = { Text("روز ۶", fontFamily = Fa) })
                    }
                }
            }
            item {
                Text(
                    when (datingMethod) {
                        DATING_ULTRASOUND -> "برای تاریخ‌گذاری، گزارش سونوگرافی اولیه و اندازه CRL را با نظر پزشک مبنا قرار بده."
                        DATING_LMP -> "سن بارداری از روز اول آخرین قاعدگی محاسبه می‌شود؛ دو هفته نخست هم در شمارش بارداری قرار می‌گیرد."
                        DATING_OVULATION -> "در محاسبه پزشکی، روز تخمک‌گذاری تقریباً معادل ۲ هفته و صفر روز سن بارداری در نظر گرفته می‌شود."
                        DATING_IVF -> "برای بارداری IVF، تاریخ انتقال و سن جنین مبنای محاسبه است؛ تاریخ آخرین قاعدگی روش اصلی نیست."
                        DATING_CLINICIAN -> "موعد زایمان ثبت‌شده توسط پزشک را به‌عنوان تاریخ مرجع پرونده نگه می‌داریم."
                        else -> "بدون اطلاعات کافی، نونو سن بارداری یا موعد زایمان ساختگی نمایش نمی‌دهد."
                    },
                    fontFamily = Fa, fontSize = 11.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(
                    onClick = onContinuePregnancy,
                    enabled = datingMethod == DATING_UNKNOWN || (dateSelected &&
                        ((datingMethod == DATING_ULTRASOUND && (gestationalWeeks.toIntOrNull() ?: -1) in 4..42 && (gestationalDays.toIntOrNull() ?: -1) in 0..6) ||
                        (datingMethod == DATING_LMP && (cycleLength.toIntOrNull() ?: 28) in 21..45) ||
                        datingMethod in listOf(DATING_OVULATION, DATING_IVF, DATING_CLINICIAN))),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)
                ) { Text(if (datingMethod == DATING_UNKNOWN) "ادامه بدون تاریخ‌گذاری" else "ثبت اطلاعات و ادامه", fontFamily = Fa, fontWeight = FontWeight.Bold) }
            }
        }
        item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت", fontFamily = Fa) } }
    }
}

@Composable
private fun JourneyOption(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Blush else MaterialTheme.colorScheme.surface),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, RoseDark) else null
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RoseDark, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Fa, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(description, fontFamily = Fa, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = RoseDark)
        }
    }
}

@Composable
internal fun CycleModeScreen(
    stage: String,
    lastPeriod: LocalDate?,
    cycleLength: Int,
    onPickPeriodDate: () -> Unit,
    onCycleLength: (Int) -> Unit,
    onSwitchToPregnancy: () -> Unit,
    onEditStage: () -> Unit,
    cycleLogs: List<CycleDayLog>,
    onSaveCycleLog: (CycleDayLog) -> Unit,
    onDeleteCycleLog: (String) -> Unit,
    preconceptionDone: Set<Int>,
    onPreconceptionToggle: (Int, Boolean) -> Unit
) {
    val observedLength = calculatedCycleLength(cycleLogs) ?: cycleLength
    val predictionStart = detectedPeriodStarts(cycleLogs).lastOrNull() ?: lastPeriod
    val nextPeriod = predictionStart?.plusDays(observedLength.toLong())
    val ovulation = predictionStart?.plusDays((observedLength - 14).toLong())
    val fertileStart = ovulation?.minusDays(5)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
    ) {
        item {
            Text("سلام، به مسیر سلامت باروری نونو خوش آمدی", fontFamily = Fa, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("پیش‌بینی‌ها تخمینی‌اند و برای جلوگیری از بارداری یا تشخیص ناباروری قابل استفاده نیستند.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text(if (stage == STAGE_POSTPARTUM) "ثبت بازگشت قاعدگی" else "ثبت شروع آخرین قاعدگی", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(lastPeriod?.let(::jalali) ?: "هنوز تاریخی ثبت نشده", fontFamily = Fa, fontSize = 18.sp, color = RoseDark, modifier = Modifier.padding(top = 5.dp))
                    Button(onClick = onPickPeriodDate, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(7.dp)); Text(if (stage == STAGE_POSTPARTUM) "ثبت تاریخ بازگشت قاعدگی" else "انتخاب تاریخ شروع قاعدگی", fontFamily = Fa)
                    }
                }
            }
        }
        if (stage != STAGE_POSTPARTUM) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("طول معمول چرخه", fontFamily = Fa, fontWeight = FontWeight.Bold)
                    Text("$cycleLength روز", fontFamily = Fa, fontSize = 22.sp, color = RoseDark)
                    Slider(value = cycleLength.toFloat(), onValueChange = { onCycleLength(it.toInt()) }, valueRange = 21f..45f, steps = 23)
                    Text("اگر چرخه‌ها نامنظم‌اند، این پیش‌بینی‌ها دقت کمتری دارند.", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (stage == STAGE_POSTPARTUM) {
                        Text("پیگیری پس از زایمان", fontFamily = Fa, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("این بخش برای پیگیری مراقبت‌های مادر، علائم جسمی و روانی، شیردهی و زمان بازگشت چرخه طراحی می‌شود. در صورت علائم نگران‌کننده، پیگیری پزشکی را به تعویق نینداز.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("پیش‌بینی چرخه بعدی", fontFamily = Fa, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        CyclePredictionRow("قاعدگی بعدی (تخمینی)", nextPeriod?.let(::jalali) ?: "پس از ثبت قاعدگی")
                        CyclePredictionRow("تخمک‌گذاری (تخمینی)", ovulation?.let(::jalali) ?: "پس از ثبت قاعدگی")
                        CyclePredictionRow("شروع پنجره باروری", fertileStart?.let(::jalali) ?: "پس از ثبت قاعدگی")
                        Text("پنجره باروری معمولاً شامل پنج روز پیش از تخمک‌گذاری و روز تخمک‌گذاری است؛ زمان واقعی می‌تواند جابه‌جا شود.", fontFamily = Fa, fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { CycleAnalyticsSection(logs = cycleLogs) }
        item {
            CycleLogSection(
                logs = cycleLogs,
                onSave = onSaveCycleLog,
                onDelete = onDeleteCycleLog
            )
        }
        item {
            CycleHistoryEditor(
                logs = cycleLogs,
                onSave = onSaveCycleLog,
                onDelete = onDeleteCycleLog
            )
        }
        if (stage == STAGE_PLANNING || stage == STAGE_TTC) item {
            PreconceptionSection(completed = preconceptionDone, onToggle = onPreconceptionToggle)
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ثبت و پیگیری", fontFamily = Fa, fontWeight = FontWeight.Bold)
                    Text("ثبت روزانه، ویرایش روزهای گذشته، تاریخچه شروع قاعدگی و نمودار روند داده‌های ثبت‌شده در همین بخش در دسترس است.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    TextButton(onClick = onSwitchToPregnancy) { Text("ثبت بارداری یا نتیجه تست مثبت", fontFamily = Fa) }
                    TextButton(onClick = onEditStage) { Text("تغییر مرحله مسیر", fontFamily = Fa) }
                }
            }
        }
    }
}

@Composable
private fun CyclePredictionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Fa, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Fa, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoseDark)
    }
}


@Composable
internal fun DatingPendingScreen(onComplete: () -> Unit, onChangeStage: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, null, tint = RoseDark, modifier = Modifier.size(54.dp))
        Text("سن بارداری هنوز مشخص نشده", fontFamily = Fa, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        Text(
            "نونو برایت هفته یا موعد زایمان حدس نمی‌زند. اگر تست مثبت شده، برای تأیید و تعیین سن بارداری با پزشک یا ماما مشورت کن. سونوگرافی سه‌ماهه اول معمولاً دقیق‌ترین روش سونوگرافیک تاریخ‌گذاری است.",
            fontFamily = Fa, fontSize = 13.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 22.dp)
        )
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("ثبت تاریخ سونوگرافی یا اطلاعات پزشک", fontFamily = Fa) }
        TextButton(onClick = onChangeStage) { Text("تغییر مرحله مسیر", fontFamily = Fa) }
    }
}
