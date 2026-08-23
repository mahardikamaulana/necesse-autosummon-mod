package autosummon.compat;

import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.WormMobBody;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Specialized compatibility provider for the "Summoner Expansion" mod (gagadoliano.summonerexpansion).
 * Accurately classifies slot summons, secondary/follower staves with independent caps, and support sentries.
 */
public class SummonerExpansionCompatProvider implements SummonCompatProvider {

    public static final String MOD_ID = "gagadoliano.summonerexpansion";

    // Multi-mob mappings: item string ID -> Set of possible spawned minion mob string IDs
    private static final Map<String, Set<String>> MULTI_MOB_MAPPINGS = new HashMap<>();

    // Set of item string IDs that are secondary / follower summons with isolated active caps
    private static final Set<String> SECONDARY_SUMMON_IDS = new HashSet<>();

    // Set of item string IDs that are support staves / sentries
    private static final Set<String> SUPPORT_SUMMON_IDS = new HashSet<>();

    private boolean isLoaded = false;

    static {
        // Multi-mob variant registrations
        registerMultiMob("redspiderstaff", "redspidermeleeminion", "redspiderrangeminion");
        registerMultiMob("runebonestaff", "runemeleeminion", "runerangedminion");
        registerMultiMob("bookbutterfly", "butterflygreenminion", "butterflyblueminion", "butterflyredminion");
        registerMultiMob("ancestorstaff", "ancestorknight", "ancestormage");
        registerMultiMob("magicwoodtools", "woodpickminion", "woodaxeminion");
        registerMultiMob("magiccoppertools", "copperpickminion", "copperaxeminion");
        registerMultiMob("magicirontools", "ironpickminion", "ironaxeminion");
        registerMultiMob("magicgoldtools", "goldpickminion", "goldaxeminion");
        registerMultiMob("enchantedbrainonastick", "enchantedbabyzombieminion", "enchantedbabyzombiearcherminion");

        // Standard single mob mappings for reference
        registerMultiMob("magiccopperlamp", "lampminioncopper");
        registerMultiMob("magicgoldlamp", "lampminiongold");
        registerMultiMob("magictungstenlamp", "lampminiontungsten");
        registerMultiMob("magiccastlecandelabra", "lampminioncastle");
        registerMultiMob("magicdungeoncandelabra", "lampminiondungeon");
        registerMultiMob("cactusstaff", "cactusminion");
        registerMultiMob("polarhead", "bearpolarminion");
        registerMultiMob("royalhive", "beequeenminion");
        registerMultiMob("bearhead", "bearminion");
        registerMultiMob("icewizardstaff", "icewizardminion");
        registerMultiMob("goldpitchfork", "farmerminion");
        registerMultiMob("vampirewings", "vampireminion");
        registerMultiMob("sandwormstaff", "sandwormheadminion");
        registerMultiMob("dryadessence", "spiritghoulminion");
        registerMultiMob("ashgolemhead", "ashgolemminion");
        registerMultiMob("gemamethystshards", "golemamethystminion");
        registerMultiMob("gemsapphireshards", "golemsapphireminion");
        registerMultiMob("gememeraldshards", "golememeraldminion");
        registerMultiMob("gemtopazshards", "golemtopazminion");
        registerMultiMob("gemrubyshards", "golemrubyminion");
        registerMultiMob("caveglowstaff", "caveglowsentry");
        registerMultiMob("vampirecoffin", "coffinsentry");
        registerMultiMob("hungrymimic", "hungrymimicminion");
        registerMultiMob("bookmagma", "magmaslimeminion");
        registerMultiMob("bookbee", "beebookminion");
        registerMultiMob("bookfrozen", "frozendwarfminion");
        registerMultiMob("bookrunic", "runicshieldminion");
        registerMultiMob("portablefurnace", "furnacegolemminion");
        registerMultiMob("explosivesnowball", "explosivesnowmanminion");
        registerMultiMob("bookmushroom", "mushroomsentry");
        registerMultiMob("xmastreescepter", "xmastreesentry");

        // Secondary / Follower summons with isolated active caps (0 Global Summon Slots consumed)
        SECONDARY_SUMMON_IDS.addAll(Arrays.asList(
            "bookmagma", "bookbee", "bookfrozen", "bookrunic",
            "portablefurnace", "explosivesnowball",
            "magicwoodtools", "magiccoppertools", "magicirontools", "magicgoldtools",
            "bookmushroom", "ancestorstaff"
        ));

        // Support weapons & sentries (0 Global Summon Slots consumed)
        SUPPORT_SUMMON_IDS.addAll(Arrays.asList(
            "xmastreescepter", "caveglowstaff", "vampirecoffin",
            "leafshotcoldsentry", "leafshotheatsentry", "leafshotsentry",
            "leafshotcoldpack", "leafshotheatpack", "leafshotpack",
            "coffebeampack"
        ));
    }

