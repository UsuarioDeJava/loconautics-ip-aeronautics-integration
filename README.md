# Loconautics × Immersive Portals: Aeronautics

**English** | [Português (Brasil)](README.pt-BR.md)

A small NeoForge 1.21.1 compatibility mod that lets **Loconautics** trains ride through **Immersive Portals: Aeronautics** (IP Aero) portals in one piece. Cars don't derail or vanish, couplings hold, you keep control of the train, and junction choices stay consistent.

The crossing itself is done by IP Aero's *straddle*, which carries the train's ship from one dimension to the other. This mod turns off Loconautics' own portal crossing and fixes what would otherwise break on the other side.

> Unofficial add-on. It is not affiliated with the authors of Loconautics, Immersive Portals: Aeronautics, Sable or Create.

---

## Features

### Crossing
- **One crossing system:** Loconautics' own portal crossing (`TrainPortalCrossing`) is turned off, so its "twin" copy and fade cut never show up. Without this, the two mods fight over the train.
- **The train survives the move:** while IP Aero moves a car, the car is marked as crossing, so Loconautics doesn't delete the train when its ship leaves the world.
- **Ghost wheels stay behind:** the ghost wheel bodies are separate ships tied to each car. They are removed right before the crossing and Loconautics builds new ones on the other side, instead of the car crashing into loose ones.
- **Internal coordinates are remapped:** IP Aero shifts a ship's internal (plot) coordinates. The mod corrects everything Loconautics stores in them:
  - bogey positions
  - lead/trail points
  - coupling partners
  - controller positions

### Back on the rails
- **Rerail in place:** each car is seated on the track under it without rebuilding the ship. Rebuilding is what used to make the train vanish for players watching through the portal.
- **Movement is preserved:** speed, throttle, direction of travel and station stop state are the same as before the portal.
- **No 180° turn:** cars keep their orientation, including cars with a single bogey.
- **Rails that already crossed are kept:** when the track itself continues through the portal (a Create portal track), the train's rail followers reach the other side before the ship does. Their positions, and the junction branches they already took, are kept.

### Junctions
- **Junction at the portal exit:** a junction can end up between a car's bogeys, or between cars, when the train arrives. The mod records it in the train's junction memory with the side the bogeys are actually on, so the bogeys and cars behind follow the same side instead of splitting.
- **Stale choices are cleared:** junction choices made before the hand-off that no longer match the real bogey positions are discarded.

### Couplings and controls
- **Couplings hold:** cars joined by a **Knuckle** stay coupled, each partner reference follows its car, and coupled cars still don't collide with each other.
- **Manual control continues:** the **Analog Controller** stays with the same player, with the same throttle and keys held. If the player and the train are briefly in different dimensions, it waits up to 5 seconds instead of letting go. On the client, held keys are never handed back mid-crossing, so you don't start walking.

