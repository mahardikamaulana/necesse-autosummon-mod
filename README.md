# Auto Summon (Necesse Mod)

A quality-of-life Necesse mod that automatically summons your minions using the staves in your hotbar. Includes an in-game configuration GUI (**F9**), per-weapon summon limit quotas, smart slot/follower detection, and performance optimizations.

---

## 📖 How It Works

1. **Automatic Summoning**: Whenever you are below your summon limit (e.g. joining a world, traveling between islands, or after a minion dies), the mod automatically casts the summon weapons located in your hotbar (slots 1–10).
2. **Rightmost Hotbar Priority**: The mod scans your hotbar from **right to left** (Slot 10 down to Slot 1). Weapons placed further to the right are prioritized first until their quota or available summon slots are filled.
3. **Attack-Safe**: Does not interrupt manual attacks, mining, or interacting with items.

---

## 🎮 In-Game Configuration GUI (`F9`)

Press **`F9`** in-game at any time to open the Auto Summon Configuration menu:

- **Master Toggle**: Enable or disable the entire auto-summoning system on the fly.
- **Global Slots Header**: Real-time monitor displaying your current slot consumption vs. your player's maximum capacity (e.g., `Global Summon Slots: 6 / 6 used`).
- **Hotbar Weapon Cards**: Shows every summon weapon currently equipped in your hotbar with:
  - **Item Icon & Slot Number**: Hover over the icon to view native Necesse tooltips.
  - **Cost & Capacity**: Displays whether it consumes global slots (e.g. `2 slot(s)/summon (Max: 3)`) or has a dedicated follower cap (e.g. `Follower (Max: 8)`).
  - **Live Active Counter**: Tracks how many minions of that specific weapon are currently active and following you.
  - **Individual Limit Stepper (`[-]` `[ Auto / N ]` `[+]` `[Auto]`)**:
    - Set exact quotas (e.g., summon exactly **2** dragons and fill the rest with other minions).
    - Set to **`Auto`** to dynamically summon as many as your available slots allow.
- **Reset All Limits**: One-click button to reset all weapon quotas back to `Auto`.

---

## ⚡ Smart Mechanics & Fallbacks

- **Slot-Based Dynamic Validation**: The stepper `[+]` button dynamically caps each weapon at $\lfloor \text{Player Max Slots} / \text{Weapon Cost} \rfloor$, preventing you from setting impossible quotas.
- **Follower Weapon Support**: Non-slot summons (like the *Frost Piercer*) are tracked independently and will not consume or block your global summon slot pool.
- **Automatic Fallback on Capacity Decrease**: If your maximum summon slots decrease (e.g., unequipping summoner armor/trinkets or when a summoning potion expires), all slot-consuming weapons automatically reset to **`Auto`** with a chat notification to prevent quota deadlocks.
- **Optimized & Lag-Free**: Built with single-pass entity scanning and tick throttling when at full capacity, ensuring smooth 60+ FPS even during chaotic boss fights.

---

## ⌨️ Controls & Keybindings

| Key | Action |
|:---:|:---|
| **`F9`** | Toggle the Auto Summon Configuration GUI |

*(Keybinding can also be reconfigured in Necesse's standard Controls menu).*

---

## 📥 Installation

1. Download the latest `AutoSummon-<game-version>-<mod-version>.jar` from the [Releases](https://github.com/mahardikamaulana/necesse-autosummon-mod/releases) page.
2. Place the `.jar` file into your Necesse `mods` folder:
   - **Windows**: `%APPDATA%/Necesse/mods/`
   - **macOS**: `~/Library/Application Support/Necesse/mods/`
   - **Linux**: `~/.config/Necesse/mods/`
3. Launch Necesse and ensure the mod is enabled in the in-game **Mods** menu.

---

## 🛠️ Building From Source

1. Clone this repository:
   ```bash
   git clone https://github.com/mahardikamaulana/necesse-autosummon-mod.git
   ```
2. Build the mod using Gradle:
   ```bash
   ./gradlew buildModJar
   ```
3. The compiled `.jar` will be available in `build/jar/`.

### Requirements
- **Necesse**: 1.3.2
- **Java**: 8 or higher

---

## 📜 Credits & Attribution

- **Original Author**: [brydonm (Street Noodle)](https://github.com/brydonm) — Original creator of [necesse-autosummon-mod](https://github.com/brydonm/necesse-autosummon-mod).
- **Enhanced & Maintained by**: [Xeraphire](https://github.com/mahardikamaulana) — Interactive configuration GUI, per-weapon quotas, slot calculation engine, capacity fallbacks, and performance optimizations.