package ir.herotux.pregnancytracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NonoWelcomeScreen(email: String?, error: String?, busy: Boolean, onGoogle: () -> Unit, onGuest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = Blush, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChildCare, null, tint = RoseDark, modifier = Modifier.size(54.dp)) }
        }
        Text("به نونو خوش آمدی", fontFamily = Fa, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp))
        Text("همراه در مسیر سلامت باروری، بارداری و والد شدن", fontFamily = Fa, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Button(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Default.AccountCircle, null)
            Spacer(Modifier.width(9.dp))
            Text(if (busy) "در حال اتصال به Google..." else if (email == null) "ورود با حساب Google" else "ادامه با $email", fontFamily = Fa)
        }
        OutlinedButton(onClick = onGuest, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(8.dp)); Text("ادامه بدون حساب کاربری", fontFamily = Fa)
        }
        Text("بدون حساب هم می‌توانی آفلاین استفاده کنی. برای پرونده مشترک خانوادگی، ورود با Google لازم است.", fontFamily = Fa, fontSize = 11.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
fun NonoRoleScreen(hasLocalProfile: Boolean, onRole: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("نقش شما در مسیر سلامت باروری چیست؟", fontFamily = Fa, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(if (hasLocalProfile) "اطلاعات بارداری فعلی حفظ می‌شود." else "نقش فقط برای شخصی‌سازی تجربه نونو است.", fontFamily = Fa, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        RoleOption(Icons.Default.Person, "مادر", "مدیریت چرخه قاعدگی، باروری و پرونده سلامت") { onRole("mother") }
        Spacer(Modifier.height(12.dp))
        RoleOption(Icons.Default.Face, "پدر", "همراهی در مسیر برنامه‌ریزی، بارداری و سلامت خانواده") { onRole("father") }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("بازگشت", fontFamily = Fa) }
    }
}

@Composable
private fun RoleOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Blush, modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = RoseDark, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(description, fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronLeft, null)
        }
    }
}

@Composable
fun NonoFamilySetupScreen(role: String, email: String?, inviteCode: String, busy: Boolean, error: String?, onInviteCode: (String) -> Unit, onCreate: () -> Unit, onJoin: () -> Unit, onGoogle: () -> Unit, onLocal: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.FamilyRestroom, null, tint = RoseDark, modifier = Modifier.size(48.dp))
        Text("پرونده بارداری خانواده", fontFamily = Fa, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(if (role == "father") "به‌عنوان پدر می‌توانی با کد دعوت به پرونده‌ای که مادر ساخته بپیوندی؛ یا اگر اولین نفر هستی، پرونده بارداری همسرت را ایجاد کنی." else "می‌توانی پرونده بارداری را ایجاد کنی یا با کد دعوت به پرونده‌ای که همسرت ساخته بپیوندی.", fontFamily = Fa, fontSize = 13.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        Button(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.AddCircleOutline, null); Spacer(Modifier.width(8.dp))
            Text(if (email == null) "ثبت بارداری روی همین گوشی" else "ایجاد پرونده مشترک بارداری", fontFamily = Fa)
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp))
        OutlinedTextField(value = inviteCode, onValueChange = { onInviteCode(it.uppercase().filter(Char::isLetterOrDigit).take(8)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("کد دعوت ۸ رقمی پرونده", fontFamily = Fa) }, placeholder = { Text("مثلاً A7K9M2QX", fontFamily = Fa) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(14.dp))
        OutlinedButton(onClick = onJoin, enabled = !busy && email != null && inviteCode.length == 8, modifier = Modifier.fillMaxWidth().padding(top = 9.dp), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text("پیوستن به پرونده همسر", fontFamily = Fa)
        }
        if (email == null) {
            TextButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text("برای پرونده مشترک، ابتدا با Google وارد شو", fontFamily = Fa) }
            TextButton(onClick = onLocal, enabled = !busy) { Text("فعلاً فقط به‌صورت محلی ادامه می‌دهم", fontFamily = Fa) }
        } else {
            Text("حساب متصل: $email", fontFamily = Fa, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
        if (busy) CircularProgressIndicator(Modifier.padding(top = 12.dp).size(24.dp), strokeWidth = 2.dp)
        Text("پرونده مشترک فقط برای اعضای دعوت‌شده قابل مشاهده است.", fontFamily = Fa, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    }
}