    private static void registerMultiMob(String itemID, String... minionIDs) {
        MULTI_MOB_MAPPINGS.put(itemID.toLowerCase(), new HashSet<>(Arrays.asList(minionIDs)));
    }

    @Override
    public String getModID() {
        return MOD_ID;
    }

    @Override
    public boolean isModLoaded() {
        if (isLoaded) return true;
        try {
            // Check enabled mods
            if (ModLoader.getEnabledMods() != null) {
                for (LoadedMod mod : ModLoader.getEnabledMods()) {
                    if (mod != null && (MOD_ID.equalsIgnoreCase(mod.id) || "Summoner Expansion".equalsIgnoreCase(mod.name))) {
                        isLoaded = true;
                        return true;
                    }
                }
            }
            // Check registry
            if (ItemRegistry.getItem("redspiderstaff") != null || ItemRegistry.getItem("enchantedbrainonastick") != null) {
                isLoaded = true;
                return true;
            }
            // Check classpath
            Class.forName("summonerexpansion.SummonerExpansion");
            isLoaded = true;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void init() {
        isLoaded = isModLoaded();
        if (isLoaded) {
            System.out.println("[Auto Summon] Summoner Expansion compatibility provider initialized!");
        }
    }

    @Override
    public boolean canHandle(InventoryItem item) {
        if (item == null || item.item == null) return false;
        String stringID = item.item.getStringID().toLowerCase();

        // 1. Direct ID match from known SummonerExpansion items
        if (MULTI_MOB_MAPPINGS.containsKey(stringID) || SECONDARY_SUMMON_IDS.contains(stringID) || SUPPORT_SUMMON_IDS.contains(stringID)) {
            return true;
        }

        // 2. Class hierarchy match (e.g. BaseSummonWeapon, BaseSupportWeapon)
        String className = item.item.getClass().getName();
        return className.startsWith("summonerexpansion.items.weapons");
    }

    @Override
    public SummonWeaponAdapter createAdapter(InventoryItem item) {
        if (item == null || item.item == null) return null;
        return new SummonerExpansionWeaponAdapter(item.item);
    }

    public static class SummonerExpansionWeaponAdapter implements SummonWeaponAdapter {
        private final Item itemObj;
        private final String stringID;
        private final Set<String> matchingMinionIDs;
        private final boolean isSecondary;
        private final boolean isSupport;

        public SummonerExpansionWeaponAdapter(Item itemObj) {
            this.itemObj = itemObj;
            this.stringID = itemObj.getStringID().toLowerCase();
            this.matchingMinionIDs = MULTI_MOB_MAPPINGS.getOrDefault(this.stringID, Collections.emptySet());

            boolean secondaryCheck = SECONDARY_SUMMON_IDS.contains(this.stringID);
            String className = itemObj.getClass().getName();
            if (!secondaryCheck && (className.contains("BaseSummonSecondary") || className.contains("BaseMagicTools") || className.contains("Secondary"))) {
                secondaryCheck = true;
            }

            // Universal rule: If summonType is a custom follower group (not "summonedmob"), it is a follower/secondary summon
            if (!secondaryCheck && itemObj instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) itemObj;
                if (staff.summonType != null && !staff.summonType.equalsIgnoreCase("summonedmob")) {
                    secondaryCheck = true;
                }
            }
            this.isSecondary = secondaryCheck;

            boolean supportCheck = SUPPORT_SUMMON_IDS.contains(this.stringID);
            if (!supportCheck && (className.contains("BaseSupportWeapon") || className.contains("SeedPack") || className.contains("Sentry"))) {
                supportCheck = true;
            }
            this.isSupport = supportCheck;
        }

        @Override
        public String getItemStringID() {
            return itemObj.getStringID();
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            return item != null ? item.getItemDisplayName() : itemObj.getStringID();
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            // Secondary summons and support sentries NEVER consume global player summon slots
            if (isSecondary || isSupport) {
                return false;
            }
            if (itemObj instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) itemObj;
                boolean isStandardSummonMob = staff.summonType == null || staff.summonType.equalsIgnoreCase("summonedmob");
                return staff.drawMaxSummons && isStandardSummonMob;
            }
            return false;
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            // Hungry Mimic takes all available player summon capacity
            if ("hungrymimic".equalsIgnoreCase(stringID)) {
                return player != null && player.buffManager != null
                    ? (float) player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS)
                    : 1.0f;
            }

            if (itemObj instanceof SummonToolItem) {
                try {
                    return ((SummonToolItem) itemObj).getSummonSpaceTaken(item, player);
                } catch (Exception ignored) {}
            }

            // Reflection fallback for BaseSupportWeapon / custom weapons
            try {
                Method m = itemObj.getClass().getMethod("getSummonSpaceTaken", InventoryItem.class, necesse.entity.mobs.itemAttacker.ItemAttackerMob.class);
                Object res = m.invoke(itemObj, item, player);
                if (res instanceof Float) {
                    return (Float) res;
                }
            } catch (Exception ignored) {}

            return 1.0f;
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            if (isGlobalSlotWeapon(item, player)) {
                float cost = getSummonSpaceCost(item, player);
                if (cost <= 0) cost = 1.0f;
                return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
            } else {
                // Secondary summon or dedicated follower staff: use weapon's own getMaxSummons
                try {
                    if (itemObj instanceof SummonToolItem) {
                        return Math.max(1, ((SummonToolItem) itemObj).getMaxSummons(item, player));
                    }
                    Method m = itemObj.getClass().getMethod("getMaxSummons", InventoryItem.class, necesse.entity.mobs.itemAttacker.ItemAttackerMob.class);
                    Object res = m.invoke(itemObj, item, player);
                    if (res instanceof Integer) {
                        return Math.max(1, (Integer) res);
                    }
                } catch (Exception ignored) {}
                return 1;
            }
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || mob.removed() || mob.getHealth() <= 0) return false;
            if (mob instanceof WormMobBody) return false;

