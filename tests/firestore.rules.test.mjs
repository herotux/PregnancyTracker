import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  getDocs,
  collection,
  setDoc,
  updateDoc,
  serverTimestamp,
  query,
  whereArrayContains,
  writeBatch,
} from "firebase/firestore";
import { after, before, beforeEach, test } from "node:test";

const PROJECT_ID = "demo-nono-rules";
const FAMILY_ID = "ABCDEFGH";
let env;

const family = (ownerUid = "mother-uid") => ({
  inviteCode: FAMILY_ID,
  ownerUid,
  dueDate: "2027-02-04",
  members: { [ownerUid]: "mother" },
  createdAt: new Date("2026-11-10T00:00:00.000Z"),
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
    },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  await env.cleanup();
});

test("authenticated mother can create a family with herself as the first member", async () => {
  const db = env.authenticatedContext("mother-uid").firestore();
  await assertSucceeds(setDoc(doc(db, "families/" + FAMILY_ID), family()));
  await assertSucceeds(getDoc(doc(db, "families/" + FAMILY_ID)));
});

test("invited father can read by invite code and join as the second member", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "families/" + FAMILY_ID), family());
  });

  const fatherDb = env.authenticatedContext("father-uid").firestore();
  const ref = doc(fatherDb, "families/" + FAMILY_ID);
  const beforeJoin = await assertSucceeds(getDoc(ref));
  if (!beforeJoin.exists()) throw new Error("Invite-code lookup did not find family");

  await assertSucceeds(updateDoc(ref, { "members.father-uid": "father" }));
  const afterJoin = await assertSucceeds(getDoc(ref));
  if (afterJoin.data().members["father-uid"] !== "father") {
    throw new Error("Father membership was not saved");
  }
});

test("a third member cannot join a family that already has two members", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "families/" + FAMILY_ID), {
      ...family(),
      members: { "mother-uid": "mother", "father-uid": "father" },
    });
  });

  const thirdDb = env.authenticatedContext("third-uid").firestore();
  await assertFails(updateDoc(doc(thirdDb, "families/" + FAMILY_ID), {
    "members.third-uid": "father",
  }));
});

test("only an existing family member can update the due date", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "families/" + FAMILY_ID), family());
  });

  const outsiderDb = env.authenticatedContext("outsider-uid").firestore();
  await assertFails(updateDoc(doc(outsiderDb, "families/" + FAMILY_ID), {
    dueDate: "2027-02-05",
    updatedAt: new Date(),
  }));

  const motherDb = env.authenticatedContext("mother-uid").firestore();
  await assertSucceeds(updateDoc(doc(motherDb, "families/" + FAMILY_ID), {
    dueDate: "2027-02-05",
    updatedAt: new Date(),
  }));
});

test("family collection listing is denied", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "families/" + FAMILY_ID), family());
  });

  const memberDb = env.authenticatedContext("mother-uid").firestore();
  await assertFails(getDocs(collection(memberDb, "families")));
});

test("unauthenticated users cannot read or create a family", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "families/" + FAMILY_ID)));
  await assertFails(setDoc(doc(db, "families/" + FAMILY_ID), family()));
});

const PROFESSIONAL_UID = "midwife-uid";
const INVITE_CODE = "ABCDEFGHJK";

async function seedProfessional() {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "professionals/" + PROFESSIONAL_UID), {
      uid: PROFESSIONAL_UID,
      role: "midwife",
      displayName: "ماما نمونه",
      licenseNumber: "MID-123",
      reviewStatus: "pending",
    });
    await setDoc(doc(db, "professionalInvites/" + INVITE_CODE), {
      professionalUid: PROFESSIONAL_UID,
      role: "midwife",
    });
    await setDoc(doc(db, "families/" + FAMILY_ID), family());
  });
}

test("professional cannot self-verify or read another professional profile", async () => {
  const db = env.authenticatedContext(PROFESSIONAL_UID).firestore();
  await assertFails(setDoc(doc(db, "professionals/" + PROFESSIONAL_UID), {
    uid: PROFESSIONAL_UID, role: "midwife", displayName: "ماما", reviewStatus: "verified",
  }));
  await assertFails(getDoc(doc(db, "professionals/another-professional")));
});

test("patient can create a scoped consent request using a professional invite code", async () => {
  await seedProfessional();
  const db = env.authenticatedContext("mother-uid").firestore();
  const requestRef = doc(db, "careRequests/request-1");
  await assertSucceeds(setDoc(requestRef, {
    patientUid: "mother-uid",
    familyId: FAMILY_ID,
    professionalUid: PROFESSIONAL_UID,
    professionalRole: "midwife",
    inviteCode: INVITE_CODE,
    scopes: ["pregnancy_summary", "exam_records"],
    status: "pending",
  }));
  await assertSucceeds(getDoc(requestRef));
});

