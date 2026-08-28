package dev.superseller.easymending.gui;

import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.ItemUtil;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and refreshes the interactive EasyMending GUI.
 */
public final class EasyMendingGui {

    public static final int SLOT_PROFILE = 4;
    public static final int SLOT_HAND = 10;
    public static final int SLOT_OFFHAND = 12;
    public static final int SLOT_ARMOR = 14;
    public static final int SLOT_ALL = 16;
    public static final int SLOT_CLOSE = 22;
    public static final int SLOT_INFO = 26;

    private final PluginConfig config;
    private final RepairService repairService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public EasyMendingGui(PluginConfig config, RepairService repairService) {
        this.config = config;
        this.repairService = repairService;
    }

    /**
     * Opens the repair menu for a player.
     *
     * @param player player to show GUI to
     */
    public void open(Player player) {
        if (player == null || !player.isOnline()) return;

        EasyMendingHolder holder = new EasyMendingHolder();
        Component titleComponent = miniMessage.deserialize(config.getGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, 27, titleComponent);
        holder.setInventory(inv);

        populate(player, inv);
        player.openInventory(inv);
    }

    /**
     * Re-renders the contents of an open EasyMending inventory.
     *
     * @param player viewer
     * @param inv inventory to refresh
     */
    public void refresh(Player player, Inventory inv) {
        if (player == null || inv == null) return;
        populate(player, inv);
    }

