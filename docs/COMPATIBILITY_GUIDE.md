# Modder's Compatibility Guide

Welcome! Making your custom summon weapons, independent followers, and sentries work seamlessly with **Auto Summon** is fast, flexible, and completely painless.

Auto Summon supports **5 different integration methods** ranging from **Zero-Code JSON** to **1-Line Java APIs** and **Drop-In Bridges**.

---

## 🎯 Which Integration Method Should I Choose?

| Method | When to Use | Requires Java Code? | Requires Compile Dependency? |
| :--- | :--- | :---: | :---: |
| **1. [Zero-Code JSON](#method-1-zero-code-json-autosummonjson-recommended)** | You want instant compatibility without writing any Java code. | ❌ No | ❌ No |
| **2. [Drop-In Bridge](#method-2-drop-in-bridge-autosummonbridgejava-recommended)** | You want simple 1-line registrations in Java without adding compile dependencies. | ✅ Yes (1 line) | ❌ No |
| **3. [Direct Fluent API](#method-3-direct-fluent-api-autosummonapi)** | You have `AutoSummon.jar` on your compile classpath. | ✅ Yes (1 line) | ✅ Yes |
| **4. [Item Interface](#method-4-item-interface-autosummonableweapon)** | You want your custom item class to declare its own summon behavior. | ✅ Yes | Optional |
| **5. [Custom Adapter](#method-5-custom-adapter-simplesummonweaponadapter)** | Complex weapons with dynamic NBT state, custom raycasts, or unique despawn logic. | ✅ Yes | Optional |

---

## Method 1: Zero-Code JSON (`autosummon.json`) ⭐ *Recommended*

You can achieve 100% Auto Summon compatibility simply by placing an `autosummon.json` file inside your mod's JAR (in the root directory or inside `resources/` / `res/` / `assets/`).

Auto Summon will **automatically discover and load it** when the game starts!

### Example `autosummon.json`:
```json
{
  "modId": "author.mymod",
  "weapons": [
    {
      "id": "myskeletonstaff",
      "displayName": "Skeleton Staff",
      "minion": "myskeletonminion",
      "category": "GLOBAL_SLOT",
      "slotCost": 1.0
    },
    {
      "id": "myspiderstaff",
      "displayName": "Brood Spider Staff",
      "minions": [
        "spidermeleeminion",
        "spiderrangeminion"
      ],
      "category": "GLOBAL_SLOT",
      "slotCost": 1.0
    },
    {
      "id": "bookofbees",
      "displayName": "Book of Bees",
      "minion": "beeminion",
      "category": "INDEPENDENT_FOLLOWER",
      "maxAllowed": 2
    },
    {
      "id": "crystalturret",
      "displayName": "Crystal Turret Scepter",
      "minion": "crystalturretmob",
      "category": "SENTRY_SUPPORT",
      "maxAllowed": 1
    }
  ],
  "ignoredMobs": [
    "mycosmeticpet",
    "mycustommount"
  ]
}
```

### JSON Property Reference:
- `id` *(required)*: The item string ID registered in Necesse (e.g. `"myspiderstaff"`).
- `minion` / `minions`: One or more mob string IDs that this weapon spawns.
- `category`:
  - `"GLOBAL_SLOT"` (default): Standard combat summons consuming global player summon capacity.
  - `"INDEPENDENT_FOLLOWER"`: Secondary pets/books with their own isolated active cap.
  - `"SENTRY_SUPPORT"`: Stationary turrets, totems, and sentries with an isolated cap.
- `slotCost`: Global summon slots consumed per cast (default `1.0`).
- `maxAllowed`: Maximum active minions allowed for followers or sentries.
- `ignoredMobs`: List of companion/cosmetic mob IDs that should never be counted or despawned.

---

## Method 2: Drop-In Bridge (`AutoSummonBridge.java`) ⭐ *Recommended for Java*

If you want to register weapons via code but **do NOT want compile-time dependencies** on Auto Summon:

1. Copy the standalone helper file [`src/main/java/autosummon/api/AutoSummonBridge.java`](file:///Users/dika/Project/Games/necesse-autosummon-mod/src/main/java/autosummon/api/AutoSummonBridge.java) into your mod project.
2. In your mod's `postInit()`, call the 1-line registration methods:

```java
package mymod;

import mymod.compat.AutoSummonBridge;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class MyMod {

    public void postInit() {
        // Standard staff (1 global slot)
        AutoSummonBridge.registerStaff("myskeletonstaff", "myskeletonminion");

        // Multi-minion staff (spawns both melee and ranged minions)
        AutoSummonBridge.registerStaff("myspiderstaff", "spidermeleeminion", "spiderrangeminion");

        // Independent follower (isolated cap of 2, 0 global slots consumed)
        AutoSummonBridge.registerFollower("bookofbees", 2, "beeminion");

        // Stationary sentry (isolated cap of 1)
        AutoSummonBridge.registerSentry("crystalturret", 1, "crystalturretmob");

        // Cosmetic pet/mount (ignored by Auto Summon)
        AutoSummonBridge.ignoreMob("mycosmeticpet");
    }
}
```

> [!NOTE]
> If players do not have Auto Summon installed, all `AutoSummonBridge` calls safely no-op with zero performance impact or errors.

---

## Method 3: Direct Fluent API (`AutoSummonAPI`)

If you have `AutoSummon.jar` on your compile classpath or in your `libDepends`, you can use `autosummon.api.AutoSummonAPI`:

```java
import autosummon.api.AutoSummonAPI;
import autosummon.api.SummonWeaponSpec;

public class MyModEntrypoint {

    public void postInit() {
        // 1-line helpers
        AutoSummonAPI.registerStaff("myskeletonstaff", "myskeletonminion");
        AutoSummonAPI.registerFollower("bookofbees", 2, "beeminion");
        AutoSummonAPI.registerSentry("crystalturret", 1, "crystalturretmob");

        // Fluent Builder for advanced configuration
        AutoSummonAPI.registerWeapon(SummonWeaponSpec.builder("enchantedstaff")
                .displayName("Enchanted Staff")
                .asGlobalSlot(2.0f) // Takes 2 slots
                .withMinions("enchantedminion_warrior", "enchantedminion_mage")
                .withAttackCheck((player, item) -> {
                    // Custom attack requirement check (return null if ready)
                    return player.getHealth() > 50 ? null : "Low Health";
                })
                .build());
    }
}
```

---

## Method 4: Item Interface (`AutoSummonableWeapon`)

If you prefer object-oriented design directly in your custom `Item` classes, implement `AutoSummonableWeapon`:

```java
package mymod.items;

import autosummon.api.AutoSummonableWeapon;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.Arrays;
import java.util.Collection;

public class CustomNecromancerStaff extends SummonToolItem implements AutoSummonableWeapon {

    public CustomNecromancerStaff() {
        super("undeadwarrior", FollowPosition.PYRAMID, 1.0f, 100, SummonWeaponsLootTable.summonWeapons);
    }

    @Override
    public Collection<String> getSummonMinionIDs(InventoryItem item) {
        // Return all possible minion variants this staff can spawn
        return Arrays.asList("undeadwarrior", "undeadarcher", "undeadmage");
    }

    @Override
    public SummonCategory getSummonCategory(InventoryItem item, PlayerMob player) {
        return SummonCategory.GLOBAL_SLOT;
    }
}
```

Auto Summon automatically detects any item in the player's inventory that implements `AutoSummonableWeapon` with zero extra registration!

---

## Method 5: Custom Adapter (`SimpleSummonWeaponAdapter`)

For weapons requiring custom targeting, dynamic NBT/GND state matching, or unique despawn hooks, subclass `SimpleSummonWeaponAdapter`:

```java
package mymod.compat;

import autosummon.compat.SimpleSummonWeaponAdapter;
import necesse.entity.mobs.Mob;
import necesse.inventory.InventoryItem;

public class MyComplexStaffAdapter extends SimpleSummonWeaponAdapter {

    public MyComplexStaffAdapter() {
        super("complexstaff", SummonCategory.GLOBAL_SLOT, "complexminion");
    }

    @Override
    public boolean isMatchingMinion(Mob mob, InventoryItem item) {
        // Custom matching logic (e.g. check mob custom data or sub-types)
        return super.isMatchingMinion(mob, item) || mob instanceof MyCustomMinionMob;
    }
}
```

Register it in your mod's `postInit()`:
```java
AutoSummonAPI.registerCustom("complexstaff", new MyComplexStaffAdapter());
```

---

## 🔍 In-Game Modder Diagnostics & Debugging

Auto Summon provides real-time in-game inspection tools to verify your integration:

1. Launch Necesse with your mod and Auto Summon enabled.
2. Put your summon weapon in **Hotbar Slot 1**.
3. Press **F9** to open the Auto Summon configuration menu:
   - Verify that your weapon appears with its icon, name, and proper badge (`[SLOT]`, `[FOLLOWER]`, or `[SENTRY]`).
   - Summon a minion and check that the **Active Minion** counter increments in real time.
4. Programmatically, you can inspect any item at runtime using `AutoSummonDiagnostic.inspectItem(player, item)`.

---

## 💡 Summary & Best Practices

- **Standard Weapons:** Most standard Necesse `SummonToolItem` weapons work automatically with **Zero Code**.
- **Multi-Mob Variants or Custom Followers:** Use **Method 1 (JSON)** or **Method 2 (Drop-In Bridge)**.
- **Cosmetic Pets & Mounts:** Always declare them in `ignoredMobs` or call `ignoreMob("mypet")` so Auto Summon never despawns them.
