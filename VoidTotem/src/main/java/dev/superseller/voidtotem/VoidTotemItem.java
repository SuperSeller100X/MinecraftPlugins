package dev.superseller.voidtotem;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Builds the Void Totem item and recognises it again later. The totem is a
 * TOTEM_OF_UNDYING stamped with a custom item_model (the Blockbench model from
 * the resource pack) and a PersistentData marker so the listener can find and
 * consume exactly this totem - and no other (vanilla) totem.
 */
public final class VoidTotemItem {

    private final VoidTotemPlugin plugin;
    private final NamespacedKey marker;

    public VoidTotemItem(VoidTotemPlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "void-totem");
    }

    /** Creates {@code amount} Void Totems, each carrying the custom model. */
    public ItemStack create(int amount) {
        int stack = Math.max(1, Math.min(amount, Material.TOTEM_OF_UNDYING.getMaxStackSize()));
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING, stack);
        MiniMessage mm = MiniMessage.miniMessage();
        item.editMeta(meta -> {
            meta.displayName(mm.deserialize("<!italic><gradient:#7a00ff:#00e5ff>Void Totem</gradient>"));
            meta.lore(java.util.List.of(
                    mm.deserialize("<!italic><gray>A shard-forged charm that catches you"),
                    mm.deserialize("<!italic><gray>at the edge of the void and drags you back.</gray>"),
                    mm.deserialize("<!italic><dark_gray>Consumed once, then gone.</dark_gray>")));
            // Only this totem gets the custom Blockbench model.
            meta.setItemModel(plugin.config().itemModel());
            // Marker so the listener can find and consume exactly this item.
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** True only for a VoidTotemPlugin-issued Void Totem. */
    public boolean isVoidTotem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte value = meta.getPersistentDataContainer().get(marker, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public NamespacedKey markerKey() {
        return marker;
    }
}
