package dev.superseller.playervault.integration;

import java.util.Locale;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;

import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.gui.GuiLayout;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.service.VaultService;
import dev.superseller.playervault.util.Numbers;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI expansion exposing vault statistics as {@code %playervault_*%}.
 *
 * <p>Registered only when PlaceholderAPI is installed; the plugin never hard
 * depends on it. {@link #persist()} is {@code true} so the placeholders survive a
 * PlaceholderAPI reload.
 *
 * <table>
 *   <caption>Placeholders</caption>
 *   <tr><td>{@code %playervault_rows%}</td><td>rows owned</td></tr>
 *   <tr><td>{@code %playervault_slots%}</td><td>total slots</td></tr>
 *   <tr><td>{@code %playervault_used%}</td><td>slots in use</td></tr>
 *   <tr><td>{@code %playervault_free%}</td><td>slots still empty</td></tr>
 *   <tr><td>{@code %playervault_pages%}</td><td>GUI pages</td></tr>
 *   <tr><td>{@code %playervault_next_price%}</td><td>price of the next row</td></tr>
 *   <tr><td>{@code %playervault_spent%}</td><td>money spent on rows so far</td></tr>
 *   <tr><td>{@code %playervault_max_rows%}</td><td>configured row limit, {@code -1} when unlimited</td></tr>
 *   <tr><td>{@code %playervault_provider%}</td><td>hooked economy provider</td></tr>
 * </table>
 */
public final class VaultPlaceholders extends PlaceholderExpansion {

    private final VaultService service;
    private final Supplier<Settings> settings;
    private final String author;
    private final String version;

    public VaultPlaceholders(VaultService service, Supplier<Settings> settings, String author, String version) {
        this.service = service;
        this.settings = settings;
        this.author = author;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "playervault";
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("provider")) {
            return service.economy().isEnabled() ? service.economy().providerName() : "none";
        }
        if (key.equals("max_rows") || key.equals("maxrows")) {
            return String.valueOf(settings.get().vault().maxRows());
        }
        VaultData data = service.vaultById(player.getUniqueId());
        return switch (key) {
            case "rows", "size" -> String.valueOf(data.rows());
            case "slots", "capacity" -> String.valueOf(data.capacity());
            case "used" -> String.valueOf(data.usedSlots());
            case "free", "empty" -> String.valueOf(data.capacity() - data.usedSlots());
            case "pages" -> String.valueOf(
                    new GuiLayout(settings.get().gui().pageRows(), data.rows()).pageCount());
            case "next_price", "nextprice", "price" -> service.money(service.nextPrice(data));
            case "spent", "totalspent" -> service.money(data.totalSpent());
            case "purchased" -> String.valueOf(data.purchasedRows());
            case "percent" -> percent(data);
            default -> null;
        };
    }

    private String percent(VaultData data) {
        int capacity = data.capacity();
        if (capacity <= 0) {
            return "0";
        }
        double value = data.usedSlots() * 100.0d / capacity;
        return String.valueOf(Numbers.round(value, 1));
    }
}