### Fixes that also apply away from portals
- **Redstone Link inside ships:** Loconautics link receivers (such as the Bearing Axle) compute link range from the ship's real position when they re-register (after loading or a crossing). Before, a transmitter on the ground could look millions of blocks away.
- **Steering with nobody at the controller:** a car whose Bearing Axle is receiving a steering signal (a panel switch, a Redstone Link or plain redstone) counts as being driven. The whole train then follows that signal at junctions. Before, whichever part reached the junction first (the car's span, or an unpowered car in front) decided on its own and usually went straight.

## Requirements

Tested with:

| Mod | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Loconautics | 0.1.3 BETA (0.1.2 BETA also works) |
| Immersive Portals: Aeronautics | 2.8.27 |
| Sable | 2.0.6 |
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

Loconautics and Immersive Portals: Aeronautics are **required**. Other versions may work, but the mod uses their internals (see [Compatibility](#compatibility)).

## Installation

1. Download the jar from [Releases](../../releases) or build it yourself (see below).
2. Put `loconautics_ip_compat-<version>.jar` in the `mods/` folder, on **both the client and the server**: part of the fix runs on each side.
3. Start the game. There is nothing to configure.

## Usage

Build your Loconautics train as usual and run it through an IP Aero portal on track that continues on the other side.

You can drive it:
- with the Analog Controller;
- with external controls (redstone, schedules);
- with a steering switch on board, as an "autopilot".

It works both ways and repeatedly, with single cars and coupled consists.

The mod logs what it does with the prefix `[LocoIP]`. These messages are in Portuguese. For example, `assentado no trilho` means "seated on the track", `engate … continua preso` means "coupling still attached", and `desvio(s) … gravado(s)` means "junction(s) recorded".

### Diagnostics

`/locoip debug` (permission level 2) toggles a detailed junction trace:
- Loconautics' own junction log, which is filtered out in its release builds;
- one line for each junction-memory decision;
- the state of each bogey right after it's seated across a portal.

It is off by default and does not change behaviour. Turn it off again when you're done, because it fills the log quickly.

## How it works

All hooks are mixins. There is no config file.

| Mixin | Target | What it does |
| --- | --- | --- |
| `TrainPortalCrossingMixin` | Loconautics `TrainPortalCrossing` | Cancels its `tick`; `isCrossing` returns true while IP Aero moves the car. |
| `IPSableBridgeMixin` | IP Aero `IPSableBridge.moveThroughPortal` | **HEAD:** discards ghost wheels, saves the car's state and the controller users, and marks the ship as in flight. **RETURN:** moves the train to the new level, remaps internal positions, schedules the rerail and restores the controllers. |
| `IPShipJointsMixin` | IP Aero `IPShipJoints.shiftLoadedJoints` | Runs once per group of tied ships after they've all moved; shifts every Knuckle partner position. |
| `AnalogControllerServerMixin` | Loconautics `AnalogControllerBlockEntity.serverTick` | Keeps the user for up to 100 ticks while they're in another dimension. |
| `AnalogControllerClientMixin` (client) | Loconautics `AnalogControllerClientHandler` | Swaps the controller position silently and holds control during the crossing. |
| `RedstoneLinkPairMixin` | Loconautics `RedstoneLinkPair.deliverCurrent` | Computes Redstone Link range from the ship's real position. |
| `ConsistTurnsMixin` | Loconautics `ConsistTurns.isDriver` | Counts a car with an active steering signal on its Bearing Axle as driven. |
| `DiagnosticsConfigMixin`, `TurnMemoryMixin` | Loconautics diagnostics / junction memory | Only used by `/locoip debug`. |

The rerail (`PendingRerail`) runs on the server tick right after the move:
1. Find the track under the car.
2. Build a temporary carriage that keeps the car's old reference frame.
3. Seat the bogeys, keeping rail followers that already crossed into this dimension.
4. Restore speed and throttle, flipping their sign if the new track runs the other way.

After the last car of a train is seated, `StraddledJunctions`:
1. clears stale junction choices;
2. walks the track bogey by bogey;
3. records every junction between bogeys with the side the bogey ahead is on.

## Building

There is no Gradle setup. `build.sh` compiles with `javac` against the jars of a real modpack instance.

You need:
- bash
- `unzip`
- JDK 21
- a NeoForge 1.21.1 instance with the mods above
- the launcher's `libraries` folder

```bash
INST="/path/to/instance/mods" LIB="/path/to/launcher/libraries" JDK="/path/to/jdk-21/bin" ./build.sh
```

The jar is written to the project root as `loconautics_ip_compat-<version>.jar`. The version comes from `res/META-INF/neoforge.mods.toml`.

## Project structure

```
src/locoipcompat/
  LocoIpCompat.java            mod entry point (server tick, /locoip command)
  PendingRerail.java           train state across the move + in-place rerail
  StraddledJunctions.java      junctions between bogeys after a crossing
  ControllerHandoff.java       Analog Controller users across the move
  KnuckleHandoff.java          Knuckle couplings across the move
  LinkRange.java               Redstone Link range for blocks inside ships
  LocoDebug.java               /locoip debug
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
- If something the mod needs is gone, the log says so (`[LocoIP] a versão do Loconautics mudou…`) and that part of the fix is skipped instead of crashing.
- Tested with Nether-style portal pairs. Portals that rotate the train should work, but haven't been tested.

## Known issues

- **Standing passengers slide back a little.** A player standing on the train (not seated) slides back slightly while crossing, more at higher speed. They pass the portal a moment before the train and stand on the "image" of the train that IP Aero creates on the other side. Sitting on a seat should avoid it.
- **Junction right at the portal exit:**
  - If the track continues through the portal (a Create portal track), the train takes the side chosen while it was crossing, so steering before the portal works.
  - If the tracks on each side are separate, the train arrives straight and follows the side under it.
- **First trip into a new area:** it can pause the server for a moment while the destination chunks generate. That's world generation, not this mod.

## License

MIT, as declared in `neoforge.mods.toml`.
