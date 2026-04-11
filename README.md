# LegacyLink

Lightweight 26.2-to-26.1 protocol translation layer for Fabric servers.

## What it does

LegacyLink allows Minecraft 26.1 clients (protocol 775) to connect to a Minecraft 26.2-snapshot server (protocol 1073742132). It runs as a Fabric server mod — no proxy needed.

**Outbound path:** clientbound packets for legacy connections are rewritten only in `LegacyPacketHandler` (Netty, after `packet_handler`). There is no second pass in `Connection.send`—doing both was redundant and could damage chunk buffers.

This is not a ViaVersion port: it is a small server-only subset. For full multi-version coverage, run a Via proxy in front of the server.

### Translation scope (v0.1)

- **Handshake**: Accepts 26.1 client protocol version during login
- **Registry sync**: Filters 26.2-only entries (sulfur blocks, sulfur_cube archetype, sulfur_caves biome) from data-driven registries sent during CONFIGURATION; also drops 26.2-only `minecraft:attribute` entries (`bounciness`, `air_drag_modifier`, `friction_modifier`) so legacy clients keep the same attribute network IDs as 26.1 (fixes mis-decoded `camera_distance` / `scale` and bad third-person camera)
- **Block updates & chunks**: Remaps sulfur and 26.2-only block state IDs (above the 26.1 client registry) to stone in block updates, section updates, and full chunk packets (`LegacyChunkTranslator`)
- **Entity spawns**: Remaps sulfur_cube entity to slime for 26.1 clients
- **Telemetry**: Logs all translation actions with session stats on shutdown

### Known limitations (v0.1)

- **Block state ID ceiling** (`MAX_26_1_BLOCKSTATE_ID` in `LegacyLinkConstants`) is the **inclusive** last index the legacy client accepts (`0..MAX`). It is pinned to the lowest supported legacy client (`26.1.1`: `30207`) so `26.2` palette ids (`30208+`) are always remapped; setting this too high causes legacy clients to crash in `LinearPalette.read`.
- **Item stacks (outbound):** `LegacyPacketHandler` remaps and sanitizes stacks for legacy clients on container set slot/content, cursor item, player inventory, advancements (icons), and recipes/tags; wire-level item IDs are translated via `LegacyItemIdTable` + `ItemStackOptionalCodecMixin`. Inbound (client→server) item data is not rewritten.

## Requirements

- Java 25+
- Fabric Loader 0.19.1+
- Fabric API 0.145.5+26.2
- Minecraft server 26.2-snapshot-1

## Build

```bash
export JAVA_HOME="/path/to/java25"
./gradlew build -x test
```

Output jar: `build/libs/legacylink-0.1.0.jar`

## Deploy

Copy the jar to the server's `mods/` directory and restart:

```bash
cp build/libs/legacylink-0.1.0.jar /path/to/server/mods/
sudo systemctl restart minecraft-cabal
```

## Configuration

Set `-Dlegacylink.verbose=true` in JVM args for detailed packet translation logging.

### Comparing 26.2 vs 26.1 spawn / join (wrong initial placement)

Add `-Dlegacylink.traceSpawn=true` to the **server** JVM, then grep `logs/latest.log` for `[LegacyLink][SpawnTrace]`. Same `stage` / `legacy` columns as below: `connection_send` (26.2) vs `post_legacy_rewrite` (26.1). Logged types: `login` (includes `CommonPlayerSpawnInfo`), `respawn`, `set_default_spawn`, `add_entity`, `player_position` — **not** per-tick `move_entity` spam.

### Comparing 26.2 vs 26.1 position traffic (snap-back / desync)

Add `-Dlegacylink.tracePositions=true` to the **server** JVM. Stand still or reproduce the issue, then grep `logs/latest.log` for `[LegacyLink][PosTrace]`:

| `stage` | `legacy` | Meaning |
|--------|----------|--------|
| `connection_send` | `false` | Outbound packet as the server emits it for a **26.2** client (before the Netty pipeline; anticheat may still change it later). |
| `post_legacy_rewrite` | `true` | Same logical packet **after** LegacyLink translation for a **26.1** client — closest to what 26.1 actually receives for movement types LegacyLink rewrites. |

`seq` is global and not aligned between sessions; diff by wall-clock order and packet type. LegacyLink does not add PacketEvents/Reaper hooks — this is vanilla `Packet` logging on the game connection.

### Entity metadata for the local player (wrong POV / pose)

Add `-Dlegacylink.tracePlayerEntityData=true` on the **server** JVM. Join with a 26.1 client and grep `logs/latest.log` for `[LegacyLink][EntityDataTrace]` — one line per `ClientboundSetEntityDataPacket` for your player entity listing every synced metadata index. Compare with a 26.2 client session (no rewrite) if the index set or max id diverges.

### Camera attach packet (`set_camera`)

Add `-Dlegacylink.traceCamera=true` on the **server** JVM. Grep `[LegacyLink][CameraTrace]`: `connection_send` is the pre-legacy 26.2 path; `post_legacy_rewrite` is what legacy clients receive (packet is normally unchanged — only the camera entity id is on the wire). Use this to confirm spectator / forced-camera targets match between client versions.

### Full legacy outbound capture (diff logs vs a pure 26.1 server)

Add `-Dlegacylink.captureOutbound=true` on the **server** JVM (or `LEGACYLINK_CAPTURE_OUTBOUND=1` with `minecraft-cabal/scripts/start.sh`). Grep `logs/latest.log` for `[LegacyLink][OutboundCapture]`. Only **legacy** connections are logged. Stages: `connection_send` (as `Connection.send` hands off; bundles expanded) vs `post_legacy_rewrite` (after `LegacyPacketHandler` + bundle flatten — matches per-frame encode). **Very verbose** — turn off after capture.

## License

MIT — see `LICENSE` for details.
