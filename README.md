# Auto Summon Mod

A Necesse mod that automatically summons using the rightmost staff in your hotbar when you're below your maximum summon count.

## Features

- **Automatic Summoning**: Automatically uses the rightmost summoning staff in your hotbar.
- **Interactive In-Game GUI (`F9`)**: Open the configuration GUI at any time with **F9** to inspect hotbar staves, slot costs, and active minion counts.
- **Per-Weapon Custom Limits**: Manually set individual summon quotas per weapon (or leave on Auto/Max).
- **Smart Slot & Capacity Detection**: Respects both global summon slot capacities and dedicated follower minion caps.
- **Dynamic Slot Validation**: Caps counter adjusters based on player's available slots and weapon cost.
- **Automatic Fallback on Capacity Decrease**: Automatically resets slot-consuming staves to Auto if summon slots drop.
- **Optimized Performance**: Zero per-tick heap allocations, single-pass entity scanning, and intelligent tick backoff.
- **Persistent Settings**: Saves all configurations across sessions using Necesse's native save/load system.

## Controls

- **`F9`**: Open / Close the Auto Summon Configuration GUI.

## Building

To build the mod yourself:

1. Run `./gradlew buildModJar`
2. The built JAR will be in `build/jar/`

## Requirements

- Necesse 1.3.2
- Java 8 or higher

## Credits

- **Original Author**: [brydonm (Street Noodle)](https://github.com/brydonm) - Creator of the original [necesse-autosummon-mod](https://github.com/brydonm/necesse-autosummon-mod).
- **Enhanced & Maintained by**: [Xeraphire](https://github.com/mahardikamaulana) - Interactive GUI configuration, per-weapon limit controls, slot tracking, capacity fallbacks, and performance optimizations.