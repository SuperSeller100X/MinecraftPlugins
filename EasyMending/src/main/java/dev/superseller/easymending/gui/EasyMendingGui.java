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
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Interactive chest repair station featuring a drop-in repair slot,
 * real-time XP calculation, and quick-repair action buttons.
 */
public final class EasyMendingGui {

    public static final int GUI_SIZE = 45;

    // Header
    public static final int SLOT_PROFILE = 4;

    // Interactive Anvil Station (Row 2)
    public static final int SLOT_STATION_GUIDE = 19;
    public static final int SLOT_ITEM_INPUT = 20;
    public static final int SLOT_ARROW = 21;
    public static final int SLOT_ANVIL_BUTTON = 22;

    // Quick Action Buttons (Row 3)
    public static final int SLOT_HAND = 29;
    public static final int SLOT_OFFHAND = 30;
    public static final int SLOT_ARMOR = 31;
    public static final int SLOT_HOTBAR = 32;
    public static final int SLOT_ALL = 33;

    // Footer (Row 4)
    public static final int SLOT_INFO = 36;
    public static final int SLOT_CLOSE = 40;
    public static final int SLOT_REFRESH = 44;

    private final PluginConfig config;
    private final RepairService repairService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public EasyMendingGui(PluginConfig config, RepairService repairService) {
        this.config = config;
        this.repairService = repairService;
    }

    /**
     * Opens the repair station GUI for the player.
     *
     * @param player player to show GUI to
     */
    public void open(Player player) {
        if (player == null || !player.isOnline()) return;

        EasyMendingHolder holder = new EasyMendingHolder();
        Component titleComponent = miniMessage.deserialize(config.getGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, titleComponent);
        holder.setInventory(inv);

        setupStaticSlots(player, inv);
        updateAnvilButton(player, inv);

        player.openInventory(inv);
    }

    /**
     * Refreshes dynamic status icons, quick repair buttons, and anvil evaluation.
     * Preserves the item in the input slot if present.
     *
     * @param player viewing player
     * @param inv open inventory
     */
    public void refresh(Player player, Inventory inv) {
        if (player == null || inv == null) return;
        setupStaticSlots(player, inv);
        updateAnvilButton(player, inv);
    }

