package autosummon.api;

import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolItem;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Immutable specification representing a summon weapon registration.
 * Built via {@link SummonWeaponSpec.Builder}.
 */
public class SummonWeaponSpec {

    private final String itemID;
    private final String displayName;
    private final Set<String> minionIDs;
    private final SummonCategory category;
    private final float slotCost;
    private final int maxAllowed;
    private final BiPredicate<Mob, InventoryItem> customMatcher;
    private final BiFunction<PlayerMob, InventoryItem, String> attackChecker;

    private SummonWeaponSpec(Builder builder) {
        this.itemID = builder.itemID.toLowerCase().trim();
        this.displayName = builder.displayName;
        this.minionIDs = Collections.unmodifiableSet(new HashSet<>(builder.minionIDs));
        this.category = builder.category != null ? builder.category : SummonCategory.GLOBAL_SLOT;
        this.slotCost = builder.slotCost > 0 ? builder.slotCost : 1.0f;
        this.maxAllowed = builder.maxAllowed;
        this.customMatcher = builder.customMatcher;
        this.attackChecker = builder.attackChecker;
    }

    public static Builder builder(String itemID) {
        return new Builder(itemID);
    }

    public String getItemID() {
        return itemID;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getMinionIDs() {
        return minionIDs;
    }

    public SummonCategory getCategory() {
        return category;
    }

    public float getSlotCost() {
        return slotCost;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }

    public BiPredicate<Mob, InventoryItem> getCustomMatcher() {
        return customMatcher;
    }

    public BiFunction<PlayerMob, InventoryItem, String> getAttackChecker() {
        return attackChecker;
    }

    /**
     * Converts this specification into an active SummonWeaponAdapter.
     */
    public SummonWeaponAdapter toAdapter() {
        return new SpecWeaponAdapter(this);
    }

    public static class Builder {
        private final String itemID;
        private String displayName;
        private final Set<String> minionIDs = new HashSet<>();
        private SummonCategory category = SummonCategory.GLOBAL_SLOT;
        private float slotCost = 1.0f;
        private int maxAllowed = -1;
        private BiPredicate<Mob, InventoryItem> customMatcher;
        private BiFunction<PlayerMob, InventoryItem, String> attackChecker;

        public Builder(String itemID) {
            if (itemID == null || itemID.trim().isEmpty()) {
                throw new IllegalArgumentException("itemID cannot be null or blank");
            }
            this.itemID = itemID;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder withMinions(String... minions) {
            if (minions != null) {
                for (String m : minions) {
                    if (m != null && !m.trim().isEmpty()) {
                        this.minionIDs.add(m.trim().toLowerCase());
                    }
                }
            }
            return this;
        }

        public Builder withMinions(Collection<String> minions) {
            if (minions != null) {
                for (String m : minions) {
                    if (m != null && !m.trim().isEmpty()) {
                        this.minionIDs.add(m.trim().toLowerCase());
                    }
                }
            }
            return this;
        }

        public Builder asGlobalSlot(float slotCost) {
            this.category = SummonCategory.GLOBAL_SLOT;
            this.slotCost = slotCost > 0 ? slotCost : 1.0f;
            return this;
        }

        public Builder asFollower(int maxAllowed) {
            this.category = SummonCategory.INDEPENDENT_FOLLOWER;
            this.maxAllowed = Math.max(1, maxAllowed);
            this.slotCost = 0.0f;
            return this;
        }

        public Builder asSentry(int maxAllowed) {
            this.category = SummonCategory.SENTRY_SUPPORT;
            this.maxAllowed = Math.max(1, maxAllowed);
            this.slotCost = 0.0f;
            return this;
        }

        public Builder category(SummonCategory category) {
            if (category != null) {
                this.category = category;
            }
            return this;
        }

        public Builder slotCost(float slotCost) {
            this.slotCost = slotCost;
            return this;
        }

        public Builder maxAllowed(int maxAllowed) {
            this.maxAllowed = maxAllowed;
            return this;
        }

        public Builder customMatcher(BiPredicate<Mob, InventoryItem> matcher) {
            this.customMatcher = matcher;
            return this;
        }

        public Builder attackChecker(BiFunction<PlayerMob, InventoryItem, String> checker) {
            this.attackChecker = checker;
            return this;
        }

        public SummonWeaponSpec build() {
            return new SummonWeaponSpec(this);
        }

        /**
         * Convenience method to build and register immediately with AutoSummonAPI.
         */
        public SummonWeaponSpec register() {
            SummonWeaponSpec spec = build();
            AutoSummonAPI.registerWeapon(spec);
            return spec;
        }
    }

    /**
     * Inner adapter implementation wrapping a SummonWeaponSpec
     */
    private static class SpecWeaponAdapter implements SummonWeaponAdapter {
        private final SummonWeaponSpec spec;

        public SpecWeaponAdapter(SummonWeaponSpec spec) {
            this.spec = spec;
        }

        @Override
        public String getItemStringID() {
            return spec.getItemID();
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            if (spec.getDisplayName() != null && !spec.getDisplayName().isEmpty()) {
                return spec.getDisplayName();
            }
            try {
                if (item != null) {
                    return item.getItemDisplayName();
                }
            } catch (Throwable ignored) {}
            return spec.getItemID();
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            return spec.getCategory() == SummonCategory.GLOBAL_SLOT;
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            return spec.getSlotCost();
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            if (isGlobalSlotWeapon(item, player)) {
                if (playerMaxSummons <= 0) return 0;
                float cost = getSummonSpaceCost(item, player);
                if (cost <= 0) cost = 1.0f;
                return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
            } else {
                return spec.getMaxAllowed() > 0 ? spec.getMaxAllowed() : 1;
            }
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || mob.removed() || mob.getHealth() <= 0) return false;
            if (SummonCompatRegistry.isIgnoredFollower(mob)) return false;

            if (spec.getCustomMatcher() != null) {
                try {
                    if (spec.getCustomMatcher().test(mob, item)) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            String mobID = mob.getStringID();
            if (mobID == null) return false;
            String lower = mobID.toLowerCase();

            if (spec.getMinionIDs().contains(lower) || spec.getMinionIDs().contains(mobID)) {
                return true;
            }

            // Prefix match if weapon specifies a base minion ID
            for (String registered : spec.getMinionIDs()) {
                if (!registered.isEmpty() && lower.startsWith(registered)) {
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
            if (spec.getAttackChecker() != null) {
                try {
                    return spec.getAttackChecker().apply(player, item);
                } catch (Throwable t) {
                    return "Attack check failed";
                }
            }
            if (item.item instanceof ToolItem) {
                try {
                    return ((ToolItem) item.item).canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
                } catch (Throwable t) {
                    return "Attack check error";
                }
            }
            return null;
        }

        @Override
        public boolean isSecondarySummon() {
            return spec.getCategory() == SummonCategory.INDEPENDENT_FOLLOWER;
        }

        @Override
        public boolean isSupportWeapon() {
            return spec.getCategory() == SummonCategory.SENTRY_SUPPORT;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            return spec.getCategory();
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            try {
                if (isSecondarySummon()) {
                    return "Follower (Cap: " + maxAllowed + ")";
                } else if (isSupportWeapon()) {
                    return "Sentry (Cap: " + maxAllowed + ")";
                } else {
                    float cost = getSummonSpaceCost(item, player);
                    String costDisplay = (cost == (int) cost) ? String.valueOf((int) cost) : String.format("%.1f", cost);
                    return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
                }
            } catch (Throwable t) {
                return "Summon (Cap: " + maxAllowed + ")";
            }
        }
    }
}
