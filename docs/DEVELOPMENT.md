# Developer Guide

Welcome to the **Auto Summon** developer guide! This document will help you set up your local development environment, build the mod, run tests, and launch Necesse with your changes.

---

## 1. Prerequisites

Before you begin, make sure you have the following installed:

1. **Java Development Kit (JDK)**:
   - **JDK 8** or **JDK 17** (recommended: JDK 17, as Gradle will automatically compile bytecode targeting Java 8).
   - Check your version in your terminal: `java -version`
2. **Necesse**:
   - Installed via Steam on macOS, Windows, or Linux.
3. **Git**:
   - To clone the repository and manage code changes.

> [!NOTE]
> You **do not** need to install Gradle manually. The project includes the Gradle Wrapper (`./gradlew` on macOS/Linux or `gradlew.bat` on Windows), which automatically downloads the correct Gradle version.

---

## 2. Quickstart (5-Minute Setup)

### Step 1: Clone the Repository
```bash
git clone https://github.com/mahardikamaulana/necesse-autosummon-mod.git
cd necesse-autosummon-mod
```

### Step 2: Configure the Necesse Game Path
The build script (`build.gradle`) automatically checks default Steam installation paths across Windows, macOS, and Linux.

If your game is installed in a custom library folder, tell Gradle where to find it using **one** of the following methods:

- **Option A (Recommended): Environment Variable**
  ```bash
  # macOS Example
  export NECESSE_GAME_DIR="$HOME/Library/Application Support/Steam/steamapps/common/Necesse/Necesse.app/Contents/Resources"

  # Windows Example (PowerShell)
  $env:NECESSE_GAME_DIR="D:\SteamLibrary\steamapps\common\Necesse"
  ```
- **Option B: `gradle.properties` file**
  Create or edit `gradle.properties` in the project root:
  ```properties
  necesseGameDir=/path/to/Necesse
  ```
- **Option C: Command-line Flag**
  ```bash
  ./gradlew -PnecesseGameDir="/path/to/Necesse" buildModJar
  ```

---

## 3. Common Commands & Workflow

Here are the primary commands you will use while developing:

| Action | Command | What It Does |
| :--- | :--- | :--- |
| **Build Mod Jar** | `./gradlew buildModJar` | Compiles code and outputs the mod `.jar` to `build/jar/`. |
| **Run Unit Tests** | `./gradlew test` | Runs all automated JUnit 5 tests without opening the game. |
| **Launch Game Client** | `./gradlew runClient` | Builds the mod and launches Necesse via Steam with the mod loaded. |
| **Launch Dev Client** | `./gradlew runDevClient` | Launches Necesse in developer mode (`-dev 1`) with extra debug info. |
| **Launch Server** | `./gradlew runServer` | Launches a local dedicated Necesse server with the mod. |

---

## 4. Testing Your Changes

### Automated Unit Tests
We use JUnit 5 to test settings management, limit constraints, logging, and compatibility adapters without needing to launch the game:

```bash
./gradlew test
```

Test reports are generated in HTML format at:
```text
build/reports/tests/test/index.html
```

### In-Game Testing Checklist
When you launch the game with `./gradlew runClient`, verify the following:
1. **Hotbar Summoning**: Place summon staves in hotbar slots 1–10. Check that the player automatically casts them until capacity is reached.
2. **Priority**: Place multiple staves in your hotbar. Verify that the rightmost staff (Slot 10 down to Slot 1) is summoned first.
3. **Configuration GUI (`F9`)**:
   - Press **F9** to open the menu.
   - Adjust individual weapon limits using `[-]`, `[+]`, and `[Auto]`.
   - Verify that counters update in real time when minions spawn or despawn.
   - Toggle the mod **Off** and click **Done** — verify all active summon buffs and minions are dismissed.

---

## 5. Project Directory Map

```text
necesse-autosummon-mod/
├── build.gradle                       # Gradle build script & game path resolution
├── docs/                              # Project documentation
│   ├── ARCHITECTURE.md                # System design & tick lifecycle
│   ├── COMPATIBILITY_GUIDE.md         # Guide for adding custom mod weapons
│   └── DEVELOPMENT.md                 # This developer setup guide
├── src/
│   ├── main/
│   │   ├── java/autosummon/
│   │   │   ├── AutoSummonMod.java             # Mod entrypoint & keybind registration
│   │   │   ├── AutoSummonConfig.java          # Runtime configuration & status flags
│   │   │   ├── AutoSummonModSettings.java     # Persistent settings save/load logic
│   │   │   ├── compat/                        # Compatibility providers & weapon adapters
│   │   │   ├── gui/                           # In-game F9 configuration window
│   │   │   ├── patches/                       # ByteBuddy bytecode patch on PlayerMob
│   │   │   └── util/                          # Logging & diagnostics
│   │   └── resources/
│   │       ├── locale/                        # Language localization strings
│   │       └── mod.info                       # Mod metadata (ID, version, author)
│   └── test/java/autosummon/                  # JUnit 5 test suites
```

---

## 6. Important Tips & Troubleshooting

### 1. `IllegalAccessError` in Patches
Necesse uses **ByteBuddy** to inline bytecode from patch classes (like `AutoSummonPatch`) directly into core game classes (like `PlayerMob`).
> [!IMPORTANT]
> Any field, helper method, or inner class referenced inside an `@Advice.OnMethodExit` or `@Advice.OnMethodEnter` method **must be declared `public static`**. If it is `private`, the JVM will throw an `IllegalAccessError` when the game ticks!

### 2. "Could not find Necesse game install directory"
If Gradle outputs this error, verify that `Necesse.jar` exists inside your configured path. On macOS, Steam places the jar inside the app bundle:
```text
/Users/<user>/Library/Application Support/Steam/steamapps/common/Necesse/Necesse.app/Contents/Resources
```

### 3. Clean Build
If you experience strange caching issues or stale jars:
```bash
./gradlew clean buildModJar
```
