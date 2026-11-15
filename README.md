# Loconautics × Immersive Portals: Aeronautics

**English** | [Português (Brasil)](README.pt-BR.md)

A small NeoForge 1.21.1 compatibility mod. With it, **Loconautics** trains ride through **Immersive Portals: Aeronautics** (IP Aero) portals in one piece: no derailing, no vanishing cars, no lost couplings, and you keep control of the train.

The crossing itself is done by IP Aero's *straddle*, which carries the train's ship from one dimension to the other. This mod turns off Loconautics' own portal crossing and fixes what would otherwise break on the other side.

> Unofficial add-on. It is not affiliated with the authors of Loconautics, Immersive Portals: Aeronautics, Sable or Create.

---

## Features

- **One crossing system.** Loconautics' own portal crossing (`TrainPortalCrossing`) is turned off, so its "twin" copy and fade cut never show up. Without this, the two mods fight over the train.
- **The train survives the move.** Loconautics normally deletes a train when its ship leaves the world. While IP Aero is moving a car, the car is marked as crossing, so the train is kept.
- **Ghost wheels stay behind.** Loconautics' ghost wheel bodies are separate ships tied to each car. They are removed just before the crossing, and Loconautics builds new ones on the other side. If they crossed loose, the car would hit them and get thrown off the track.
- **Internal coordinates are remapped.** IP Aero shifts a ship's internal (plot) coordinates when it changes dimension. The mod corrects the positions that Loconautics stores:
  - bogey positions, which are relative to the plot center;
  - lead and trail points, which are absolute plot coordinates;
  - coupling partners;
  - controller positions.
- **Rerail in place.** Each car is seated on the track right under it, without rebuilding the ship. Rebuilding is what made the train disappear for players watching through the portal.
- **Movement is preserved.** Speed, throttle, direction of travel and station stop state are the same as before the portal.
- **Couplings hold.** Cars joined by a **Knuckle** stay coupled. Each partner reference follows its car's shift, and coupled cars still don't collide with each other.
- **Manual control continues.** The **Analog Controller** stays with the same player, with the same throttle and keys held. If the player and the train are briefly in different dimensions, the controller waits up to 5 seconds instead of letting go. On the client, held keys are never handed back to the player mid-crossing, so you don't suddenly start walking.

## Requirements

Tested with:

| Mod | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Loconautics | 0.1.2 BETA |
| Immersive Portals: Aeronautics | 2.8.27 |
| Sable | 2.0.6 |
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

