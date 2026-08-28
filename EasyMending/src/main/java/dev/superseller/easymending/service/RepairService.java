package dev.superseller.easymending.service;

import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.util.ExperienceCalculator;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.ItemUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Core business service handling repair evaluations, durability restoration,
 * XP calculations, and cooldown tracking.
 */
public final class RepairService {

    private final PluginConfig config;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> adminBypassUsers = new HashSet<>();

    private final AtomicInteger totalRepairs = new AtomicInteger(0);
    private final AtomicLong totalXpSpent = new AtomicLong(0);

    public RepairService(PluginConfig config) {
        this.config = config;
    }

    /**
     * Calculates the XP cost to repair a single item.
     *
     * @param item item to calculate
     * @param mendingBypass whether player bypasses mending enchantment requirement
     * @return XP points needed, or -1 if item cannot be repaired
     */
    public int calculateItemCost(ItemStack item, boolean mendingBypass) {
        if (!ItemUtil.isRepairable(item)) {
            return -1;
        }
        int damage = ItemUtil.getDamage(item);
        if (damage <= 0) {
            return 0;
        }
        boolean hasMending = ItemUtil.hasMending(item);
        if (config.isRequireMending() && !hasMending && !mendingBypass) {
            return -1;
        }

        double multiplier = hasMending ? 1.0 : config.getNonMendingMultiplier();
        return ExperienceCalculator.calculateRepairCost(
                damage,
                config.getDurabilityPerXp(),
                multiplier,
                config.getMinXpPerRepair()
        );
    }