test("patient consent request cannot grant unapproved or unknown scopes", async () => {
  await seedProfessional();
  const db = env.authenticatedContext("mother-uid").firestore();
  await assertFails(setDoc(doc(db, "careRequests/bad-scope"), {
    patientUid: "mother-uid",
    familyId: FAMILY_ID,
    professionalUid: PROFESSIONAL_UID,
    professionalRole: "midwife",
    inviteCode: INVITE_CODE,
    scopes: ["all_records"],
    status: "pending",
  }));
  await assertFails(setDoc(doc(db, "careRequests/forged-patient"), {
    patientUid: "someone-else",
    familyId: FAMILY_ID,
    professionalUid: PROFESSIONAL_UID,
    professionalRole: "midwife",
    inviteCode: INVITE_CODE,
    scopes: ["pregnancy_summary"],
    status: "pending",
  }));
});

test("only the patient can approve or revoke a request and scopes stay immutable", async () => {
  await seedProfessional();
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "careRequests/request-2"), {
      patientUid: "mother-uid",
      familyId: FAMILY_ID,
      professionalUid: PROFESSIONAL_UID,
      professionalRole: "midwife",
      inviteCode: INVITE_CODE,
      scopes: ["pregnancy_summary"],
      status: "pending",
    });
  });
  const professionalDb = env.authenticatedContext(PROFESSIONAL_UID).firestore();
  await assertSucceeds(getDoc(doc(professionalDb, "careRequests/request-2")));
  await assertFails(updateDoc(doc(professionalDb, "careRequests/request-2"), { status: "approved" }));

  const patientDb = env.authenticatedContext("mother-uid").firestore();
  await assertSucceeds(updateDoc(doc(patientDb, "careRequests/request-2"), { status: "approved" }));
  await assertFails(updateDoc(doc(patientDb, "careRequests/request-2"), {
    status: "revoked",
    scopes: ["private_notes"],
  }));
  await assertSucceeds(updateDoc(doc(patientDb, "careRequests/request-2"), { status: "revoked" }));
});

test("unrelated users cannot read a patient's care request", async () => {
  await seedProfessional();
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "careRequests/request-3"), {
      patientUid: "mother-uid",
      familyId: FAMILY_ID,
      professionalUid: PROFESSIONAL_UID,
      professionalRole: "midwife",
      inviteCode: INVITE_CODE,
      scopes: ["pregnancy_summary"],
      status: "pending",
    });
  });
  const outsiderDb = env.authenticatedContext("outsider-uid").firestore();
  await assertFails(getDoc(doc(outsiderDb, "careRequests/request-3")));
});


test("authenticated users can read public profiles but cannot edit another user's profile", async () => {
  const ownerDb = env.authenticatedContext("profile-owner").firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "publicProfiles/profile-owner"), {
    uid: "profile-owner",
    displayName: "کاربر نمونه",
    username: "sample_user",
    photoUrl: null,
    avatarKey: "🌸",
    createdAt: new Date(),
    updatedAt: new Date(),
  }));
  const otherDb = env.authenticatedContext("profile-viewer").firestore();
  await assertSucceeds(getDoc(doc(otherDb, "publicProfiles/profile-owner")));
  await assertFails(updateDoc(doc(otherDb, "publicProfiles/profile-owner"), {
    displayName: "تغییر غیرمجاز",
  }));
});

test("username reservations are private lookups and cannot be claimed by another user", async () => {
  const ownerDb = env.authenticatedContext("profile-owner").firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "usernames/sample_user"), {
    uid: "profile-owner",
    createdAt: new Date(),
  }));
  const otherDb = env.authenticatedContext("profile-viewer").firestore();
  await assertSucceeds(getDoc(doc(otherDb, "usernames/sample_user")));
  await assertFails(setDoc(doc(otherDb, "usernames/sample_user"), {
    uid: "profile-viewer",
    createdAt: new Date(),
  }));
  await assertFails(getDocs(collection(otherDb, "usernames")));
});

test("private conversations and messages are visible only to their two members", async () => {
  const senderDb = env.authenticatedContext("sender-uid").firestore();
  const conversationRef = doc(senderDb, "conversations/sender-uid_recipient-uid");
  await assertSucceeds(setDoc(conversationRef, {
    members: ["recipient-uid", "sender-uid"],
    lastMessage: "",
    lastSenderId: "",
    unreadBy: { "sender-uid": 0, "recipient-uid": 0 },
    createdAt: new Date(),
    updatedAt: new Date(),
  }));
  await assertSucceeds(setDoc(doc(senderDb, "conversations/sender-uid_recipient-uid/messages/message-1"), {
    senderId: "sender-uid",
    text: "سلام",
    createdAt: new Date(),
  }));
  const recipientDb = env.authenticatedContext("recipient-uid").firestore();
  await assertSucceeds(getDoc(doc(recipientDb, "conversations/sender-uid_recipient-uid")));
  await assertSucceeds(getDoc(doc(recipientDb, "conversations/sender-uid_recipient-uid/messages/message-1")));
  const outsiderDb = env.authenticatedContext("outsider-uid").firestore();
  await assertFails(getDoc(doc(outsiderDb, "conversations/sender-uid_recipient-uid")));
  await assertFails(getDoc(doc(outsiderDb, "conversations/sender-uid_recipient-uid/messages/message-1")));
});

