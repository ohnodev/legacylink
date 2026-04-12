<div align="center">
 <h1>LegacyLink</h1>

 <div>
  <a href="https://github.com/ohnodev/legacylink">
   <img alt="GitHub" src="https://img.shields.io/badge/github-ohnodev%2Flegacylink-181717?style=flat&logo=github"/>
  </a>&nbsp;&nbsp;
  <a href="https://github.com/ohnodev/legacylink/blob/main/LICENSE">
   <img alt="License" src="https://img.shields.io/github/license/ohnodev/legacylink?style=flat"/>
  </a>&nbsp;&nbsp;
  <a href="https://smp.thecabal.app">
   <img alt="Website" src="https://img.shields.io/badge/website-smp.thecabal.app-4caf50?style=flat">
  </a>&nbsp;&nbsp;
  <a href="https://discord.gg/2NR3W7j4vP">
   <img alt="Discord" src="https://img.shields.io/badge/discord-Cabal%20SMP-5865F2?style=flat&logo=discord&logoColor=white">
  </a>
 </div>
 <br>
</div>

Lightweight **26.2 → 26.1** protocol translation for **Fabric servers**. Lets **Minecraft 26.1.x** clients join a **26.2-snapshot** world without a proxy. Designed for performance-oriented stacks (e.g. alongside [ReaperAC](https://github.com/ohnodev/reaper-ac)).

**Outbound path:** clientbound packets for legacy connections are rewritten in `LegacyPacketHandler` (Netty, after `packet_handler`). This is not a ViaVersion port—only a small server-side subset. For broad multi-version support, use Via on a proxy.

## Downloads

- **[GitHub Releases](https://github.com/ohnodev/legacylink/releases)** — primary jar for each tagged version (attach the same file to Modrinth).
- **Prebuilt in repo:** [`prebuilt/legacylink-0.1.1.jar`](prebuilt/legacylink-0.1.1.jar) — convenience copy of the current `build.gradle.kts` version; checksum below.
- **Modrinth:** submit using the release jar + the dependency metadata in this README and in `fabric.mod.json`.

## Dependencies (libraries & runtime)

LegacyLink is a **server-side only** Fabric mod. Install these on the **dedicated server** (clients do not install LegacyLink).

| Requirement | Role | Version this repo is built against |
|-------------|------|-------------------------------------|
| **Java** | JVM for the server | **25+** (see `build.gradle.kts` toolchain) |
| **Minecraft** | Game / protocol | **`26.2-snapshot-2`** (`gradle.properties` → `minecraft_version`) |
| **Fabric Loader** | Mod bootstrap | **`>= 0.19.1`** (`fabricloader` in `fabric.mod.json`) |
| **Fabric API** | Library jar (`fabric-api` on Modrinth / Maven). Used at runtime (e.g. `ServerLifecycleEvents`). | **`>= 0.145.5`** — compile against **`0.145.5+26.2`** (`gradle.properties` → `fabric_version`). Use the **Fabric API build that matches your exact 26.2 snapshot** if yours differs. |
| **MixinExtras** | Embedded in the LegacyLink jar (jar-in-jar). Registers `@WrapMethod` and related injectors at preLaunch; **not** a separate mod to install. | **0.5.3** (`build.gradle.kts`, `io.github.llamalad7:mixinextras-fabric`) |

Maven coordinates for the Fabric API artifact (for reference only):

- `net.fabricmc.fabric-api:fabric-api` — version string like `0.145.5+26.2` from [Fabric Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/).

Aside from **MixinExtras** (above), there are **no other** bundled libraries: no ViaVersion, no PacketEvents, no optional mods required.

## Requirements & installation

1. Install **Fabric Loader** on the server.
2. Put **`fabric-api-<version>.jar`** in `mods/` (same line as your Minecraft build).
3. Put **`legacylink-0.1.1.jar`** in `mods/` (or the version you built).
4. Run the server with **Java 25+**.

```bash
cp build/libs/legacylink-0.1.1.jar /path/to/server/mods/
# or the prebuilt copy:
cp prebuilt/legacylink-0.1.1.jar /path/to/server/mods/
```

## Version policy

- **Legacy clients:** **26.1.1+** (protocol floor aligned with supported join path).
- **Server:** current **26.2-snapshot** line this mod is built against; newer Minecraft versions require refreshing LegacyLink when protocols diverge.
- Full multi-version / old-client matrices are out of scope.

## What it does (v0.1 scope)

- **Handshake:** accepts 26.1 client protocol during login
- **Registry sync:** filters 26.2-only entries (sulfur blocks, `sulfur_cube` archetype, `sulfur_caves` biome) from configuration registries; drops 26.2-only `minecraft:attribute` entries (`bounciness`, `air_drag_modifier`, `friction_modifier`) so legacy clients keep 26.1-aligned attribute network IDs (avoids mis-decoded `camera_distance` / `scale` and bad third-person camera)
- **Block updates & chunks:** remaps sulfur and 26.2-only block state IDs above the 26.1 client registry in block updates, section updates, and full chunks (`LegacyChunkTranslator`)
- **Entity spawns:** remaps `sulfur_cube` to slime for 26.1 clients
- **Telemetry:** optional trace flags and session stats (see below)

### Known limitations (v0.1)

- **Block state ID ceiling** (`MAX_26_1_BLOCKSTATE_ID` in `LegacyLinkConstants`) is the **inclusive** last index the legacy client accepts (`0..MAX`). Pinned to **26.1.1** (`30207`) so 26.2 palette IDs remap safely; setting it too high can crash legacy clients in `LinearPalette.read`.
- **Item stacks (wire):** 26.2↔26.1 numeric item ids for legacy connections via `LegacyItemIdTable` + `ItemStackOptionalCodecMixin` (optional stacks, including creative untrusted codec) and `HashedStackActualItemMixin` (container clicks). Inbound decode is scoped to `PacketDecoder#decode` (`PacketDecoderMixin`).

## Configuration

Set `-Dlegacylink.verbose=true` in JVM args for detailed translation logging.

### Spawn / join (wrong initial placement)

`-Dlegacylink.traceSpawn=true` on the **server** JVM → grep `logs/latest.log` for `[LegacyLink][SpawnTrace]`. Stages: `connection_send` (26.2) vs `post_legacy_rewrite` (26.1). Types: `login` (incl. `CommonPlayerSpawnInfo`), `respawn`, `set_default_spawn`, `add_entity`, `player_position` — not per-tick `move_entity` spam.

### Position traffic (snap-back / desync)

`-Dlegacylink.tracePositions=true` on the **server** JVM → grep `[LegacyLink][PosTrace]`:

| `stage` | `legacy` | Meaning |
|--------|----------|--------|
| `connection_send` | `false` | Outbound as the server emits for a **26.2** client (before Netty; anticheat may still alter later). |
| `post_legacy_rewrite` | `true` | After LegacyLink for a **26.1** client — closest to what 26.1 receives for rewritten movement types. |

`seq` is not aligned across sessions; compare by order and packet type.

### Entity metadata rewrites (synced data)

`-Dlegacylink.traceEntityDataRewrite=true` on the **server** JVM → grep `logs/latest.log` for `[LegacyLink][EntityDataRewrite]` — logs when `remapEntityData` changes metadata index sets (`beforeIds` vs `afterIds`). Also enabled when outbound capture is on (see below).

Optional: `-Dlegacylink.tracePlayerEntityData=true` logs player entity data index dumps as `[LegacyLink][EntityDataTrace]` (separate from rewrite trace).

### Camera (`set_camera`)

`-Dlegacylink.traceCamera=true` → grep `[LegacyLink][CameraTrace]`.

### Full legacy outbound capture

`-Dlegacylink.captureOutbound=true` on the **server** JVM, **or** set environment variable `LEGACYLINK_CAPTURE_OUTBOUND` to `1` or `true` (case-insensitive). Grep `logs/latest.log` for `[LegacyLink][OutboundCapture]`. Only **legacy** connections are logged. Stages: `connection_send` (as `Connection.send` hands off; bundles expanded) vs `post_legacy_rewrite` (after `LegacyPacketHandler` + bundle flatten — matches per-frame encode). **Very verbose** — disable after capture.

## Build from source

```bash
export JAVA_HOME="/path/to/java25"
./gradlew build -x test
```

Output: `build/libs/legacylink-0.1.1.jar`

## Prebuilt artifact in this repo

This repository includes a **prebuilt** Fabric mod jar under `prebuilt/` for direct deployment and Modrinth packaging.

Current file:

- `prebuilt/legacylink-0.1.1.jar`
- SHA-256: `bb0c95bb9d4f3f91caab249df9ee20ea4acfb3f7ca2f6ca236bd319a584aaea2`

Verify:

```bash
shasum -a 256 prebuilt/legacylink-0.1.1.jar
```

Update this checksum in the README whenever the prebuilt jar is replaced.

## Resources

- Server: [smp.thecabal.app](https://smp.thecabal.app)
- Discord: [Cabal SMP](https://discord.gg/2NR3W7j4vP)

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/ohnodev/legacylink).

## License

MIT — see [`LICENSE`](LICENSE).
