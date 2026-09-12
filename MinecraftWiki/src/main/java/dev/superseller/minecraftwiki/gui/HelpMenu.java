package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The in-GUI command reference.
 *
 * <p>Every line comes from {@code messages.yml}, so the help screen can never drift away from
 * what the commands actually do. Lines are trusted configuration, which is why they are
 * substituted into the template rather than escaped.</p>
 */
public final class HelpMenu extends WikiMenu {

    public HelpMenu(MenuContext ctx, Player player) {
        super(ctx, player);
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.HELP;
    }

    @Override
    protected Component title() {
        return ctx.messages().get(language(), "help-header", Map.of());
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.ARTICLE);
    }

    @Override
    protected List<Crumb> crumbs() {
        return List.of(new Crumb(ctx.gui().item(GuiItemKey.HOME), Map.of("title", "Home"),
                () -> ctx.navigation().open(player, ctx.menus().home(player))));
    }

    @Override
    protected Crumb currentCrumb() {
        return new Crumb(ctx.gui().item(GuiItemKey.HELP), Map.of(), null);
    }

    @Override
    protected void build() {
        List<Integer> slots = ctx.gui().contentSlots();
        if (slots.isEmpty()) {
            return;
        }
        var spec = ctx.gui().item(GuiItemKey.ARTICLE_TEXT);
        List<Component> lines = ctx.messages().getList(language(), "help-lines", Map.of());
        boolean admin = player.hasPermission(ctx.permissions().reload());
        if (admin) {
            List<Component> adminLines = ctx.messages().getList(language(), "help-admin-line", Map.of());
            lines = new java.util.ArrayList<>(lines);
            lines.addAll(adminLines);
        }
        List<Component> lore = new ArrayList<>();
        for (String configured : spec.lore()) {
            lore.add(Text.mini(configured, Map.of()));
        }
        int index = 0;
        for (Component line : lines) {
            if (index >= slots.size()) {
                break;
            }
            // The line is rendered by MiniMessage in Messages, so it is passed through as a
            // component instead of being re-parsed or escaped.
            put(slots.get(index++), GuiButton.display(
                    ItemFactory.create(spec.material(), line, lore, false)));
        }
    }
}
