package dev.superseller.veltryx.smoke;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.home.HomeService;
import dev.superseller.veltryx.home.HomesGui;
import dev.superseller.veltryx.model.HomeLocation;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.market.OrderGui;
import dev.superseller.veltryx.market.OrderService;
import dev.superseller.veltryx.market.OrderCommand;
import dev.superseller.veltryx.settings.PreferencesStore;
import dev.superseller.veltryx.settings.SettingsGui;
import dev.superseller.veltryx.settings.SettingsListener;
import dev.superseller.veltryx.shop.*;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.stats.ScoreboardService;
import dev.superseller.veltryx.stats.StatsService;
import dev.superseller.veltryx.stats.StatsListener;
import dev.superseller.veltryx.shards.*;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import dev.superseller.veltryx.util.YamlTextMerge;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.MapDecorations;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.StubComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.map.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.util.StructureSearchResult;

/** Stateful offline regression tests. These test doubles do not establish real-Paper compatibility. */
public final class Round12Test {
    private static int passed, failed;
    private static final Logger LOG = Logger.getLogger("round12-test");
    private static final Path RES = Path.of("src/main/resources");
    private static String shopText, recipes;
    private static PriceTable table;

    public static void main(String[] args) throws Exception {
        shopText = Files.readString(RES.resolve("shop.yml"));
        recipes = Files.readString(RES.resolve("shop-recipes.txt"));
        table = PriceTable.parse(shopText, recipes, name -> true);
        testCatalogAndSafety();
        testMigrationAndMessages();
        Path dir = Files.createTempDirectory("veltryx-round12-");
        try {
            testPersistence(dir);
            testInteraction(dir);
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
            }
        }
        System.out.println("Round 12: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }

    private static void testCatalogAndSafety() throws Exception {
        Set<String> expected = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of("tools/survival-items-26.3.txt")))
            if (!line.isBlank() && !line.startsWith("#")) expected.add(line.trim());
        Set<String> actual = new LinkedHashSet<>();
        int entries = 0, namedMaps = 0;
        for (PriceTable.Category c : table.categories()) {
            for (PriceTable.Entry e : c.items()) {
                entries++;
                check("catalog has no duplicate " + e.material(), actual.add(e.material()));
                check("catalog entry is allowed " + e.material(), SurvivalItems.allowed(e.material()));
                check("buy positive / sell below buy " + e.material(), e.buyCents() > 0 && e.sellCents() >= 0 && e.sellCents() < e.buyCents());
                if (SurvivalItems.noFlatSell(e.material())) eq("metadata-dependent sell disabled " + e.material(), e.sellCents(), 0L);
                if (e.material().endsWith("_MAP") && !e.material().equals("FILLED_MAP")) {
                    namedMaps++;
                    check("explorer destination exists " + e.material(), ExplorerMaps.target(e.material()) != null);
                }
            }
        }
        eq("catalog item count", entries, 1530);
        eq("catalog manifest exact", actual, expected);
        eq("catalog categories", table.categories().size(), 11);
        eq("all named maps handled", namedMaps, 16);
        for (String blocked : List.of("ELYTRA", "BEDROCK", "COMMAND_BLOCK", "DRAGON_EGG", "ZOMBIE_SPAWN_EGG", "PLAYER_HEAD"))
            check("forbidden item " + blocked, table.get(blocked) == null);
        eq("legacy chain rename", SurvivalItems.canonical("CHAIN"), "IRON_CHAIN");
        eq("legacy crimson wood rename", SurvivalItems.canonical("CRIMSON_WOOD"), "CRIMSON_HYPHAE");

