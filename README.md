# Create Factory Schematic Fix

Small NeoForge addon for **Minecraft 1.21.1 + Create 6.0.10**.

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

## Test procedure

1. Install the addon on client and server.
2. Start a test world/server.
3. Make 2-3 Factory Gauges with visible arrows/connections and non-default bend/amount where possible.
4. Select the whole set with Create's Schematic and Quill.
5. Save a **new** schematic.
6. Move/rotate the schematic and print it with Schematicannon / schematic deployment.
7. Wait a few seconds for all gauges to be placed.
8. Verify arrows and gauge relationships.
9. Test a Frogport separately. Its inventory should stay empty by design.

## Frogport rotation fix in 0.1.1

Create stores Frogport targets as relative vectors. For Chain Conveyor targets there are two vectors that
matter: the Frogport-to-conveyor `relativePos` and the conveyor-to-connected-conveyor `connection`.
Version 0.1.1 makes Frogport block entities transformable and applies Create's own `StructureTransform` to
both vectors during schematic rotation/mirroring. Translation still needs no special handling because the
target position is relative.

Factory Gauge graph reconstruction remains unchanged.

## Sable sub-level compatibility (0.2.0)

When Sable assembles/disassembles blocks, it copies full block-entity NBT and rewrites the
block entity's own coordinates. Create Factory Gauge connections contain additional absolute
positions, so those links normally point into the old coordinate space while the structure is
inside a sub-level.

Version 0.2.0 adds optional Sable compatibility:

- before a Sable move, the mod snapshots Factory Gauge -> Factory Gauge edges;
- only edges whose **source and target blocks are both part of the same move** are retained;
- after Sable finishes moving the blocks, stale gauge-to-gauge references on moved gauges are
  cleared and the internal graph is rebuilt at the transformed positions;
- connection `amount` and arrow bend mode are restored;
- gauge connections crossing the assembly boundary are deliberately dropped. The mod never
  attempts to connect a gauge in a Sable sub-level to a gauge left in the normal world;
- the Sable integration is optional and uses a pseudo-mixin, so the mod still loads without Sable.

This compatibility currently targets Sable's public `SubLevelAssemblyHelper.moveBlocks` /
`AssemblyTransform` API shape used by Sable 2.0.x.

## Frogport graph + Sable fix (0.2.1)

Version 0.2.1 replaces the fragile Frogport target copy path with the same kind of
position-independent reconstruction used for Factory Gauges.

For schematics:

- a Frogport targeting a Chain Conveyor stores the exact conveyor as a node ID;
- if it targets a chain between two conveyors, the opposite endpoint is another node ID;
- after placement, the target is rebuilt from the real transformed block positions;
- `relativePos` and `connection` therefore survive translation, 90/180/270-degree rotation and mirroring;
- for a Frogport attached to the loop around one conveyor, Create's `chainPos` is an angle, so that angle is now transformed too.

For Sable assemble/disassemble:

- only Frogport targets whose required Chain Conveyor blocks are inside the same moved block set are retained;
- cross-boundary Frogport targets are deliberately cleared;
- Chain Conveyor `connections` are rebuilt from transformed absolute endpoints before Frogports are re-registered;
- loop `chainPos` is rotated using Sable's `AssemblyTransform`;
- travelling-chain distance remains unchanged under the rigid transform.

For a reliable test, create a **new schematic** after installing 0.2.1. Test both kinds of Frogport target:
(1) the loop of a single Chain Conveyor and (2) the chain stretched between two Chain Conveyors.
