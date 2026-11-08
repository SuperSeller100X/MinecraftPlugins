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
    /** Current unit price, or -1 for a stale/invalid choice. */
    public long unitPrice(PriceTable table) {
        PriceTable.Entry entry = table.get(material);
        if (entry == null || entry.buyCents() <= 0) return -1;
        if (material.equals("ENCHANTED_BOOK")) {
            ChoiceCatalog.BookOption option = ChoiceCatalog.book(choice);
            return option == null || level < 1 || level > option.maxLevel() ? -1 : ChoiceCatalog.bookPriceCents(option, level);
        }
        if (SurvivalItems.needsChoice(material)) {
            ChoiceCatalog.PotionOption option = ChoiceCatalog.potion(choice);
            if (option == null) return -1;
            if (material.equals("TIPPED_ARROW")) return (ChoiceCatalog.arrowPackCents(option) + 7) / 8;
            if (material.equals("SPLASH_POTION")) return option.priceCents() * 11 / 10;
            if (material.equals("LINGERING_POTION")) return option.priceCents() * 13 / 10;
            return option.priceCents();
        }
        return choice.isEmpty() ? entry.buyCents() : -1;
    }
}
