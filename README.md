# Gauge Frogport Schematic Fix for Aeronautics

<p align="center">
  <img src="assets/Schematic Fix.png"
       alt="Gauge Frogport Schematic Fix"
       height="256">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge&logo=minecraft">
  <img src="https://img.shields.io/badge/NeoForge-21.1.250-EF6C00?style=for-the-badge">
  <img src="https://img.shields.io/badge/Create-6.0.10-8B6F47?style=for-the-badge">
  <img src="https://img.shields.io/badge/Create_Aeronautics-1.3.2-4A90E2?style=for-the-badge">
</p>

## What it fixes

Create intentionally sanitizes block-entity NBT when printing schematics. That is good for inventories,
but it also means some factory configuration is lost.

This addon currently preserves:

- **Factory Gauge → Factory Gauge connections inside the same schematic**
  - source/target relationship
  - connection amount
  - arrow bend mode
  - connections are stored as schematic-local node IDs, not absolute world coordinates
- **Frogport → Chain Conveyor targets**
  - address filter and accepts-packages flag
  - target conveyor is stored as a schematic-local node ID
  - travelling-chain endpoint is stored as a second node ID
  - loop `chainPos` angle is rotated/mirrored with the schematic
  - no Frogport inventory is copied

It intentionally does **not** copy:

- chest/container inventories
- Frogport inventory
- Factory Gauge connections whose other endpoint is outside the selected schematic
- Factory Gauge support-link connections (`targetedByLinks`)

## Why the Factory Gauge fix survives moving/rotating a schematic

During schematic export every active Factory Gauge panel gets a temporary integer node ID. Connections are
saved as edges `node -> node`. While the schematic is printed, the addon observes the real positions of the
newly placed gauges and reconstructs the graph using Create's own `addConnection()` API.

This avoids storing the original absolute `BlockPos`, so ordinary translation and schematic rotation/mirroring
do not invalidate gauge-to-gauge links.

## Important

**Create a new schematic after installing this addon.** Existing `.nbt` schematic files do not contain the
extra graph metadata and cannot be repaired automatically.

Install the mod on **both client and server**. Export happens from the player's schematic tools while final
block placement/reconstruction happens in the world/server.

Back up the world before the first test.

## Target versions

- Minecraft: `1.21.1`
- NeoForge: `21.1.250`
- Create: `6.0.10` / Maven artifact `6.0.10-280`
- Java: `21`

## Build

Using the included Gradle wrapper (only JDK 21 is required):

```bash
.\gradlew.bat build
```

Output:

```text
build/libs/factory_schematic_fix-0.2.1.jar
```

A GitHub Actions workflow is included (`.github/workflows/build.yml`), so pushing this project to GitHub and
running the `build` workflow also produces the JAR as an artifact.