test("a user cannot send a message pretending to be another participant", async () => {
  const senderDb = env.authenticatedContext("sender-uid").firestore();
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "conversations/sender-uid_recipient-uid"), {
      members: ["sender-uid", "recipient-uid"],
      lastMessage: "",
      lastSenderId: "",
      unreadBy: { "sender-uid": 0, "recipient-uid": 0 },
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });
  await assertFails(setDoc(doc(senderDb, "conversations/sender-uid_recipient-uid/messages/forged"), {
    senderId: "recipient-uid",
    text: "پیام جعلی",
    createdAt: new Date(),
  }));
});


test("users can submit and read only their own issue reports", async () => {
  const ownerDb = env.authenticatedContext("report-owner").firestore();
  const report = {
    uid: "report-owner",
    title: "مشکل باز شدن تقویم",
    category: "bug",
    description: "وقتی تقویم را باز می‌کنم صفحه بسته می‌شود.",
    appVersion: "1.0.0",
    versionCode: 1,
    androidVersion: "16",
    device: "Samsung Galaxy",
    status: "new",
    createdAt: serverTimestamp(),
  };
  await assertSucceeds(setDoc(doc(ownerDb, "issueReports/report-1"), report));
  await assertSucceeds(getDoc(doc(ownerDb, "issueReports/report-1")));
  const otherDb = env.authenticatedContext("report-other").firestore();
  await assertFails(getDoc(doc(otherDb, "issueReports/report-1")));
  await assertFails(updateDoc(doc(ownerDb, "issueReports/report-1"), { status: "resolved" }));
});

test("users cannot submit issue reports on behalf of another account or forge review status", async () => {
  const db = env.authenticatedContext("report-owner").firestore();
  await assertFails(setDoc(doc(db, "issueReports/forged-owner"), {
    uid: "someone-else",
    title: "عنوان مشکل",
    category: "bug",
    description: "شرح کافی برای گزارش مشکل آزمایشی",
    appVersion: "1.0.0",
    versionCode: 1,
    androidVersion: "16",
    device: "Android",
    status: "new",
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(db, "issueReports/forged-status"), {
    uid: "report-owner",
    title: "عنوان مشکل",
    category: "bug",
    description: "شرح کافی برای گزارش مشکل آزمایشی",
    appVersion: "1.0.0",
    versionCode: 1,
    androidVersion: "16",
    device: "Android",
    status: "resolved",
    createdAt: serverTimestamp(),
  }));
});


test("users can send friend requests, but unrelated accounts cannot read them", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "publicProfiles/friend-recipient"), {
      uid: "friend-recipient",
      displayName: "دوست نمونه",
      username: "friend_sample",
      avatarKey: "🌸",
    });
  });

  const senderDb = env.authenticatedContext("friend-sender").firestore();
  const requestRef = doc(senderDb, "friendRequests/friend-sender_friend-recipient");
  await assertSucceeds(setDoc(requestRef, {
    senderUid: "friend-sender",
    recipientUid: "friend-recipient",
    status: "pending",
    createdAt: new Date(),
    updatedAt: new Date(),
  }));

  const recipientDb = env.authenticatedContext("friend-recipient").firestore();
  await assertSucceeds(getDoc(requestRef));
  await assertSucceeds(getDocs(collection(recipientDb, "friendRequests")));

  const outsiderDb = env.authenticatedContext("friend-outsider").firestore();
  await assertFails(getDoc(doc(outsiderDb, "friendRequests/friend-sender_friend-recipient")));
});

test("friendship is created only when the recipient accepts the request atomically", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "friendRequests/friend-sender_friend-recipient"), {
      senderUid: "friend-sender",
      recipientUid: "friend-recipient",
      status: "pending",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const recipientDb = env.authenticatedContext("friend-recipient").firestore();
  const batch = writeBatch(recipientDb);
  batch.update(doc(recipientDb, "friendRequests/friend-sender_friend-recipient"), {
    status: "accepted",
    updatedAt: new Date(),
  });
  batch.set(doc(recipientDb, "friendships/friend-recipient_friend-sender"), {
    members: ["friend-recipient", "friend-sender"],
    requestId: "friend-sender_friend-recipient",
    createdBy: "friend-recipient",
    createdAt: new Date(),
  });
  await assertSucceeds(batch.commit());

  await assertSucceeds(getDocs(query(
    collection(recipientDb, "friendships"),
    whereArrayContains("members", "friend-recipient")
  )));
  const senderDb = env.authenticatedContext("friend-sender").firestore();
  await assertSucceeds(getDocs(query(
    collection(senderDb, "friendships"),
    whereArrayContains("members", "friend-sender")
  )));
});

test("a user cannot create a friendship without accepting a matching friend request", async () => {
  const db = env.authenticatedContext("friend-recipient").firestore();
  await assertFails(setDoc(doc(db, "friendships/friend-recipient_friend-sender"), {
    members: ["friend-recipient", "friend-sender"],
    requestId: "missing-request",
    createdBy: "friend-recipient",
    createdAt: new Date(),
  }));
});