        int checked = 0;
        for (String line : recipes.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\|");
            PriceTable.Entry output = table.get(parts[0]);
            if (output == null) continue;
            long cost = 0;
            boolean possible = true;
            for (String group : parts[2].split(";")) {
                long cheapest = Long.MAX_VALUE;
                for (String name : group.split(",")) {
                    long buy = table.buyCents(name);
                    if (buy > 0) cheapest = Math.min(cheapest, buy);
                }
                if (cheapest == Long.MAX_VALUE) { possible = false; break; }
                cost += cheapest;
            }
            if (possible) {
                checked++;
                long payout = output.sellCents() * Long.parseLong(parts[1]);
                check("modeled base sale <= half direct cost: " + line, payout * 2 <= cost);
            }
        }
        check("large modeled recipe set covered", checked > 1500);
        check("fortune copper record", recipes.contains("RAW_COPPER|20|COPPER_ORE"));
        check("bucket return record", recipes.contains("BUCKET|1|MILK_BUCKET"));
        check("concrete transformation record", recipes.contains("BLACK_CONCRETE|1|BLACK_CONCRETE_POWDER"));
        check("stripping transformation record", recipes.contains("STRIPPED_OAK_LOG|1|OAK_LOG"));
        check("bookshelf drop modeled", recipes.contains("BOOK|3|BOOKSHELF"));
        String partialCatalog = "building:\n  items:\n    OAK_LOG: { buy: 1.00, sell: 0.10 }\n"
                + "    STICK: { buy: 10.00, sell: 9.00 }\n";
        PriceTable withCraftOnlyIntermediate = PriceTable.parse(partialCatalog,
                "OAK_PLANKS|4|OAK_LOG\nSTICK|4|OAK_PLANKS;OAK_PLANKS\n", n -> true);
        eq("unlisted but craftable ingredient still constrains sell", withCraftOnlyIntermediate.sellCents("STICK"), 6L);
        for (PriceTable.Category c : table.categories()) for (PriceTable.Entry e : c.items()) {
            if (e.sellCents() > 0) {
                long best = ItemValue.valueCents(e.sellCents(), e.buyCents(), 100, 1.0);
                check("enchantment cannot double modeled base " + e.material(), best < 2 * e.sellCents());
                check("enchantment remains below buy " + e.material(), best < e.buyCents());
            }
        }
        eq("potion options", ChoiceCatalog.potions().size(), 45);
        eq("enchantment options", ChoiceCatalog.books().size(), 43);
        Set<String> choices = new HashSet<>();
        for (var option : ChoiceCatalog.potions()) {
            check("unique potion " + option.key(), choices.add(option.key()));
            check("potion type exists " + option.key(), PotionType.valueOf(option.key()) != null);
            check("potion is purchasable " + option.key(), new Purchase("POTION", option.key(), 0).unitPrice(table) > 0);
            check("arrow is purchasable " + option.key(), new Purchase("TIPPED_ARROW", option.key(), 0).unitPrice(table) > 0);
        }
        choices.clear();
        for (var option : ChoiceCatalog.books()) {
            check("unique book " + option.key(), choices.add(option.key()));
            check("valid max level " + option.key(), new Purchase("ENCHANTED_BOOK", option.key(), option.maxLevel()).unitPrice(table) > 0);
            check("invalid book level rejected " + option.key(), new Purchase("ENCHANTED_BOOK", option.key(), option.maxLevel()+1).unitPrice(table) < 0);
        }
        check("unselected potion rejected", Purchase.of("POTION").unitPrice(table) < 0);
        check("invalid potion rejected", new Purchase("POTION", "NOT_AN_EFFECT", 0).unitPrice(table) < 0);
        check("creative luck rejected", new Purchase("POTION", "LUCK", 0).unitPrice(table) < 0);
    }

    private static void testMigrationAndMessages() throws Exception {
        String old = "ores:\n  display: \"My Resources\"\n  icon: DIAMOND\n  items:\n"
                + "    DIAMOND: { buy: 999.00, sell: 3.00 }\n"
                + "    CHAIN: { buy: 17.00, sell: 1.00 }\n"
                + "    ELYTRA: { buy: 10.00, sell: 1.00 }\n";
        String merged = YamlTextMerge.merge(shopText, old);
        PriceTable upgraded = PriceTable.parse(merged, recipes, name -> true);
        eq("legacy shop upgrade adds entire catalog", upgraded.size(), 1530);
        eq("legacy diamond buy preserved", upgraded.buyCents("DIAMOND"), 99900L);
        eq("legacy diamond sell preserved where safe", upgraded.sellCents("DIAMOND"), 300L);
        eq("legacy chain price preserved", upgraded.buyCents("IRON_CHAIN"), 1700L);
        check("legacy forbidden elytra filtered", upgraded.get("ELYTRA") == null);
        eq("legacy display preserved", upgraded.categories().get(0).display(), "My Resources");
        eq("shop merge idempotent", YamlTextMerge.merge(shopText, merged), merged);
        String defaults = Files.readString(RES.resolve("messages.yml"));
        String custom = "general:\n  no-permission: \"Custom permission message\"\nshop.buy-done: \"Custom purchase\"\n";
        String messagesMerged = YamlTextMerge.merge(defaults, custom);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader(messagesMerged));
        eq("nested message override preserved", yaml.getString("general.no-permission"), "Custom permission message");
        eq("flat message override preserved", yaml.getString("shop.buy-done"), "Custom purchase");
        check("new message merged", yaml.isString("settings.updated"));
        check("multiline message merged", yaml.isString("shards.info"));
        eq("message merge idempotent", YamlTextMerge.merge(defaults, messagesMerged), messagesMerged);
        Map<String,String> fallback = Messages.mergeTemplates(Map.of("a", "default", "b", "default b"), Map.of("a", "custom", "c", "custom c"));
        eq("fallback preserves translation", fallback.get("a"), "custom");
        eq("fallback supplies missing default", fallback.get("b"), "default b");
        eq("fallback keeps extra custom message", fallback.get("c"), "custom c");
        Map<String,String> palette = Messages.mergeTemplates(Map.of("old", "new colors", "edited", "new", "empty", "new"),
                Map.of("old", "old colors", "edited", "server custom", "empty", ""), Map.of("old", "old colors", "edited", "old", "empty", "old"));
        eq("exact old default receives colors", palette.get("old"), "new colors");
        eq("custom message kept", palette.get("edited"), "server custom");
        eq("empty custom message kept", palette.get("empty"), "");
    }

    private static void testPersistence(Path dir) throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Path preferencesFile = dir.resolve("preferences.properties");
        PreferencesStore p = new PreferencesStore(preferencesFile, LOG);
        eq("preferences default vanilla", p.get(alice), PreferencesStore.Preferences.DEFAULT);
        var configured = p.get(alice).toggleMobs().togglePhantoms().toggleNightVision();
        p.set(alice, configured);
        PreferencesStore reopened = new PreferencesStore(preferencesFile, LOG); reopened.load();
        eq("preferences survive restart", reopened.get(alice), configured);
        eq("preferences isolated per player", reopened.get(bob), PreferencesStore.Preferences.DEFAULT);
        reopened.set(alice, PreferencesStore.Preferences.DEFAULT);
        p.load(); eq("preferences default clears persisted state", p.get(alice), PreferencesStore.Preferences.DEFAULT);

        Path quickFile = dir.resolve("quick.properties");
        QuickShopStore quick = new QuickShopStore(quickFile, LOG);
        Purchase potion = new Purchase("POTION", "LONG_SWIFTNESS", 0);
        Purchase book = new Purchase("ENCHANTED_BOOK", "SHARPNESS", 5);
        quick.set(alice, 0, new QuickShopStore.QuickSlot(potion, 32));
        quick.set(alice, 44, new QuickShopStore.QuickSlot(book, 1));
        quick.set(bob, 0, new QuickShopStore.QuickSlot(Purchase.of("STONE"), 64));
        QuickShopStore restarted = new QuickShopStore(quickFile, LOG); restarted.load();
        eq("quick potion and amount persist", restarted.get(alice, 0), new QuickShopStore.QuickSlot(potion, 32));
        eq("quick book variant persists", restarted.get(alice, 44), new QuickShopStore.QuickSlot(book, 1));
        eq("quick slots isolated", restarted.get(bob, 0).amount(), 64);
        restarted.set(alice, 0, null); quick.load();
        check("quick clear persists", quick.get(alice, 0) == null);
        eq("quick clear leaves other slot", quick.get(alice, 44).purchase(), book);
        boolean rejected = false; try { new QuickShopStore.QuickSlot(book, 65); } catch (IllegalArgumentException ok) { rejected = true; }
        check("quick rejects amount over 64", rejected);
    }

    private static void testInteraction(Path dir) throws Exception {
        FakePlugin plugin = new FakePlugin(dir);
        FakeScheduler scheduler = new FakeScheduler();
        Bukkit.testScheduler = scheduler;
        Bukkit.testInventoryFactory = (owner, size) -> new TestInventory(owner, size);
        PlatformScheduler.init(plugin);
        Files.writeString(dir.resolve("messages.yml"), "general:\n  no-permission: \"Custom permission\"\nshop.buy-done: \"Custom purchase\"\nlegacy.lime: \"<lime>$</lime>\"\n");
        Messages messages = new Messages(plugin); messages.reload();
        eq("reload prefers custom nested message", text(messages.render("general.no-permission")), "Custom permission");
        eq("reload prefers custom flat message", text(messages.render("shop.buy-done")), "Custom purchase");
        eq("legacy lime tag normalized without changing the file", text(messages.render("legacy.lime")), "<green>$</green>");
        check("custom message file remains unchanged", Files.readString(dir.resolve("messages.yml")).contains("<lime>$</lime>"));
        check("shipped templates use valid green tag", !Files.readString(RES.resolve("messages.yml")).contains("<lime>"));
        check("reload falls back to bundled new message", text(messages.render("settings.updated")).contains("is now"));
        check("reload falls back for older missing key", !text(messages.render("shards.pay-sent")).contains("Missing message"));
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(Files.newBufferedReader(RES.resolve("messages.yml")));
        int loadedMessages = 0;
        for (String key : bundled.getKeys(true)) if (bundled.isString(key)) {
            loadedMessages++;
            check("bundled message available with partial custom file: " + key,
                    !text(messages.render(key)).contains("Missing message:"));
        }
        check("all bundled messages covered", loadedMessages >= 225);

        TestWorld world = new TestWorld();
        PlayerState state = new PlayerState(world.proxy);
        Player player = state.proxy;
        Bukkit.testOnlinePlayers = List.of(player);
        Bukkit.testMapFactory = w -> { world.lastMap = new TestMap(); return world.lastMap; };
        RegistryAccess.TestState.access = new TestRegistries();
        BalanceData balances = new BalanceData();
        EconomyService economy = new EconomyService(balances.proxy, new VeltryxConfig(new YamlConfiguration()));
        economy.setBalance(state.id, "Alice", 50_000_000L);
        testScoreboard(world, state, scheduler, balances, economy);
        Bukkit.testOnlinePlayers = List.of(player);
        int[] changes = {0}; economy.onChange(id -> changes[0]++);
        Path shopPath = dir.resolve("shop.yml"); Files.writeString(shopPath, shopText);
        ShopService shop = new ShopService(economy, shopPath);
        eq("shop runtime loads all stubbed materials", shop.prices().size(), 1530);
        ShopGui gui = new ShopGui(shop, messages);
        Path quickPath = dir.resolve("gui-quick.properties");
        ShopExtrasGui extras = new ShopExtrasGui(shop, messages, quickPath, LOG);
        SellGui sellGui = new SellGui(shop, messages);
        gui.attachExtras(extras); extras.attachShopGui(gui); gui.attachSellOpener(sellGui::open);
        gui.openMain(player); check("GUI opens on a later tick", state.open == null); scheduler.drain();
        eq("main size", state.open.getSize(), 54);
        eq("main background", state.open.getItem(0).getType(), Material.GRAY_STAINED_GLASS_PANE);
        eq("main resources icon", state.open.getItem(14).getType(), Material.IRON_INGOT);
        eq("main sell icon", state.open.getItem(45).getType(), Material.YELLOW_SHULKER_BOX);
        eq("main close icon", state.open.getItem(53).getType(), Material.ARROW);
        StubItemMeta icon = (StubItemMeta) state.open.getItem(14).getItemMeta();
        check("GUI item name non-italic", ((StubComponent)icon.name).italicDisabled);
        check("GUI lore non-italic", icon.lore().stream().allMatch(c -> ((StubComponent)c).italicDisabled));
        check("direct text helper disables italics", ((StubComponent)Txt.legacy("§bHello")).italicDisabled);
        Inventory main = state.open;
        state.allowSell = false;
        click(gui, state, scheduler, 45, false, false);
        check("shop sell shortcut respects sell permission", state.open == main);
        state.allowSell = true;

        Click outside = new Click(state.open, state.inventory, player, 14, false, false);
        gui.onClick(outside); scheduler.drain();
        check("bottom inventory click cancelled", outside.cancelled);
        eq("bottom click does not navigate", state.open.getSize(), 54);
        click(gui, state, scheduler, 14, false, false);
        eq("category size", state.open.getSize(), 45);
        eq("resources first item", state.open.getItem(10).getType(), Material.COPPER_BLOCK);
        eq("resources ingot badge", state.open.getItem(19).getAmount(), 9);
        eq("category next", state.open.getItem(41).getType(), Material.BLUE_STAINED_GLASS_PANE);
        eq("category back", state.open.getItem(40).getType(), Material.NETHER_STAR);
        click(gui, state, scheduler, 19, false, false);
        eq("buy size", state.open.getSize(), 45);
        eq("buy selected nine", state.open.getItem(22).getAmount(), 9);
        eq("buy paper slot", state.open.getItem(13).getType(), Material.PAPER);
        eq("buy stack slot", state.open.getItem(31).getType(), Material.CHEST);
        click(gui, state, scheduler, 24, false, false);
        eq("buy plus sixteen", state.open.getItem(22).getAmount(), 25);
        click(gui, state, scheduler, 23, true, false);
        eq("buy right click subtracts one", state.open.getItem(22).getAmount(), 24);
        long before = economy.getBalance(state.id);
        long unit = shop.prices().buyCents("COPPER_INGOT");
        click(gui, state, scheduler, 13, false, false);
        eq("buy charges selected amount", before - economy.getBalance(state.id), unit * 24);
        eq("buy gives selected amount", state.inventory.count(Material.COPPER_INGOT), 24);
        check("economy changed callback fires", changes[0] > 0);
        Drag drag = new Drag(state.open, state.inventory); gui.onDrag(drag); check("shop drag cancelled", drag.cancelled);

        sellGui.open(player); scheduler.drain();
        eq("sell GUI size", state.open.getSize(), 54);
        eq("sell tray placeholder", state.open.getItem(0).getType(), Material.GRAY_STAINED_GLASS_PANE);
        eq("sell confirm pane", state.open.getItem(49).getType(), Material.LIME_STAINED_GLASS_PANE);
        before = economy.getBalance(state.id);
        sellGui.onClick(new Click(state.open, state.open, player, 49, false, false)); scheduler.drain();
        eq("empty tray does not sell inventory", state.inventory.count(Material.COPPER_INGOT), 24);
        eq("empty tray grants no payout", economy.getBalance(state.id), before);
        int copperSlot = -1;
        for (int i = 0; i < state.inventory.getSize(); i++) if (state.inventory.getItem(i) != null && state.inventory.getItem(i).getType() == Material.COPPER_INGOT) { copperSlot = i; break; }
        Click deposit = new Click(state.open, state.inventory, player, copperSlot, false, true);
        sellGui.onClick(deposit);
        check("shift deposit is controlled", deposit.cancelled);
        eq("deposited copper shown in tray", state.open.getItem(0).getType(), Material.COPPER_INGOT);
        eq("deposit removed from player inventory", state.inventory.count(Material.COPPER_INGOT), 0);
        state.inventory.setItem(copperSlot, new ItemStack(Material.BARRIER, 2));
        sellGui.onClick(new Click(state.open, state.inventory, player, copperSlot, false, true));
        eq("unsellable stack deposited", state.open.getItem(1).getType(), Material.BARRIER);
        state.cursor = new ItemStack(Material.COPPER_INGOT, 7);
        Click place = new Click(state.open, state.open, player, 2, false, false); place.cursor = state.cursor; sellGui.onClick(place);
        eq("left click replaces gray placeholder with cursor stack", state.open.getItem(2).getAmount(), 7);
        check("left click clears cursor after deposit", state.cursor == null);
        Click split = new Click(state.open, state.open, player, 2, true, false); sellGui.onClick(split);
        eq("right click takes half", state.cursor.getAmount(), 4);
        eq("right click leaves remaining half", state.open.getItem(2).getAmount(), 3);
        Click putOne = new Click(state.open, state.open, player, 2, true, false); putOne.cursor = state.cursor; sellGui.onClick(putOne);
        eq("right click deposits one", state.open.getItem(2).getAmount(), 4);
        eq("right click retains cursor remainder", state.cursor.getAmount(), 3);
        Click merge = new Click(state.open, state.open, player, 2, false, false); merge.cursor = state.cursor; sellGui.onClick(merge);
        eq("left click merges remaining stack", state.open.getItem(2).getAmount(), 7);
        check("merge clears cursor", state.cursor == null);
        Drag sellDrag = new Drag(state.open, state.inventory); sellDrag.raw = Set.of(1); sellGui.onDrag(sellDrag);
        check("drag into input is cancelled safely", sellDrag.cancelled);
        Click sell = new Click(state.open, state.open, player, 49, false, false);
        sellGui.onClick(sell); scheduler.drain();
        check("deposited sale grants payout", economy.getBalance(state.id) > before);
        eq("sold slot resets to placeholder", state.open.getItem(0).getType(), Material.GRAY_STAINED_GLASS_PANE);
        eq("unsellable remains in tray", state.open.getItem(1).getAmount(), 2);
        Close close = new Close(state.open, player); sellGui.onClose(close); sellGui.onClose(close);
        eq("unsold stack returned exactly once on close", state.inventory.count(Material.BARRIER), 2);
        eq("placeholder panes were not returned as items", state.inventory.count(Material.GRAY_STAINED_GLASS_PANE), 0);
        sellGui.open(player); scheduler.drain();
        state.inventory.setItem(copperSlot, new ItemStack(Material.BARRIER, 3));
        sellGui.onClick(new Click(state.open, state.inventory, player, copperSlot, false, true));
        state.inventory.capacity = 0;
        sellGui.onClose(new Close(state.open, player));
        eq("full inventory drops overflow stack", world.dropped.getAmount(), 3);
        eq("overflow is owner-locked", world.dropOwner, state.id);
        check("overflow has unlimited lifetime", world.unlimitedDrop);
        state.inventory.capacity = Integer.MAX_VALUE;
        testShardItemsAndLunge(dir, state, messages);
        testShardTools(dir, state, world);
        testShardVoid(dir, state, world, scheduler, messages);

        world.fakeBlock = true;
        extras.openSearchPrompt(player); scheduler.drain();
        check("search opens virtual sign", state.signOpened);
        SignInput wrong = new SignInput(player, 101, 64, 200, "diamond"); extras.onSign(wrong);
        check("wrong sign position ignored", !wrong.cancelled);
        SignInput sign = new SignInput(player, 100, 64, 200, "NETHERITE", "INGOT"); extras.onSign(sign); scheduler.drain();
        check("sign response handled privately", sign.cancelled);
        eq("sign search finds item", state.open.getItem(0).getType(), Material.NETHERITE_INGOT);
        check("virtual block restored", state.blockChanges >= 2);
        extras.openQuickshop(player); scheduler.drain();
        eq("quick GUI has 45 configurable slots", state.open.getSize(), 45);
        eq("quick empty slot", state.open.getItem(2).getType(), Material.GRAY_STAINED_GLASS_PANE);
        click(extras, state, scheduler, 2, false, false);
        check("quick prompt closes GUI on next tick", state.open == null);
        SignInput query = new SignInput(player, 100, 64, 200, "NETHERITE", "INGOT"); extras.onSign(query);
        check("quick sign search is private", query.cancelled); scheduler.drain();
        eq("search finds selected item", state.open.getItem(0).getType(), Material.NETHERITE_INGOT);
        click(extras, state, scheduler, 0, false, false);
        eq("quick amount screen", state.open.getSize(), 27);
        click(extras, state, scheduler, 16, false, false);
        eq("quick plus sixteen", state.open.getItem(13).getAmount(), 17);
        click(extras, state, scheduler, 22, false, false);
        eq("quick saved item", state.open.getItem(2).getType(), Material.NETHERITE_INGOT);
        eq("quick saved amount", state.open.getItem(2).getAmount(), 17);
        QuickShopStore saved = new QuickShopStore(quickPath, LOG); saved.load();
        eq("configured quick survives reload", saved.get(state.id, 2).amount(), 17);
        // A stored slot must charge the current configured price, not a cached old one.
        String reprice = shopText.replaceFirst("NETHERITE_INGOT: \\{ buy: [0-9]+\\.[0-9]{2}, sell: [0-9]+\\.[0-9]{2} }",
                "NETHERITE_INGOT: { buy: 200.00, sell: 1.00 }");
        Files.writeString(shopPath, reprice); shop.reload();
        before = economy.getBalance(state.id);
        click(extras, state, scheduler, 2, false, false);
        eq("quick purchase uses current price", before - economy.getBalance(state.id), 17 * 20_000L);
        Files.writeString(shopPath, reprice.replaceFirst("(?m)^    NETHERITE_INGOT:.*\\R", ""));
        shop.reload(); extras.openQuickshop(player); scheduler.drain();
        click(extras, state, scheduler, 2, true, true);
        saved.load(); check("shift-right clears even a stale quick slot", saved.get(state.id, 2) == null);
        Files.writeString(shopPath, shopText); shop.reload();

        // Sign search and home-rename chat prompts must not overlap.
        HomeLocation home = new HomeLocation(state.id, "base", "world", 10, 64, 20, 0, 0);
        balances.homes.put(state.id, List.of(home));
        HomesGui homes = new HomesGui(new HomeService(balances.proxy, economy, messages, new VeltryxConfig(new YamlConfiguration())),
                economy, messages, new VeltryxConfig(new YamlConfiguration()));
        extras.attachPromptCancellation(homes::cancelPendingRename);
        homes.attachPromptCancellation(extras::cancelPendingSearch);
        homes.openDetail(player, "base"); scheduler.drain();
        homes.onClick(new Click(state.open, state.open, player, 12, false, false)); scheduler.drain();
        check("home rename prompt closes on next tick", state.open == null);
        extras.openSearchPrompt(player); scheduler.drain();
        SignInput searchWhileRenaming = new SignInput(player, 100, 64, 200, "diamond"); extras.onSign(searchWhileRenaming); scheduler.drain();
        check("sign search consumed instead of renaming home", searchWhileRenaming.cancelled);
        Chat normal = new Chat(player, "ordinary chat"); homes.onChat(normal);
        check("superseded home prompt does not capture later chat", !normal.cancelled);
        extras.openSearchPrompt(player); scheduler.drain();
        homes.openDetail(player, "base"); scheduler.drain();
        homes.onClick(new Click(state.open, state.open, player, 12, false, false)); scheduler.drain();
        SignInput staleSign = new SignInput(player, 100, 64, 200, "diamond"); extras.onSign(staleSign);
        check("home prompt cancels pending sign search", !staleSign.cancelled);
        Chat cancelRename = new Chat(player, "cancel");
        homes.onChat(cancelRename); check("home prompt consumes its own response", cancelRename.cancelled);

        // Exercise both GUI pickers, metadata delivery, invalid variants and refund paths.
        extras.openChoice(player, "POTION", -1, null, 0); scheduler.drain();
        eq("potion picker shows options", state.open.getItem(0).getType(), Material.POTION);
        click(extras, state, scheduler, 0, false, false);
        eq("potion picker proceeds to buy", state.open.getItem(22).getType(), Material.POTION);
        before = economy.getBalance(state.id);
        click(gui, state, scheduler, 13, false, false);
        ItemStack purchasedPotion = state.inventory.last(Material.POTION);
        eq("chosen potion metadata delivered", ((PotionMeta)purchasedPotion.getItemMeta()).getBasePotionType(), PotionType.NIGHT_VISION);
        check("variant purchase charged", economy.getBalance(state.id) < before);
        extras.openChoice(player, "ENCHANTED_BOOK", -1, null, 0); scheduler.drain();
        click(extras, state, scheduler, 0, false, false);
        eq("book level picker displays levels", state.open.getItem(13).getAmount(), 4);
        click(extras, state, scheduler, 13, false, false);
        click(gui, state, scheduler, 13, false, false);
        ItemStack book = state.inventory.last(Material.ENCHANTED_BOOK);
        var stored = ((StubEnchantmentStorageMeta)book.getItemMeta()).stored;
        eq("chosen enchant stored once at chosen level", new ArrayList<>(stored.values()), List.of(4));
        before = economy.getBalance(state.id);
        var invalid = shop.buy(player, new Purchase("POTION", "NO_SUCH_EFFECT", 0), 1);
        check("invalid variant rejected", !invalid.ok()); eq("invalid variant not charged", economy.getBalance(state.id), before);
        state.inventory.capacity = 0;
        var full = shop.buy(player, "STONE", 3);
        eq("full inventory reports no-space", full.failKey(), "shop.no-space");
        eq("full inventory refunded", economy.getBalance(state.id), before);
        state.inventory.capacity = 2;
        var partial = shop.buy(player, "STONE", 3);
        check("partial inventory purchase succeeds", partial.ok());
        eq("partial purchase delivered count", partial.given(), 2);
        eq("partial purchase only charges delivered items", before - economy.getBalance(state.id), shop.prices().buyCents("STONE") * 2);
        state.inventory.capacity = Integer.MAX_VALUE;
        var arrows = shop.buy(player, new Purchase("TIPPED_ARROW", "STRONG_SWIFTNESS", 0), 8);
        check("tipped arrow variant succeeds", arrows.ok());
        eq("tipped arrow metadata delivered", ((PotionMeta)state.inventory.last(Material.TIPPED_ARROW).getItemMeta()).getBasePotionType(), PotionType.STRONG_SWIFTNESS);
        var map = shop.buy(player, "WOODLAND_MANSION_MAP", 1);
        check("named explorer map succeeds when structure found", map.ok());
        ItemStack mapStack = state.inventory.last(Material.WOODLAND_MANSION_MAP);
        check("map search targets mansion", world.searched.contains("minecraft:mansion"));
        check("map lookup permits explored structures", !world.findUnexplored);
        eq("map centered on structure x", world.lastMap.x, 1234);
        eq("map centered on structure z", world.lastMap.z, -567);
        check("map has persistent map id", ((StubMapMeta)mapStack.getItemMeta()).view == world.lastMap);
        MapDecorations decorations = mapStack.getData(DataComponentTypes.MAP_DECORATIONS);
        eq("map marker x", decorations.decorations().get("+").x(), 1234.0);
        eq("map marker z", decorations.decorations().get("+").z(), -567.0);
        world.findStructure = false; before = economy.getBalance(state.id);
        var noMap = shop.buy(player, "OCEAN_MONUMENT_MAP", 1);
        eq("no destination reports map unavailable", noMap.failKey(), "shop.map-unavailable");
        eq("no destination refunded", economy.getBalance(state.id), before);
        world.findStructure = true;

        testOrders(world, scheduler, messages);
        Bukkit.testOnlinePlayers = List.of(player);
        PreferencesStore prefs = new PreferencesStore(dir.resolve("gui-settings.properties"), LOG);
        SettingsListener listener = new SettingsListener(prefs, plugin);
        SettingsGui settings = new SettingsGui(prefs, listener, messages);
        settings.open(player); scheduler.drain(); eq("settings screen size", state.open.getSize(), 27);
        Click mobClick = new Click(state.open, state.open, player, 11, false, false);
        settings.onClick(mobClick); scheduler.drain(); check("mob preference toggled", prefs.get(state.id).mobsDisabled());
        Natural natural = new Natural(player); listener.onNaturalSpawns(natural); check("natural spawn cancelled for opted out player", natural.cancelled);
        Phantom phantom = new Phantom(player); listener.onPhantom(phantom); check("phantoms also disabled with mobs", phantom.cancelled);
        settings.onClick(new Click(state.open, state.open, player, 11, false, false)); scheduler.drain();
        natural = new Natural(player); listener.onNaturalSpawns(natural); check("natural spawning restored", !natural.cancelled);
        settings.onClick(new Click(state.open, state.open, player, 13, false, false)); scheduler.drain();
        phantom = new Phantom(player); listener.onPhantom(phantom); check("phantom-only opt out works", phantom.cancelled);
        settings.onClick(new Click(state.open, state.open, player, 15, false, false)); scheduler.drain();
        check("night vision enabled", state.effects.containsKey(PotionEffectType.NIGHT_VISION));
        state.effects.remove(PotionEffectType.NIGHT_VISION);
        listener.onEffectRemoved(new VisionRemoved(player)); scheduler.drain();
        check("enabled night vision reapplies after removal", state.effects.containsKey(PotionEffectType.NIGHT_VISION));
        settings.onClick(new Click(state.open, state.open, player, 15, false, false)); scheduler.drain();
        check("owned night vision removed", !state.effects.containsKey(PotionEffectType.NIGHT_VISION));
        PotionEffect external = new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 1);
        state.effects.put(PotionEffectType.NIGHT_VISION, external);
        settings.onClick(new Click(state.open, state.open, player, 15, false, false)); scheduler.drain();
        settings.onClick(new Click(state.open, state.open, player, 15, false, false)); scheduler.drain();
        check("external night vision never removed", state.effects.get(PotionEffectType.NIGHT_VISION) == external);
        PreferencesStore prefsRestart = new PreferencesStore(dir.resolve("gui-settings.properties"), LOG); prefsRestart.load();
        check("GUI phantom setting survives restart", prefsRestart.get(state.id).phantomsDisabled());
    }

    private static void testOrders(TestWorld world, FakeScheduler scheduler, Messages messages) {
        PlayerState buyer=new PlayerState(world.proxy,"Buyer"), seller=new PlayerState(world.proxy,"Seller");
        Bukkit.testOnlinePlayers=List.of(buyer.proxy,seller.proxy);
        BalanceData data=new BalanceData();
        VeltryxConfig config=new VeltryxConfig(new YamlConfiguration());
        EconomyService economy=new EconomyService(data.proxy,config);
        economy.setBalance(buyer.id,"Buyer",50_000);
        OrderService service=new OrderService(data.proxy,economy,messages,config);
        OrderGui gui=new OrderGui(service,economy,messages);
        OrderCommand command=new OrderCommand(gui,service,messages);
        org.bukkit.command.Command order=new org.bukkit.command.Command(){@Override public String getName(){return "order";}};
        org.bukkit.command.Command orders=new org.bukkit.command.Command(){@Override public String getName(){return "orders";}};
        command.onCommand(buyer.proxy,order,"order",new String[0]); scheduler.drain();
        check("/order opens creation GUI",buyer.open!=null && buyer.open.getSize()==54);
        eq("default item is unselected",buyer.open.getItem(4).getType(),Material.PAPER);
        click(gui,buyer,scheduler,4,false,false);
        check("item picker opens",buyer.open.getItem(0)!=null && buyer.open.getItem(49).getType()==Material.BARRIER);
        click(gui,buyer,scheduler,49,false,false);
        buyer.inventory.setItem(10,new ItemStack(Material.DIAMOND,1));
        Click select=new Click(buyer.open,buyer.inventory,buyer.proxy,10,false,false);gui.onClick(select);scheduler.drain();
        check("selecting from own inventory is cancelled",select.cancelled);
        eq("order GUI selects clicked material",buyer.open.getItem(4).getType(),Material.DIAMOND);
        eq("selection does not consume original",buyer.inventory.count(Material.DIAMOND),1);
        click(gui,buyer,scheduler,15,false,false); // amount 2
        click(gui,buyer,scheduler,24,false,false); // $2 each
        long before=economy.getBalance(buyer.id);
        Click confirm=new Click(buyer.open,buyer.open,buyer.proxy,49,false,false);
        gui.onClick(confirm);gui.onClick(confirm);scheduler.drain();
        eq("one order despite double click",data.orders.size(),1);
        eq("GUI escrows confirmed amount and price",before-economy.getBalance(buyer.id),400L);
        BuyOrder placed=data.orders.values().iterator().next();
        eq("GUI amount",placed.amount(),2);eq("GUI price",placed.priceEachCents(),200L);
        Drag drag=new Drag(buyer.open,buyer.inventory);gui.onDrag(drag);check("order GUI blocks drags",drag.cancelled);
        seller.inventory.setItem(0,new ItemStack(Material.DIAMOND,2));
        command.onCommand(seller.proxy,orders,"orders",new String[0]);scheduler.drain();
        click(gui,seller,scheduler,0,false,false);
        eq("seller gets escrow payout",economy.getBalance(seller.id),400L);
        eq("seller supplied items removed",seller.inventory.count(Material.DIAMOND),0);
        BuyOrder fulfilled=data.orders.get(placed.id());
        check("filled order is closed and claimable",fulfilled.closed() && fulfilled.claimable()==2);
        eq("non-owner cannot claim",service.claim(seller.proxy,placed.id()).templateKey(),"order.gone");
        command.onCommand(buyer.proxy,orders,"orders",new String[]{"mine"});scheduler.drain();
        check("closed order remains visible for collection",buyer.open.getItem(0).getType()==Material.DIAMOND);
        click(gui,buyer,scheduler,0,false,false);
        eq("buyer receives supplied items once",buyer.inventory.count(Material.DIAMOND),3);
        eq("claim count saved",data.orders.get(placed.id()).claimable(),0);
        eq("repeated claim delivers nothing",service.claim(buyer.proxy,placed.id()).templateKey(),"order.nothing-to-claim");
        eq("repeated claim does not duplicate items",buyer.inventory.count(Material.DIAMOND),3);
        check("fully claimed closed order no longer visible",service.ordersOf(buyer.id).isEmpty());

        OrderService.Result created=service.create(buyer.proxy,"IRON_INGOT",5,100);
        check("direct order syntax remains functional",created.ok());
        String id=created.values().get("id");
        seller.inventory.setItem(0,new ItemStack(Material.IRON_INGOT,3));
        Logger orderLog=Logger.getLogger(OrderService.class.getName());
        java.util.logging.Level previousLogLevel=orderLog.getLevel();
        orderLog.setLevel(java.util.logging.Level.OFF); // expected injected persistence failures
        data.failOrderWrites=true;
        eq("failed fill reports storage problem",service.fill(seller.proxy,id,3).templateKey(),"order.storage-error");
        eq("failed fill restores seller items",seller.inventory.count(Material.IRON_INGOT),3);
        eq("failed fill leaves buyer claim unchanged",data.orders.get(id).claimable(),0);
        data.failOrderWrites=false;
        data.failNextBalance=true;
        eq("failed seller payout reports storage problem",service.fill(seller.proxy,id,3).templateKey(),"order.storage-error");
        eq("failed payout restores seller items",seller.inventory.count(Material.IRON_INGOT),3);
        eq("failed payout rolls back order",data.orders.get(id).filled(),0);
        check("partial order fill succeeds",service.fill(seller.proxy,id,3).ok());
        buyer.inventory.capacity=1;
        check("partial-capacity claim succeeds",service.claim(buyer.proxy,id).ok());
        eq("only one item fits",buyer.inventory.count(Material.IRON_INGOT),1);
        eq("uncollected items remain",data.orders.get(id).claimable(),2);
        buyer.inventory.capacity=Integer.MAX_VALUE;
        data.failOrderWrites=true;
        eq("failed claim is rejected",service.claim(buyer.proxy,id).templateKey(),"order.storage-error");
        eq("failed claim rolls back delivered items",buyer.inventory.count(Material.IRON_INGOT),1);
        eq("failed claim remains available",data.orders.get(id).claimable(),2);
        data.failOrderWrites=false;
        long prior=economy.getBalance(buyer.id);
        check("cancel refunds unfilled portion",service.cancel(buyer.proxy,id).ok());
        eq("cancel refund only remaining escrow",economy.getBalance(buyer.id)-prior,200L);
        check("cancelled order keeps purchased items",data.orders.get(id).closed() && data.orders.get(id).claimable()==2);
        check("claim survives cancellation",service.claim(buyer.proxy,id).ok());
        eq("buyer collects all supplied items",buyer.inventory.count(Material.IRON_INGOT),3);
        ItemStack named=new ItemStack(Material.IRON_INGOT,1);
        ItemMeta meta=named.getItemMeta();meta.displayName(new StubComponent("custom"));named.setItemMeta(meta);
        seller.inventory.setItem(0,named);
        String second=service.create(buyer.proxy,"IRON_INGOT",1,100).values().get("id");
        eq("custom metadata cannot be silently erased",service.fill(seller.proxy,second,1).templateKey(),"order.no-metadata");
        eq("metadata item remains with seller",seller.inventory.getItem(0).getType(),Material.IRON_INGOT);
        data.failOrderWrites=true;
        long beforeFailedCreate=economy.getBalance(buyer.id);
        eq("failed order creation reports failure",service.create(buyer.proxy,"DIAMOND",1,100).templateKey(),"order.storage-error");
        eq("failed order creation refunds escrow",economy.getBalance(buyer.id),beforeFailedCreate);
        eq("failed cancellation reports failure",service.cancel(buyer.proxy,second).templateKey(),"order.storage-error");
        check("failed cancellation leaves order open",!data.orders.get(second).closed());
        data.failOrderWrites=false;
        data.failNextBalance=true;
        eq("failed cancellation refund reports failure",service.cancel(buyer.proxy,second).templateKey(),"order.storage-error");
        check("failed refund restores open order",!data.orders.get(second).closed());
        orderLog.setLevel(previousLogLevel);
        scheduler.drain();
        // Competing sellers in different regions must never fill the same final item twice.
        PlayerState other=new PlayerState(world.proxy,"Other");
        seller.inventory.setItem(0,new ItemStack(Material.DIAMOND,1));
        other.inventory.setItem(0,new ItemStack(Material.DIAMOND,1));
        String raceId=service.create(buyer.proxy,"DIAMOND",1,100).values().get("id");
        Bukkit.testOnlinePlayers=List.of(); // no cross-thread notification scheduling in this test
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> service.fill(seller.proxy,raceId,1));
            var secondFill=pool.submit(() -> service.fill(other.proxy,raceId,1));
            int successes=(first.get().ok()?1:0)+(secondFill.get().ok()?1:0);
            eq("concurrent sellers fill final item once",successes,1);
            eq("concurrent fill creates one buyer claim",data.orders.get(raceId).claimable(),1);
            eq("only one seller item consumed",seller.inventory.count(Material.DIAMOND)+other.inventory.count(Material.DIAMOND),1);
        } catch(Exception e) { throw new RuntimeException(e); }
        finally {pool.shutdownNow();}
    }

    private static void testShardItemsAndLunge(Path dir, PlayerState state, Messages messages) {
        ShardCatalog catalog = new ShardCatalog(); ShardItems items = new ShardItems(new FakePlugin(dir));
        ItemStack spear = items.create(catalog.byId("shard_spear"), 0);
        eq("native spear material", spear.getType(), Material.NETHERITE_SPEAR);
        eq("native Lunge III on spear", spear.getEnchantmentLevel(Enchantment.LUNGE), 3);
        eq("totem model", items.create(catalog.byId("void_totem"), 0).getItemMeta().getItemModel(), ShardItems.VOID_TOTEM_MODEL);
        eq("totem icon model", items.displayIcon(catalog.byId("void_totem"), 150).getItemMeta().getItemModel(), ShardItems.VOID_TOTEM_MODEL);
        ItemStack old = spear.clone(); old.setType(Material.NETHERITE_SWORD);
        ItemMeta oldMeta = old.getItemMeta(); oldMeta.removeEnchant(Enchantment.LUNGE);
        ((Damageable)oldMeta).setDamage(37); old.setItemMeta(oldMeta);
        long created = items.created(old), lifetime = items.lifetime(old);
        ItemStack migrated = items.upgradeLegacy(old);
        eq("legacy spear becomes spear", migrated.getType(), Material.NETHERITE_SPEAR);
        eq("legacy creation retained", items.created(migrated), created);
        eq("legacy lifetime retained", items.lifetime(migrated), lifetime);
        eq("legacy existing enchant retained", migrated.getEnchantmentLevel(Enchantment.SHARPNESS), 5);
        eq("legacy spear gains Lunge III", migrated.getEnchantmentLevel(Enchantment.LUNGE), 3);
        eq("legacy damage retained", ((Damageable)migrated.getItemMeta()).getDamage(), 37);
        ItemStack customizedTotem = items.create(catalog.byId("void_totem"), 0);
        ItemMeta customMeta = customizedTotem.getItemMeta(); customMeta.setItemModel(NamespacedKey.fromString("other:my_totem")); customizedTotem.setItemMeta(customMeta);
        check("custom totem model not overwritten", items.upgradeLegacy(customizedTotem) == customizedTotem);
        ItemStack legacyTotem = items.create(catalog.byId("void_totem"), 0);
        ItemMeta legacyMeta = legacyTotem.getItemMeta(); legacyMeta.setItemModel(null); legacyTotem.setItemMeta(legacyMeta);
        long totemCreated = items.created(legacyTotem);
        ItemStack updatedTotem = items.upgradeLegacy(legacyTotem);
        eq("legacy totem receives model", updatedTotem.getItemMeta().getItemModel(), ShardItems.VOID_TOTEM_MODEL);
        eq("legacy totem timestamp retained", items.created(updatedTotem), totemCreated);
        legacyMeta = legacyTotem.getItemMeta(); legacyMeta.setCustomModelData(123); legacyTotem.setItemMeta(legacyMeta);
        check("custom model data is not overwritten", items.upgradeLegacy(legacyTotem) == legacyTotem);
        check("vanilla totem is never migrated", items.upgradeLegacy(new ItemStack(Material.TOTEM_OF_UNDYING)).getItemMeta().getItemModel() == null);
        ItemStack.testRejectItemMeta = true;
        boolean failedClosed = false;
        try { items.create(catalog.byId("void_totem"), 0); } catch (IllegalStateException expected) { failedClosed = true; }
        finally { ItemStack.testRejectItemMeta = false; }
        check("item creation fails closed when metadata rejected", failedClosed);
        state.inventory.setItemInMainHand(spear);
        ShardWeaponListener listener = new ShardWeaponListener(items, catalog, messages);
        Exhaust sprint = new Exhaust(state.proxy, Exhaust.ExhaustionReason.SPRINT, 3f);
        listener.onLunge(new Lunge(state.proxy)); listener.onExhaustion(sprint);
        eq("sprint exhaustion unchanged", sprint.value, 3f);
        Exhaust lunge = new Exhaust(state.proxy, Exhaust.ExhaustionReason.ENCHANTMENT_EFFECT, 12f);
        listener.onExhaustion(lunge);
        check("native Lunge exhaustion reduced by 90 percent", Math.abs(lunge.value - 1.2f) < .001f);
        Exhaust unrelated = new Exhaust(state.proxy, Exhaust.ExhaustionReason.ENCHANTMENT_EFFECT, 12f);
        listener.onExhaustion(unrelated);
        eq("unmarked enchantment exhaustion unchanged", unrelated.value, 12f);
        listener.onLunge(new Lunge(state.proxy));
        Exhaust different = new Exhaust(state.proxy, Exhaust.ExhaustionReason.ENCHANTMENT_EFFECT, 2f);
        listener.onExhaustion(different);
        eq("other exhaustion amount unchanged", different.value, 2f);
        ItemStack trident = items.create(catalog.byId("shard_trident"), 0);
        eq("native Riptide III on trident", trident.getEnchantmentLevel(Enchantment.RIPTIDE), 3);
        state.inventory.setItemInMainHand(trident);
        listener.onInteract(new Interact(state.proxy)); listener.onInteract(new Interact(state.proxy));
        eq("dry trident activates only once during cooldown", state.riptides, 1);
        eq("dry trident uses server durability handling", state.durabilityUses, 1);
        try {
            Field f = ShardWeaponListener.class.getDeclaredField("nextRiptide"); f.setAccessible(true);
            @SuppressWarnings("unchecked") Map<UUID,Long> cooldowns = (Map<UUID,Long>)f.get(listener);
            long remaining = cooldowns.get(state.id) - System.currentTimeMillis();
            check("dry cooldown is five seconds", remaining > 4_000L && remaining <= 5_000L);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        state.inWater = true; listener.onInteract(new Interact(state.proxy)); listener.onInteract(new Interact(state.proxy));
        eq("wet use is passed through to vanilla without plugin cooldown", state.riptides, 1);
        state.inWater = false;
        state.inventory.setItemInMainHand(null);
    }
    private static void testShardTools(Path dir, PlayerState state, TestWorld world) {
        ShardCatalog catalog = new ShardCatalog(); ShardItems items = new ShardItems(new FakePlugin(dir));
        ShardToolListener listener = new ShardToolListener(items, catalog, new VeltryxConfig(new YamlConfiguration()));
        world.fakeBlock = true; world.blockType = Material.STONE; world.breaks = 0; int sounds = state.sounds;
        state.inventory.setItemInMainHand(items.create(catalog.byId("shard_spear"), 0));
        listener.onBlockBreak(new BreakBlock(state.proxy, world.block));
        state.inventory.setItemInMainHand(items.create(catalog.byId("shard_trident"), 0));
        listener.onBlockBreak(new BreakBlock(state.proxy, world.block));
        eq("weapons do not area mine", world.breaks, 0);
        eq("weapons do not play mining sound", state.sounds, sounds);
        state.inventory.setItemInMainHand(items.create(catalog.byId("shard_pickaxe"), 0));
        listener.onBlockBreak(new BreakBlock(state.proxy, world.block));
        eq("pickaxe mines eight extra allowed blocks", world.breaks, 8);
        eq("one amethyst sound per pickaxe use", state.sounds - sounds, 1);
        eq("pickaxe uses ShardTools mining sound", state.lastSound, "block.amethyst_block.step");
        world.breaks = 0; sounds = state.sounds;
        state.inventory.setItemInMainHand(items.create(catalog.byId("shard_axe"), 0));
        listener.onBlockBreak(new BreakBlock(state.proxy, world.block));
        eq("axe mines eight extra allowed non-log blocks", world.breaks, 8);
        eq("one amethyst sound per axe use", state.sounds - sounds, 1);
        world.breaks = 0; world.blockType = Material.DIAMOND_BLOCK;
        listener.onBlockBreak(new BreakBlock(state.proxy, world.block));
        eq("valuable block outside allowed list is not area mined", world.breaks, 0);
        state.inventory.setItemInMainHand(null); world.fakeBlock = false; world.blockType = Material.STONE;
    }
    private static void testShardVoid(Path dir, PlayerState state, TestWorld world, FakeScheduler scheduler, Messages messages) {
        ShardCatalog catalog = new ShardCatalog(); ShardItems items = new ShardItems(new FakePlugin(dir));
        ShardVoidListener listener = new ShardVoidListener(items, catalog, messages);
        ItemStack totem = items.create(catalog.byId("void_totem"), 0); totem.setAmount(2);
        state.inventory.setItem(10, totem); state.health = 10; state.absorption = 5;
        VoidDamage survivable = new VoidDamage(state.proxy, 9.0); listener.onDamage(survivable);
        check("absorption prevents premature totem consumption", !survivable.cancelled);
        eq("survivable damage keeps both totems", state.inventory.getItem(10).getAmount(), 2);
        VoidDamage lethal = new VoidDamage(state.proxy, 10.0); listener.onDamage(lethal); scheduler.drain();
        check("lethal void hit cancelled", lethal.cancelled);
        eq("exactly one totem consumed", state.inventory.getItem(10).getAmount(), 1);
        eq("rescue heals to max", state.health, 20.0);
        eq("rescue clears absorption", state.absorption, 0.0);
        eq("resistance lasts 45 seconds", state.effects.get(PotionEffectType.RESISTANCE).getDuration(), 900);
        eq("slow falling lasts 30 seconds", state.effects.get(PotionEffectType.SLOW_FALLING).getDuration(), 600);
        check("spawn fallback teleported", state.teleportDestination != null);
        VoidDamage repeat = new VoidDamage(state.proxy, 30.0); listener.onDamage(repeat);
        check("rescue lock prevents repeat lethal void", repeat.cancelled);
        eq("rescue lock does not consume another totem", state.inventory.getItem(10).getAmount(), 1);
        state.inventory.setItem(10, null);
        state.teleportSuccess = false; state.online = false; state.health = 10; state.absorption = 0;
        ItemStack another = items.create(catalog.byId("void_totem"), 0); state.inventory.setItem(10, another);
        ShardVoidListener failing = new ShardVoidListener(items, catalog, messages);
        VoidDamage failed = new VoidDamage(state.proxy, 15); failing.onDamage(failed); scheduler.drain();
        state.online = true; failing.onJoin(new Join(state.proxy)); scheduler.drain();
        int refunded = 0; for (ItemStack item : state.inventory.getContents()) if ("void_totem".equals(items.itemId(item))) refunded += item.getAmount();
        eq("failed teleport refunds on reconnect", refunded, 1);
        for (int i = 0; i < state.inventory.getSize(); i++) if ("void_totem".equals(items.itemId(state.inventory.getItem(i)))) state.inventory.setItem(i, null);
        state.teleportSuccess = true;
        state.inventory.setItem(10, new ItemStack(Material.TOTEM_OF_UNDYING)); state.health = 5;
        VoidDamage vanilla = new VoidDamage(state.proxy, 50); new ShardVoidListener(items, catalog, messages).onDamage(vanilla);
        check("vanilla totem is not treated as a shard totem", !vanilla.cancelled);
        state.inventory.setItem(10, null);
        state.effects.clear(); state.health = 20; state.absorption = 0; state.teleportDestination = null;
        PlayerState slow=new PlayerState(world.proxy,"Slow");
        slow.health=10; slow.teleportFuture=new java.util.concurrent.CompletableFuture<>();
        ItemStack pair=items.create(catalog.byId("void_totem"),0);pair.setAmount(2);slow.inventory.setItem(0,pair);
        ShardVoidListener awaiting=new ShardVoidListener(items,catalog,messages);
        awaiting.onDamage(new VoidDamage(slow.proxy,15));
        try {
            Field field=ShardVoidListener.class.getDeclaredField("pending");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<UUID,Object> locks=(Map<UUID,Object>)field.get(awaiting);
            Object pending=locks.get(slow.id);Field until=pending.getClass().getDeclaredField("until");until.setAccessible(true);
            eq("pending rescue stays locked until teleport finishes",until.getLong(pending),Long.MAX_VALUE);
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        VoidDamage during=new VoidDamage(slow.proxy,15);awaiting.onDamage(during);
        check("pending teleport cancels repeated void damage",during.cancelled);
        eq("pending teleport consumes just one totem",slow.inventory.count(Material.TOTEM_OF_UNDYING),1);
        slow.teleportFuture.complete(false);scheduler.drain();
        eq("failed pending teleport refunds its totem",slow.inventory.count(Material.TOTEM_OF_UNDYING),2);
    }

    private static void testScoreboard(TestWorld world, PlayerState alice, FakeScheduler scheduler,
                                       BalanceData data, EconomyService economy) {
        PlayerState bob = new PlayerState(world.proxy, "Bob");
        Bukkit.testOnlinePlayers = List.of(alice.proxy, bob.proxy);
        Bukkit.testScoreboardManager = new ScoreboardManager() {
            public Scoreboard getMainScoreboard() { return null; }
            public Scoreboard getNewScoreboard() { return new TestBoard(); }
        };
        StatsService stats = new StatsService(data.proxy);
        ShardService shards = new ShardService(data.proxy);
        ScoreboardService boards = new ScoreboardService(stats, economy, shards, 1, false);
        economy.onChange(boards::refreshChanged); shards.onChange(boards::refreshChanged);
        boards.refreshAll();
        eq("scoreboard refresh is scheduled per player", scheduler.tasks.size(), 2);
        scheduler.drain();
        TestBoard aliceBoard = (TestBoard)alice.board;
        TestBoard bobBoard = (TestBoard)bob.board;
        eq("sidebar has five rows", aliceBoard.objective.scores.size(), 5);
        check("sidebar numbers blank", aliceBoard.objective.scores.values().stream().allMatch(s -> s.format == NumberFormat.BLANK));
        Set<String> rows = new HashSet<>(aliceBoard.objective.scores.keySet());
        Set<String> bobRows = new HashSet<>(bobBoard.objective.scores.keySet());
        economy.deposit(alice.id, "Alice", 10_000_000L);
        eq("balance change schedules only its sidebar when nametags off", scheduler.tasks.size(), 1);
        eq("balance is not rendered until scheduled tick", new HashSet<>(aliceBoard.objective.scores.keySet()), rows);
        scheduler.drain();
        check("money sidebar refreshes after balance change", !rows.equals(aliceBoard.objective.scores.keySet()));
        eq("old money score removed", aliceBoard.objective.scores.size(), 5);
        eq("other player's board untouched", new HashSet<>(bobBoard.objective.scores.keySet()), bobRows);
        rows = new HashSet<>(aliceBoard.objective.scores.keySet());
        shards.give(alice.id, 25); scheduler.drain();
        check("shard sidebar refreshes after change", !rows.equals(aliceBoard.objective.scores.keySet()));
        alice.killer = bob.proxy;
        StatsListener events = new StatsListener(stats, boards::refreshAll);
        events.onDeath(new Death(alice.proxy)); scheduler.drain();
        eq("death recorded", stats.deaths(alice.id), 1L);
        eq("player kill recorded", stats.kills(bob.id), 1L);
        check("death displayed", aliceBoard.objective.scores.keySet().stream().anyMatch(v -> v.contains("☠ §f1")));
        check("kill displayed", bobBoard.objective.scores.keySet().stream().anyMatch(v -> v.contains("⚔ §f1")));
        boards.startTimer();
        eq("playtime refresh default one second", scheduler.period, 20L);
        scheduler.timer.run();
        eq("timer delegates to viewer threads", scheduler.tasks.size(), 2); scheduler.drain();
        check("all refreshed rows still blank numbers", aliceBoard.objective.scores.values().stream().allMatch(s -> s.format == NumberFormat.BLANK));

        ScoreboardService moneyBoards = new ScoreboardService(stats, economy, shards, 1, true);
        economy.onChange(moneyBoards::refreshChanged);
        moneyBoards.refreshAll(); scheduler.drain();
        TestBoard av=(TestBoard)alice.board, bv=(TestBoard)bob.board;
        TestObjective aNames=av.objectives.get("vx_money"), bNames=bv.objectives.get("vx_money");
        check("other players' money uses below-name slot",aNames != null && aNames.display==DisplaySlot.BELOW_NAME);
        check("no own below-name score",!aNames.scores.containsKey("Alice") && !bNames.scores.containsKey("Bob"));
        check("each viewer sees the other player's score",aNames.scores.containsKey("Bob") && bNames.scores.containsKey("Alice"));
        check("no tab objective or team suffixes",av.objectives.values().stream().noneMatch(x -> x.display==DisplaySlot.PLAYER_LIST) && av.teamCalls==0 && bv.teamCalls==0);
        economy.setBalanceExact(bob.id,"Bob",dev.superseller.veltryx.util.Money.parseExact("1l")); scheduler.drain();
        NumberFormat format=aNames.scores.get("Bob").format;
        check("other viewer sees full-range compact money",format instanceof NumberFormat.TestFixed
                && text(((NumberFormat.TestFixed)format).component).equals("§a$ §f1l"));
        check("sidebar uses full-range compact money",bv.objective.scores.keySet().stream().anyMatch(x -> x.contains("1l")));
        org.bukkit.event.player.PlayerQuitEvent quit=new org.bukkit.event.player.PlayerQuitEvent() {
            @Override public Player getPlayer(){return bob.proxy;}
        };
        moneyBoards.onQuit(quit); scheduler.drain();
        check("quit removes stale below-name score",!aNames.scores.containsKey("Bob"));

        dev.superseller.veltryx.command.EcoCommand command = new dev.superseller.veltryx.command.EcoCommand(
                economy, new Messages(new FakePlugin(Path.of("."))), new VeltryxConfig(new YamlConfiguration()));
        org.bukkit.command.Command dummy = new org.bukkit.command.Command() { };
        java.math.BigInteger prior= economy.getBalanceExact(alice.id);
        economy.setBalanceExact(alice.id,"Alice",dev.superseller.veltryx.util.Money.parseExact("3l"));
        command.onCommand(alice.proxy,dummy,"eco",new String[]{"pay","Bob","1.5l"}); scheduler.drain();
        eq("eco pay accepts l and debits exactly",economy.getBalanceExact(alice.id),dev.superseller.veltryx.util.Money.parseExact("1.5l"));
        eq("eco pay credits recipient exactly",economy.getBalanceExact(bob.id),dev.superseller.veltryx.util.Money.parseExact("2.5l"));
        command.onCommand(alice.proxy,dummy,"eco",new String[]{"set","Bob","2g"}); scheduler.drain();
        eq("admin set accepts g",economy.getBalanceExact(bob.id),dev.superseller.veltryx.util.Money.parseExact("2g"));
        command.onCommand(alice.proxy,dummy,"eco",new String[]{"reset","Bob"}); scheduler.drain();
        eq("admin reset needs no amount",economy.getBalanceExact(bob.id),java.math.BigInteger.ZERO);
        economy.setBalanceExact(alice.id,"Alice",prior); scheduler.drain();
    }

    private static void click(Object listener, PlayerState state, FakeScheduler scheduler, int slot, boolean right, boolean shift) {
        Click e = new Click(state.open, state.open, state.proxy, slot, right, shift);
        if (listener instanceof ShopGui g) g.onClick(e); else if (listener instanceof ShopExtrasGui g) g.onClick(e); else if (listener instanceof OrderGui g) g.onClick(e);
        check("GUI click cancelled", e.cancelled); scheduler.drain();
    }
    private static String text(Component c) { return ((StubComponent)c).content; }
    private static void check(String name, boolean ok) { if (ok) passed++; else { failed++; System.out.println("FAIL " + name); } }
    private static void eq(String name, Object actual, Object expected) { check(name + " expected=" + expected + " actual=" + actual, Objects.equals(actual, expected)); }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false; if (type == long.class) return 0L;
        if (type == double.class) return 0.0; if (type == float.class) return 0f;
        if (type == short.class) return (short)0; if (type == byte.class) return (byte)0;
        if (type == char.class) return '\0'; return 0;
    }
    private static final class FakePlugin extends JavaPlugin {
        final Path dir; FakePlugin(Path dir) { this.dir=dir; }
        @Override public java.io.File getDataFolder() { return dir.toFile(); }
        @Override public Logger getLogger() { return LOG; }
        @Override public java.io.InputStream getResource(String name) { try { return Files.newInputStream(RES.resolve(name)); } catch (Exception e) { return null; } }
    }
    private static final class FakeScheduler implements BukkitScheduler {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        long period; Runnable timer;
        BukkitTask add(Runnable r) { tasks.add(r); return () -> { }; }
        public BukkitTask runTask(Plugin p, Runnable r) { return add(r); }
        public BukkitTask runTaskAsynchronously(Plugin p, Runnable r) { return add(r); }
        public BukkitTask runTaskLater(Plugin p, Runnable r, long delay) { return add(r); }
        public BukkitTask runTaskTimer(Plugin p, Runnable r, long delay, long period) { this.period=period; this.timer=r; return () -> { }; }
        void drain() { int n=0; while (!tasks.isEmpty()) { if (++n > 10000) throw new IllegalStateException("scheduler loop"); tasks.removeFirst().run(); } }
    }
    private static final class TestInventory implements PlayerInventory {
        final InventoryHolder holder; final ItemStack[] items; int capacity = Integer.MAX_VALUE;
        TestInventory(InventoryHolder holder, int size) { this.holder=holder; this.items=new ItemStack[size]; }
        public void setItem(int index, ItemStack item) { items[index]=item; }
        public ItemStack getItem(int index) { return items[index]; }
        public int getSize() { return items.length; }
        public ItemStack[] getContents() { return items; }
        public InventoryHolder getHolder() { return holder; }
        public ItemStack getChestplate() { return null; }
        public ItemStack getItemInMainHand() { return items[0]; }
        public void setItemInMainHand(ItemStack item) { items[0]=item; }
        public HashMap<Integer,ItemStack> addItem(ItemStack... additions) {
            HashMap<Integer,ItemStack> left = new HashMap<>();
            for (int j=0;j<additions.length;j++) {
                ItemStack stack=additions[j]; int amount=stack.getAmount(); int accept=Math.min(amount,capacity);
                int slot=-1; for(int i=0;i<items.length;i++) if(items[i]==null || items[i].getAmount()<=0 || items[i].getType()==Material.AIR) { slot=i; break; }
                if(slot<0) accept=0;
                if(accept>0) { stack.setAmount(accept); items[slot]=stack; if(capacity!=Integer.MAX_VALUE) capacity-=accept; }
                if(accept<amount) left.put(j,new ItemStack(stack.getType(),amount-accept));
            }
            return left;
        }
        int count(Material m) { int n=0; for(ItemStack s:items) if(s!=null&&s.getType()==m)n+=s.getAmount(); return n; }
        ItemStack last(Material m) { ItemStack found=null; for(ItemStack s:items) if(s!=null&&s.getType()==m&&s.getAmount()>0) found=s; return found; }
    }
    private static final class TestPdc implements PersistentDataContainer {
        final Map<NamespacedKey,Object> map=new HashMap<>();
        public <T,Z> void set(NamespacedKey k, PersistentDataType<T,Z> t, Z v) { map.put(k,v); }
        @SuppressWarnings("unchecked") public <T,Z> Z get(NamespacedKey k, PersistentDataType<T,Z> t) { return (Z)map.get(k); }
        @SuppressWarnings("unchecked") public <T,Z> T getOrDefault(NamespacedKey k, PersistentDataType<T,Z> t, T def) { return (T)map.getOrDefault(k,def); }
        public void remove(NamespacedKey k) { map.remove(k); }
        public <T,Z> boolean has(NamespacedKey k, PersistentDataType<T,Z> t) { return map.containsKey(k); }
    }
    private static final class PlayerState implements InvocationHandler {
        final UUID id=UUID.randomUUID(); final TestInventory inventory=new TestInventory(null, 36); final TestPdc pdc=new TestPdc();
        final Map<PotionEffectType,PotionEffect> effects=new HashMap<>(); final World world; final Player proxy; final String name;
        Inventory open; ItemStack cursor; Scoreboard board; Player killer; boolean allowSell=true, signOpened, inWater, inRain, teleportSuccess=true, online=true;
        int blockChanges, riptides, durabilityUses, sounds; String lastSound; double health=20, absorption; Location teleportDestination;
        java.util.concurrent.CompletableFuture<Boolean> teleportFuture;
        PlayerState(World world) { this(world, "Alice"); }
        PlayerState(World world, String name) { this.world=world; this.name=name; proxy=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},this); }
        public Object invoke(Object o, Method m, Object[] a) {
            return switch(m.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "isOnline" -> online;
                case "hasPermission" -> !"veltryx.sell".equals(a[0]) || allowSell;
                case "getKiller" -> killer; case "setScoreboard" -> { board=(Scoreboard)a[0]; yield null; }
                case "getScoreboard" -> board; case "getGameMode" -> GameMode.SURVIVAL;
                case "playSound" -> { sounds++; lastSound=(String)a[1]; yield null; }
                case "setItemOnCursor" -> { cursor=(ItemStack)a[0]; yield null; }
                case "openVirtualSign" -> { signOpened=true; yield null; }
                case "sendBlockChange" -> { blockChanges++; yield null; }
                case "getInventory" -> inventory; case "getWorld" -> world;
                case "getLocation" -> new Location(world,100,64,200) { @Override public org.bukkit.util.Vector getDirection(){return new org.bukkit.util.Vector(0,0,1);} };
                case "getVelocity" -> new org.bukkit.util.Vector(0,0,0);
                case "isInWater" -> inWater; case "isInRain" -> inRain;
                case "damageItemStack" -> { durabilityUses++; yield null; }
                case "startRiptideAttack" -> { riptides++; yield null; }
                case "getHealth" -> health; case "setHealth" -> {health=(Double)a[0]; yield null;}
                case "getAbsorptionAmount" -> absorption; case "setAbsorptionAmount" -> {absorption=(Double)a[0]; yield null;}
                case "teleportAsync" -> {teleportDestination=(Location)a[0]; yield teleportFuture != null ? teleportFuture : java.util.concurrent.CompletableFuture.completedFuture(teleportSuccess);}
                case "getPersistentDataContainer" -> pdc; case "getPotionEffect" -> effects.get(a[0]);
                case "addPotionEffect" -> { PotionEffect e=(PotionEffect)a[0]; effects.put(e.getType(),e); yield true; }
                case "removePotionEffect" -> { effects.remove(a[0]); yield null; }
                case "openInventory" -> { open=(Inventory)a[0]; yield null; }
                case "closeInventory" -> { open=null; yield null; }
                case "equals" -> o==a[0]; case "hashCode" -> id.hashCode(); case "toString" -> "Alice";
                default -> defaultValue(m.getReturnType());
            };
        }
    }
    private static final class BalanceData implements InvocationHandler {
        final Map<UUID,java.math.BigInteger> balances=new HashMap<>();
        final Map<UUID,Long> shards=new HashMap<>(), kills=new HashMap<>(), deaths=new HashMap<>();
        final Map<UUID,List<HomeLocation>> homes = new HashMap<>();
        final Map<String,BuyOrder> orders = new HashMap<>();
        boolean failOrderWrites, failNextBalance;
        final DataStore proxy=(DataStore)Proxy.newProxyInstance(DataStore.class.getClassLoader(),new Class<?>[]{DataStore.class},this);
        public Object invoke(Object o, Method m, Object[] a) {
            return switch (m.getName()) {
                case "getBalance" -> dev.superseller.veltryx.util.Money.legacyLong(balances.getOrDefault((UUID)a[0],java.math.BigInteger.ZERO));
                case "getBalanceExact" -> balances.getOrDefault((UUID)a[0],java.math.BigInteger.ZERO);
                case "setBalance" -> { balances.put((UUID)a[0],java.math.BigInteger.valueOf((Long)a[2])); yield null; }
                case "setBalanceExact" -> { if(failNextBalance){failNextBalance=false;throw new IllegalStateException("simulated balance failure");} balances.put((UUID)a[0],(java.math.BigInteger)a[2]); yield null; }
                case "allBuyOrders" -> List.copyOf(orders.values());
                case "saveBuyOrder" -> {if(failOrderWrites)throw new IllegalStateException("simulated order failure"); BuyOrder order=(BuyOrder)a[0];orders.put(order.id(),order);yield null;}
                case "setBalancesExact" -> { for (dev.superseller.veltryx.model.Account account : (List<dev.superseller.veltryx.model.Account>) a[0]) balances.put(account.uuid(),account.balanceExact()); yield null; }
                case "shards" -> shards.getOrDefault((UUID)a[0],0L);
                case "setShards" -> { shards.put((UUID)a[0],(Long)a[1]); yield null; }
                case "kills" -> kills.getOrDefault((UUID)a[0],0L);
                case "setKills" -> { kills.put((UUID)a[0],(Long)a[1]); yield null; }
                case "homesFor" -> homes.getOrDefault((UUID)a[0], List.of());
                case "deaths" -> deaths.getOrDefault((UUID)a[0],0L);
                case "setDeaths" -> { deaths.put((UUID)a[0],(Long)a[1]); yield null; }
                default -> defaultValue(m.getReturnType());
            };
        }
    }
    private static final class Death extends org.bukkit.event.entity.PlayerDeathEvent {
        final Player player; Death(Player p) { player=p; } @Override public Player getEntity() { return player; }
    }
    private static final class TestBoard implements Scoreboard {
        TestObjective objective; final Map<String,TestObjective> objectives=new HashMap<>(); int teamCalls;
        public Objective registerNewObjective(String name, Criteria criteria, Component title, RenderType render) {
            TestObjective result=new TestObjective(this,name,title); objectives.put(name,result);
            if(name.equals("veltryx")) objective=result; return result;
        }
        public Objective getObjective(String name) { return objectives.get(name); }
        public Team registerNewTeam(String name) { teamCalls++; return null; }
        public Team getTeam(String name) { teamCalls++; return null; }
        public void resetScores(String entry) { for(TestObjective obj:objectives.values()) obj.scores.remove(entry); }
    }
    private static final class TestObjective implements Objective {
        final TestBoard board; final String name; Component title; DisplaySlot display; final Map<String,TestScore> scores=new LinkedHashMap<>();
        TestObjective(TestBoard board,String name,Component title){this.board=board;this.name=name;this.title=title;}
        public String getName(){return name;} public Component displayName(){return title;} public void displayName(Component c){title=c;}
        public String getCriteria(){return "dummy";} public Scoreboard getScoreboard(){return board;}
        public void setDisplaySlot(DisplaySlot s){display=s;} public DisplaySlot getDisplaySlot(){return display;}
        public RenderType getRenderType(){return RenderType.INTEGER;}
        public Score getScore(String entry){return scores.computeIfAbsent(entry,e->new TestScore(this,e));} public void unregister(){}
    }
    private static final class TestScore implements Score {
        final TestObjective objective; final String entry; NumberFormat format; int score;
        TestScore(TestObjective objective,String entry){this.objective=objective;this.entry=entry;}
        public NumberFormat numberFormat(){return format;} public void numberFormat(NumberFormat f){format=f;}
        public String getEntry(){return entry;} public Objective getObjective(){return objective;}
        public int getScore(){return score;} public void setScore(int s){score=s;}
        public void resetScore(){objective.scores.remove(entry);} public boolean isScoreSet(){return true;}
    }
    private static final class Click extends InventoryClickEvent {
        final Inventory top, clicked; final Player player; final int slot; final boolean right, shift; boolean cancelled; ItemStack cursor;
        Click(Inventory top, Inventory clicked, Player player, int slot, boolean right, boolean shift) { this.top=top;this.clicked=clicked;this.player=player;this.slot=slot;this.right=right;this.shift=shift; }
        @Override public Inventory getInventory() { return top; } @Override public Inventory getClickedInventory() { return clicked; }
        @Override public ItemStack getCursor() { return cursor; }
        @Override public HumanEntity getWhoClicked() { return player; } @Override public int getSlot() { return slot; }
        @Override public boolean isRightClick() { return right; } @Override public boolean isShiftClick() { return shift; }
        @Override public ClickType getClick() { return shift ? (right ? ClickType.SHIFT_RIGHT : ClickType.SHIFT_LEFT) : (right ? ClickType.RIGHT : ClickType.LEFT); }
        @Override public void setCancelled(boolean b) { cancelled=b; } @Override public boolean isCancelled() { return cancelled; }
    }
    private static final class Close extends InventoryCloseEvent {
        final Inventory inventory; final Player player; Close(Inventory i, Player p){inventory=i;player=p;}
        @Override public Inventory getInventory(){return inventory;} @Override public HumanEntity getPlayer(){return player;}
    }
    private static final class Join extends org.bukkit.event.player.PlayerJoinEvent {
        final Player player; Join(Player p){player=p;} @Override public Player getPlayer(){return player;}
    }
    private static final class BreakBlock extends org.bukkit.event.block.BlockBreakEvent {
        final Player player; final org.bukkit.block.Block block; BreakBlock(Player p,org.bukkit.block.Block b){player=p;block=b;}
        @Override public Player getPlayer(){return player;} @Override public org.bukkit.block.Block getBlock(){return block;}
    }
    private static final class Interact extends org.bukkit.event.player.PlayerInteractEvent {
        final Player player; Interact(Player p){player=p;} @Override public Player getPlayer(){return player;}
        @Override public org.bukkit.event.block.Action getAction(){return org.bukkit.event.block.Action.RIGHT_CLICK_AIR;}
        @Override public EquipmentSlot getHand(){return EquipmentSlot.HAND;}
    }
    private static final class VoidDamage extends org.bukkit.event.entity.EntityDamageEvent {
        final Player player; final double damage; boolean cancelled; VoidDamage(Player p,double d){player=p;damage=d;}
        @Override public Entity getEntity(){return player;} @Override public DamageCause getCause(){return DamageCause.VOID;}
        @Override public double getFinalDamage(){return damage;} @Override public void setCancelled(boolean b){cancelled=b;} @Override public boolean isCancelled(){return cancelled;}
    }
    private static final class Lunge extends io.papermc.paper.event.entity.EntityLungeEvent {
        final Player player; Lunge(Player p){player=p;} @Override public Entity getEntity(){return player;} @Override public int getLungePower(){return 3;}
    }
    private static final class Exhaust extends org.bukkit.event.entity.EntityExhaustionEvent {
        final Player player; final ExhaustionReason reason; float value;
        Exhaust(Player p, ExhaustionReason r, float v){player=p;reason=r;value=v;}
        @Override public org.bukkit.entity.HumanEntity getEntity(){return player;} @Override public ExhaustionReason getExhaustionReason(){return reason;}
        @Override public float getExhaustion(){return value;} @Override public void setExhaustion(float v){value=v;}
    }
    private static final class SignInput extends io.papermc.paper.event.packet.UncheckedSignChangeEvent {
        final Player player; final io.papermc.paper.math.BlockPosition pos; final List<Component> lines; boolean cancelled;
        SignInput(Player player,int x,int y,int z,String... lines) { this.player=player; this.pos=io.papermc.paper.math.Position.block(x,y,z); this.lines=Arrays.stream(lines).map(StubComponent::new).map(c -> (Component)c).toList(); }
        @Override public Player getPlayer(){return player;} @Override public io.papermc.paper.math.BlockPosition getEditedBlockPosition(){return pos;}
        @Override public List<Component> lines(){return lines;} @Override public void setCancelled(boolean b){cancelled=b;} @Override public boolean isCancelled(){return cancelled;}
    }
    private static final class Drag extends InventoryDragEvent {
        final Inventory top,bottom; boolean cancelled; Set<Integer> raw=Set.of(); Drag(Inventory top,Inventory bottom){this.top=top;this.bottom=bottom;}
        @Override public Set<Integer> getRawSlots(){return raw;}
        @Override public InventoryView getView() { return new InventoryView() { public Inventory getTopInventory(){return top;} public Inventory getBottomInventory(){return bottom;} public ItemStack getCursor(){return null;} public void setCursor(ItemStack s){} }; }
        @Override public void setCancelled(boolean b){cancelled=b;}
    }
    private static final class Chat extends AsyncPlayerChatEvent {
        final Player player; final String value; boolean cancelled; Chat(Player p,String s){player=p;value=s;}
        @Override public Player getPlayer(){return player;} @Override public String getMessage(){return value;}
        @Override public void setCancelled(boolean b){cancelled=b;} @Override public boolean isCancelled(){return cancelled;}
    }
    private static final class Natural extends PlayerNaturallySpawnCreaturesEvent {
        final Player player; boolean cancelled; Natural(Player p){player=p;} @Override public Player getPlayer(){return player;}
        @Override public void setCancelled(boolean b){cancelled=b;} @Override public boolean isCancelled(){return cancelled;}
    }
    private static final class Phantom extends PhantomPreSpawnEvent {
        final Entity player; boolean cancelled; Phantom(Entity p){player=p;} @Override public Entity getSpawningEntity(){return player;}
        @Override public void setCancelled(boolean b){cancelled=b;} @Override public boolean isCancelled(){return cancelled;}
    }
    private static final class VisionRemoved extends org.bukkit.event.entity.EntityPotionEffectEvent {
        final Player player; VisionRemoved(Player player) { this.player = player; }
        @Override public Entity getEntity() { return player; }
        @Override public PotionEffectType getModifiedType() { return PotionEffectType.NIGHT_VISION; }
        @Override public PotionEffect getNewEffect() { return null; }
    }
    private static final class TestMap implements MapView { int x,z; Scale scale; public void setScale(Scale s){scale=s;} public void setCenterX(int x){this.x=x;} public void setCenterZ(int z){this.z=z;} }
    private static final class TestWorld implements InvocationHandler {
        final World proxy=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},this);
        boolean findStructure=true, findUnexplored, fakeBlock, unlimitedDrop; int breaks; Material blockType=Material.STONE; UUID dropOwner; ItemStack dropped;
        final List<String> searched=new ArrayList<>(); TestMap lastMap;
        final org.bukkit.block.data.BlockData blockData = new org.bukkit.block.data.BlockData() { public org.bukkit.block.data.BlockData clone(){return this;} };
        final org.bukkit.block.Block block = (org.bukkit.block.Block)Proxy.newProxyInstance(org.bukkit.block.Block.class.getClassLoader(),new Class<?>[]{org.bukkit.block.Block.class},
                (o,m,a) -> switch(m.getName()) {
                    case "getBlockData" -> blockData; case "getType" -> blockType; case "getWorld" -> proxy;
                    case "getX" -> 100; case "getY" -> 64; case "getZ" -> 200;
                    case "getLocation" -> new Location(proxy, 100, 64, 200);
                    case "breakNaturally" -> { breaks++; yield true; }
                    default -> defaultValue(m.getReturnType());
                });
        public Object invoke(Object o,Method m,Object[] a) {
            if(m.getName().equals("equals")) return o == a[0];
            if(m.getName().equals("hashCode")) return System.identityHashCode(o);
            if(m.getName().equals("getBlockAt")) return fakeBlock ? block : null;
            if(m.getName().equals("isChunkLoaded")) return true;
            if(m.getName().equals("getSpawnLocation")) return new Location(proxy, 0, 80, 0);
            if(m.getName().equals("dropItemNaturally")) {
                dropped=(ItemStack)a[1];
                return Proxy.newProxyInstance(org.bukkit.entity.Item.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Item.class},
                        (obj,method,args) -> { if(method.getName().equals("setOwner")) dropOwner=(UUID)args[0];
                            if(method.getName().equals("setUnlimitedLifetime")) unlimitedDrop=(Boolean)args[0];
                            return defaultValue(method.getReturnType()); });
            }
            if(m.getName().equals("locateNearestStructure")) {
                searched.add(((Structure)a[1]).getKey().toString()); findUnexplored=(boolean)a[3];
                if(!findStructure)return null;
                return (StructureSearchResult)() -> new Location(proxy,1234,64,-567);
            }
            if(m.getName().equals("getEnvironment"))return World.Environment.NORMAL;
            return defaultValue(m.getReturnType());
        }
    }
    private static final class TestRegistries implements RegistryAccess {
        @SuppressWarnings("unchecked") public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
            if(key==RegistryKey.STRUCTURE) return (Registry<T>)(Registry<Structure>)(id -> new Structure(){public NamespacedKey getKey(){return id;}});
            if(key==RegistryKey.MAP_DECORATION_TYPE) return (Registry<T>)(Registry<MapCursor.Type>)(id -> () -> id);
            if(key==RegistryKey.ENCHANTMENT) return (Registry<T>)(Registry<Enchantment>)(id -> new Enchantment(){public NamespacedKey getKey(){return id;}});
            throw new IllegalArgumentException("unknown registry");
        }
    }
}
