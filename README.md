# Ultracraft

The real ULTRAKILL, played inside the real Minecraft 1.21.11. ULTRAKILL runs alongside Minecraft and is drawn into
its window; Minecraft's blocks and mobs become ULTRAKILL's world and enemies. Bosses with modifiers, P, the shop,
upgrades, the Cyber Grind, and LAN co-op.

You need to own **ULTRAKILL** (Steam) and **Minecraft Java Edition**. Windows only.

## Install

1. **BepInEx 5** (x64) into ULTRAKILL: download `BepInEx_win_x64_5.4.x.zip` from
   https://github.com/BepInEx/BepInEx/releases, unzip it into the ULTRAKILL folder
   (Steam → ULTRAKILL → Manage → Browse local files), start ULTRAKILL once, then close it.
2. From this repository's **Releases**, download `Ultracraft.zip`:
   - put `UltraBridge.dll` in `ULTRAKILL/BepInEx/plugins/UltraBridge/`
   - put `ultracraft-x.y.z.jar` in your Minecraft `mods` folder
3. Install **Fabric Loader** for Minecraft **1.21.11** (https://fabricmc.net/use/installer/) and put
   **Fabric API** for 1.21.11 (https://modrinth.com/mod/fabric-api) in the same `mods` folder.
4. Start Steam, then start Minecraft with the Fabric profile. ULTRAKILL starts by itself (and closes with
   Minecraft); open a world and you become V1 (F8 toggles back to Steve).

Settings: the **Ultracraft...** button on the title screen, the pause menu and Options (or `/uc settings`).
Commands: `/uc help`.

## If it's laggy

Two games run at once and share your graphics card. In **Ultracraft...** settings:
- **ULTRAKILL Resolution**: 540p or 480p is the biggest frame rate win (720p is the default).
- **ULTRAKILL FPS Cap**: 60 or 90 leaves Minecraft more room (120 is the default).
- **Sharp Shop Screen**: off.

Also: Minecraft render distance 8-12 chunks, and Sodium (Fabric) helps Minecraft's side. ULTRAKILL's own
graphics options (in ULTRAKILL itself) apply too.

Multiplayer: open a world to LAN; everyone needs ULTRAKILL plus both mods.

Your own ULTRAKILL save is never written: progress lives in the Minecraft world.

## Build from source

- Fabric mod: JDK 21, `cd fabric && ./gradlew build` → `fabric/build/libs/`.
- ULTRAKILL plugin: .NET SDK, `dotnet build -c Release ultrabridge/UltraBridge.csproj -p:GameDir="<your ULTRAKILL folder>"`
  (it compiles against your own install's DLLs and copies the result into its BepInEx plugins).

No game files are included in this repository.
