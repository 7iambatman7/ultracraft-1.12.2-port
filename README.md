# Ultracraft — Minecraft Forge 1.12.2 Port

A work-in-progress port of [Ultracraft](https://github.com/alfr0762/ultracraft) to **Minecraft Java Edition 1.12.2 using Forge**.

This repository is the Forge port, not the original Fabric project. The original project targets a much newer Minecraft version, so features that depend on newer registries, entities, rendering hooks, or data formats need separate 1.12.2 implementations.

> **Status: experimental / incomplete.** The project has source-level changes, but it has not been verified in a live Minecraft session. Expect missing features and input conflicts; do not treat the current source as a finished release.

## Compatibility

- **Minecraft:** Java Edition 1.12.2
- **Forge:** 14.23.5.2860
- **Java:** Java 8 for the legacy ForgeGradle toolchain
- **Gradle:** 4.9
- **ForgeGradle:** 3.0.197

Do not use this build with Fabric or newer Minecraft versions.

## What this port currently contains

- A Forge 1.12.2 mod entry point and client/server proxies.
- Communication between the Minecraft mod and the external UltraBridge process.
- Client-side frame compositing, camera synchronization, and world data export.
- Server-side handling for the world events implemented in this port.
- A settings screen available through **F7** or the client command **`/uc settings`**.
- The Forge commands **`/uc help`**, **`/uc status`**, **`/uc cheats`**, and **`/uc cheat`**.
- Forge-side configuration saved in **`config/ultracraft.properties`**.

The list above describes code present in this repository; it does not mean every feature has been tested in-game.

## Required components

1. Minecraft Java Edition 1.12.2.
2. Forge 1.12.2 build 14.23.5.2860.
3. The built `ultracraft-1.0.4.jar` in the Minecraft `mods` folder.
4. A compatible ULTRAKILL installation with BepInEx 5 and the UltraBridge plugin. Those external components are **not installed automatically by this Forge project**.

For multiplayer, use a setup where the required mod and compatible external components are installed and configured for the players who need them. The current port has not received comprehensive multiplayer compatibility testing; a permissive Forge version check does not guarantee that every server-side feature will work on a server without the mod.

## Building from source

Use a Java 8 JDK and Gradle 4.9. From the repository root, run:

```sh
gradle --stop
gradle clean build
```

If the build succeeds, the mod jar should be created under:

```
build/libs/ultracraft-1.0.4.jar
```

To start a development client, run:

```sh
gradle runClient
```

The build has not been confirmed in this environment. If Gradle fails, check that Java 8 is actually selected by `JAVA_HOME` and that the installed Gradle version is 4.9.

## Configuration and commands

Configuration is stored in `config/ultracraft.properties`. The current port reads the settings represented by `UltracraftConfig.java`, including rendering/performance, audio, world interaction, and the implemented server-side toggles.

- `/uc help` — list available commands.
- `/uc status` — report the current status.
- `/uc cheats` — show supported Forge-side toggles.
- `/uc cheat neverhungry [on|off|toggle]` — toggle the implemented food/exhaustion behavior.
- `/uc cheat superspeed [on|off|toggle]` — toggle the implemented ordinary Minecraft movement speed effect.
- `/uc settings` — open the settings screen.

Server-side cheat commands require permission level 2. The exact command set may change as the port develops.

## Known limitations

- The keyboard/input handoff still needs in-game validation. In particular, Minecraft key bindings can conflict with input intended for the external game; do not assume input isolation is fixed.
- Hit-driven block damage and several other world interactions from the newer upstream project are not implemented in this port.
- Terrain detail, fluids, lighting, weather, and some rendering/world synchronization systems are incomplete.
- Several upstream settings and gameplay systems have not been rewritten for 1.12.2.
- Singleplayer and multiplayer/Essential behavior have not been comprehensively tested.
- The external bridge/plugin setup is not automated by the Forge build.

These are known gaps, not features promised by this README. Consult `PORT-STATUS.md` and `PATCH-NOTES-1.0.4.md` for the current implementation notes.

## Project references

- Original project: https://github.com/alfr0762/ultracraft
- Forge port: https://github.com/7iambatman7/ultracraft-1.12.2-port
