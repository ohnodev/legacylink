# LegacyLink

Lightweight 26.2-to-26.1 protocol translation layer for Fabric servers.

## What it does

LegacyLink allows Minecraft 26.1 clients (protocol 775) to connect to a Minecraft 26.2-snapshot server (protocol 1073742132). It runs as a Fabric server mod — no proxy needed.

### Translation scope (v0.1)

- **Handshake**: Accepts 26.1 client protocol version during login
- **Registry sync**: Filters 26.2-only entries (sulfur blocks, sulfur_cube archetype, sulfur_caves biome) from data-driven registries sent during CONFIGURATION
- **Block updates**: Remaps sulfur block state IDs to stone in single-block update packets
- **Entity spawns**: Remaps sulfur_cube entity to slime for 26.1 clients
- **Telemetry**: Logs all translation actions with session stats on shutdown

### Known limitations (v0.1)

- Chunk data is not remapped — sulfur blocks in existing chunks will appear as missing/unknown blocks (purple checkerboard) on 26.1 clients
- Multi-block section updates are passed through without remapping
- Item stack remapping in inventory packets is not yet implemented

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

## License

MIT — see `LICENSE` for details.
