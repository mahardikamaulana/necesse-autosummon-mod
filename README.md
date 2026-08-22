# Auto Summon

A Necesse mod that automatically casts summon weapons in your hotbar when below your summon limit.

## Features

- **Auto Summoning**: Automatically casts summon weapons placed in hotbar slots 1–10 whenever minion slots are available.
- **Hotbar Priority**: Scans hotbar from right to left (Slot 10 down to Slot 1), prioritizing weapons placed further to the right.
- **In-Game Configuration (`F9`)**:
  - Master toggle to enable/disable auto summoning.
  - Live global slot monitor and per-weapon active minion counts.
  - Per-weapon limit controls: set fixed limits or leave on `Auto` to use available slots.
- **Slot & Follower Support**: Correctly separates slot-consuming summons (e.g. Crystallized Skull) and follower summons (e.g. Frost Piercer).
- **Capacity Fallback**: Automatically resets slot-consuming staves to `Auto` if player summon slots decrease (e.g. unequipping gear or buff expiration).
- **Performance Optimized**: Single-pass level scanning and tick backoff when capacity is full.

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

## Credits

- [brydonm](https://github.com/brydonm) - Original mod author ([necesse-autosummon-mod](https://github.com/brydonm/necesse-autosummon-mod)).
- [Xeraphire](https://github.com/mahardikamaulana) - In-game GUI, per-weapon limit controls, slot tracking, and optimizations.