Loconautics and Immersive Portals: Aeronautics are **required** dependencies. Other versions may work, but the mod uses their internals (see [Compatibility](#compatibility)).

## Installation

1. Download the jar from the [Releases](../../releases) page, or build it yourself (see below).
2. Put `loconautics_ip_compat-<version>.jar` in the `mods/` folder.
   - Install it on **both the client and the server**: part of the fix runs on each side.
3. Start the game. There is nothing to configure.

## Usage

Build your Loconautics train as usual and run it through an IP Aero portal on track that continues on the other side.
- Trains assembled at a station, and cars coupled with Knuckles, are supported.
- You can drive it with the Analog Controller or with external controls (redstone, schedules).
- It works in both directions and repeatedly.

The mod writes one line to the log for each thing it does, prefixed with `[LocoIP]`. Those messages are in Portuguese. Examples:

```
[LocoIP] vagão 3ae44c9e levado pelo Immersive Portals para minecraft:the_nether; assentar no trilho agendado
[LocoIP] vagão 3ae44c9e assentado no trilho em minecraft:the_nether (1 truque(s), velocidade 27.3 b/s, acelerador 28.0 b/s)
[LocoIP] engate do vagão 8289414d continua preso ao 3ae44c9e do outro lado do portal
[LocoIP] controle manual em 10, 120, 20 continua com Player do outro lado do portal
```

In order, they mean:
1. "car carried to the Nether; rerail scheduled"
2. "car seated on the track (bogeys, speed, throttle)"
3. "coupling still attached on the other side"
4. "manual control kept by this player on the other side"

## How it works

All hooks are mixins; there is no config file.

| Mixin | Target | What it does |
| --- | --- | --- |
| `TrainPortalCrossingMixin` | Loconautics `TrainPortalCrossing` | Cancels its `tick`. Makes `isCrossing` return true while IP Aero is moving the car, so `SubLevelSplitWatcher` does not delete the train. |
| `IPSableBridgeMixin` | IP Aero `IPSableBridge.moveThroughPortal` | **HEAD:** discards ghost wheels and saves the car's state (center, speed, throttle, rail frame) and the controller users. It also marks the ship as in flight for Loconautics and releases its wheels and contacts. **RETURN:** moves the train to the new level, remaps bogeys and lead/trail, schedules the rerail and restores the controller users. |
| `IPShipJointsMixin` | IP Aero `IPShipJoints.shiftLoadedJoints` | Runs once per group of tied ships after all of them moved. Shifts every Knuckle partner position by its partner's shift. |
| `AnalogControllerServerMixin` | Loconautics `AnalogControllerBlockEntity.serverTick` | Keeps the user while they are in another dimension, for up to 100 ticks. |
| `AnalogControllerClientMixin` (client) | Loconautics `AnalogControllerClientHandler` | Swaps the controller position silently and finds the controller again inside the moved ship. Holds control, with keys suppressed, during the crossing. |

The rerail itself (`PendingRerail`) runs on the server tick one tick after the move:
1. Finds the track under the car.
2. Builds a temporary carriage that keeps the car's old reference frame, so the car is not turned 180°.
3. Seats every bogey on the rail under it.
4. Restores speed and throttle, flipping their sign if the new track's direction is reversed.

## Building

There is no Gradle setup. `build.sh` compiles with `javac` against the jars of a real modpack instance.

You need:
- bash
- `unzip`
- JDK 21
- a NeoForge 1.21.1 instance with the mods listed above
- the launcher's `libraries` folder

```bash
INST="/path/to/instance/mods" LIB="/path/to/launcher/libraries" JDK="/path/to/jdk-21/bin" ./build.sh
```

- The jar is written to the project root as `loconautics_ip_compat-<version>.jar`.
- The version comes from `res/META-INF/neoforge.mods.toml`.
- Without the variables, the script uses the paths written at its top.
- The `build/` folder holds copies of other mods' jars and is not committed.

## Project structure

```
src/locoipcompat/
  LocoIpCompat.java            mod entry point (server tick hook)
  PendingRerail.java           train state across the move + in-place rerail
  ControllerHandoff.java       Analog Controller users across the move
  KnuckleHandoff.java          Knuckle couplings across the move
  client/ClientControllerHandoff.java
  mixin/                       the mixins listed above
res/
  META-INF/neoforge.mods.toml
  loconautics_ip_compat.mixins.json
  pack.mcmeta
build.sh
```

## Compatibility

- The mod relies on **internal classes and private fields** of Loconautics, which is still in beta, and of IP Aero.
  - If either mod updates, rebuild against the new jars and test a crossing.
  - If a method it needs is gone, the log says so (`[LocoIP] a versão do Loconautics mudou…`, "the Loconautics version changed"). That part of the fix is then skipped instead of crashing.
- Tested with Nether-style portal pairs, whose rotation keeps the same orientation. Portals that rotate the train should work, but have not been tested.

## Known issues

- **Standing passengers slide back a little.**
  - A player standing on the train (not seated) slips back a bit while crossing. The faster the train, the more they slip.
  - The player passes the portal a fraction of a second before the train. For that moment they stand on the "image" of the train that IP Aero creates on the other side, and that image doesn't carry them along.
  - Sitting on a seat should avoid it.
- **First trip into a new area.** The very first crossing into an area that hasn't been generated yet can pause the server for a moment while the destination chunks generate. This is world generation, not this mod.

## License

MIT, as declared in `neoforge.mods.toml`.