    /**
     * Sets up the borders, profile status, and quick repair buttons without altering the input slot.
     */
    public void setupStaticSlots(Player player, Inventory inv) {
        ItemStack filler = createItem(config.getGuiFillMaterial(), "<gray> </gray>");
        ItemStack border = createItem(config.getGuiBorderMaterial(), "<gray> </gray>");

        // Fill non-interactive slots
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i == SLOT_ITEM_INPUT) {
                continue; // Do not overwrite user's placed item
            }
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            } else {
                inv.setItem(i, filler);
            }
        }

        // Profile Slot (Slot 4)
        int totalXp = ExperienceUtil.getPlayerTotalExperience(player);
        int level = player.getLevel();
        RepairEstimate allEstimate = repairService.estimate(player, RepairScope.ALL);

        List<String> profileLore = List.of(
                "<gray>Current Level: <gold><b>" + level + "</b></gold></gray>",
                "<gray>Total Experience: <gold><b>" + totalXp + " XP</b></gold></gray>",
                "<gray>Damaged Items in Inventory: <yellow>" + allEstimate.eligibleItemsCount() + "</yellow></gray>",
                "<gray>Missing Durability Total: <aqua>" + allEstimate.totalMissingDurability() + "</aqua></gray>",
                "",
                "<gray>Repair Rate: <aqua>1 XP = " + config.getDurabilityPerXp() + " Durability</aqua></gray>",
                "<gray>Requires Mending: " + (config.isRequireMending() ? "<green>Yes</green>" : "<yellow>No</yellow>") + "</gray>",
                "<gray>Bypass Active: " + (repairService.hasCostBypass(player) ? "<green>Yes (Free)</green>" : "<red>No (Standard XP)</red>") + "</gray>"
        );
        inv.setItem(SLOT_PROFILE, createItem(Material.EXPERIENCE_BOTTLE,
                "<gradient:#4facfe:#00f2fe><b>Your Experience Profile</b></gradient>", profileLore));

        // Anvil Station Guide (Slot 19)
        List<String> guideLore = List.of(
                "<gray>Drop any damaged tool, weapon,</gray>",
                "<gray>or armor piece into the slot</gray>",
                "<gray>directly to the right ➔</gray>",
                "",
                "<yellow>Then click the Anvil on the right</yellow>",
                "<yellow>to restore its durability instantly!</yellow>"
        );
        inv.setItem(SLOT_STATION_GUIDE, createItem(Material.SMITHING_TABLE,
                "<aqua><b>Anvil Repair Station</b></aqua>", guideLore));

        // Arrow Indicator (Slot 21)
        inv.setItem(SLOT_ARROW, createItem(Material.SPECTRAL_ARROW,
                "<yellow><b>➔ Click Anvil to Repair ➔</b></yellow>",
                List.of("<gray>Click the anvil button to process.</gray>")));

        // Quick Action Buttons (Row 3)
        // Hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        inv.setItem(SLOT_HAND, createQuickButton(player, mainHand, "Main Hand", RepairScope.HAND, Material.DIAMOND_SWORD));

        // Offhand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        inv.setItem(SLOT_OFFHAND, createQuickButton(player, offHand, "Offhand", RepairScope.OFFHAND, Material.SHIELD));

        // Armor
        RepairEstimate armorEst = repairService.estimate(player, RepairScope.ARMOR);
        inv.setItem(SLOT_ARMOR, createScopeButton("Equipped Armor", armorEst, Material.DIAMOND_CHESTPLATE));

        // Hotbar
        RepairEstimate hotbarEst = repairService.estimate(player, RepairScope.HOTBAR);
        inv.setItem(SLOT_HOTBAR, createScopeButton("Hotbar Items", hotbarEst, Material.IRON_PICKAXE));

        // All
        inv.setItem(SLOT_ALL, createScopeButton("Entire Inventory", allEstimate, Material.NETHER_STAR));

        // Footer Actions (Row 4)
        inv.setItem(SLOT_INFO, createItem(Material.BOOK, "<aqua><b>EasyMending Info</b></aqua>", List.of(
                "<gray>• Restore Mending equipment with XP.</gray>",
                "<gray>• 1 XP restores " + config.getDurabilityPerXp() + " durability.</gray>",
                "<gray>• Partial repairs are supported.</gray>",
                "<gray>• Chat command: <aqua>/em hand</aqua> or <aqua>/em all</aqua></gray>"
        )));

        inv.setItem(SLOT_CLOSE, createItem(Material.BARRIER, "<red><b>Close Menu</b></red>",
                List.of("<gray>Click to exit the repair station.</gray>", "<dark_gray>Items in slot 20 are safely returned.</dark_gray>")));

        inv.setItem(SLOT_REFRESH, createItem(Material.CLOCK, "<yellow><b>Refresh Menu</b></yellow>",
                List.of("<gray>Recalculate costs and durability.</gray>")));
    }

    /**
     * Updates the Anvil Button (Slot 22) based on the contents of the Input Slot (Slot 20).
     *
     * @param player viewing player
     * @param inv open inventory
     */
    public void updateAnvilButton(Player player, Inventory inv) {
        ItemStack input = inv.getItem(SLOT_ITEM_INPUT);

        if (input == null || input.getType().isAir()) {
            List<String> emptyLore = List.of(
                    "<gray>No item in repair slot.</gray>",
                    "",
                    "<yellow>Place any damaged tool, weapon,</yellow>",
                    "<yellow>or armor into the left slot.</yellow>"
            );
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.ANVIL,
                    "<gray><b>Repair Button (Empty)</b></gray>", emptyLore));
            return;
        }

        String itemName = ItemUtil.getFriendlyName(input);
        boolean repairable = ItemUtil.isRepairable(input);
        int damage = ItemUtil.getDamage(input);
        int max = ItemUtil.getMaxDurability(input);
        boolean hasMending = ItemUtil.hasMending(input);
        boolean mendingBypass = repairService.hasMendingBypass(player);
        boolean costBypass = repairService.hasCostBypass(player);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Item: <white>" + itemName + "</white></gray>");

        if (!repairable) {
            lore.add("<red>This item cannot take damage or be repaired.</red>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.BARRIER,
                    "<red><b>Cannot Repair Item</b></red>", lore));
            return;
        }

        if (damage <= 0) {
            lore.add("<gray>Durability: <white>" + max + " / " + max + "</white></gray>");
            lore.add("<green>✔ Already at maximum durability!</green>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.CHIPPED_ANVIL,
                    "<green><b>Full Durability</b></green>", lore));
            return;
        }

        if (config.isRequireMending() && !hasMending && !mendingBypass) {
            lore.add("<red>✘ Item does not have the Mending enchantment!</red>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.BARRIER,
                    "<red><b>Missing Mending</b></red>", lore));
            return;
        }

        int cost = repairService.calculateItemCost(input, mendingBypass);
        int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
        int remaining = max - damage;
        int percent = (int) Math.round(((double) remaining / max) * 100.0);

        lore.add("<gray>Durability: <white>" + remaining + " / " + max + " (" + percent + "% intact)</white></gray>");
        lore.add("<gray>Missing Durability: <aqua>" + damage + "</aqua></gray>");

        if (costBypass) {
            lore.add("<gray>Repair Cost: <gold>FREE (Bypass)</gold></gray>");
            lore.add("");
            lore.add("<green><b>▶ Click to Repair Free!</b></green>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.DAMAGED_ANVIL,
                    "<gradient:#4facfe:#00f2fe><b>▶ Click to Repair Item</b></gradient>", lore));
            return;
        }

        lore.add("<gray>Repair Cost: <gold>" + cost + " XP</gold></gray>");
        lore.add("<gray>Your Experience: <gold>" + currentXp + " XP</gold></gray>");
        lore.add("");

        if (currentXp >= cost) {
            lore.add("<green>✔ You have enough XP!</green>");
            lore.add("<green><b>▶ Click to Repair Now</b></green>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.ANVIL,
                    "<gradient:#4facfe:#00f2fe><b>▶ Click to Repair Item</b></gradient>", lore));
        } else if (currentXp >= config.getMinXpPerRepair() && config.isAllowPartialRepair()) {
            double multiplier = hasMending ? 1.0 : config.getNonMendingMultiplier();
            int affordableDurability = (int) Math.floor((currentXp * config.getDurabilityPerXp()) / multiplier);
            lore.add("<yellow>Notice: Insufficient XP for full repair.</yellow>");
            lore.add("<yellow>Can restore: <aqua>+" + affordableDurability + " durability</aqua></yellow>");
            lore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.CHIPPED_ANVIL,
                    "<yellow><b>▶ Partial Repair Available</b></yellow>", lore));
        } else {
            lore.add("<red>✘ Insufficient XP (" + currentXp + "/" + cost + " XP)</red>");
            inv.setItem(SLOT_ANVIL_BUTTON, createItem(Material.BARRIER,
                    "<red><b>Not Enough XP</b></red>", lore));
        }
    }

    private ItemStack createQuickButton(Player player, ItemStack item, String label, RepairScope scope, Material defaultIcon) {
        Material iconMat = defaultIcon;
        List<String> lore = new ArrayList<>();

        if (item == null || item.getType().isAir()) {
            lore.add("<gray>No item held in " + label.toLowerCase() + ".</gray>");
            return createItem(defaultIcon, "<gray>" + label + ": Empty</gray>", lore);
        }

        iconMat = item.getType();
        String itemName = ItemUtil.getFriendlyName(item);
        boolean repairable = ItemUtil.isRepairable(item);
        int damage = ItemUtil.getDamage(item);
        int max = ItemUtil.getMaxDurability(item);
        boolean hasMending = ItemUtil.hasMending(item);
        boolean mendingBypass = repairService.hasMendingBypass(player);
        boolean costBypass = repairService.hasCostBypass(player);

        lore.add("<gray>Item: <white>" + itemName + "</white></gray>");

        if (!repairable) {
            lore.add("<red>Cannot take damage or be repaired.</red>");
            return createItem(iconMat, "<gray>" + label + "</gray>", lore);
        }

        if (damage <= 0) {
            lore.add("<green>✔ Already at full durability!</green>");
            return createItem(iconMat, "<green>" + label + ": Full</green>", lore);
        }

        if (config.isRequireMending() && !hasMending && !mendingBypass) {
            lore.add("<red>✘ Missing Mending enchantment!</red>");
            return createItem(iconMat, "<red>" + label + ": No Mending</red>", lore);
        }

        int cost = repairService.calculateItemCost(item, mendingBypass);
        int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
        int remaining = max - damage;
        int percent = (int) Math.round(((double) remaining / max) * 100.0);

        lore.add("<gray>Durability: <white>" + remaining + " / " + max + " (" + percent + "%)</white></gray>");
        lore.add("<gray>Missing: <aqua>" + damage + "</aqua></gray>");

        if (costBypass) {
            lore.add("<gray>Cost: <gold>FREE (Bypass)</gold></gray>");
            lore.add("<green><b>▶ Click to Repair</b></green>");
        } else {
            lore.add("<gray>Cost: <gold>" + cost + " XP</gold></gray>");
            if (currentXp >= cost) {
                lore.add("<green><b>▶ Click to Repair</b></green>");
            } else if (currentXp >= config.getMinXpPerRepair() && config.isAllowPartialRepair()) {
                lore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
            } else {
                lore.add("<red>✘ Insufficient XP</red>");
            }
        }

        return createItem(iconMat, "<gradient:#4facfe:#00f2fe><b>Repair " + label + "</b></gradient>", lore);
    }

    private ItemStack createScopeButton(String label, RepairEstimate est, Material iconMat) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Quickly repair " + label.toLowerCase() + ".</gray>");
        lore.add("");
        lore.add("<gray>Damaged Items: <yellow>" + est.eligibleItemsCount() + "</yellow></gray>");
        lore.add("<gray>Missing Durability: <aqua>" + est.totalMissingDurability() + "</aqua></gray>");
        lore.add("<gray>Total Cost: <gold>" + est.totalXpCost() + " XP</gold></gray>");
        lore.add("");

        if (!est.hasRepairableItems()) {
            lore.add("<green>✔ Fully repaired!</green>");
        } else if (est.canAffordFull()) {
            lore.add("<green><b>▶ Click to Repair " + label + "</b></green>");
        } else if (est.canAffordPartial() && config.isAllowPartialRepair()) {
            lore.add("<yellow><b>▶ Click for Partial Repair</b></yellow>");
        } else {
            lore.add("<red>✘ Insufficient XP</red>");
        }

        return createItem(iconMat, "<gradient:#4facfe:#00f2fe><b>Repair " + label + "</b></gradient>", lore);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> compLore = new ArrayList<>();
                for (String line : loreLines) {
                    compLore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
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
