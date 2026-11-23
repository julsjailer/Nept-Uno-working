const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_neptuno_123";
const BOB_UID = "bob_neptuno_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied read and write on all Neptuno collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Anon",
      languageCode: "es",
      personalityId: "analytical",
      avatarStyleId: "abyssal_core",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
});

test("User profile: owner create, partial update, immutable guards, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid Kotlin create payload
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      languageCode: "es",
      personalityId: "analytical",
      avatarStyleId: "abyssal_core",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      personalityId: "calm_guide",
      avatarStyleId: "tidal_pulse",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / privilege escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user read/write denied, list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijack",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authorized memories: owner CRUD, validation bounds, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid memory create payload (mirrors Kotlin toMap without @DocumentId id)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        title: "Idioma técnico",
        content: "Prefiero respuestas concisas en Kotlin.",
        category: "PREFERENCE",
        authorizedByUser: true,
        localId: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  // List subcollection succeeds for owner (even when filtered/ordered)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .update({
        content: "Prefiero respuestas en Kotlin con Compose.",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject unauthorized memory (authorizedByUser: false)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_unauth")
      .set({
        userId: ALICE_UID,
        title: "No autorizado",
        content: "Dato no consentido",
        category: "GENERAL",
        authorizedByUser: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject redundant @DocumentId `id` key in payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_2")
      .set({
        id: "mem_2",
        userId: ALICE_UID,
        title: "Con id",
        content: "Contenido",
        category: "GENERAL",
        authorizedByUser: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .delete()
  );
});

test("Conversations and messages: owner create, parent exists guard, immutable messages, cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Orphan message without parent conversation must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_missing")
      .collection("messages")
      .doc("msg_orphan")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_missing",
        sender: "USER",
        content: "Hola",
        interrupted: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create conversation session
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Sesión principal",
        messageCount: 0,
        localId: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create message under existing conversation (without null providerId key)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        sender: "USER",
        content: "Hola Neptuno",
        interrupted: false,
        createdAt: new Date(Date.now() - 5000),
        updatedAt: new Date(Date.now() - 5000),
      })
  );

  // Create assistant message with providerId
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_2")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        sender: "ASSISTANT",
        content: "Hola, estoy listo.",
        interrupted: false,
        providerId: "gemini_firebase",
        createdAt: new Date(Date.now() - 2000),
        updatedAt: new Date(Date.now() - 2000),
      })
  );

  // Update conversation metadata (messageCount & updatedAt)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .update({
        messageCount: 2,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Messages are immutable on update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        content: "Editado",
      })
  );

  // Owner can list & delete
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .get()
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .get()
  );
});
