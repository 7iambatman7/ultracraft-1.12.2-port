# Patch notes — 1.0.4

- Cancels only Minecraft fall damage while the server tracks a player as V1.
- Clears the server-side fall-distance counter while V1 is active to prevent fall distance from accumulating.
- All other damage sources remain enabled, including attacks, fire, drowning, and void damage. ULTRAKILL death synchronization is unchanged.
- Keeps V1 respawn handling, logout cleanup, and suppression of conflicting Minecraft gameplay key bindings while V1 controls the game.
- Mod version remains `1.0.4`.

This patch has received source-level checks only. It has not been compiled or runtime-tested here because a compatible JDK 8 + Gradle 4.9 + ForgeGradle 3.0.197 build toolchain is unavailable in this environment.


## Settings GUI increment (version remains 1.0.4)
- Added a client settings screen accessible with F7 or `/uc settings`.
- Added Gameplay, Performance, Music, and Cheats tabs for settings already represented in the Forge config.
- Changes save to `config/ultracraft.properties` and send current supported options to UltraBridge.
- This is a GUI foundation, not a complete feature port. Upstream systems that do not exist in the Forge 1.12.2 code remain unimplemented.
