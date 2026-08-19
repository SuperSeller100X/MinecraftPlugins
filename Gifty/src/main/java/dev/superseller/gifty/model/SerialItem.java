package dev.superseller.gifty.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A version-safe, server-safe representation of an ItemStack that survives
 * restarts and Minecraft version upgrades. Only the commonly relevant data
 * is preserved (material, amount, display name, lore, item flags, enchants,
 * custom model data).
 */
public final class SerialItem {

    private final String material;
    private final int amount;
    private final String displayName;   // legacy §-coded, nullable
    private final List<String> lore;    // legacy §-coded, nullable
    private final List<String> flags;
    private final Map<String, Integer> enchants;
    private final Integer customModelData;

    public SerialItem(String material, int amount, String displayName, List<String> lore,
                      List<String> flags, Map<String, Integer> enchants, Integer customModelData) {
        this.material = material;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore;
        this.flags = flags;
        this.enchants = enchants;
        this.customModelData = customModelData;
    }

    public String material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public Map<String, Integer> enchants() {
        return enchants;
    }

    public Integer customModelData() {
        return customModelData;
    }

    // ------------------------------------------------------------ convert

    public ItemStack toItemStack() {
        Material mat = Material.valueOf(material);
        ItemStack stack = new ItemStack(mat, Math.max(1, Math.min(amount, 127)));
        if (displayName != null || lore != null || customModelData != null
                || (flags != null && !flags.isEmpty()) || (enchants != null && !enchants.isEmpty())) {
            ItemMeta meta = stack.getItemMeta();
            if (displayName != null) {
                meta.setDisplayName(displayName);
            }
            if (lore != null) {
                meta.setLore(lore);
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            if (flags != null) {
                for (String flag : flags) {
                    try {
                        meta.addItemFlags(ItemFlag.valueOf(flag));
                    } catch (IllegalArgumentException ignored) {
                        // flag no longer exists on this server version
                    }
                }
            }
            stack.setItemMeta(meta);
        }
        if (enchants != null) {
            for (Map.Entry<String, Integer> e : enchants.entrySet()) {
                try {
                    Enchantment ench = Enchantment.getByName(e.getKey());
                    if (ench != null) {
                        stack.addUnsafeEnchantment(ench, e.getValue());
                    }
                } catch (Throwable ignored) {
                    // unknown enchant on this server version
                }
            }
        }
        return stack;
    }

    public static SerialItem fromItemStack(ItemStack stack) {
        List<String> lore = null;
        String name = null;
        List<String> flags = new ArrayList<>();
        Map<String, Integer> enchants = new LinkedHashMap<>();
        Integer model = null;
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasDisplayName()) {
                name = meta.getDisplayName();
            }
            if (meta.hasLore()) {
                lore = meta.getLore();
            }
            if (meta.hasCustomModelData()) {
                model = meta.getCustomModelData();
            }
            for (Map.Entry<Enchantment, Integer> e : stack.getEnchantments().entrySet()) {
                enchants.put(e.getKey().getKey().getKey(), e.getValue());
            }
        }
        return new SerialItem(stack.getType().name(), stack.getAmount(), name, lore, flags, enchants, model);
    }

    // ------------------------------------------------------------ storage

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", material);
        map.put("amount", (long) amount);
        if (displayName != null) {
            map.put("display", displayName);
        }
        if (lore != null && !lore.isEmpty()) {
            map.put("lore", new ArrayList<Object>(lore));
        }
        if (flags != null && !flags.isEmpty()) {
            map.put("flags", new ArrayList<Object>(flags));
        }
        if (enchants != null && !enchants.isEmpty()) {
            Map<String, Object> en = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : enchants.entrySet()) {
                en.put(e.getKey(), (long) e.getValue());
            }
            map.put("enchants", en);
        }
        if (customModelData != null) {
            map.put("model", (long) customModelData);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static SerialItem fromMap(Map<String, Object> map) {
        String material = str(map.get("type"));
        int amount = intVal(map.get("amount"), 1);
        String display = strOrNull(map.get("display"));
        List<String> lore = strList(map.get("lore"));
        List<String> flags = strList(map.get("flags"));
        Map<String, Integer> enchants = new LinkedHashMap<>();
        Object en = map.get("enchants");
        if (en instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) en).entrySet()) {
                enchants.put(String.valueOf(e.getKey()), intVal(e.getValue(), 1));
            }
        }
        Integer model = map.containsKey("model") ? intVal(map.get("model"), 0) : null;
        return new SerialItem(material, amount, display, lore, flags, enchants, model);
    }

    private static String str(Object o) {
        return o == null ? "STONE" : String.valueOf(o);
    }

    private static String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                out.add(String.valueOf(item));
            }
        }
        return out.isEmpty() ? null : out;
    }
}
