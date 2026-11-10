# Firebase setup for Nono

Firebase project: `nono-ef27d`  
Android package: `ir.herotux.pregnancytracker`

## What is already in the repository

- Firebase Google Services Gradle plugin and Firebase BoM.
- Firebase Authentication and Cloud Firestore SDK dependencies.
- `app/google-services.json` for the registered Android app.
- Firestore rules scoped to private `users/{uid}/...` documents and shared `families/{inviteCode}` pregnancy profiles.
- Family profiles are created by a signed-in mother or father. The 8-character invite code lets the other parent join the same profile; family listing is disabled.
- Public messaging profiles live in `publicProfiles/{uid}` and contain only a display name, unique username, avatar key, and optional Google photo URL. Username reservations are stored in `usernames/{username}`.
- One-to-one conversations live in `conversations/{conversationId}` with messages in a private `messages` subcollection. Only the two conversation members can read them.
- The in-app notification bell derives unread-message alerts from each user's unread counter. Push notifications (FCM) and media attachments are not part of this first version.
- Profile pictures use the signed-in Google account photo or built-in emoji avatars; no Firebase Storage upload or Storage billing is required.
- The initial shared family document stores the pregnancy due date and member roles. Other records remain on-device until their dedicated sync is implemented.
- Firebase CLI project alias in `.firebaserc`.

## One-time Firebase Console steps

1. Open Firebase Console → Authentication → Sign-in method → Google, enable Google sign-in, and set the project support email.
2. In Firebase Console → Project settings → Your apps → Android app, add the SHA-1 fingerprint for the signing certificate used by the build.
3. The GitHub Actions build workflow prints the debug SHA-1 in its run summary. Add that fingerprint for debug testing. Add the release signing SHA-1 separately before distributing a release APK.
4. After adding SHA-1 and enabling Google sign-in, download a fresh `google-services.json` and replace `app/google-services.json`. The current file has a Web OAuth client but does not yet contain an Android OAuth client.
5. Confirm Cloud Firestore is provisioned in the Firebase project.

## Deploy Firestore rules

After Firebase CLI is authenticated with an account that has access to `nono-ef27d`, run:

```bash
npx --yes firebase-tools@latest deploy --project nono-ef27d --only firestore:rules
```

The rules allow authenticated users to access only their own `users/{uid}` data and family profiles they belong to (or can access using a valid invite code). Family listing is disabled. Do not loosen these rules to public read/write. Deploy the updated rules after pulling this commit.

## GitHub secret-scanning alert for google-services.json

The detected value is the Firebase app API key in `google-services.json`. Firebase documents these keys as public project/app identifiers, not authentication secrets. Access to Firestore data is controlled by Firebase Authentication, Firestore Security Rules, and (optionally) App Check—not by hiding this key.

Do not delete `google-services.json` or replace its key with a random value; the Android Firebase SDK needs a valid project API key. In Google Cloud Console → APIs & Services → Credentials, inspect the key referenced by this Android Firebase config:

- Keep its API restrictions limited to Firebase-related APIs.
- For Firebase Authentication, the allowlist must include Identity Toolkit API and Token Service API.
- Do not allow the Gemini Developer API on this public Firebase key.
- If applying Android application restrictions, add package `ir.herotux.pregnancytracker` and the appropriate SHA-1 fingerprints for every signing certificate that uses this key (debug and release).

Once those checks are complete, the GitHub alert can be dismissed as a false positive / won't fix because this is an intentionally public Firebase client configuration value. A rotation is not required solely because this Firebase API key appears in the repository.
