package dev.superseller.veltryx.smoke;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.home.HomeService;
import dev.superseller.veltryx.home.HomesGui;
import dev.superseller.veltryx.model.HomeLocation;
import dev.superseller.veltryx.settings.PreferencesStore;
import dev.superseller.veltryx.settings.SettingsGui;
import dev.superseller.veltryx.settings.SettingsListener;
import dev.superseller.veltryx.shop.*;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.stats.ScoreboardService;
import dev.superseller.veltryx.stats.StatsService;
import dev.superseller.veltryx.stats.StatsListener;
import dev.superseller.veltryx.shards.ShardService;
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
        eq("sell GUI star", state.open.getItem(49).getType(), Material.NETHER_STAR);
        before = economy.getBalance(state.id);
        Click sell = new Click(state.open, state.open, player, 49, false, false);
        sellGui.onClick(sell); scheduler.drain();
        check("sell all grants payout", economy.getBalance(state.id) > before);
        eq("sell all removes sold items", state.inventory.count(Material.COPPER_INGOT), 0);

        extras.openQuickshop(player); scheduler.drain();
        eq("quick GUI has 45 configurable slots", state.open.getSize(), 45);
        eq("quick empty slot", state.open.getItem(2).getType(), Material.GRAY_STAINED_GLASS_PANE);
        click(extras, state, scheduler, 2, false, false);
        check("quick prompt closes GUI on next tick", state.open == null);
        Chat query = new Chat(player, "NETHERITE_INGOT"); extras.onChat(query);
        check("quick search is private", query.cancelled); scheduler.drain();
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

        // Only one private chat prompt per player; search must not rename a home.
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
        Chat searchWhileRenaming = new Chat(player, "diamond"); extras.onChat(searchWhileRenaming); homes.onChat(searchWhileRenaming); scheduler.drain();
        check("search consumed privately instead of renaming home", searchWhileRenaming.cancelled);
        Chat normal = new Chat(player, "ordinary chat"); homes.onChat(normal);
        check("superseded home prompt does not capture later chat", !normal.cancelled);
        extras.openSearchPrompt(player); scheduler.drain();
        Chat alreadyCancelled = new Chat(player, "ignored"); alreadyCancelled.cancelled = true; extras.onChat(alreadyCancelled);
        homes.openDetail(player, "base"); scheduler.drain();
        homes.onClick(new Click(state.open, state.open, player, 12, false, false)); scheduler.drain();
        Chat cancelRename = new Chat(player, "cancel"); extras.onChat(cancelRename);
        check("home prompt cancels pending search", !cancelRename.cancelled);
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
    }

    private static void click(Object listener, PlayerState state, FakeScheduler scheduler, int slot, boolean right, boolean shift) {
        Click e = new Click(state.open, state.open, state.proxy, slot, right, shift);
        if (listener instanceof ShopGui g) g.onClick(e); else if (listener instanceof ShopExtrasGui g) g.onClick(e);
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
        Inventory open; Scoreboard board; Player killer; boolean allowSell=true;
        PlayerState(World world) { this(world, "Alice"); }
        PlayerState(World world, String name) { this.world=world; this.name=name; proxy=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},this); }
        public Object invoke(Object o, Method m, Object[] a) {
            return switch(m.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "isOnline" -> true;
                case "hasPermission" -> !"veltryx.sell".equals(a[0]) || allowSell;
                case "getKiller" -> killer; case "setScoreboard" -> { board=(Scoreboard)a[0]; yield null; }
                case "getScoreboard" -> board;
                case "getInventory" -> inventory; case "getWorld" -> world; case "getLocation" -> new Location(world,100,64,200);
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
        final Map<UUID,Long> balances=new HashMap<>(), shards=new HashMap<>(), kills=new HashMap<>(), deaths=new HashMap<>();
        final Map<UUID,List<HomeLocation>> homes = new HashMap<>();
        final DataStore proxy=(DataStore)Proxy.newProxyInstance(DataStore.class.getClassLoader(),new Class<?>[]{DataStore.class},this);
        public Object invoke(Object o, Method m, Object[] a) {
            return switch (m.getName()) {
                case "getBalance" -> balances.getOrDefault((UUID)a[0],0L);
                case "setBalance" -> { balances.put((UUID)a[0],(Long)a[2]); yield null; }
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
        TestObjective objective;
        public Objective registerNewObjective(String name, Criteria criteria, Component title, RenderType render) { return objective=new TestObjective(this, name, title); }
        public Objective getObjective(String name) { return objective; }
        public Team registerNewTeam(String name) { return null; }
        public Team getTeam(String name) { return null; }
        public void resetScores(String entry) { if(objective!=null)objective.scores.remove(entry); }
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
        final Inventory top, clicked; final Player player; final int slot; final boolean right, shift; boolean cancelled;
        Click(Inventory top, Inventory clicked, Player player, int slot, boolean right, boolean shift) { this.top=top;this.clicked=clicked;this.player=player;this.slot=slot;this.right=right;this.shift=shift; }
        @Override public Inventory getInventory() { return top; } @Override public Inventory getClickedInventory() { return clicked; }
        @Override public HumanEntity getWhoClicked() { return player; } @Override public int getSlot() { return slot; }
        @Override public boolean isRightClick() { return right; } @Override public boolean isShiftClick() { return shift; }
        @Override public void setCancelled(boolean b) { cancelled=b; } @Override public boolean isCancelled() { return cancelled; }
    }
    private static final class Drag extends InventoryDragEvent {
        final Inventory top,bottom; boolean cancelled; Drag(Inventory top,Inventory bottom){this.top=top;this.bottom=bottom;}
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
        boolean findStructure=true, findUnexplored; final List<String> searched=new ArrayList<>(); TestMap lastMap;
        public Object invoke(Object o,Method m,Object[] a) {
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
