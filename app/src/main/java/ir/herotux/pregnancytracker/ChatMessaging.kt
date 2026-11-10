package ir.herotux.pregnancytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val presetAvatars = listOf("🌸", "🐻", "🦊", "🐼", "🐱", "🦋", "🌙", "🌻", "🐨", "🐰", "🦁", "🐧")

data class NonoPublicProfile(
    val uid: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val avatarKey: String
)

data class NonoMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long
)

data class NonoConversation(
    val id: String,
    val members: List<String>,
    val lastMessage: String,
    val lastSenderId: String,
    val updatedAt: Long,
    val unreadCount: Int
)

object NonoMessaging {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun ensureProfile(user: FirebaseUser) = withContext(Dispatchers.IO) {
        val ref = db.collection("publicProfiles").document(user.uid)
        val snapshot = Tasks.await(ref.get())
        if (!snapshot.exists()) {
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: "کاربر نونو"
            val photo = user.photoUrl?.toString()
            Tasks.await(ref.set(mapOf(
                "uid" to user.uid,
                "displayName" to name,
                "username" to "",
                "photoUrl" to photo,
                "avatarKey" to if (photo.isNullOrBlank()) presetAvatars.first() else "google",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )))
        }
    }

    suspend fun getProfile(uid: String): NonoPublicProfile? = withContext(Dispatchers.IO) {
        val doc = Tasks.await(db.collection("publicProfiles").document(uid).get())
        if (!doc.exists()) null else NonoPublicProfile(
            uid, doc.getString("displayName") ?: "کاربر نونو",
            doc.getString("username") ?: "", doc.getString("photoUrl"),
            doc.getString("avatarKey") ?: presetAvatars.first()
        )
    }

