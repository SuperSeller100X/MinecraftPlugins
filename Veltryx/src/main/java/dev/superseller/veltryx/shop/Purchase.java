package dev.superseller.veltryx.shop;

/** A material and optional potion/enchantment choice; amounts belong to the GUI slot. */
public record Purchase(String material, String choice, int level) {
    public Purchase {
        choice = choice == null ? "" : choice;
    }
    public static Purchase of(String material) { return new Purchase(material, "", 0); }
    public String label() {
        if (choice.isEmpty()) return PriceTable.displayName(material);
        if (material.equals("ENCHANTED_BOOK")) {
            ChoiceCatalog.BookOption book = ChoiceCatalog.book(choice);
            return (book == null ? PriceTable.displayName(choice) : book.displayName()) + " " + level + " Book";
        }
        ChoiceCatalog.PotionOption potion = ChoiceCatalog.potion(choice);
        return (potion == null ? PriceTable.displayName(choice) : potion.displayName())
                + " " + PriceTable.displayName(material);
    }
    /** True only for the selected material and, when relevant, its selected variant. */
    public boolean matches(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || !stack.getType().name().equals(material) || stack.getAmount() <= 0) return false;
        if (material.equals("ENCHANTED_BOOK")) {
            if (ChoiceCatalog.book(choice) == null || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book)) return false;
            try {
                var stored = book.getStoredEnchants();
                if (stored.size() != 1) return false;
                var entry = stored.entrySet().iterator().next();
                var expected = org.bukkit.NamespacedKey.minecraft(choice.toLowerCase(java.util.Locale.ROOT));
                return expected.equals(entry.getKey().getKey()) && entry.getValue() == level;
            } catch (RuntimeException ex) { return false; }
        }
        if (SurvivalItems.needsChoice(material)) {
            org.bukkit.potion.PotionType type = ShopExtrasGui.resolvePotion(choice);
            if (type == null || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion)
                    || type != potion.getBasePotionType() || potion.hasCustomEffects()) return false;
            Float scale = stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.POTION_DURATION_SCALE);
            float effectiveScale = scale == null ? 1.0f : scale;
            return Float.compare(effectiveScale, (float) PotionDisplay.durationScale(material)) == 0;
        }
        return choice.isEmpty();
    }

    /** Current unit price, or -1 for a stale/invalid choice. */
    public long unitPrice(PriceTable table) {
        PriceTable.Entry entry = table.get(material);
        if (entry == null || entry.buyCents() <= 0) return -1;
        if (material.equals("ENCHANTED_BOOK")) {
            ChoiceCatalog.BookOption option = ChoiceCatalog.book(choice);
            return option == null || level < 1 || level > option.maxLevel() ? -1 : ChoiceCatalog.bookPriceCents(option, level);
        }
        if (SurvivalItems.needsChoice(material)) {
            if (ChoiceCatalog.potion(choice) == null) return -1;
            return VariantPricing.buyCents(table, material, choice);
        }
        return choice.isEmpty() ? entry.buyCents() : -1;
    }
}
