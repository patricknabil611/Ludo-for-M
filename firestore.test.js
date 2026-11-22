const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, nickname = "Alice") {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    nickname,
    googleLinked: true,
    wins: 2,
    gamesPlayed: 5,
    weeklyWins: 1,
    weeklyGamesPlayed: 2,
    weekKey: "2026-W47",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validRoomPayload(roomCode, hostUid, publicMatchmaking = false) {
  const ts = new Date(Date.now() - 2000);
  return {
    roomCode,
    hostUid,
    mode: "CLASSIC",
    targetPlayerCount: 2,
    timedDurationSeconds: 300,
    status: "LOBBY",
    publicMatchmaking,
    memberUids: [hostUid],
    seats: [
      {
        seatIndex: 0,
        color: "RED",
        uid: hostUid,
        nickname: "Alice",
        controllerType: "HUMAN",
        botDifficulty: "MEDIUM",
        connected: true,
        botTakeover: false,
        consecutiveMisses: 0,
        lastHeartbeatMs: Date.now(),
        rematchVoted: false,
      },
      {
        seatIndex: 1,
        color: "YELLOW",
        uid: "",
        nickname: "Open Seat",
        controllerType: "OPEN",
        botDifficulty: "MEDIUM",
        connected: false,
        botTakeover: false,
        consecutiveMisses: 0,
        lastHeartbeatMs: 0,
        rematchVoted: false,
      },
    ],
    diceSeed: 123456789,
    rollSeq: 0,
    turnSeq: 0,
    lastActionType: "NONE",
    lastActionSummary: "Room created",
    lastMovedColor: "",
    lastMovedTokenId: -1,
    lastMovedFromProgress: -1,
    lastMovedToProgress: -1,
    gameStateMap: {},
    turnStartedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

// --- 1. UNAUTHENTICATED DENIAL ---

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("leaderboard").where("googleLinked", "==", true).get());
  await assertFails(unauthDb.collection("rooms").doc("LUDO12").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("rooms").doc("LUDO12").set(validRoomPayload("LUDO12", ALICE_UID))
  );
});

// --- 2. USERS COLLECTION (PII ISOLATION & SCHEMA) ---

test("Users collection: owner create/read/update succeeds; cross-user and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice"))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").where("userId", "==", ALICE_UID).get()
  );

  // Partial update matching Kotlin UserProfileRepository (including custom avatarId)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "AlicePro",
      avatarId: "v1:FOX:COBALT:GOLD_RING:CROWN",
      wins: 3,
      gamesPlayed: 6,
      weeklyWins: 2,
      weeklyGamesPlayed: 3,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Oversized avatarId rejected (>64 chars)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarId: "v1:" + "X".repeat(70),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read & write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      nickname: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow field / @DocumentId 'id' / future timestamp / immutable createdAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// --- 3. LEADERBOARD COLLECTION (GOOGLE ACCOUNTS ONLY + QUERY ALIGNMENT) ---

test("Leaderboard: Google user creates/updates own entry; filtered query succeeds; unfiltered fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice"))
  );

  // Bob can query Google-linked leaderboard entries (All-time & Weekly)
  await assertSucceeds(
    bobDb.collection("leaderboard").where("googleLinked", "==", true).get()
  );
  await assertSucceeds(
    bobDb
      .collection("leaderboard")
      .where("googleLinked", "==", true)
      .where("weekKey", "==", "2026-W47")
      .get()
  );

  // Unfiltered list fails
  await assertFails(bobDb.collection("leaderboard").get());

  // Non-Google entry cannot be added to leaderboard
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      ...validUserPayload(BOB_UID, "GuestBob"),
      googleLinked: false,
    })
  );

  // Bob cannot modify Alice's leaderboard score
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      wins: 99,
      gamesPlayed: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice cannot inflate wins beyond gamesPlayed or jump by +500
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      wins: 10,
      gamesPlayed: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      wins: 200,
      gamesPlayed: 200,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid incremental match + avatarId update succeeds
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      avatarId: "v1:LION:SUNSET:DOUBLE_RIM:CROWN",
      wins: 3,
      gamesPlayed: 6,
      weeklyWins: 2,
      weeklyGamesPlayed: 3,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

// --- 4. MULTIPLAYER ROOMS (FRIEND CODES, MATCHMAKING, TURNS, REMATCH, TERMINAL LOCK) ---

