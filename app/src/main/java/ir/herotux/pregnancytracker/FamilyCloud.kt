package ir.herotux.pregnancytracker

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.LocalDate

data class FamilyCloudProfile(val familyId: String, val inviteCode: String, val dueDate: LocalDate, val memberCount: Int)

object FamilyCloud {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun newCode(): String {
        val random = SecureRandom()
        return buildString(8) { repeat(8) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    suspend fun create(uid: String, role: String, dueDate: LocalDate): FamilyCloudProfile = withContext(Dispatchers.IO) {
        require(role == "mother" || role == "father") { "نقش کاربر معتبر نیست." }
        val db = FirebaseFirestore.getInstance()
        var code = newCode()
        var ref = db.collection("families").document(code)
        repeat(5) {
            if (Tasks.await(ref.get()).exists()) {
                code = newCode()
                ref = db.collection("families").document(code)
            }
        }
        val data = hashMapOf<String, Any>(
            "inviteCode" to code,
            "ownerUid" to uid,
            "dueDate" to dueDate.toString(),
            "members" to mapOf(uid to role),
            "createdAt" to FieldValue.serverTimestamp()
        )
        Tasks.await(ref.set(data))
        FamilyCloudProfile(code, code, dueDate, 1)
    }

    suspend fun join(uid: String, role: String, inviteCode: String): FamilyCloudProfile = withContext(Dispatchers.IO) {
        require(role == "mother" || role == "father") { "ابتدا نقش خود را انتخاب کن." }
        val code = inviteCode.trim().uppercase().filter { it.isLetterOrDigit() }
        require(code.length == 8) { "کد دعوت باید ۸ نویسه باشد." }
        val ref = FirebaseFirestore.getInstance().collection("families").document(code)
        val snapshot = Tasks.await(ref.get())
        require(snapshot.exists() && snapshot.getString("inviteCode") == code) { "کد دعوت پیدا نشد یا معتبر نیست." }
        val members = snapshot.get("members") as? Map<*, *> ?: emptyMap<Any, Any>()
        val existingRole = members[uid] as? String
        if (existingRole == null) {
            require(members.size < 2) { "این پرونده قبلاً به دو عضو خانواده متصل شده است." }
            Tasks.await(ref.update("members.$uid", role))
        }
        val due = snapshot.getString("dueDate")?.let(LocalDate::parse)
            ?: throw IllegalStateException("تاریخ بارداری در پرونده ثبت نشده است.")
        FamilyCloudProfile(code, code, due, members.size + if (existingRole == null) 1 else 0)
    }
}
