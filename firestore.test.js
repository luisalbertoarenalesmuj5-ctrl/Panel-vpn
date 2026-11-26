const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_admin_01";
const BOB_UID = "bob_admin_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Admin Alice",
    email: "alice@panelvpn.local",
    role: "SUPER_ADMIN",
    panelName: "Panel VPN",
    defaultDns: "1.1.1.1, 8.8.8.8",
    defaultMtu: 1420,
    defaultSubnet: "10.8.0.0/24",
    autoSuspendExpired: true,
    apiTimeoutSeconds: 10,
    maskSensitiveKeys: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validVpsPayload(ts) {
  return {
    name: "VPS Madrid 01",
    ipAddress: "45.80.12.10",
    location: "Madrid, ES",
    apiEndpoint: "https://vps1.example.com:8443",
    apiToken: "tok_secret_12345",
    sshPort: 22,
    wireguardPort: 51820,
    openVpnPort: 1194,
    status: "ONLINE",
    cpuPercent: 24.5,
    ramUsedMb: 1024,
    ramTotalMb: 4096,
    storageUsedGb: 18,
    storageTotalGb: 80,
    latencyMs: 32,
    activeConnections: 4,
    wireguardEnabled: true,
    openVpnEnabled: true,
    lastDiagnostic: "HTTP 200 OK",
    lastCheckedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user cannot read or write any workspace collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vpsServers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vpsServers").doc("srv1").set(validVpsPayload(ts)));
});

test("User profile creation, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      panelName: "CoreVPN NOC",
      defaultMtu: 1380,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp & ghost field & role mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      panelName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpsServers").get());
});

test("VPS servers, protocols, VPN users, configs, devices, members, notifications, and logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);
  const userRoot = aliceDb.collection("users").doc(ALICE_UID);

  // VPS Server
  await assertSucceeds(userRoot.collection("vpsServers").doc("srv_1").set(validVpsPayload(ts)));
  await assertSucceeds(userRoot.collection("vpsServers").get());
  await assertSucceeds(
    userRoot.collection("vpsServers").doc("srv_1").update({
      status: "OFFLINE",
      cpuPercent: 0.0,
      lastDiagnostic: "Connection timed out",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Reject @DocumentId id field injection
  await assertFails(
    userRoot.collection("vpsServers").doc("srv_2").set({
      ...validVpsPayload(ts),
      id: "srv_2",
    })
  );

  // Protocol
  await assertSucceeds(
    userRoot.collection("protocols").doc("proto_wg").set({
      serverId: "srv_1",
      serverName: "VPS Madrid 01",
      type: "WIREGUARD",
      enabled: true,
      port: 51820,
      transport: "UDP",
      subnet: "10.8.0.0/24",
      dns: "1.1.1.1, 8.8.8.8",
      mtu: 1420,
      cipher: "ChaCha20-Poly1305",
      keepaliveSeconds: 25,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // VPN User
  await assertSucceeds(
    userRoot.collection("vpnUsers").doc("vuser_1").set({
      username: "carlos_dev",
      email: "carlos@corp.local",
      serverId: "srv_1",
      serverName: "VPS Madrid 01",
      protocol: "WIREGUARD",
      status: "ACTIVE",
      maxDevices: 3,
      activeConnections: 1,
      dataLimitGb: 100,
      dataUsedMb: 512,
      expiresAtEpochMs: Date.now() + 86400000 * 30,
      notes: "Equipo ingeniería",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    userRoot.collection("vpnUsers").doc("vuser_1").update({
      status: "SUSPENDED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // VPN Config Profile
  await assertSucceeds(
    userRoot.collection("vpnConfigs").doc("cfg_1").set({
      vpnUserId: "vuser_1",
      vpnUsername: "carlos_dev",
      serverId: "srv_1",
      serverName: "VPS Madrid 01",
      deviceId: "dev_1",
      deviceName: "Pixel 9",
      protocol: "WIREGUARD",
      assignedIp: "10.8.0.2/32",
      publicKey: "abcPublic123==",
      privateKey: "xyzPrivate456==",
      presharedKey: "psk789==",
      endpoint: "45.80.12.10:51820",
      configContent: "[Interface]\nPrivateKey = xyzPrivate456==\nAddress = 10.8.0.2/32",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Device
  await assertSucceeds(
    userRoot.collection("devices").doc("dev_1").set({
      vpnUserId: "vuser_1",
      vpnUsername: "carlos_dev",
      serverId: "srv_1",
      deviceName: "Pixel 9",
      platform: "ANDROID",
      assignedIp: "10.8.0.2/32",
      publicKeyFingerprint: "abcPublic123==",
      authorized: true,
      connected: false,
      lastSeenEpochMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    userRoot.collection("devices").doc("dev_1").update({
      authorized: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Member
  await assertSucceeds(
    userRoot.collection("members").doc("mem_1").set({
      name: "Operador NOC",
      email: "noc@panelvpn.local",
      role: "OPERATOR",
      status: "ACTIVE",
      twoFactorRequired: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Notification
  await assertSucceeds(
    userRoot.collection("notifications").doc("notif_1").set({
      title: "VPS en línea",
      message: "VPS Madrid 01 verificada correctamente",
      severity: "INFO",
      category: "VPS",
      read: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    userRoot.collection("notifications").doc("notif_1").update({
      read: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Activity Log (create succeeds, update fails)
  await assertSucceeds(
    userRoot.collection("activityLogs").doc("log_1").set({
      actorEmail: "alice@panelvpn.local",
      action: "VPS_CREATED",
      targetType: "VPS",
      targetName: "VPS Madrid 01",
      details: "Registrado nodo 45.80.12.10",
      status: "SUCCESS",
      createdAt: ts,
    })
  );
  await assertFails(
    userRoot.collection("activityLogs").doc("log_1").update({
      status: "FAILED",
    })
  );

  // Bob cannot access Alice's subcollections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpnUsers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vpnConfigs").doc("cfg_1").get());
});
