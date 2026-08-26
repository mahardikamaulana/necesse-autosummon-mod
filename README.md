# Auto Summon

Tired of manually spam-recasting all your minions every time you die, travel between islands, or enter a dungeon?

**Auto Summon** takes the tedious micromanagement out of summoner builds by automatically casting the summoning staves in your hotbar whenever you're below your minion limit. Mix and match different staves, set custom summon limits per weapon, and stay focused on combat without ever having to pause and spam-click staves again.

## Features

- **Hands-Free Summoning**: Keep your summon staves in hotbar slots 1–10, and the mod automatically summons and maintains your minion army whenever you're short on minions.
- **Smart Hotbar Priority**: Rightmost weapons get cast first (Slot 10 down to Slot 1). Want your favorite summons out first? Just place them on the right side of your bar!
- **Simple In-Game Menu (`F9`)**:
  - One-click toggle to turn auto-summoning on or off anytime.
  - Live overview showing your active summon slots, free slots, follower buddies, and sentries in real time.
  - Customize minion limits per weapon with quick `[-]` and `[+]` buttons, or leave them on `Auto` to fill whatever slots you have available.
- **Summoner's Expansion Ready**: Works right out of the box with the **Summoner's Expansion** mod! It understands multi-type summons (like baby zombie archers, spiders, and skeletons) and keeps follower summons (like books of bees or magma slimes) from eating your main summon slots.
- **Zero Lag & Smooth Gameplay**: Runs effortlessly in the background with zero frame drops or micro-stutter, even in giant army boss fights.
- **Multiplayer Friendly**: 100% client-side. Hop onto any server and enjoy auto-summoning without needing the server or host to install anything!

## Controls

- **F9**: Open / close the Auto Summon configuration menu (rebindable in Necesse's Controls settings).

## Installation

1. Download the latest jar from [Releases](https://github.com/mahardikamaulana/necesse-autosummon-mod/releases).
2. Place the jar file in your Necesse `mods` folder:
   - **Windows**: `%APPDATA%/Necesse/mods/`
   - **macOS**: `~/Library/Application Support/Necesse/mods/`
   - **Linux**: `~/.config/Necesse/mods/`
3. Launch Necesse and enable the mod in the in-game Mods menu.

## Building

Requires JDK 8 or higher.

```bash
./gradlew buildModJar
```

The compiled jar will be in `build/jar/`.

## Documentation

Detailed technical and developer documentation is available in the [`docs/`](docs/) directory:

- [**Architecture & Technical Design**](docs/ARCHITECTURE.md) – Internal execution lifecycle, performance optimizations, bytecode patch architecture, and memory efficiency.
- [**Compatibility Guide**](docs/COMPATIBILITY_GUIDE.md) – Guide on integrating custom summon weapons, staves, and minions using the `SummonCompatProvider` API.
- [**Developer Guide**](docs/DEVELOPMENT.md) – Local development environment setup, build workflows, testing, and debugging.

## Credits

- [brydonm](https://github.com/brydonm) - Original mod author ([necesse-autosummon-mod](https://github.com/brydonm/necesse-autosummon-mod)).
- [Xeraphire](https://github.com/mahardikamaulana) - In-game GUI, Summoner's Expansion compatibility, dynamic slot tracking, and optimizations.