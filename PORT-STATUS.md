# Ultracraft Forge 1.12.2 — port status (version 1.0.4)

This package is an incremental Forge 1.12.2 port, not a feature-complete conversion of the upstream Fabric 1.21.11 project.

## Included in this source
- ULTRAKILL/UltraBridge launch and communication path from the existing Forge project.
- Minecraft-side block/entity/projectile export and server-side application of supported bridge events.
- F8 V1/Steve toggle and V Minecraft-hands toggle.
- Existing `/uc help`, `/uc status`, `/uc cheats`, and supported Forge-side cheat commands.
- New F7 settings screen and client `/uc settings` entry point.
- Settings tabs for the existing gameplay, performance, music, and implemented cheat configuration fields.
- Existing version remains `1.0.4`.

## Not yet ported from upstream
- Complete terrain/fluid/light synchronization and all upstream rendering features.
- The full original settings pages, including all ULTRAKILL settings and key-rebinding screens.
- Enemy and boss systems, biome rules, arena generation, arena boss encounters, and related music/rewards.
- Shop terminal, P currency progression, weapon variants/upgrades, and full reward economy.
- Cyber Grind and its arenas/progression.
- Full ULTRAKILL Sandbox cheat integration and the upstream cheats/settings protocol.
- Full upstream multiplayer parity and comprehensive compatibility testing.

The settings GUI only exposes values already represented in this Forge port. A setting appearing in the GUI does not imply the missing upstream gameplay system has been ported.
