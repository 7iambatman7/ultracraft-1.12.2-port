# Ultracraft for Minecraft 1.12.2 (Forge)

A port of [Ultracraft](https://github.com/alfr0762/ultracraft) from Fabric 1.21.11 to **Forge 1.12.2**. The real
ULTRAKILL runs next to Minecraft; its frames are drawn over Minecraft's world, Minecraft's blocks and mobs go to ULTRAKILL as
colliders and targets, and what ULTRAKILL hits is applied to the world.

**Status: source patch prepared; runtime behavior has not been tested in-game.** This build cancels only Minecraft fall damage while V1 is active and suppresses conflicting Minecraft gameplay key bindings while V1 controls the game. It remains a partial port, not the whole mod (see below).

## Build

Use **JDK 8** and **Gradle 4.9**. This project uses ForgeGradle 3.0.197 because current Forge 1.12.2 Maven artifacts are packaged as UserDev 3; ForgeGradle 2.3 looks for the old `*-userdev.jar` artifact and fails during `extractUserdev`.

From this directory, run:

    gradle --stop
    gradle clean build

The output jar should be in `build/libs/ultracraft-1.0.4.jar` if compilation succeeds. You can run a development client with `gradle runClient`.

## Install

1. Minecraft 1.12.2 with Forge 14.23.5.2860.
2. In `mods/`: `ultracraft-1.0.4.jar` and **Essential** for 1.12.2 Forge (for hosting a world with friends).
   Every player needs both. The server side of the mod is what applies ULTRAKILL's hits, so the host's game needs it too.
3. ULTRAKILL's half, the same as the Fabric version needs: **BepInEx 5** and the **UltraBridge** plugin in ULTRAKILL's folder
   (`BepInEx/plugins/UltraBridge/`). Take them from an Ultracraft release (the Fabric jar carries them under `ultrakill/`).
   This port does not install them for you yet. It speaks the same protocol as the ported version.
4. Start Minecraft: it starts ULTRAKILL through Steam (`launchUltrakill`). Once "ULTRAKILL ready" shows, F8 becomes V1
   (or it happens by itself with `autoV1`). V switches Minecraft hands / V1's guns.

## Settings

`config/ultracraft.properties`, the same keys as the Fabric version (this port reads: `v1Height`, `autoV1`, `launchUltrakill`,
`sharpShop`, `ukFpsCap`, `lockStep`, `lowLatency`, `effects`, `stainCap`, `extraGore`, `terrainRange`, `playerBlockDamage`,
`enemyBlockDamage`, `impactFrames`, `fightMusic`, `calmMusic`, `uk.*`). For a weak laptop (i3-N305, 8 GB, integrated graphics):

    v1Height=360
    effects=0
    stainCap=0
    extraGore=false
    terrainRange=64
    lowLatency=false
    sharpShop=false
    lockStep=true
    ukFpsCap=0

Give Minecraft 2 GB (`-Xms1G -Xmx2G`), render distance 6, Fast graphics, VSync off, and close everything else: ULTRAKILL and Minecraft
share your RAM and your graphics.

## V1 fall-damage protection

While the server recognizes a player as V1, only Minecraft fall damage is canceled. Attacks, fire, drowning, void damage, and other damage sources remain enabled. The mod also clears the server-side fall-distance counter while V1 is active so falling does not accumulate fall damage.

## Commands and non-weapon cheats in this Forge increment

The Forge side now registers `/uc help`, `/uc status`, `/uc cheats`, and `/uc cheat <neverhungry|superspeed> [on|off|toggle]`.
These commands require permission level 2 (singleplayer with commands enabled, or an operator). The toggles are saved in
`config/ultracraft.properties`:

- `neverhungry`: server-authoritatively refills food/saturation and disables exhaustion from the exported V1 movement.
  It is a world-wide setting, so it affects everyone in the world.
- `superspeed`: applies Speed II while using ordinary Minecraft/Steve movement. It does **not** alter V1's movement in
  ULTRAKILL; that needs corresponding bridge-side cheat integration.

This is a partial port increment, not a full port. The original repository contains much newer systems and a separate C#
`UltraBridge` project; that bridge source is not part of this Forge project ZIP. The commands above do not pretend to expose
settings that the current bridge cannot apply.

## What is in this port

| Part | State |
|---|---|
| TCP link to UltraBridge, input (keys, mouse, wheel, pointer in menus), window size | ported |
| ULTRAKILL's frames: shared file, upload to GL, composite shader (colour + V1 mask) over the world | ported |
| Frame lock step (Minecraft waits for each ULTRAKILL frame, backs off when it can't keep up) | ported |
| Camera: player follows V1, world drawn from the frame's own view, lean (roll) and FOV | ported |
| HUD replaced while V1 (hearts, food, armour, hotbar, crosshair); Minecraft hands (V) | ported |
| Blocks (boxes, vertical runs), mobs, end crystals and projectiles sent to ULTRAKILL | ported |
| Server side: DMG, BOOM, SLAM, FIRE, MKNOCK, WHIP, SHURT, VITALS (hearts mirror V1), DEAD | ported |
| Starting and closing ULTRAKILL with Minecraft | ported (no BepInEx/plugin installer) |
| Far terrain (WorldMesh), fluids, lighting, weather, shop export | **not ported** |
| Block damage from hits (HIT, RAIL), blood stains, oil, fight music, themes | **not ported** |
| Steve mode (ULTRAKILL's enemies staying as Steve), spectating, teammate markers, V1 bodies of other players | **not ported** |
| P / gear / upgrades, shops (block), bosses, arenas, Cyber Grind, duels, style rewards | **not ported** |
| Settings screens, full cheat UI, keybind screen, UkEnemyEntity stand-ins | **not ported** (only the two Forge-side non-weapon command toggles above are available) |

So: you get V1 in a 1.12.2 world, fighting Minecraft's mobs, with shared worlds working through Essential; the progression
and extra systems of the Fabric version are not there. Those rely on Minecraft 1.21 features (custom entities and block
entities with new registries, data-driven structures, mixins) and each needs its own rewrite for 1.12.2.

## Known risks

- Roll (camera lean) sign and the GLSL `#version 120` shader are the likeliest things to need a tweak: if the view tilts
  the wrong way, negate `f[5]` in `ClientEvents.onCamera`.
- 1.12.2 has no `/tick freeze`, so ULTRAKILL's hitstop does not freeze Minecraft.
- The player is moved to V1's position every tick; in a world hosted by someone else, Minecraft's "moved too quickly" check can
  pull a guest back after a very fast move or a teleport (the host's own player is exempt).
- Entity type names differ from 1.21's in a few places (for example pigmen), so ULTRAKILL may treat those mobs generically.


## Settings GUI in 1.0.4

Open the settings screen with **F7** or `/uc settings`. This GUI exposes the configuration controls currently implemented in this Forge port (gameplay, performance, music, and the two Forge-side cheats). It is not yet the complete upstream settings interface: enemy/boss, arena, shop, rewards, Cyber Grind, and the full ULTRAKILL cheat/settings/control pages are not ported in this build.