    /**
     * Retrieves all items corresponding to a given RepairScope from the player's inventory.
     */
    public List<ItemStack> getItemsForScope(Player player, RepairScope scope) {
        if (player == null || scope == null) {
            return Collections.emptyList();
        }
        PlayerInventory inv = player.getInventory();
        List<ItemStack> list = new ArrayList<>();

        switch (scope) {
            case HAND -> {
                ItemStack mainHand = inv.getItemInMainHand();
                if (mainHand != null && !mainHand.getType().isAir()) {
                    list.add(mainHand);
                }
            }
            case OFFHAND -> {
                ItemStack offHand = inv.getItemInOffHand();
                if (offHand != null && !offHand.getType().isAir()) {
                    list.add(offHand);
                }
            }
            case ARMOR -> {
                for (ItemStack armor : inv.getArmorContents()) {
                    if (armor != null && !armor.getType().isAir()) {
                        list.add(armor);
                    }
                }
            }
            case HOTBAR -> {
                for (int i = 0; i < 9; i++) {
                    ItemStack it = inv.getItem(i);
                    if (it != null && !it.getType().isAir()) {
                        list.add(it);
                    }
                }
            }
            case ALL -> {
                for (ItemStack it : inv.getContents()) {
                    if (it != null && !it.getType().isAir()) {
                        list.add(it);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Produces a cost and durability estimate for a given scope without executing the repair.
     */
    public RepairEstimate estimate(Player player, RepairScope scope) {
        List<ItemStack> items = getItemsForScope(player, scope);
        boolean mendingBypass = player.hasPermission("easymending.bypass.mending") || hasAdminBypass(player.getUniqueId());
        int count = 0;
        int totalDamage = 0;
        int totalXpCost = 0;

        for (ItemStack it : items) {
            if (!ItemUtil.isRepairable(it)) continue;
            int dmg = ItemUtil.getDamage(it);
            if (dmg <= 0) continue;
            int cost = calculateItemCost(it, mendingBypass);
            if (cost >= 0) {
                count++;
                totalDamage += dmg;
                totalXpCost += cost;
            }
        }

        int availableXp = ExperienceUtil.getPlayerTotalExperience(player);
        boolean canAffordFull = availableXp >= totalXpCost;
        boolean canAffordPartial = availableXp >= config.getMinXpPerRepair();
        int affordableDurability = ExperienceCalculator.calculateAffordableDurability(
                availableXp,
                config.getDurabilityPerXp(),
                1.0
        );

        return new RepairEstimate(count, totalDamage, totalXpCost, availableXp, canAffordFull, canAffordPartial, affordableDurability);
    }

    /**
     * Executes the repair operation for the target scope.
     *
     * @param player player performing the repair
     * @param scope scope of items to repair
     * @param forceFree whether to bypass XP cost
     * @param adminForce whether this is an administrative action bypassing cooldowns
     * @return RepairResult indicating outcome and metrics
     */
    public RepairResult repair(Player player, RepairScope scope, boolean forceFree, boolean adminForce) {
        if (player == null || !player.isOnline()) {
            return RepairResult.failure("player-only");
        }

        // Cooldown check
        boolean bypassCooldown = adminForce || player.hasPermission("easymending.bypass.cooldown");
        if (!bypassCooldown && config.getCooldownSeconds() > 0) {
            long now = System.currentTimeMillis();
            Long last = cooldowns.get(player.getUniqueId());
            if (last != null) {
                long elapsedSeconds = (now - last) / 1000L;
                if (elapsedSeconds < config.getCooldownSeconds()) {
                    return RepairResult.failure("cooldown-active");
                }
            }
        }

        List<ItemStack> rawItems = getItemsForScope(player, scope);
        if (rawItems.isEmpty()) {
            if (scope == RepairScope.HAND) {
                return RepairResult.failure("not-repairable");
            }
            return RepairResult.failure("no-damage-target");
        }

        boolean mendingBypass = player.hasPermission("easymending.bypass.mending") || hasAdminBypass(player.getUniqueId());
        boolean costBypass = forceFree || player.hasPermission("easymending.bypass.cost") || hasAdminBypass(player.getUniqueId());

        // Check specific single item errors for HAND / OFFHAND scope
        if (scope == RepairScope.HAND || scope == RepairScope.OFFHAND) {
            ItemStack single = rawItems.get(0);
            if (!ItemUtil.isRepairable(single)) {
                return RepairResult.failure("not-repairable");
            }
            if (ItemUtil.getDamage(single) <= 0) {
                return RepairResult.failure("no-damage-held");
            }
            if (config.isRequireMending() && !ItemUtil.hasMending(single) && !mendingBypass) {
                return RepairResult.failure("no-mending");
            }
        }

        List<RepairTarget> targets = new ArrayList<>();
        int totalDamage = 0;
        int totalXpNeeded = 0;

        for (ItemStack it : rawItems) {
            if (!ItemUtil.isRepairable(it)) continue;
            int dmg = ItemUtil.getDamage(it);
            if (dmg <= 0) continue;
            int cost = calculateItemCost(it, mendingBypass);
            if (cost >= 0) {
                targets.add(new RepairTarget(it, dmg, cost));
                totalDamage += dmg;
                totalXpNeeded += cost;
            }
        }

        if (targets.isEmpty()) {
            return RepairResult.failure("no-damage-target");
        }

        int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
        String primaryName = targets.size() == 1 ? ItemUtil.getFriendlyName(targets.get(0).item) : (targets.size() + " items");

        // Free repair path
        if (costBypass) {
            int restoredDurability = 0;
            for (RepairTarget target : targets) {
                restoredDurability += ItemUtil.repair(target.item, target.damage);
            }
            totalRepairs.addAndGet(targets.size());
            if (!bypassCooldown) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            }
            return RepairResult.successful(targets.size(), restoredDurability, 0, currentXp, true, primaryName);
        }

        // Full affordable repair path
        if (currentXp >= totalXpNeeded) {
            ExperienceUtil.deductExperience(player, totalXpNeeded);
            int restoredDurability = 0;
            for (RepairTarget target : targets) {
                restoredDurability += ItemUtil.repair(target.item, target.damage);
            }
            totalRepairs.addAndGet(targets.size());
            totalXpSpent.addAndGet(totalXpNeeded);
            if (!bypassCooldown) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            }
            int remaining = ExperienceUtil.getPlayerTotalExperience(player);
            return RepairResult.successful(targets.size(), restoredDurability, totalXpNeeded, remaining, false, primaryName);
        }

        // Insufficient XP path
        if (!config.isAllowPartialRepair()) {
            return RepairResult.failure("insufficient-xp");
        }

        if (currentXp < config.getMinXpPerRepair()) {
            return RepairResult.failure("insufficient-xp");
        }

        // Partial repair: distribute available XP across damaged items
        int availablePool = currentXp;
        int spentTotal = 0;
        int restoredTotal = 0;
        int itemsTouched = 0;

        for (RepairTarget target : targets) {
            if (availablePool < config.getMinXpPerRepair()) {
                break;
            }
            if (availablePool >= target.xpCost) {
                // Fully repair this target
                availablePool -= target.xpCost;
                spentTotal += target.xpCost;
                restoredTotal += ItemUtil.repair(target.item, target.damage);
                itemsTouched++;
            } else {
                // Partially repair this target with remaining XP
                boolean hasMending = ItemUtil.hasMending(target.item);
                double multiplier = hasMending ? 1.0 : config.getNonMendingMultiplier();
                int affordableDurability = ExperienceCalculator.calculateAffordableDurability(
                        availablePool,
                        config.getDurabilityPerXp(),
                        multiplier
                );

                if (affordableDurability > 0) {
                    int restored = ItemUtil.repair(target.item, affordableDurability);
                    int actualSpent = ExperienceCalculator.calculateSpentXp(
                            restored,
                            config.getDurabilityPerXp(),
                            multiplier
                    );
                    actualSpent = Math.min(actualSpent, availablePool);
                    spentTotal += actualSpent;
                    restoredTotal += restored;
                    availablePool -= actualSpent;
                    itemsTouched++;
                }
                break;
            }
        }

        if (spentTotal > 0) {
            ExperienceUtil.deductExperience(player, spentTotal);
            totalRepairs.addAndGet(itemsTouched);
            totalXpSpent.addAndGet(spentTotal);
            if (!bypassCooldown) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            }
            int remaining = ExperienceUtil.getPlayerTotalExperience(player);
            return RepairResult.partiallySuccessful(itemsTouched, restoredTotal, spentTotal, remaining, false, primaryName);
        }

        return RepairResult.failure("insufficient-xp");
    }

    public long getRemainingCooldownSeconds(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, config.getCooldownSeconds() - elapsed);
    }

    public boolean toggleAdminBypass(UUID uuid) {
        if (adminBypassUsers.contains(uuid)) {
            adminBypassUsers.remove(uuid);
            return false;
        } else {
            adminBypassUsers.add(uuid);
            return true;
        }
    }

    public boolean hasAdminBypass(UUID uuid) {
        return adminBypassUsers.contains(uuid);
    }

    public int getTotalRepairs() {
        return totalRepairs.get();
    }

    public long getTotalXpSpent() {
        return totalXpSpent.get();
    }

    private record RepairTarget(ItemStack item, int damage, int xpCost) {
    }
}