    private void populate(Player player, Inventory inv) {
        inv.clear();

        // 1. Fill borders and empty slots
        ItemStack filler = createItem(config.getGuiFillMaterial(), "<gray> </gray>");
        ItemStack border = createItem(config.getGuiBorderMaterial(), "<gray> </gray>");

        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            } else {
                inv.setItem(i, filler);
            }
        }

        // 2. Profile Slot (Slot 4)
        int totalXp = ExperienceUtil.getPlayerTotalExperience(player);
        int level = player.getLevel();
        RepairEstimate allEstimate = repairService.estimate(player, RepairScope.ALL);

        List<String> profileLore = List.of(
                "<gray>Current Level: <gold><b>" + level + "</b></gold></gray>",
                "<gray>Total Experience: <gold><b>" + totalXp + " XP</b></gold></gray>",
                "<gray>Damaged Items: <yellow>" + allEstimate.eligibleItemsCount() + "</yellow></gray>",
                "<gray>Total Missing Durability: <aqua>" + allEstimate.totalMissingDurability() + "</aqua></gray>",
                "",
                "<gray>Durability per XP: <aqua>" + config.getDurabilityPerXp() + "</aqua></gray>",
                "<gray>Requires Mending: " + (config.isRequireMending() ? "<green>Yes</green>" : "<yellow>No</yellow>") + "</gray>"
        );
        inv.setItem(SLOT_PROFILE, createItem(Material.EXPERIENCE_BOTTLE,
                "<gradient:#4facfe:#00f2fe><b>Player Experience Profile</b></gradient>", profileLore));

        // 3. Hand Slot (Slot 10)
        ItemStack handItem = player.getInventory().getItemInMainHand();
        inv.setItem(SLOT_HAND, createHandButton(player, handItem, "Main Hand", RepairScope.HAND));

        // 4. Offhand Slot (Slot 12)
        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        inv.setItem(SLOT_OFFHAND, createHandButton(player, offhandItem, "Offhand", RepairScope.OFFHAND));

        // 5. Armor Slot (Slot 14)
        RepairEstimate armorEst = repairService.estimate(player, RepairScope.ARMOR);
        List<String> armorLore = new ArrayList<>();
        armorLore.add("<gray>Repair all currently equipped armor.</gray>");
        armorLore.add("");
        armorLore.add("<gray>Damaged Pieces: <yellow>" + armorEst.eligibleItemsCount() + "</yellow></gray>");
        armorLore.add("<gray>Missing Durability: <aqua>" + armorEst.totalMissingDurability() + "</aqua></gray>");
        armorLore.add("<gray>Repair Cost: <gold>" + armorEst.totalXpCost() + " XP</gold></gray>");
        armorLore.add("");
        if (!armorEst.hasRepairableItems()) {
            armorLore.add("<green>✔ Armor is already fully repaired!</green>");
        } else if (armorEst.canAffordFull()) {
            armorLore.add("<green><b>▶ Click to Repair Armor</b></green>");
        } else if (armorEst.canAffordPartial() && config.isAllowPartialRepair()) {
            armorLore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
        } else {
            armorLore.add("<red>✘ Insufficient XP to repair</red>");
        }
        inv.setItem(SLOT_ARMOR, createItem(Material.DIAMOND_CHESTPLATE,
                "<gradient:#4facfe:#00f2fe><b>Repair Equipped Armor</b></gradient>", armorLore));

        // 6. All Inventory Slot (Slot 16)
        List<String> allLore = new ArrayList<>();
        allLore.add("<gray>Repair all eligible damaged items in your inventory.</gray>");
        allLore.add("");
        allLore.add("<gray>Damaged Items: <yellow>" + allEstimate.eligibleItemsCount() + "</yellow></gray>");
        allLore.add("<gray>Total Missing Durability: <aqua>" + allEstimate.totalMissingDurability() + "</aqua></gray>");
        allLore.add("<gray>Total Repair Cost: <gold>" + allEstimate.totalXpCost() + " XP</gold></gray>");
        allLore.add("");
        if (!allEstimate.hasRepairableItems()) {
            allLore.add("<green>✔ All items are already fully repaired!</green>");
        } else if (allEstimate.canAffordFull()) {
            allLore.add("<green><b>▶ Click to Repair All Items</b></green>");
        } else if (allEstimate.canAffordPartial() && config.isAllowPartialRepair()) {
            allLore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
        } else {
            allLore.add("<red>✘ Insufficient XP to repair</red>");
        }
        inv.setItem(SLOT_ALL, createItem(Material.NETHER_STAR,
                "<gradient:#4facfe:#00f2fe><b>Repair Entire Inventory</b></gradient>", allLore));

        // 7. Close Slot (Slot 22)
        inv.setItem(SLOT_CLOSE, createItem(Material.BARRIER, "<red><b>Close Menu</b></red>", List.of("<gray>Click to exit the repair interface.</gray>")));

        // 8. Info Slot (Slot 26)
        List<String> infoLore = List.of(
                "<gray>EasyMending lets you restore tools,</gray>",
                "<gray>weapons, and armor with existing XP.</gray>",
                "",
                "<gray>• Vanilla rate: <white>1 XP = 2 Durability</white></gray>",
                "<gray>• Partial repairs are supported.</gray>",
                "<gray>• Chat commands: <aqua>/em hand</aqua>, <aqua>/em all</aqua></gray>"
        );
        inv.setItem(SLOT_INFO, createItem(Material.BOOK, "<aqua><b>EasyMending Help</b></aqua>", infoLore));
    }

    private ItemStack createHandButton(Player player, ItemStack item, String label, RepairScope scope) {
        boolean mendingBypass = player.hasPermission("easymending.bypass.mending") || repairService.hasAdminBypass(player.getUniqueId());
        Material iconMat = Material.ANVIL;
        List<String> lore = new ArrayList<>();

        if (item == null || item.getType().isAir()) {
            lore.add("<gray>No item held in " + label.toLowerCase() + ".</gray>");
            return createItem(Material.GRAY_DYE, "<dark_gray>Empty " + label + "</dark_gray>", lore);
        }

        iconMat = item.getType();
        String itemName = ItemUtil.getFriendlyName(item);
        boolean repairable = ItemUtil.isRepairable(item);
        int damage = ItemUtil.getDamage(item);
        int max = ItemUtil.getMaxDurability(item);
        boolean hasMending = ItemUtil.hasMending(item);

        lore.add("<gray>Item: <white>" + itemName + "</white></gray>");

        if (!repairable) {
            lore.add("<red>This item cannot take damage or be repaired.</red>");
            return createItem(iconMat, "<gray>" + label + ": " + itemName + "</gray>", lore);
        }

        if (damage <= 0) {
            lore.add("<gray>Durability: <white>" + max + "/" + max + "</white></gray>");
            lore.add("<green>✔ Already at maximum durability!</green>");
            return createItem(iconMat, "<green>" + label + ": Full Durability</green>", lore);
        }

        if (config.isRequireMending() && !hasMending && !mendingBypass) {
            lore.add("<red>✘ Item does not have Mending!</red>");
            return createItem(iconMat, "<red>" + label + ": Missing Mending</red>", lore);
        }

        int cost = repairService.calculateItemCost(item, mendingBypass);
        int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
        int remainingDurability = max - damage;
        int percent = (int) Math.round(((double) remainingDurability / max) * 100.0);

        lore.add("<gray>Durability: <white>" + remainingDurability + " / " + max + " (" + percent + "% intact)</white></gray>");
        lore.add("<gray>Missing Durability: <aqua>" + damage + "</aqua></gray>");
        lore.add("<gray>Repair Cost: <gold>" + cost + " XP</gold></gray>");
        lore.add("");

        if (currentXp >= cost || player.hasPermission("easymending.bypass.cost") || repairService.hasAdminBypass(player.getUniqueId())) {
            lore.add("<green><b>▶ Click to Repair " + label + "</b></green>");
        } else if (currentXp >= config.getMinXpPerRepair() && config.isAllowPartialRepair()) {
            lore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
        } else {
            lore.add("<red>✘ Insufficient XP (" + currentXp + "/" + cost + " XP)</red>");
        }

        return createItem(iconMat, "<gradient:#4facfe:#00f2fe><b>Repair " + label + "</b></gradient>", lore);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(miniMessage.deserialize(name));
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> compLore = new ArrayList<>();
                for (String line : loreLines) {
                    compLore.add(miniMessage.deserialize(line));
                }
                meta.lore(compLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }
}
