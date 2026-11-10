# Professional care access (foundation)

This feature introduces independent professional accounts for midwives and OB/GYNs.
A professional is **not** a third member of a family.

## Consent model

- A professional creates a profile with role `midwife` or `obgyn`.
- The profile starts with `reviewStatus: pending`; the professional cannot self-verify.
- A 10-character high-entropy invite code is generated for the professional.
- A mother/father who belongs to a family enters that code and chooses explicit data scopes.
- The patient owns the request and can approve, reject, or revoke it.
- A professional can read only requests addressed to their UID. A patient can read only their own requests.
- Requests are retained as audit history and cannot be deleted by clients.

## Important current limitation

This commit implements the consent and access-request security foundation only. It does **not** grant professionals access to family documents or clinical records. Existing pregnancy records are largely stored locally on the device; no professional-facing clinical data API is enabled here. Do not describe the app as sharing medical records with clinicians until scoped record synchronization, professional verification, and end-to-end tests are implemented.

## Planned next layer

1. Professional identity and license verification workflow.
2. Patient-facing consent UI and professional request inbox.
3. Separate scoped clinical-record documents; never expose the family root document.
4. Audit events for every record read/write and access revocation.
5. Integration tests using two authenticated accounts and explicit consent.
