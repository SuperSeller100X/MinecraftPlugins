package com.giftsystem.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GiftGUICommand implements CommandExecutor {

    private final GiftSystemPlugin plugin;

    public GiftGUICommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("giftsystem.use")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        // Open the advanced gifting GUI
        openGiftCreationGUI(player);
        return true;
    }

    private void openGiftCreationGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6Create Gift");

        // Information item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§e§lHow to Gift");
            infoMeta.setLore(java.util.Arrays.asList(
                "§71. Place items in the",
                "§7   middle slots",
                "§72. Click the recipient",
                "§7   head to select them",
                "§73. Click the gift box to",
                "§7   send your gift!",
                "",
                "§eTip: You can add a message",
                "§eby clicking the book!"
            ));
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(0, infoItem);

        // Filler for top row
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 1; i < 9; i++) {
            gui.setItem(i, filler);
        }

        // Recipient selector (compass or player head placeholder)
        ItemStack recipientItem = new ItemStack(Material.COMPASS);
        ItemMeta recipientMeta = recipientItem.getItemMeta();
        if (recipientMeta != null) {
            recipientMeta.setDisplayName("§a§lSelect Recipient");
            recipientMeta.setLore(java.util.Arrays.asList(
                "§7Click to choose who to",
                "§7send the gift to!",
                "",
                "§eCurrent: §fNone Selected"
            ));
            recipientItem.setItemMeta(recipientMeta);
        }
        gui.setItem(9, recipientItem);

        // Message item
        ItemStack messageItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta messageMeta = messageItem.getItemMeta();
        if (messageMeta != null) {
            messageMeta.setDisplayName("§b§lAdd Message");
            messageMeta.setLore(java.util.Arrays.asList(
                "§7Click to add a personal",
                "§7message to your gift!",
                "",
                "§eCurrent: §fNo message"
            ));
            messageItem.setItemMeta(messageMeta);
        }
        gui.setItem(17, messageItem);

        // Send button
        ItemStack sendItem = new ItemStack(Material.CHEST);
        ItemMeta sendMeta = sendItem.getItemMeta();
        if (sendMeta != null) {
            sendMeta.setDisplayName("§a§lSend Gift");
            sendMeta.setLore(java.util.Arrays.asList(
                "§7Click to send your gift!",
                "",
                "§cRequires a recipient!"
            ));
            sendItem.setItemMeta(sendMeta);
        }
        gui.setItem(49, sendItem);

        // Fill bottom row with glass
        for (int i = 45; i < 54; i++) {
            if (i != 49) {
                gui.setItem(i, filler);
            }
        }

        // Middle area (slots 19-25, 28-34, 37-43) for items to gift
        // These are left empty for the player to place items

        player.openInventory(gui);
    }
}