test("Rooms: create, matchmaking query, join, play turn, finish, rematch, and close lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRefAlice = aliceDb.collection("rooms").doc("ABCD12");
  const roomRefBob = bobDb.collection("rooms").doc("ABCD12");
  const roomRefCharlie = charlieDb.collection("rooms").doc("ABCD12");

  // 1. Alice creates a public matchmaking lobby
  await assertSucceeds(roomRefAlice.set(validRoomPayload("ABCD12", ALICE_UID, true)));

  // Invalid room code format rejected
  await assertFails(
    aliceDb.collection("rooms").doc("bad").set(validRoomPayload("bad", ALICE_UID, true))
  );

  // 2. Bob runs aligned matchmaking query (publicMatchmaking == true && status == LOBBY)
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .where("publicMatchmaking", "==", true)
      .where("status", "==", "LOBBY")
      .where("mode", "==", "CLASSIC")
      .where("targetPlayerCount", "==", 2)
      .get()
  );

  // Unfiltered rooms list query must fail
  await assertFails(bobDb.collection("rooms").get());

  // 3. Bob joins Alice's room and auto-starts (2/2 players)
  const joinedSeats = [
    ...validRoomPayload("ABCD12", ALICE_UID, true).seats.slice(0, 1),
    {
      seatIndex: 1,
      color: "YELLOW",
      uid: BOB_UID,
      nickname: "Bob",
      controllerType: "HUMAN",
      botDifficulty: "MEDIUM",
      connected: true,
      botTakeover: false,
      consecutiveMisses: 0,
      lastHeartbeatMs: Date.now(),
      rematchVoted: false,
    },
  ];
  await assertSucceeds(
    roomRefBob.update({
      memberUids: [ALICE_UID, BOB_UID],
      seats: joinedSeats,
      status: "PLAYING",
      turnSeq: 1,
      lastActionType: "START",
      lastActionSummary: "Match started!",
      gameStateMap: { turnNumber: 1 },
      turnStartedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 4. Non-member Charlie cannot mutate PLAYING room
  await assertFails(
    roomRefCharlie.update({
      turnSeq: 2,
      lastActionType: "ROLL",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 5. Member Alice cannot change diceSeed during PLAYING
  await assertFails(
    roomRefAlice.update({
      diceSeed: 999999,
      turnSeq: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 6. Member Alice submits valid turn roll & move, then finishes
  await assertSucceeds(
    roomRefAlice.update({
      rollSeq: 1,
      turnSeq: 2,
      lastActionType: "ROLL",
      lastActionSummary: "Alice rolled 6",
      gameStateMap: { turnNumber: 1, lastDiceValue: 6 },
      turnStartedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    roomRefAlice.update({
      status: "FINISHED",
      turnSeq: 3,
      lastActionType: "MOVE",
      lastActionSummary: "Alice won!",
      gameStateMap: { phase: "MATCH_FINISHED" },
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 7. Member Bob votes rematch & starts rematch (FINISHED -> PLAYING)
  await assertSucceeds(
    roomRefBob.update({
      status: "PLAYING",
      diceSeed: 777777,
      rollSeq: 0,
      turnSeq: 4,
      lastActionType: "REMATCH",
      lastActionSummary: "Rematch started!",
      gameStateMap: { turnNumber: 1 },
      turnStartedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 8. Closing room locks it permanently (Terminal State Locking)
  await assertSucceeds(
    roomRefAlice.update({
      status: "CLOSED",
      turnSeq: 5,
      lastActionType: "LEAVE",
      lastActionSummary: "Room closed",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    roomRefAlice.update({
      status: "PLAYING",
      turnSeq: 6,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

// --- 5. PRIVATE MATCH HISTORY SUBCOLLECTION ---

test("Match history: owner creates and reads own records; cross-user and mutation fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const record = {
    userId: ALICE_UID,
    mode: "POWER_UP",
    onlineMatch: true,
    roomCode: "ABCD12",
    winnerName: "Alice",
    winnerColor: "RED",
    won: true,
    rank: 1,
    playerSummary: "Alice (1st) • Bob (2nd)",
    turnsPlayed: 38,
    capturesMade: 4,
    tokensHome: 4,
    powerUpsUsed: 3,
    players: [
      { nickname: "Alice", color: "RED", isBot: false, rank: 1, capturesMade: 4, tokensHome: 4, powerUpsUsed: 3, score: 0 },
      { nickname: "Bob", color: "YELLOW", isBot: false, rank: 2, capturesMade: 1, tokensHome: 2, powerUpsUsed: 1, score: 0 },
    ],
    createdAt: new Date(Date.now() - 1000),
  };

  const aliceRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("matchHistory")
    .doc("match_001");

  await assertSucceeds(aliceRef.set(record));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matchHistory").get()
  );

  // Out-of-bounds tokensHome (>4) must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("matchHistory")
      .doc("match_bad")
      .set({ ...record, tokensHome: 9 })
  );

  // Append-only: updating an existing history entry must fail
  await assertFails(aliceRef.update({ won: false }));

  // Cross-user read & write must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("matchHistory").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("matchHistory")
      .doc("match_002")
      .set({ ...record, userId: BOB_UID })
  );
});
