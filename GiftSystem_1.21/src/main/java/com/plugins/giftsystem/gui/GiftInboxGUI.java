package com.plugins.giftsystem.gui;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.models.Gift;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GiftInboxGUI implements InventoryHolder {
    private final GiftSystem plugin;
    private Inventory inventory;
    private final Player viewer;
    private final List<Gift> gifts;
    private final int page;
    private final int itemsPerPage = 45;

    public GiftInboxGUI(GiftSystem plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public GiftInboxGUI(GiftSystem plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        this.gifts = plugin.getGiftManager().getPlayerGifts(viewer.getName());
        createInventory();
    }

    private void createInventory() {
        String title = MessageUtils.colorize(plugin.getConfig().getString("messages.gui-title-inbox", "&6Gift Inbox"));
        int rows = plugin.getConfig().getInt("gui.rows", 5);
        inventory = Bukkit.createInventory(this, rows * 9, title);
        
        fillBackground();
        addGiftItems();
        addNavigationItems();
    }

    private void fillBackground() {
        Material fillMaterial;
        try {
            fillMaterial = Material.valueOf(plugin.getConfig().getString("gui.fill-material", "GRAY_STAINED_GLASS_PANE"));
        } catch (IllegalArgumentException e) {
            fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }
        
        ItemStack glass = new ItemStack(fillMaterial);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void addGiftItems() {
        if (gifts.isEmpty()) {
            ItemStack noGifts = new ItemStack(Material.BARRIER);
            ItemMeta meta = noGifts.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.colorize("&c&lNo Gifts"));
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&7You don't have any pending gifts."));
                lore.add(MessageUtils.colorize("&7Ask friends to send you some!"));
                lore.add(MessageUtils.colorize(""));
                meta.setLore(lore);
                noGifts.setItemMeta(meta);
            }
            inventory.setItem(22, noGifts);
            return;
        }

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, gifts.size());
        
        if (startIndex >= gifts.size()) {
            // Page out of bounds, show first page
            new GiftInboxGUI(plugin, viewer, 0).open();
            return;
        }

        List<Gift> pageGifts = gifts.subList(startIndex, endIndex);
        int slot = 0;
        
        for (Gift gift : pageGifts) {
            if (slot >= 45) break;
            
            ItemStack giftItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = giftItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.colorize("&e&lGift from " + gift.getSenderName()));
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&7Items: &f" + gift.getItems().size()));
                
                if (!gift.getMessage().isEmpty()) {
                    lore.add(MessageUtils.colorize("&7Message: &f" + gift.getMessage()));
                }
                
                long timeSinceSent = System.currentTimeMillis() - gift.getCreatedAt();
                lore.add(MessageUtils.colorize("&7Sent: &e" + MessageUtils.formatTime(timeSinceSent) + " ago"));
                
                if (gift.getExpiresAt() > 0) {
                    long timeLeft = gift.getExpiresAt() - System.currentTimeMillis();
                    if (timeLeft > 0) {
                        lore.add(MessageUtils.colorize("&7Expires in: &e" + MessageUtils.formatTime(timeLeft)));
                    } else {
                        lore.add(MessageUtils.colorize("&7Status: &cExpired"));
                    }
                }
                
                lore.add(MessageUtils.colorize(""));
                lore.add(MessageUtils.colorize("&a&lClick to open!"));
                
                meta.setLore(lore);
                giftItem.setItemMeta(meta);
            }
            
            // Store gift ID in item for retrieval
            // Note: In a real implementation, use NMS or persistent data container
            
            inventory.setItem(slot, giftItem);
            slot++;
        }
    }

    private void addNavigationItems() {
        int totalPages = (int) Math.ceil((double) gifts.size() / itemsPerPage);
        
        if (totalPages > 1) {
            // Previous page
            if (page > 0) {
                Material prevMaterial;
                try {
                    prevMaterial = Material.valueOf(plugin.getConfig().getString("gui.previous-page-material", "ARROW"));
                } catch (IllegalArgumentException e) {
                    prevMaterial = Material.ARROW;
                }
                
                ItemStack prevItem = new ItemStack(prevMaterial);
                ItemMeta meta = prevItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(MessageUtils.colorize("&a&lPrevious Page"));
                    List<String> lore = new ArrayList<>();
                    lore.add(MessageUtils.colorize("&7Page " + (page + 1) + " of " + totalPages));
                    meta.setLore(lore);
                    prevItem.setItemMeta(meta);
                }
                inventory.setItem(48, prevItem);
            }
            
            // Next page
            if (page < totalPages - 1) {
                Material nextMaterial;
                try {
                    nextMaterial = Material.valueOf(plugin.getConfig().getString("gui.next-page-material", "ARROW"));
                } catch (IllegalArgumentException e) {
                    nextMaterial = Material.ARROW;
                }
                
                ItemStack nextItem = new ItemStack(nextMaterial);
                ItemMeta meta = nextItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(MessageUtils.colorize("&a&lNext Page"));
                    List<String> lore = new ArrayList<>();
                    lore.add(MessageUtils.colorize("&7Page " + (page + 1) + " of " + totalPages));
                    meta.setLore(lore);
                    nextItem.setItemMeta(meta);
                }
                inventory.setItem(50, nextItem);
            }
        }
        
        // Info item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize("&b&lGift Info"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&7Total Gifts: &e" + gifts.size()));
            lore.add(MessageUtils.colorize("&7Page: &e" + (page + 1) + "/" + totalPages(totalPages)));
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&eClick on a gift to open it!"));
            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }
        inventory.setItem(49, infoItem);
    }

    private String totalPages(int total) {
        return total > 0 ? String.valueOf(total) : "1";
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public int getPage() {
        return page;
    }

    public List<Gift> getGifts() {
        return gifts;
    }
}
