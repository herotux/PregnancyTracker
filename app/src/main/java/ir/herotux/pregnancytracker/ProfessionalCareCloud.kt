package ir.herotux.pregnancytracker

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

enum class ProfessionalRole(val wireValue: String, val label: String) {
    MIDWIFE("midwife", "ماما"),
    OBGYN("obgyn", "متخصص زنان و زایمان");

    companion object {
        fun fromWire(value: String?): ProfessionalRole? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class CareScope(val wireValue: String, val label: String) {
    PREGNANCY_SUMMARY("pregnancy_summary", "خلاصه بارداری"),
    EXAM_RECORDS("exam_records", "آزمایش‌ها"),
    ULTRASOUNDS("ultrasounds", "سونوگرافی‌ها"),
    MEDICATIONS("medications", "داروها و مکمل‌ها"),
    SYMPTOMS("symptoms", "علائم ثبت‌شده"),
    PRIVATE_NOTES("private_notes", "یادداشت‌های خصوصی"),
    APPOINTMENTS("appointments", "نوبت‌ها"),
    CLINICIAN_NOTES("clinician_notes", "یادداشت درمانگر");

    companion object {
        fun fromWire(value: String): CareScope? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class ProfessionalProfile(
    val uid: String,
    val role: ProfessionalRole,
    val displayName: String,
    val licenseNumber: String?,
    val reviewStatus: String
)

data class CareRequest(
    val id: String,
    val patientUid: String,
    val familyId: String,
    val professionalUid: String,
    val professionalRole: ProfessionalRole,
    val scopes: Set<CareScope>,
    val status: String
)

/**
 * Consent metadata only. This class deliberately does not expose pregnancy records.
 * Clinical data sharing must be implemented as separately scoped collections before
 * any professional can read patient records.
 */
object ProfessionalCareCloud {
    private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun newInviteCode(): String {
        val random = SecureRandom()
        return buildString(10) {
            repeat(10) { append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)]) }
        }
    }

    suspend fun createProfile(
        uid: String,
        role: ProfessionalRole,
        displayName: String,
        licenseNumber: String?
    ): String = withContext(Dispatchers.IO) {
        require(displayName.trim().length >= 2) { "نام و نام خانوادگی را وارد کن." }
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("professionals").document(uid)
        val code = newInviteCode()
        val invite = db.collection("professionalInvites").document(code)
        Tasks.await(db.runBatch { batch ->
            batch.set(profile, mapOf(
                "uid" to uid,
                "role" to role.wireValue,
                "displayName" to displayName.trim(),
                "licenseNumber" to licenseNumber?.trim().orEmpty(),
                "reviewStatus" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.set(invite, mapOf(
                "professionalUid" to uid,
                "role" to role.wireValue,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        })
        code
    }

    suspend fun requestPatientAccess(
        patientUid: String,
        familyId: String,
        inviteCode: String,
        scopes: Set<CareScope>
    ): String = withContext(Dispatchers.IO) {
        require(scopes.isNotEmpty()) { "حداقل یک بخش برای اشتراک‌گذاری انتخاب کن." }
        val db = FirebaseFirestore.getInstance()
        val code = inviteCode.trim().uppercase()
        require(code.length == 10) { "کد درمانگر باید ۱۰ نویسه باشد." }
        val invite = Tasks.await(db.collection("professionalInvites").document(code).get())
        require(invite.exists()) { "کد درمانگر معتبر نیست." }
        val professionalUid = invite.getString("professionalUid")
            ?: throw IllegalStateException("حساب درمانگر معتبر نیست.")
        val role = invite.getString("role")
            ?: throw IllegalStateException("نقش درمانگر ثبت نشده است.")
        val ref = db.collection("careRequests").document()
        Tasks.await(ref.set(mapOf(
            "patientUid" to patientUid,
            "familyId" to familyId,
            "professionalUid" to professionalUid,
            "professionalRole" to role,
            "inviteCode" to code,
            "scopes" to scopes.map { it.wireValue },
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )))
        ref.id
    }

    suspend fun respondToRequest(requestId: String, approve: Boolean) = withContext(Dispatchers.IO) {
        val status = if (approve) "approved" else "rejected"
        val ref = FirebaseFirestore.getInstance().collection("careRequests").document(requestId)
        Tasks.await(ref.update(
            mapOf(
                "status" to status,
                "respondedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ))
    }

    suspend fun revokeAccess(requestId: String) = withContext(Dispatchers.IO) {
        val ref = FirebaseFirestore.getInstance().collection("careRequests").document(requestId)
        Tasks.await(ref.update(
            mapOf(
                "status" to "revoked",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ))
    }
}