    suspend fun saveProfile(uid: String, displayName: String, username: String, avatarKey: String) = withContext(Dispatchers.IO) {
        val normalized = username.trim().lowercase(Locale.ROOT)
        require(normalized.matches(Regex("[a-z0-9_]{3,20}"))) {
            "نام کاربری باید ۳ تا ۲۰ نویسه و شامل حروف انگلیسی، عدد یا زیرخط باشد."
        }
        require(displayName.trim().length in 2..40) { "نام نمایشی باید بین ۲ تا ۴۰ نویسه باشد." }
        val profileRef = db.collection("publicProfiles").document(uid)
        val usernameRef = db.collection("usernames").document(normalized)
        val old = Tasks.await(profileRef.get()).getString("username").orEmpty()
        Tasks.await(db.runTransaction { tx ->
            val reservation = tx.get(usernameRef)
            if (reservation.exists() && reservation.getString("uid") != uid) {
                throw IllegalArgumentException("این نام کاربری قبلاً انتخاب شده است.")
            }
            if (old.isNotBlank() && old != normalized) {
                val oldRef = db.collection("usernames").document(old)
                val oldDoc = tx.get(oldRef)
                if (oldDoc.getString("uid") == uid) tx.delete(oldRef)
            }
            tx.set(usernameRef, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            tx.set(profileRef, mapOf(
                "uid" to uid, "displayName" to displayName.trim(),
                "username" to normalized, "avatarKey" to avatarKey,
                "updatedAt" to FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge())
            null
        })
    }

    suspend fun findUser(username: String, currentUid: String): NonoPublicProfile? = withContext(Dispatchers.IO) {
        val normalized = username.trim().removePrefix("@").lowercase(Locale.ROOT)
        val reservation = Tasks.await(db.collection("usernames").document(normalized).get())
        val uid = reservation.getString("uid") ?: return@withContext null
        if (uid == currentUid) return@withContext null
        getProfile(uid)
    }

    suspend fun openConversation(uid: String, otherUid: String): String = withContext(Dispatchers.IO) {
        require(uid != otherUid) { "نمی‌توانی به خودت پیام بدهی." }
        val members = listOf(uid, otherUid).sorted()
        val id = members.joinToString("_")
        val ref = db.collection("conversations").document(id)
        val current = Tasks.await(ref.get())
        if (!current.exists()) {
            Tasks.await(ref.set(mapOf(
                "members" to members,
                "lastMessage" to "",
                "lastSenderId" to "",
                "unreadBy" to mapOf(uid to 0, otherUid to 0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )))
        }
        id
    }

    fun listenConversations(uid: String, onChange: (List<NonoConversation>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("conversations").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "دریافت صندوق پیام ناموفق بود"); return@addSnapshotListener }
                val list = snapshot?.documents.orEmpty().map { doc ->
                    val unread = (doc.get("unreadBy") as? Map<*, *>)?.get(uid)?.let { (it as? Number)?.toInt() } ?: 0
                    NonoConversation(
                        doc.id, (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        doc.getString("lastMessage") ?: "", doc.getString("lastSenderId") ?: "",
                        (doc.getTimestamp("updatedAt")?.toDate()?.time) ?: 0L, unread
                    )
                }.sortedByDescending { it.updatedAt }
                onChange(list)
            }

    fun listenMessages(chatId: String, onChange: (List<NonoMessage>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("conversations").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "دریافت پیام‌ها ناموفق بود"); return@addSnapshotListener }
                onChange(snapshot?.documents.orEmpty().map { doc ->
                    NonoMessage(
                        doc.id, doc.getString("senderId") ?: "", doc.getString("text") ?: "",
                        doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                })
            }

    suspend fun sendMessage(chatId: String, senderUid: String, recipientUid: String, text: String) = withContext(Dispatchers.IO) {
        val body = text.trim()
        require(body.isNotEmpty() && body.length <= 4000) { "پیام باید بین ۱ تا ۴۰۰۰ نویسه باشد." }
        val conversation = db.collection("conversations").document(chatId)
        val message = conversation.collection("messages").document()
        val batch = db.batch()
        batch.set(message, mapOf(
            "senderId" to senderUid, "text" to body,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.update(conversation,
            mapOf(
                "lastMessage" to body.take(180),
                "lastSenderId" to senderUid,
                "updatedAt" to FieldValue.serverTimestamp(),
                "unreadBy.$recipientUid" to FieldValue.increment(1)
            )
        )
        Tasks.await(batch.commit())
    }

    suspend fun markRead(chatId: String, uid: String) = withContext(Dispatchers.IO) {
        Tasks.await(db.collection("conversations").document(chatId)
            .update("unreadBy.$uid", 0, "readAt.$uid", FieldValue.serverTimestamp()))
    }
}

@Composable
fun NonoAvatar(profile: NonoPublicProfile?, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Surface(shape = CircleShape, color = Blush, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (profile?.avatarKey == "google" && !profile.photoUrl.isNullOrBlank()) {
                AsyncImage(model = profile.photoUrl, contentDescription = "تصویر پروفایل", modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Text(profile?.avatarKey?.takeIf { it in presetAvatars } ?: "🌸", fontSize = (size.value * .48f).sp)
            }
        }
    }
}

@Composable
fun MessagingCenterScreen(
    uid: String?,
    onOpenChat: (NonoConversation, NonoPublicProfile) -> Unit,
    onProfile: () -> Unit,
    onSignIn: () -> Unit,
    onNotifications: () -> Unit
) {
    var conversations by remember { mutableStateOf(emptyList<NonoConversation>()) }
    var profiles by remember { mutableStateOf<Map<String, NonoPublicProfile>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<NonoPublicProfile?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(uid) {
        if (uid == null) onDispose { } else {
            val registration = NonoMessaging.listenConversations(uid, { list ->
                conversations = list
                list.flatMap { it.members }.filter { it != uid }.distinct().forEach { other ->
                    if (!profiles.containsKey(other)) scope.launch {
                        NonoMessaging.getProfile(other)?.let { profiles = profiles + (other to it) }
                    }
                }
            }, { loadError = it })
            onDispose { registration.remove() }
        }
    }

    if (uid == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, tint = RoseDark, modifier = Modifier.size(48.dp))
            Text("برای پیام‌رسانی وارد حساب Google شو", fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
            Text("صندوق پیام و پروفایل روی حساب کاربری ذخیره می‌شوند.", fontFamily = Fa, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("ورود با Google", fontFamily = Fa) }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("پیام‌های من", fontFamily = Fa, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("گفت‌وگوهای خصوصی و پیام‌های کاربران", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNotifications) {
                    BadgedBox(badge = { if (conversations.sumOf { it.unreadCount } > 0) Badge { Text(conversations.sumOf { it.unreadCount }.toString()) } }) {
                        Icon(Icons.Default.Notifications, contentDescription = "اعلان‌ها")
                    }
                }
                IconButton(onClick = onProfile) { Icon(Icons.Default.AccountCircle, contentDescription = "پروفایل") }
            }
        }
        item {
            Button(onClick = { searchOpen = !searchOpen; searchError = null; found = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("نوشتن پیام جدید", fontFamily = Fa)
            }
        }
        if (searchOpen) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پیدا کردن کاربر", fontFamily = Fa, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = username, onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("نام کاربری طرف مقابل", fontFamily = Fa) },
                        leadingIcon = { Text("@", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.None)
                    )
                    Button(
                        onClick = {
                            searchBusy = true; searchError = null; found = null
                            scope.launch {
                                runCatching { NonoMessaging.findUser(username, uid) }
                                    .onSuccess { found = it; if (it == null) searchError = "کاربری با این نام پیدا نشد." }
                                    .onFailure { searchError = it.message ?: "جست‌وجو ناموفق بود." }
                                searchBusy = false
                            }
                        }, enabled = username.length >= 3 && !searchBusy, modifier = Modifier.fillMaxWidth()
                    ) { if (searchBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("جست‌وجو", fontFamily = Fa) }
                    found?.let { profile ->
                        Row(Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                runCatching { NonoMessaging.openConversation(uid, profile.uid) }
                                    .onSuccess { onOpenChat(NonoConversation(it, listOf(uid, profile.uid), "", "", System.currentTimeMillis(), 0), profile) }
                                    .onFailure { searchError = it.message ?: "ساخت گفت‌وگو ناموفق بود." }
                            }
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NonoAvatar(profile)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(profile.displayName, fontFamily = Fa, fontWeight = FontWeight.Bold)
                                Text("@${profile.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Chat, null, tint = RoseDark)
                        }
                    }
                    searchError?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("همه", "دریافتی", "ارسالی").forEachIndexed { index, label ->
                    SegmentedButton(selected = tab == index, onClick = { tab = index }, shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                        Text(label, fontFamily = Fa, fontSize = 12.sp)
                    }
                }
            }
        }
        val visible = conversations.filter {
            tab == 0 || (tab == 1 && it.lastSenderId != uid && it.lastMessage.isNotBlank()) ||
                (tab == 2 && it.lastSenderId == uid && it.lastMessage.isNotBlank())
        }
        if (loadError != null) item { Text(loadError!!, fontFamily = Fa, color = MaterialTheme.colorScheme.error) }
        if (visible.isEmpty()) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MarkChatUnread, null, tint = RoseDark, modifier = Modifier.size(40.dp))
                    Text(if (tab == 0) "هنوز گفت‌وگویی نداری" else "پیامی در این بخش نیست", fontFamily = Fa, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("برای شروع، نام کاربری فرد موردنظر را جست‌وجو کن.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        items(visible, key = { it.id }) { conversation ->
            val otherUid = conversation.members.firstOrNull { it != uid }
            val profile = otherUid?.let { profiles[it] }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { if (profile != null) onOpenChat(conversation, profile) },
                shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    NonoAvatar(profile, 48.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(profile?.displayName ?: "کاربر نونو", fontFamily = Fa, fontWeight = FontWeight.Bold)
                        Text(conversation.lastMessage.ifBlank { "گفت‌وگو را شروع کنید" }, fontFamily = Fa, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile?.username?.let { "@$it" } ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (conversation.unreadCount > 0) Badge { Text(conversation.unreadCount.toString()) }
                        Text(if (conversation.lastSenderId == uid && conversation.lastMessage.isNotBlank()) "ارسالی" else "دریافتی", fontFamily = Fa, fontSize = 10.sp, color = RoseDark)
                    }
                }
            }
        }
    }
}

@Composable
fun NonoProfileScreen(uid: String?, onBack: () -> Unit, onSignIn: () -> Unit) {
    var profile by remember { mutableStateOf<NonoPublicProfile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var avatarKey by remember { mutableStateOf("google") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid) {
        if (uid != null) runCatching { NonoMessaging.getProfile(uid) }.onSuccess {
            profile = it
            displayName = it?.displayName.orEmpty()
            username = it?.username.orEmpty()
            avatarKey = it?.avatarKey ?: "google"
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت") }
            Text("پروفایل من", fontFamily = Fa, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (uid == null) {
            Text("برای ساخت پروفایل وارد حساب Google شو.", fontFamily = Fa)
            Button(onClick = onSignIn) { Text("ورود با Google", fontFamily = Fa) }
            return@Column
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NonoAvatar(profile?.copy(avatarKey = avatarKey), 92.dp)
                Text(displayName.ifBlank { "نام شما" }, fontFamily = Fa, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (username.isBlank()) "هنوز نام کاربری انتخاب نکرده‌ای" else "@$username", fontSize = 13.sp, color = RoseDark)
            }
        }
        OutlinedTextField(displayName, { displayName = it.take(40) }, modifier = Modifier.fillMaxWidth(), label = { Text("نام نمایشی", fontFamily = Fa) }, singleLine = true)
        OutlinedTextField(username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }, modifier = Modifier.fillMaxWidth(), label = { Text("نام کاربری یکتا (انگلیسی)", fontFamily = Fa) }, leadingIcon = { Text("@") }, supportingText = { Text("۳ تا ۲۰ نویسه؛ فقط حروف انگلیسی، عدد و زیرخط", fontFamily = Fa) }, singleLine = true)
        Text("تصویر پروفایل", fontFamily = Fa, fontWeight = FontWeight.Bold)
        Text("می‌توانی تصویر حساب Google را نگه داری یا یکی از آواتارهای آماده را انتخاب کنی.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = avatarKey == "google", onClick = { avatarKey = "google" }, label = { Text("تصویر Google", fontFamily = Fa) }, enabled = !profile?.photoUrl.isNullOrBlank())
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presetAvatars.chunked(6).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    group.forEach { avatar ->
                        Surface(
                            modifier = Modifier.size(44.dp).clickable { avatarKey = avatar },
                            shape = CircleShape,
                            color = if (avatarKey == avatar) Blush else MaterialTheme.colorScheme.surfaceVariant
                        ) { Box(contentAlignment = Alignment.Center) { Text(avatar, fontSize = 23.sp) } }
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Blush)) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lightbulb, null, tint = RoseDark)
                Spacer(Modifier.width(8.dp))
                Text("راهنمای تغییر عکس: از بخش «تصویر پروفایل»، گزینه «تصویر Google» را برای استفاده از عکس حساب Gmail انتخاب کن؛ یا یکی از شکلک‌های آماده را لمس کن. سپس «ذخیره پروفایل» را بزن.", fontFamily = Fa, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
        error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { NonoMessaging.saveProfile(uid, displayName, username, avatarKey) }
                        .onSuccess { profile = NonoMessaging.getProfile(uid) }
                        .onFailure { error = it.message ?: "ذخیره پروفایل ناموفق بود." }
                    busy = false
                }
            }, enabled = !busy && displayName.trim().length >= 2 && username.length >= 3,
            modifier = Modifier.fillMaxWidth()
        ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("ذخیره پروفایل", fontFamily = Fa) }
    }
}

@Composable
fun NonoNotificationsScreen(
    uid: String?,
    onBack: () -> Unit,
    onOpenChat: (NonoConversation, NonoPublicProfile) -> Unit
) {
    var conversations by remember { mutableStateOf(emptyList<NonoConversation>()) }
    var profiles by remember { mutableStateOf<Map<String, NonoPublicProfile>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    DisposableEffect(uid) {
        if (uid == null) onDispose { } else {
            val reg = NonoMessaging.listenConversations(uid, { list ->
                conversations = list
                list.filter { it.unreadCount > 0 }.flatMap { it.members }.filter { it != uid }.distinct().forEach { other ->
                    if (!profiles.containsKey(other)) scope.launch { NonoMessaging.getProfile(other)?.let { profiles = profiles + (other to it) } }
                }
            }, {})
            onDispose { reg.remove() }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت") }
            Text("اعلان‌ها", fontFamily = Fa, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (uid == null) {
            Text("برای مشاهده اعلان‌ها وارد حساب Google شو.", fontFamily = Fa)
        } else {
            val unread = conversations.filter { it.unreadCount > 0 }
            if (unread.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, tint = RoseDark, modifier = Modifier.size(48.dp))
                    Text("اعلان خوانده‌نشده‌ای نداری", fontFamily = Fa, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text("پیام‌های جدید اینجا نمایش داده می‌شوند.", fontFamily = Fa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(unread, key = { it.id }) { conversation ->
                        val other = conversation.members.firstOrNull { it != uid }
                        val profile = other?.let { profiles[it] }
                        Card(Modifier.fillMaxWidth().clickable { if (profile != null) onOpenChat(conversation, profile) }, shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                NonoAvatar(profile)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text("پیام جدید از ${profile?.displayName ?: "کاربر نونو"}", fontFamily = Fa, fontWeight = FontWeight.Bold)
                                    Text(conversation.lastMessage, fontFamily = Fa, fontSize = 12.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Badge { Text(conversation.unreadCount.toString()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NonoChatThreadScreen(
    uid: String,
    conversation: NonoConversation,
    other: NonoPublicProfile,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf(emptyList<NonoMessage>()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val otherUid = other.uid
    DisposableEffect(conversation.id) {
        val reg = NonoMessaging.listenMessages(conversation.id, { messages = it }, { error = it })
        onDispose { reg.remove() }
    }
    LaunchedEffect(conversation.id, uid) { runCatching { NonoMessaging.markRead(conversation.id, uid) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت") }
            NonoAvatar(other, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                Text(other.displayName, fontFamily = Fa, fontWeight = FontWeight.Bold)
                Text("@${other.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == uid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                    Surface(
                        color = if (mine) Blush else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                            Text(message.text, fontFamily = Fa, fontSize = 14.sp, lineHeight = 21.sp)
                            Text(
                                SimpleDateFormat("HH:mm", Locale("fa")).format(Date(message.createdAt)),
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End).padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
        error?.let { Text(it, fontFamily = Fa, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(4000) },
                modifier = Modifier.weight(1f), placeholder = { Text("پیامت را بنویس...", fontFamily = Fa) },
                shape = RoundedCornerShape(22.dp), maxLines = 4
            )
            IconButton(
                onClick = {
                    val body = text.trim()
                    if (body.isNotEmpty()) {
                        sending = true; error = null
                        scope.launch {
                            runCatching { NonoMessaging.sendMessage(conversation.id, uid, otherUid, body) }
                                .onSuccess { text = "" }
                                .onFailure { error = it.message ?: "ارسال پیام ناموفق بود." }
                            sending = false
                        }
                    }
                }, enabled = text.isNotBlank() && !sending
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Send, "ارسال", tint = RoseDark)
            }
        }
    }
}