            String mobStringID = mob.getStringID();
            if (mobStringID == null) return false;

            // 1. Direct multi-mob dictionary match
            if (!matchingMinionIDs.isEmpty() && matchingMinionIDs.contains(mobStringID.toLowerCase())) {
                return true;
            }

            // 2. Standard SummonToolItem checks
            if (itemObj instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) itemObj;
                if (staff.mobStringID != null) {
                    String staffLower = staff.mobStringID.toLowerCase();
                    String mobLower = mobStringID.toLowerCase();
                    if (mobLower.equals(staffLower) || (mobLower.startsWith(staffLower) && !staffLower.isEmpty())) {
                        return true;
                    }
                }
                if (staff.summonType != null && staff.summonType.equalsIgnoreCase(mobStringID)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String canAttack(PlayerMob player, InventoryItem item) {
            if (player == null || player.getLevel() == null || item == null) {
                return "Invalid state";
            }
            if (itemObj instanceof ToolItem) {
                return ((ToolItem) itemObj).canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
            }
            return null;
        }

        @Override
        public boolean isSecondarySummon() {
            return isSecondary;
        }

        @Override
        public boolean isSupportWeapon() {
            return isSupport;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            if (isSupport) {
                return SummonCategory.SENTRY_SUPPORT;
            } else if (isSecondary) {
                return SummonCategory.INDEPENDENT_FOLLOWER;
            } else if (isGlobalSlotWeapon(item, player)) {
                return SummonCategory.GLOBAL_SLOT;
            } else {
                return SummonCategory.INDEPENDENT_FOLLOWER;
            }
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            if (isSecondary) {
                return "Follower (Cap: " + maxAllowed + ")";
            } else if (isSupport) {
                return "Sentry (Cap: " + maxAllowed + ")";
            } else if (isGlobalSlotWeapon(item, player)) {
                float spaceCost = getSummonSpaceCost(item, player);
                String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
            } else {
                return "Follower (Cap: " + maxAllowed + ")";
            }
        }
    }
}
