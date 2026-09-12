package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.provider.RegistrySnapshot;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The recipe viewer.
 *
 * <p>A shaped recipe is drawn as its real 3x3 grid; anything with a single input uses the
 * centre slot. Every recipe the server has for the same result is reachable through the page
 * controls, so an item with several recipes shows all of them instead of just the first.</p>
 */
public final class RecipeMenu extends WikiMenu {

    private final String resultKey;
    private final List<RegistrySnapshot.RecipeInfo> recipes;
    private int index;

    public RecipeMenu(MenuContext ctx, Player player, String resultKey, int index) {
        super(ctx, player);
        this.resultKey = resultKey;
        this.recipes = ctx.snapshot().recipesFor(resultKey);
        this.index = recipes.isEmpty() ? 0 : Math.max(0, Math.min(index, recipes.size() - 1));
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.RECIPE;
    }

    @Override
    protected Component title() {
        return Text.mini(ctx.gui().titles().recipe(), Map.of(
                "item", Text.prettify(resultKey),
                "result", Text.prettify(resultKey)));
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.RECIPE);
    }

    /** Recipe switching reuses the pagination controls: one recipe per "page". */
    @Override
    protected Pagination pagination() {
        return new Pagination(recipes.size(), 1);
    }

    @Override
    protected int page() {
        return index + 1;
    }

    @Override
    protected void goToPage(int target) {
        int next = Math.max(1, Math.min(Math.max(1, recipes.size()), target)) - 1;
        if (next == index) {
            return;
        }
        ctx.navigation().open(player, ctx.menus().recipe(player, resultKey, next));
    }

    @Override
    protected List<Crumb> crumbs() {
        return List.of(new Crumb(ctx.gui().item(GuiItemKey.HOME), Map.of("title", "Home"),
                () -> ctx.navigation().open(player, ctx.menus().home(player))));
    }

    @Override
    protected Crumb currentCrumb() {
        Material material = Material.matchMaterial(resultKey);
        return new Crumb(ctx.gui().item(GuiItemKey.RECIPE_INFO),
                Map.of("result", Text.prettify(resultKey)), null);
    }

    @Override
    protected void build() {
        var layout = ctx.gui().recipe();
        if (recipes.isEmpty()) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.RECIPE_NONE);
            int slot = firstContentSlot();
            put(slot < 0 ? 0 : slot, GuiButton.display(
                    ItemFactory.create(spec, Map.of("result", Text.prettify(resultKey)))));
            return;
        }
        RegistrySnapshot.RecipeInfo recipe = recipes.get(index);

        // Empty grid cells get the configured blank so the grid reads as a grid.
        ItemSpec empty = ctx.gui().item(GuiItemKey.RECIPE_EMPTY_SLOT);
        for (Integer slot : layout.gridSlots()) {
            put(slot, GuiButton.display(ItemFactory.create(empty, Map.of())));
        }

        List<List<String>> grid = recipe.grid();
        boolean shaped = grid.size() > 1;
        if (shaped) {
            int cell = 0;
            for (List<String> row : grid) {
                for (String materialKey : row) {
                    if (cell >= layout.gridSlots().size()) {
                        break;
                    }
                    placeIngredient(layout.gridSlots().get(cell++), materialKey);
                }
            }
        } else {
            List<String> only = grid.isEmpty() ? List.of() : grid.get(0);
            if (only.size() == 1) {
                placeIngredient(layout.singleSlot(), only.get(0));
            } else {
                int cell = 0;
                for (String materialKey : only) {
                    if (cell >= layout.gridSlots().size()) {
                        break;
                    }
                    placeIngredient(layout.gridSlots().get(cell++), materialKey);
                }
            }
        }

        ItemSpec arrow = ctx.gui().item(GuiItemKey.RECIPE_ARROW);
        put(layout.arrowSlot(), GuiButton.display(ItemFactory.create(arrow,
                Map.of("result", Text.prettify(resultKey)))));

        Material resultMaterial = Material.matchMaterial(resultKey);
        put(layout.resultSlot(), GuiButton.display(ItemFactory.plain(
                resultMaterial == null ? Material.PAPER : resultMaterial)));

        ItemSpec info = ctx.gui().item(GuiItemKey.RECIPE_INFO);
        put(layout.infoSlot(), GuiButton.display(ItemFactory.create(info, Map.of(
                "result", Text.prettify(resultKey),
                "type", Text.prettify(recipe.typeKey()),
                "index", Integer.toString(index + 1),
                "count", Integer.toString(recipes.size())))));
    }

    private void placeIngredient(int slot, String materialKey) {
        if (materialKey == null || materialKey.isEmpty()) {
            return;
        }
        Material material = Material.matchMaterial(materialKey);
        if (material == null) {
            return;
        }
        ItemSpec spec = ctx.gui().item(GuiItemKey.RECIPE_INGREDIENT);
        put(slot, GuiButton.display(ItemFactory.create(material,
                new dev.superseller.minecraftwiki.config.TextSpec(spec.name(), spec.lore()),
                Map.of("label", Text.prettify(materialKey)), spec.glowing())));
    }

    private int firstContentSlot() {
        List<Integer> slots = ctx.gui().contentSlots();
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    public String resultKey() {
        return resultKey;
    }

    public int recipeIndex() {
        return index;
    }

    /** Recipe types available for this result, used by the admin command. */
    public List<String> types() {
        List<String> out = new ArrayList<>(recipes.size());
        for (RegistrySnapshot.RecipeInfo recipe : recipes) {
            out.add(recipe.typeKey());
        }
        return out;
    }
}
