# Firebase setup for Nono

Firebase project: `nono-ef27d`  
Android package: `ir.herotux.pregnancytracker`

## What is already in the repository

- Firebase Google Services Gradle plugin and Firebase BoM.
- Firebase Authentication and Cloud Firestore SDK dependencies.
- `app/google-services.json` for the registered Android app.
- Firestore rules scoped to `users/{uid}/...`; users cannot read or write another user's data.
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
npx -y firebase-tools@latest deploy --project nono-ef27d --only firestore:rules
```

The rules intentionally deny all access except authenticated users reading/writing documents below their own `users/{uid}` path. Do not loosen them to public read/write.

## GitHub secret-scanning alert for google-services.json

The detected value is the Firebase app API key in `google-services.json`. Firebase documents these keys as public project/app identifiers, not authentication secrets. Access to Firestore data is controlled by Firebase Authentication, Firestore Security Rules, and (optionally) App Check—not by hiding this key.

Do not delete `google-services.json` or replace its key with a random value; the Android Firebase SDK needs a valid project API key. In Google Cloud Console → APIs & Services → Credentials, inspect the key referenced by this Android Firebase config:

- Keep its API restrictions limited to Firebase-related APIs.
- For Firebase Authentication, the allowlist must include Identity Toolkit API and Token Service API.
- Do not allow the Gemini Developer API on this public Firebase key.
- If applying Android application restrictions, add package `ir.herotux.pregnancytracker` and the appropriate SHA-1 fingerprints for every signing certificate that uses this key (debug and release).

Once those checks are complete, the GitHub alert can be dismissed as a false positive / won't fix because this is an intentionally public Firebase client configuration value. A rotation is not required solely because this Firebase API key appears in the repository.
