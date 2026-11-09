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
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.market.AhCommand;
import dev.superseller.veltryx.market.AhGui;
import dev.superseller.veltryx.market.AhService;
import dev.superseller.veltryx.market.MarketPricePrompt;
import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
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
        testAdvancedCatalog();
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
                check("buy and sell positive; sell below buy " + e.material(), e.buyCents() > 0 && e.sellCents() > 0 && e.sellCents() < e.buyCents());
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

    private static void testAdvancedCatalog() {
        List<PriceTable.Entry> all = ShopSearch.find(table, "*");
        eq("advanced search browses all catalog entries", all.size(), 1530);
        check("advanced search excludes Elytra and dragon egg", all.stream().noneMatch(e -> e.material().equals("ELYTRA") || e.material().equals("DRAGON_EGG")));
        eq("namespaced ID searches exact item", ShopSearch.find(table, "minecraft:diamond_sword").get(0).material(), "DIAMOND_SWORD");
        eq("search words can be reversed", ShopSearch.find(table, "sword diamond").get(0).material(), "DIAMOND_SWORD");
        check("search combines category and words", ShopSearch.find(table, "cat:tools sword diamond").stream().anyMatch(e -> e.material().equals("DIAMOND_SWORD")));
        check("category excludes unrelated items", ShopSearch.find(table, "category:wood sword").isEmpty());
        check("buy price filter uses cents", ShopSearch.find(table, "* buy<=0.50").stream().allMatch(e -> e.buyCents() <= 50));
        check("sell price filter uses cents", ShopSearch.find(table, "* sell>=1").stream().allMatch(e -> e.sellCents() >= 100));
        check("invalid price filter is not broadened", ShopSearch.find(table, "* buy<=oops").isEmpty());
        check("blank search is not broadened", ShopSearch.find(table, "   ").isEmpty());
        for (String ingredient : List.of("GLASS_BOTTLE", "NETHER_WART", "SUGAR", "RABBIT_FOOT", "FERMENTED_SPIDER_EYE",
                "SPIDER_EYE", "GLISTERING_MELON_SLICE", "REDSTONE", "GLOWSTONE_DUST", "GUNPOWDER", "DRAGON_BREATH", "ARROW"))
            check("brewing ingredient has modeled cost " + ingredient, Double.isFinite(table.modeledCost(ingredient)) && table.modeledCost(ingredient) > 0);
        for (String material : List.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW")) {
            for (ChoiceCatalog.PotionOption option : ChoiceCatalog.potions()) {
                String key = option.key();
                double cost = VariantPricing.potionCost(table, material, key);
                long buy = new Purchase(material, key, 0).unitPrice(table);
                long sell = VariantPricing.sellCents(table, material, key);
                check("modeled brewing cost " + material + " " + key, Double.isFinite(cost) && cost > 0);
                check("all potion forms have buy price " + material + " " + key, buy > 0 && buy + 1.0e-6 >= cost);
                check("all potion forms have positive conservative sale " + material + " " + key,
                        sell > 0 && sell < buy && sell * 2.0 <= cost + 1.0e-6);
                check("effect details are available " + material + " " + key,
                        PotionDisplay.effects(material, key).stream().noneMatch(line -> line.contains("unavailable")));
                double arrowCost = VariantPricing.potionCost(table, "TIPPED_ARROW", key);
                double lingeringCost = VariantPricing.potionCost(table, "LINGERING_POTION", key);
                check("eight arrows include a lingering potion and eight arrows " + key,
                        Math.abs(8 * arrowCost - lingeringCost - 8 * table.modeledCost("ARROW")) < 1.0e-6);
            }
        }
        check("unknown potion form fails closed", Double.isNaN(VariantPricing.potionCost(table, "STONE", "WATER")));
        check("mundane cost includes cheap stone start-mix", VariantPricing.potionCost(table, "POTION", "MUNDANE")
                <= table.modeledCost("GLASS_BOTTLE") + table.modeledCost("STONE") / 3.0 + 1.0e-8);
        check("redstone variant costs more than base", new Purchase("POTION", "LONG_SWIFTNESS", 0).unitPrice(table)
                > new Purchase("POTION", "SWIFTNESS", 0).unitPrice(table));
        check("ordinary potion shows actual strength and duration", PotionDisplay.effects("POTION", "SWIFTNESS").stream().anyMatch(s -> s.contains("Speed 1: §f3:00")));
        check("extended potion shows actual duration", PotionDisplay.effects("POTION", "LONG_SWIFTNESS").stream().anyMatch(s -> s.contains("8:00")));
        check("strong potion shows strength and duration", PotionDisplay.effects("POTION", "STRONG_SWIFTNESS").stream().anyMatch(s -> s.contains("Speed 2: §f1:30")));
        check("lingering duration is a quarter", PotionDisplay.effects("LINGERING_POTION", "STRONG_SWIFTNESS").stream().anyMatch(s -> s.contains("0:22.5")));
        check("tipped arrow duration is an eighth to the tick", PotionDisplay.effects("TIPPED_ARROW", "STRONG_SWIFTNESS").stream().anyMatch(s -> s.contains("0:11.25")));
        check("splash duration notes direct-hit limitation", PotionDisplay.effects("SPLASH_POTION", "SWIFTNESS").stream().anyMatch(s -> s.contains("direct hit")));
        check("instant potion is described as instant", PotionDisplay.effects("POTION", "STRONG_HEALING").stream().anyMatch(s -> s.contains("instant")));
        check("multi-effect potion shows both effects", PotionDisplay.effects("POTION", "TURTLE_MASTER").stream().anyMatch(s -> s.contains("Slowness 4"))
                && PotionDisplay.effects("POTION", "TURTLE_MASTER").stream().anyMatch(s -> s.contains("Resistance 3")));
        check("water potion has no effect", PotionDisplay.effects("POTION", "WATER").stream().anyMatch(s -> s.contains("No status effect")));
        PriceTable disabled = PriceTable.parse(shopText.replace("    POTION: { buy: 35.00, sell: 0.04 }",
                "    POTION: { buy: 34.99, sell: 0.00 }"), recipes, n -> true);
        eq("custom disabled potion sell remains disabled", VariantPricing.sellCents(disabled, "POTION", "SWIFTNESS"), 0L);
        PriceTable lowered = PriceTable.parse(shopText.replace("    POTION: { buy: 35.00, sell: 0.04 }",
                "    POTION: { buy: 35.00, sell: 0.02 }"), recipes, n -> true);
        check("lower configured potion sale lowers variant payout", VariantPricing.sellCents(lowered, "POTION", "SWIFTNESS")
                < VariantPricing.sellCents(table, "POTION", "SWIFTNESS"));
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
        String previousShop = shopText.replace("POTION: { buy: 35.00, sell: 0.04 }", "POTION: { buy: 35.00, sell: 0.00 }")
                .replace("ENCHANTED_BOOK: { buy: 250.00, sell: 0.38 }", "ENCHANTED_BOOK: { buy: 250.00, sell: 0.00 }")
                .replace("FILLED_MAP: { buy: 150.00, sell: 0.50 }", "FILLED_MAP: { buy: 150.00, sell: 0.00 }");
        PriceTable previous = PriceTable.parse(previousShop, recipes, name -> true);
        check("old buy-only potion default migrates to a sell price", previous.sellCents("POTION") > 0);
        check("old buy-only book default migrates to a sell price", previous.sellCents("ENCHANTED_BOOK") > 0);
        check("old buy-only map default migrates to a sell price", previous.sellCents("FILLED_MAP") > 0);
        String customized = previousShop.replace("POTION: { buy: 35.00, sell: 0.00 }", "POTION: { buy: 99.00, sell: 0.00 }");
        eq("custom disabled variant price remains disabled", PriceTable.parse(customized, recipes, n -> true).sellCents("POTION"), 0L);
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
        YamlConfiguration oldDefaults=YamlConfiguration.loadConfiguration(new java.io.StringReader(Files.readString(RES.resolve("legacy-message-colors.yml"))));
        YamlConfiguration currentDefaults=YamlConfiguration.loadConfiguration(new java.io.StringReader(defaults));
        for (String key : List.of("ah.usage-sell","ah.bad-price","order.usage","order.bad-price","order.created")) {
            String oldValue=oldDefaults.getString(key), newValue=currentDefaults.getString(key);
            check("old marketplace default is recorded " + key,oldValue!=null && (oldValue.contains("per item") || oldValue.contains("each") || oldValue.contains("price-per-item")));
            eq("old marketplace message upgrades " + key,
                    Messages.mergeTemplates(Map.of(key,newValue),Map.of(key,oldValue),Map.of(key,oldValue)).get(key),newValue);
            eq("custom marketplace message remains " + key,
                    Messages.mergeTemplates(Map.of(key,newValue),Map.of(key,"server custom"),Map.of(key,oldValue)).get(key),"server custom");
        }
        for (String key : List.of("shop.nothing", "spawner.market-disabled")) {
            String oldValue = oldDefaults.getString(key), newValue = currentDefaults.getString(key);
            check("old changed default recorded " + key, oldValue != null && newValue != null && !oldValue.equals(newValue));
            eq("old changed message upgrades " + key,
                    Messages.mergeTemplates(Map.of(key,newValue),Map.of(key,oldValue),Map.of(key,oldValue)).get(key), newValue);
            eq("custom changed message remains " + key,
                    Messages.mergeTemplates(Map.of(key,newValue),Map.of(key,"server custom"),Map.of(key,oldValue)).get(key), "server custom");
        }
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
        testShardItemsAndLunge(dir, state, world, messages);
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
        // A stale displayed price must not charge more than the player saw.
        String reprice = shopText.replaceFirst("NETHERITE_INGOT: \\{ buy: [0-9]+\\.[0-9]{2}, sell: [0-9]+\\.[0-9]{2} }",
                "NETHERITE_INGOT: { buy: 200.00, sell: 1.00 }");
        Files.writeString(shopPath, reprice); shop.reload();
        before = economy.getBalance(state.id);
        int quickItemsBefore = state.inventory.count(Material.NETHERITE_INGOT);
        click(extras, state, scheduler, 2, false, false);
        eq("stale quick quote charges nothing", economy.getBalance(state.id), before);
        eq("stale quick quote delivers nothing", state.inventory.count(Material.NETHERITE_INGOT), quickItemsBefore);
        check("quick quote refresh shows exact new total", loreContains(state.open.getItem(2), Money.formatNumber(17 * 20_000L)));
        click(extras, state, scheduler, 2, false, false);
        eq("refreshed quick purchase uses current price", before - economy.getBalance(state.id), 17 * 20_000L);
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
        MarketPricePrompt sharedPrompt=new MarketPricePrompt(messages);
        sharedPrompt.attachPromptCancellation(p -> { homes.cancelPendingRename(p); extras.cancelPendingSearch(p); });
        extras.attachPromptCancellation(p -> { homes.cancelPendingRename(p); sharedPrompt.cancelPending(p); });
        homes.attachPromptCancellation(p -> { extras.cancelPendingSearch(p); sharedPrompt.cancelPending(p); });
        sharedPrompt.ask(player, input -> {throw new AssertionError("cancelled price prompt ran");}, () -> {}); scheduler.drain();
        homes.openDetail(player, "base"); scheduler.drain();
        homes.onClick(new Click(state.open, state.open, player, 12, false, false)); scheduler.drain();
        Chat renamedInstead=new Chat(player,"cancel");sharedPrompt.onChat(renamedInstead);
        check("home prompt cancels pending market price",!renamedInstead.cancelled);
        homes.onChat(renamedInstead);check("home consumes response after market cancelled",renamedInstead.cancelled);
        sharedPrompt.ask(player, input -> {throw new AssertionError("superseded market prompt ran");}, () -> {}); scheduler.drain();
        extras.openSearchPrompt(player);scheduler.drain();
        Chat notMarket=new Chat(player,"ordinary chat");sharedPrompt.onChat(notMarket);
        check("sign prompt cancels pending market price",!notMarket.cancelled);
        extras.cancelPendingSearch(player);

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

        testAdvancedShop(dir, world, scheduler, messages);
        testOrders(world, scheduler, messages);
        testOrderAnyMaterial(world, scheduler, messages);
        testAuction(world, scheduler, messages);
        testAuctionYaml(dir, world, scheduler, messages);
        testOrderTotals(world, scheduler, messages);
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

    private static ItemStack potionItem(Material material, PotionType type) {
        ItemStack item = new ItemStack(material, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta(); meta.setBasePotionType(type); item.setItemMeta(meta);
        return item;
    }
    private static ItemStack bookItem(String key, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK, 1);
        var enchant = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
        var meta = (EnchantmentStorageMeta) item.getItemMeta(); meta.addStoredEnchant(enchant, level, false); item.setItemMeta(meta);
        return item;
    }
    private static boolean loreContains(ItemStack stack, String value) {
        return stack != null && stack.getItemMeta() != null && stack.getItemMeta().lore() != null
                && stack.getItemMeta().lore().stream().anyMatch(line -> text(line).contains(value));
    }

    private static void testAdvancedShop(Path dir, TestWorld world, FakeScheduler scheduler, Messages messages) throws Exception {
        PlayerState state = new PlayerState(world.proxy, "ShopTester"); Player player = state.proxy;
        Bukkit.testOnlinePlayers = List.of(player);
        BalanceData data = new BalanceData();
        EconomyService economy = new EconomyService(data.proxy, new VeltryxConfig(new YamlConfiguration()));
        economy.setBalance(state.id, state.name, 10_000_000L);
        Path path = dir.resolve("advanced-shop.yml"); Files.writeString(path, shopText);
        ShopService shop = new ShopService(economy, path);
        ShopGui gui = new ShopGui(shop, messages);
        Path quickPath = dir.resolve("advanced-quick.properties");
        ShopExtrasGui extras = new ShopExtrasGui(shop, messages, quickPath, LOG);
        SellGui tray = new SellGui(shop, messages);
        gui.attachExtras(extras); extras.attachShopGui(gui); gui.attachSellOpener(tray::open);

        // Exercise command search, back-navigation, and a variant QuickShop slot end to end.
        PlayerState navigator = new PlayerState(world.proxy, "ShopNavigator");
        economy.setBalance(navigator.id, navigator.name, 1_000_000L);
        ShopCommand shopCommand = new ShopCommand(gui, shop, messages); shopCommand.attachExtras(extras);
        org.bukkit.command.Command command = new org.bukkit.command.Command() { };
        check("shop search is tab-completable", shopCommand.onTabComplete(navigator.proxy, command, "shop", new String[]{"se"}).contains("search"));
        shopCommand.onCommand(navigator.proxy, command, "shop", new String[]{"search", "minecraft:enchanted_book"}); scheduler.drain();
        eq("shop command searches namespaced book", navigator.open.getItem(0).getType(), Material.ENCHANTED_BOOK);
        click(extras, navigator, scheduler, 0, false, false);
        int bookIndex = -1;
        for (int i = 0; i < ChoiceCatalog.books().size(); i++) if (ChoiceCatalog.books().get(i).key().equals("SHARPNESS")) bookIndex = i;
        check("sharpness is present in the picker", bookIndex >= 0);
        click(extras, navigator, scheduler, bookIndex, false, false);
        eq("book choice opens levels", navigator.open.getSize(), 27);
        click(extras, navigator, scheduler, 22, false, false);
        eq("book level back restores enchantment picker", navigator.open.getItem(bookIndex).getType(), Material.ENCHANTED_BOOK);
        click(extras, navigator, scheduler, 49, false, false);
        eq("enchantment back restores the search", navigator.open.getItem(0).getType(), Material.ENCHANTED_BOOK);

        check("stone search has multiple pages", ShopSearch.find(shop.prices(), "stone").size() > 45);
        extras.openSearch(navigator.proxy, "stone", 1); scheduler.drain();
        Material pageItem = navigator.open.getItem(0).getType();
        click(extras, navigator, scheduler, 0, false, false);
        click(gui, navigator, scheduler, 44, false, false);
        eq("buy back restores the same search page", navigator.open.getItem(0).getType(), pageItem);
        eq("restored search page has previous control", navigator.open.getItem(45).getType(), Material.BLUE_STAINED_GLASS_PANE);

        world.fakeBlock = true;
        extras.openQuickshop(navigator.proxy); scheduler.drain();
        click(extras, navigator, scheduler, 3, false, false);
        SignInput quickQuery = new SignInput(navigator.proxy, 100, 64, 200, "minecraft:potion");
        extras.onSign(quickQuery); scheduler.drain();
        check("quick variant search is private", quickQuery.cancelled);
        eq("quick variant search finds potion", navigator.open.getItem(0).getType(), Material.POTION);
        click(extras, navigator, scheduler, 0, false, false);
        click(extras, navigator, scheduler, 48, false, false);
        eq("quick variant picker can switch form", navigator.open.getItem(0).getType(), Material.TIPPED_ARROW);
        int effectIndex = -1;
        for (int i = 0; i < ChoiceCatalog.potions().size(); i++) if (ChoiceCatalog.potions().get(i).key().equals("LONG_SWIFTNESS")) effectIndex = i;
        check("extended swiftness is present in the picker", effectIndex >= 0);
        click(extras, navigator, scheduler, effectIndex, false, false);
        click(extras, navigator, scheduler, 16, false, false);
        click(extras, navigator, scheduler, 22, false, false);
        Purchase quickPurchase = new Purchase("TIPPED_ARROW", "LONG_SWIFTNESS", 0);
        QuickShopStore quick = new QuickShopStore(quickPath, LOG); quick.load();
        eq("quick variant and amount persist", quick.get(navigator.id, 3), new QuickShopStore.QuickSlot(quickPurchase, 17));
        long quickBefore = economy.getBalance(navigator.id);
        long quickCost = quickPurchase.unitPrice(shop.prices()) * 17;
        check("quick variant shows exact total", loreContains(navigator.open.getItem(3), Money.formatNumber(quickCost)));
        Field liveSlotsField = ShopExtrasGui.class.getDeclaredField("slots"); liveSlotsField.setAccessible(true);
        QuickShopStore liveSlots = (QuickShopStore) liveSlotsField.get(extras);
        liveSlots.set(navigator.id, 3, new QuickShopStore.QuickSlot(Purchase.of("DIAMOND"), 2));
        click(extras, navigator, scheduler, 3, false, false);
        eq("changed quick slot charges nothing", economy.getBalance(navigator.id), quickBefore);
        eq("changed quick slot is refreshed instead of bought", navigator.open.getItem(3).getType(), Material.DIAMOND);
        eq("changed quick slot delivers no unexpected item", navigator.inventory.count(Material.DIAMOND), 0);
        liveSlots.set(navigator.id, 3, new QuickShopStore.QuickSlot(quickPurchase, 17));
        extras.openQuickshop(navigator.proxy); scheduler.drain();
        Click doubleQuick = new Click(navigator.open, navigator.open, navigator.proxy, 3, false, false);
        extras.onClick(doubleQuick); extras.onClick(doubleQuick); scheduler.drain();
        eq("quick double click charges once", quickBefore - economy.getBalance(navigator.id), quickCost);
        ItemStack quickItem = navigator.inventory.last(Material.TIPPED_ARROW);
        eq("quick variant delivers chosen amount", quickItem.getAmount(), 17);
        eq("quick variant delivers chosen effect", ((PotionMeta) quickItem.getItemMeta()).getBasePotionType(), PotionType.LONG_SWIFTNESS);
        click(extras, navigator, scheduler, 3, true, false);
        SignInput cancelEdit = new SignInput(navigator.proxy, 100, 64, 200, "cancel");
        extras.onSign(cancelEdit); scheduler.drain();
        eq("cancelled quick edit returns without losing slot", navigator.open.getItem(3).getType(), Material.TIPPED_ARROW);
        click(extras, navigator, scheduler, 3, true, true);
        quick.load(); check("quick variant clear persists", quick.get(navigator.id, 3) == null);
        long beforePlaceholder = economy.getBalance(navigator.id);
        liveSlots.set(navigator.id, 4, new QuickShopStore.QuickSlot(Purchase.of("DIAMOND"), 2));
        click(extras, navigator, scheduler, 4, false, false);
        eq("an empty-looking quick slot cannot buy an unseen item", economy.getBalance(navigator.id), beforePlaceholder);
        check("empty-looking quick slot opens configuration instead", navigator.open == null);
        SignInput cancelPlaceholder = new SignInput(navigator.proxy, 100, 64, 200, "cancel");
        extras.onSign(cancelPlaceholder); scheduler.drain();
        click(extras, navigator, scheduler, 4, true, true);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        sword.setData(DataComponentTypes.MAX_DAMAGE, 100);
        Damageable damaged = (Damageable) sword.getItemMeta(); damaged.setDamage(50); sword.setItemMeta(damaged);
        long wornValue = shop.sellUnitCents(sword);
        ItemStack fullSword = sword.clone(); Damageable fullMeta = (Damageable) fullSword.getItemMeta(); fullMeta.setDamage(0); fullSword.setItemMeta(fullMeta);
        check("sale valuation decreases with actual wear", wornValue > 0 && wornValue < shop.sellUnitCents(fullSword));
        ItemStack enchanted = fullSword.clone(); ItemMeta enchantedMeta = enchanted.getItemMeta(); enchantedMeta.addEnchant(Enchantment.SHARPNESS, 2, true); enchanted.setItemMeta(enchantedMeta);
        check("sale valuation considers enchantments", shop.sellUnitCents(enchanted) > shop.sellUnitCents(fullSword));
        state.inventory.setItem(0, sword.clone()); state.inventory.setItemInOffHand(fullSword.clone());
        extras.openSearch(player, "sword diamond", 0); scheduler.drain();
        eq("advanced GUI returns exact sword", state.open.getItem(0).getType(), Material.DIAMOND_SWORD);
        check("search result explains left buy and right sell", loreContains(state.open.getItem(0), "Left-click") && loreContains(state.open.getItem(0), "Right-click"));
        Inventory searchScreen = state.open; state.allowSell = false;
        click(extras, state, scheduler, 0, true, false);
        check("search right-sell requires permission", state.open == searchScreen);
        state.allowSell = true;
        click(extras, state, scheduler, 0, true, false);
        check("search right-click opens sale confirmation", text(state.open.getItem(13).getItemMeta().displayName()).contains("Confirm sale"));
        check("sale preview shows exact worn payout", loreContains(state.open.getItem(13), Money.formatNumber(wornValue)));
        check("sale preview displays the actual worn item", state.open.getItem(9).isSimilar(sword));
        long before = economy.getBalance(state.id);
        click(gui, state, scheduler, 13, false, false);
        eq("right-click sale credits previewed value", economy.getBalance(state.id) - before, wornValue);
        check("right-click sale only consumes matching storage", state.inventory.getItem(0) == null && state.inventory.getItemInOffHand().isSimilar(fullSword));
        click(gui, state, scheduler, 44, false, false);
        eq("back from sale restores search results", state.open.getItem(0).getType(), Material.DIAMOND_SWORD);
        click(extras, state, scheduler, 0, false, false);
        check("search left-click opens buying", text(state.open.getItem(13).getItemMeta().displayName()).contains("purchase"));
        click(gui, state, scheduler, 31, false, false);
        eq("stack button selects quantity rather than trading immediately", state.open.getItem(22).getAmount(), 64);

        state.inventory.setItem(0, new ItemStack(Material.STONE, 10));
        gui.openSelling(player, Purchase.of("STONE"), null, 0, 1); scheduler.drain();
        ItemStack renamed = new ItemStack(Material.STONE, 10); ItemMeta renamedMeta = renamed.getItemMeta();
        renamedMeta.displayName(new StubComponent("Different stone")); renamed.setItemMeta(renamedMeta);
        state.inventory.setItem(0, renamed);
        before = economy.getBalance(state.id);
        click(gui, state, scheduler, 13, false, false);
        eq("same-value item swap is rejected by quote", state.inventory.getItem(0).getAmount(), 10);
        eq("same-value stale quote pays nothing", economy.getBalance(state.id), before);
        click(gui, state, scheduler, 13, false, false);
        eq("refreshed quote may then be confirmed", state.inventory.getItem(0).getAmount(), 9);
        eq("refreshed quote pays once", economy.getBalance(state.id) - before, shop.prices().sellCents("STONE"));
        gui.openSelling(player, Purchase.of("STONE"), null, 0, 1); scheduler.drain();
        before = economy.getBalance(state.id);
        Click duplicate = new Click(state.open, state.open, player, 13, false, false);
        gui.onClick(duplicate); gui.onClick(duplicate); scheduler.drain();
        eq("double-confirm sells only once", state.inventory.getItem(0).getAmount(), 8);
        eq("double-confirm pays only once", economy.getBalance(state.id) - before, shop.prices().sellCents("STONE"));

        gui.openBuying(player, Purchase.of("STONE"), null, 0, 1); scheduler.drain();
        long oldUnit = shop.prices().buyCents("STONE");
        String reprice = shopText.replaceFirst("(?m)^    STONE: \\{ buy: [0-9]+\\.[0-9]{2}, sell: [0-9]+\\.[0-9]{2} }", "    STONE: { buy: 42.00, sell: 0.01 }");
        Files.writeString(path, reprice); shop.reload();
        check("stone price actually changed", shop.prices().buyCents("STONE") != oldUnit);
        int stonesBefore = state.inventory.count(Material.STONE); before = economy.getBalance(state.id);
        click(gui, state, scheduler, 13, false, false);
        eq("stale buy quote delivers nothing", state.inventory.count(Material.STONE), stonesBefore);
        eq("stale buy quote charges nothing", economy.getBalance(state.id), before);
        click(gui, state, scheduler, 13, false, false);
        eq("refreshed buy quote charges current price", before - economy.getBalance(state.id), shop.prices().buyCents("STONE"));
        Files.writeString(path, shopText); shop.reload();

        java.util.logging.Logger shopLog = java.util.logging.Logger.getLogger(ShopService.class.getName());
        java.util.logging.Level oldLog = shopLog.getLevel(); shopLog.setLevel(java.util.logging.Level.OFF);
        try {
            state.inventory.setItem(0, new ItemStack(Material.STONE, 5));
            data.failNextBalance = true;
            ShopService.SellResult failed = shop.sell(player, Purchase.of("STONE"), 1);
            eq("failed sale reports transaction error", failed.failKey(), "shop.transaction-error");
            eq("failed sale restores inventory", state.inventory.getItem(0).getAmount(), 5);
            ItemStack deposited = new ItemStack(Material.DIAMOND, 2); data.failNextBalance = true;
            failed = shop.sellDeposited(player, List.of(deposited));
            eq("failed tray credit reports transaction error", failed.failKey(), "shop.transaction-error");
            eq("failed tray credit keeps deposited stack", deposited.getAmount(), 2);
            before = economy.getBalance(state.id); int count = state.inventory.count(Material.POTION);
            ItemStack.testRejectItemMeta = true;
            ShopService.BuyResult rejected = shop.buy(player, new Purchase("POTION", "SWIFTNESS", 0), 1);
            check("rejected potion metadata is not sold", !rejected.ok());
            eq("failed metadata delivery refunds money", economy.getBalance(state.id), before);
            eq("failed metadata delivery adds no item", state.inventory.count(Material.POTION), count);
            ItemStack.testRejectItemMeta = false;
            state.inventory.failAddAfterInsert = true; before = economy.getBalance(state.id);
            stonesBefore = state.inventory.count(Material.STONE);
            rejected = shop.buy(player, Purchase.of("STONE"), 2);
            eq("inventory delivery exception is reported", rejected.failKey(), "shop.transaction-error");
            eq("partially inserted purchase rolled back", state.inventory.count(Material.STONE), stonesBefore);
            eq("delivery exception is refunded", economy.getBalance(state.id), before);
        } finally { ItemStack.testRejectItemMeta = false; shopLog.setLevel(oldLog); }

        for (int i = 0; i < state.inventory.getSize(); i++) state.inventory.setItem(i, null);
        ItemStack normal = potionItem(Material.POTION, PotionType.SWIFTNESS);
        ItemStack extended = potionItem(Material.POTION, PotionType.LONG_SWIFTNESS);
        ItemStack strong = potionItem(Material.POTION, PotionType.STRONG_SWIFTNESS);
        ItemStack extraEffect = extended.clone(); PotionMeta effectMeta = (PotionMeta) extraEffect.getItemMeta();
        effectMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2), true); extraEffect.setItemMeta(effectMeta);
        ItemStack changedScale = extended.clone(); changedScale.setData(DataComponentTypes.POTION_DURATION_SCALE, 2.0f);
        ItemStack splash = potionItem(Material.SPLASH_POTION, PotionType.LONG_SWIFTNESS);
        ItemStack tipped = potionItem(Material.TIPPED_ARROW, PotionType.LONG_SWIFTNESS);
        state.inventory.setItem(0, extended); state.inventory.setItem(1, normal); state.inventory.setItem(2, strong);
        state.inventory.setItem(3, extraEffect); state.inventory.setItem(4, changedScale); state.inventory.setItem(5, splash);
        state.inventory.setItem(6, tipped); state.inventory.setItemInOffHand(extended.clone());
        Purchase chosen = new Purchase("POTION", "LONG_SWIFTNESS", 0);
        check("chosen potion matches only exact base and duration", chosen.matches(extended) && !chosen.matches(normal)
                && !chosen.matches(strong) && !chosen.matches(extraEffect) && !chosen.matches(changedScale) && !chosen.matches(splash));
        check("tipped arrow matches its standard duration scale", new Purchase("TIPPED_ARROW", "LONG_SWIFTNESS", 0).matches(tipped));
        ShopService.SellResult preview = shop.previewSell(player, chosen, 64);
        eq("potion quote includes only the selected variant", preview.sold(), 1);
        eq("potion quote values its actual type", preview.earnedCents(), shop.sellUnitCents(extended));
        check("selected potion sale succeeds", shop.sell(player, chosen, 64).ok());
        check("other potion variants and custom effects remain", state.inventory.getItem(1).isSimilar(normal) && state.inventory.getItem(2).isSimilar(strong)
                && state.inventory.getItem(3).isSimilar(extraEffect) && state.inventory.getItem(4).isSimilar(changedScale) && state.inventory.getItem(5).isSimilar(splash));
        check("selected potion sale does not touch offhand", state.inventory.getItemInOffHand().isSimilar(extended));
        extras.openSearch(player, "minecraft:potion", 0); scheduler.drain();
        click(extras, state, scheduler, 0, true, false);
        check("potion picker explains duration", loreContains(state.open.getItem(0), "3:00"));
        eq("potion picker shows splash selector", state.open.getItem(46).getType(), Material.SPLASH_POTION);
        eq("potion picker shows lingering selector", state.open.getItem(47).getType(), Material.LINGERING_POTION);
        eq("potion picker shows tipped-arrow selector", state.open.getItem(48).getType(), Material.TIPPED_ARROW);
        click(extras, state, scheduler, 46, false, false);
        eq("form selector changes the effect picker", state.open.getItem(0).getType(), Material.SPLASH_POTION);
        int longIndex = -1;
        for (int i = 0; i < ChoiceCatalog.potions().size(); i++) if (ChoiceCatalog.potions().get(i).key().equals("LONG_SWIFTNESS")) longIndex = i;
        check("extended effect exists in picker", longIndex >= 0);
        check("extended splash picker shows actual duration", loreContains(state.open.getItem(longIndex), "8:00"));
        click(extras, state, scheduler, longIndex, false, false);
        eq("selected splash variant reaches sale quantity", state.open.getItem(22).getType(), Material.SPLASH_POTION);
        click(gui, state, scheduler, 13, false, false);
        check("splash selection sells its matching form", state.inventory.getItem(5) == null && state.inventory.getItem(6).isSimilar(tipped));

        ItemStack sharp3 = bookItem("SHARPNESS", 3), sharp4 = bookItem("SHARPNESS", 4), multiple = bookItem("SHARPNESS", 3);
        var multiMeta = (EnchantmentStorageMeta) multiple.getItemMeta();
        multiMeta.addStoredEnchant(RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft("mending")), 1, false);
        multiple.setItemMeta(multiMeta);
        state.inventory.setItem(10, sharp3); state.inventory.setItem(11, sharp4); state.inventory.setItem(12, multiple);
        Purchase sharpChoice = new Purchase("ENCHANTED_BOOK", "SHARPNESS", 3);
        check("book choice matches exact enchantment and level only", sharpChoice.matches(sharp3) && !sharpChoice.matches(sharp4) && !sharpChoice.matches(multiple));
        check("stored enchantments affect book valuation", shop.sellUnitCents(sharp3) > shop.prices().sellCents("ENCHANTED_BOOK"));
        extras.openSearch(player, "minecraft:enchanted_book", 0); scheduler.drain(); click(extras, state, scheduler, 0, true, false);
        int sharpIndex = -1;
        for (int i = 0; i < ChoiceCatalog.books().size(); i++) if (ChoiceCatalog.books().get(i).key().equals("SHARPNESS")) sharpIndex = i;
        click(extras, state, scheduler, sharpIndex, false, false); click(extras, state, scheduler, 12, false, false);
        eq("chosen enchantment and level reach sale quantity", state.open.getItem(22).getType(), Material.ENCHANTED_BOOK);
        click(gui, state, scheduler, 13, false, false);
        check("selected book sold without consuming other levels or multi-enchants", state.inventory.getItem(10) == null
                && state.inventory.getItem(11).isSimilar(sharp4) && state.inventory.getItem(12).isSimilar(multiple));

        state.inventory.setItem(20, new ItemStack(Material.DIAMOND, 4));
        tray.open(player); scheduler.drain(); before = economy.getBalance(state.id);
        tray.onClick(new Click(state.open, state.open, player, 49, false, false));
        eq("empty sell tray never scans player inventory", state.inventory.getItem(20).getAmount(), 4);
        eq("empty sell tray never pays", economy.getBalance(state.id), before);
        tray.onClick(new Click(state.open, state.inventory, player, 20, false, true));
        eq("only explicitly placed stack leaves inventory", state.inventory.getItem(20), null);
        tray.onClick(new Click(state.open, state.open, player, 49, false, false));
        eq("tray pays exactly for deposited diamonds", economy.getBalance(state.id) - before, shop.prices().sellCents("DIAMOND") * 4);
        check("tray does not touch unrelated book", state.inventory.getItem(11).isSimilar(sharp4));
        tray.onClose(new Close(state.open, player));

        // Exercise actual item creation, matching, and resale for every offered variant.
        PlayerState matrix = new PlayerState(world.proxy, "VariantMatrix");
        economy.setBalance(matrix.id, matrix.name, 100_000_000L);
        for (String form : List.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW")) {
            for (ChoiceCatalog.PotionOption option : ChoiceCatalog.potions()) {
                for (int i = 0; i < matrix.inventory.getSize(); i++) matrix.inventory.setItem(i, null);
                Purchase purchase = new Purchase(form, option.key(), 0);
                long balance = economy.getBalance(matrix.id);
                ShopService.BuyResult result = shop.buy(matrix.proxy, purchase, 1);
                check("matrix buys potion " + form + " " + option.key(), result.ok());
                ItemStack bought = matrix.inventory.last(Material.valueOf(form));
                check("matrix delivers selected potion " + form + " " + option.key(), bought != null && purchase.matches(bought));
                ShopService.SellResult sale = shop.sell(matrix.proxy, purchase, 1);
                check("matrix sells selected potion " + form + " " + option.key(), sale.ok() && sale.sold() == 1);
                check("matrix buy-sell cycle does not mint money " + form + " " + option.key(), economy.getBalance(matrix.id) < balance);
            }
        }
        for (ChoiceCatalog.BookOption option : ChoiceCatalog.books()) {
            for (int level = 1; level <= option.maxLevel(); level++) {
                for (int i = 0; i < matrix.inventory.getSize(); i++) matrix.inventory.setItem(i, null);
                Purchase purchase = new Purchase("ENCHANTED_BOOK", option.key(), level);
                long balance = economy.getBalance(matrix.id);
                ShopService.BuyResult result = shop.buy(matrix.proxy, purchase, 1);
                ItemStack bought = matrix.inventory.last(Material.ENCHANTED_BOOK);
                check("matrix buys book " + option.key() + " " + level, result.ok() && bought != null && purchase.matches(bought));
                ShopService.SellResult sale = shop.sell(matrix.proxy, purchase, 1);
                check("matrix sells book " + option.key() + " " + level, sale.ok() && sale.sold() == 1);
                check("book buy-sell cycle does not mint money " + option.key() + " " + level, economy.getBalance(matrix.id) < balance);
            }
        }
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
        check("/order opens order browser",buyer.open!=null && buyer.open.getSize()==54 && buyer.open.getItem(51).getType()==Material.WRITABLE_BOOK);
        click(gui,buyer,scheduler,51,false,false);
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
        click(gui,buyer,scheduler,24,false,false); // $2 total
        click(gui,buyer,scheduler,22,false,false);
        Chat standalonePrice=new Chat(buyer.proxy,"2.00");gui.onPriceChat(standalonePrice);scheduler.drain();
        check("standalone order GUI consumes typed price",standalonePrice.cancelled);
        long before=economy.getBalance(buyer.id);
        Click confirm=new Click(buyer.open,buyer.open,buyer.proxy,49,false,false);
        gui.onClick(confirm);gui.onClick(confirm);scheduler.drain();
        eq("one order despite double click",data.orders.size(),1);
        eq("GUI escrows exactly the total price",before-economy.getBalance(buyer.id),200L);
        BuyOrder placed=data.orders.values().iterator().next();
        eq("GUI amount",placed.amount(),2);eq("GUI total price",placed.totalCents(),java.math.BigInteger.valueOf(200));
        Drag drag=new Drag(buyer.open,buyer.inventory);gui.onDrag(drag);check("order GUI blocks drags",drag.cancelled);
        seller.inventory.setItem(0,new ItemStack(Material.DIAMOND,2));
        command.onCommand(seller.proxy,orders,"orders",new String[0]);scheduler.drain();
        click(gui,seller,scheduler,0,false,false);
        eq("seller gets escrow payout",economy.getBalance(seller.id),200L);
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

        OrderService.Result created=service.create(buyer.proxy,"IRON_INGOT",5,500);
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

    private static void testOrderAnyMaterial(TestWorld world, FakeScheduler scheduler, Messages messages) {
        PlayerState buyer = new PlayerState(world.proxy, "OrderPickerBuyer"), seller = new PlayerState(world.proxy, "OrderPickerSeller");
        Bukkit.testOnlinePlayers = List.of(buyer.proxy, seller.proxy);
        BalanceData data = new BalanceData();
        VeltryxConfig config = new VeltryxConfig(new YamlConfiguration());
        EconomyService economy = new EconomyService(data.proxy, config);
        economy.setBalance(buyer.id, buyer.name, 10_000);
        OrderService service = new OrderService(data.proxy, economy, messages, config);
        OrderGui gui = new OrderGui(service, economy, messages);
        OrderCommand command = new OrderCommand(gui, service, messages);
        org.bukkit.command.Command orders = new org.bukkit.command.Command(){@Override public String getName(){return "orders";}};
        command.onCommand(buyer.proxy, orders, "orders", new String[0]); scheduler.drain();
        click(gui, buyer, scheduler, 51, false, false); click(gui, buyer, scheduler, 4, false, false);
        List<Material> choices = Arrays.stream(Material.values()).filter(m -> !m.isLegacy() && m.isItem() && m != Material.AIR)
                .sorted(Comparator.comparing(Material::name)).toList();
        int index = choices.indexOf(Material.SPAWNER);
        check("order picker includes spawner among all item materials", index >= 0);
        for (int i = 0; i < index / 45; i++) click(gui, buyer, scheduler, 53, false, false);
        eq("order picker displays selected late-page material", buyer.open.getItem(index % 45).getType(), Material.SPAWNER);
        click(gui, buyer, scheduler, index % 45, false, false);
        eq("order creation selects the chosen material", buyer.open.getItem(4).getType(), Material.SPAWNER);
        click(gui, buyer, scheduler, 49, false, false);
        BuyOrder order = data.orders.values().iterator().next();
        eq("plain spawner order can be created", order.material(), "SPAWNER");
        var factory = new dev.superseller.veltryx.spawner.SpawnerItems(new FakePlugin(Path.of(".")));
        var identity = new dev.superseller.veltryx.spawner.SpawnerItems.Identity(UUID.randomUUID(), dev.superseller.veltryx.spawner.SpawnerType.COW);
        ItemStack custom = factory.create(identity);
        seller.inventory.setItem(0, custom);
        eq("custom spawner is not counted for a plain material order", OrderService.heldCount(seller.proxy, "SPAWNER"), 0);
        eq("custom spawner cannot be stripped by an order", service.fill(seller.proxy, order.id(), 1).templateKey(), "order.no-metadata");
        eq("rejected custom spawner keeps identity", factory.identity(seller.inventory.getItem(0)), identity);
        seller.inventory.setItem(1, new ItemStack(Material.SPAWNER, 1));
        eq("eligible plain spawner is counted", OrderService.heldCount(seller.proxy, "SPAWNER"), 1);
        check("plain spawner fills while custom spawner remains", service.fill(seller.proxy, order.id(), 1).ok());
        eq("plain fill does not consume custom identity", factory.identity(seller.inventory.getItem(0)), identity);
        check("plain spawner is claimable", service.claim(buyer.proxy, order.id()).ok());
        check("plain claim does not create custom identity", factory.identity(buyer.inventory.last(Material.SPAWNER)) == null);
        scheduler.drain();
    }

    private static void testOrderTotals(TestWorld world, FakeScheduler scheduler, Messages messages) {
        PlayerState buyer=new PlayerState(world.proxy,"TotalBuyer"), seller=new PlayerState(world.proxy,"TotalSeller");
        Bukkit.testOnlinePlayers=List.of(buyer.proxy,seller.proxy);
        BalanceData data=new BalanceData(); VeltryxConfig config=new VeltryxConfig(new YamlConfiguration());
        EconomyService economy=new EconomyService(data.proxy,config);
        OrderService service=new OrderService(data.proxy,economy,messages,config);
        MarketPricePrompt prompt=new MarketPricePrompt(messages);
        OrderGui gui=new OrderGui(service,economy,messages,prompt);
        OrderCommand command=new OrderCommand(gui,service,messages);
        org.bukkit.command.Command order=new org.bukkit.command.Command(){@Override public String getName(){return "order";}};
        buyer.inventory.setItemInMainHand(new ItemStack(Material.IRON_INGOT,1));
        String raw="123456789012345678901234567890.03"; BigInteger total=Money.parseExact(raw);
        economy.setBalanceExact(buyer.id,buyer.name,total.multiply(BigInteger.TWO));
        command.onCommand(buyer.proxy,order,"order",new String[0]);scheduler.drain();
        check("/order starts in browse view",buyer.open.getItem(51).getType()==Material.WRITABLE_BOOK);
        buyer.allowOrder=false;
        Click deniedOrder=new Click(buyer.open,buyer.open,buyer.proxy,51,false,false);gui.onClick(deniedOrder);scheduler.drain();
        check("revoked order permission blocks GUI actions",deniedOrder.cancelled && buyer.open.getItem(51).getType()==Material.WRITABLE_BOOK);
        buyer.allowOrder=true;
        click(gui,buyer,scheduler,51,false,false);
        eq("held material is selected without consuming it",buyer.open.getItem(4).getType(),Material.IRON_INGOT);
        click(gui,buyer,scheduler,15,false,false);click(gui,buyer,scheduler,15,false,false); // three items
        click(gui,buyer,scheduler,22,false,false);
        check("price entry closes creation GUI",buyer.open==null);
        Chat typed=new Chat(buyer.proxy,raw);prompt.onChat(typed);scheduler.drain();
        check("order GUI price input is not public",typed.cancelled);
        check("typed total shown exactly",text(buyer.open.getItem(22).getItemMeta().displayName()).contains(Money.formatNumber(total)));
        BigInteger before=economy.getBalanceExact(buyer.id);
        click(gui,buyer,scheduler,49,false,false);
        eq("order escrows precisely the typed total",before.subtract(economy.getBalanceExact(buyer.id)),total);
        BuyOrder placed=data.orders.values().iterator().next();
        eq("total order quantity",placed.amount(),3);eq("total order persisted exact",placed.totalCents(),total);
        eq("legacy per-item order view clamps",placed.priceEachCents(),Long.MAX_VALUE);
        eq("creation does not consume buyer's held item",buyer.inventory.getItemInMainHand().getAmount(),1);
        seller.inventory.setItemInMainHand(new ItemStack(Material.IRON_INGOT,1));
        BigInteger first=placed.payoutFor(1);
        check("partial large order fill succeeds",service.fill(seller.proxy,placed.id(),1).ok());
        eq("first seller payout is exact",economy.getBalanceExact(seller.id),first);
        eq("remaining escrow exact after first fill",data.orders.get(placed.id()).refundCents(),total.subtract(first));
        seller.inventory.setItemInMainHand(new ItemStack(Material.IRON_INGOT,2));
        check("remaining order fill succeeds",service.fill(seller.proxy,placed.id(),2).ok());
        eq("all partial payouts add to exact total",economy.getBalanceExact(seller.id),total);
        eq("all requested items become claimable",data.orders.get(placed.id()).claimable(),3);
        check("buyer collects supplied items",service.claim(buyer.proxy,placed.id()).ok());
        eq("buyer collects total quantity",buyer.inventory.count(Material.IRON_INGOT),4);

        BigInteger sellerStart=economy.getBalanceExact(seller.id);
        var remainder=service.create(buyer.proxy,"DIAMOND",3,BigInteger.valueOf(101));
        String id=remainder.values().get("id"); seller.inventory.setItemInMainHand(new ItemStack(Material.DIAMOND,1));
        BigInteger buyerAfterEscrow=economy.getBalanceExact(buyer.id);
        gui.open(seller.proxy,false,0);scheduler.drain();
        var staleInventoryView=seller.open;
        seller.inventory.setItem(7,new ItemStack(Material.DIAMOND,1));
        gui.onClick(new Click(staleInventoryView,staleInventoryView,seller.proxy,0,false,false));scheduler.drain();
        eq("changed inventory does not sell an unexpected amount",seller.inventory.count(Material.DIAMOND),2);
        eq("changed inventory does not change the order",data.orders.get(id).filled(),0);
        check("changed inventory refreshes the payout preview",seller.open!=staleInventoryView);
        seller.inventory.setItem(7,null);gui.open(seller.proxy,false,0);scheduler.drain();
        var staleView=seller.open;
        check("first non-divisible fill succeeds",service.fill(seller.proxy,id,1).ok());
        eq("first of three receives extra cent",economy.getBalanceExact(seller.id).subtract(sellerStart),BigInteger.valueOf(34));
        seller.inventory.setItemInMainHand(new ItemStack(Material.DIAMOND,1));
        gui.onClick(new Click(staleView,staleView,seller.proxy,0,false,false));scheduler.drain();
        eq("stale order GUI does not consume the seller's item",seller.inventory.count(Material.DIAMOND),1);
        eq("stale order GUI does not change filled quantity",data.orders.get(id).filled(),1);
        eq("stale order GUI does not pay changed amount",economy.getBalanceExact(seller.id).subtract(sellerStart),BigInteger.valueOf(34));
        check("stale order GUI refreshes",seller.open!=staleView);
        check("partially filled order can be cancelled",service.cancel(buyer.proxy,id).ok());
        eq("unfilled cancellation refund has exact remainder",economy.getBalanceExact(buyer.id).subtract(buyerAfterEscrow),BigInteger.valueOf(67));
        eq("partial payout plus refund equals total",BigInteger.valueOf(34).add(BigInteger.valueOf(67)),BigInteger.valueOf(101));
        eq("cancelled order retains supplied claim",data.orders.get(id).claimable(),1);

        var oneCent=service.create(buyer.proxy,"DIAMOND",3,BigInteger.ONE); id=oneCent.values().get("id");
        sellerStart=economy.getBalanceExact(seller.id);
        for(int i=0;i<3;i++) {
            seller.inventory.setItemInMainHand(new ItemStack(Material.DIAMOND,1));
            check("tiny total fill " + i,service.fill(seller.proxy,id,1).ok());
        }
        eq("one-cent total cannot mint money",economy.getBalanceExact(seller.id).subtract(sellerStart),BigInteger.ONE);
        eq("one-cent order completes",data.orders.get(id).claimable(),3);
        var expiring=service.create(buyer.proxy,"STONE",3,BigInteger.valueOf(101));
        String expireId=expiring.values().get("id");seller.inventory.setItemInMainHand(new ItemStack(Material.STONE,1));
        check("expiring order accepts first fill",service.fill(seller.proxy,expireId,1).ok());
        BuyOrder active=data.orders.get(expireId);
        data.orders.put(expireId,new BuyOrder(active.id(),active.buyer(),active.buyerName(),active.material(),active.amount(),active.filled(),
                active.totalCents(),active.created(),System.currentTimeMillis()-1,false,active.collected()));
        BigInteger beforeExpiry=economy.getBalanceExact(buyer.id);service.sweepExpired(System.currentTimeMillis());
        eq("expired order refunds exact unfilled total",economy.getBalanceExact(buyer.id).subtract(beforeExpiry),BigInteger.valueOf(67));
        check("expired order retains unclaimed supplied item",data.orders.get(expireId).closed() && data.orders.get(expireId).claimable()==1);

        int existing=data.orders.size();
        economy.setBalanceExact(buyer.id,buyer.name,Money.parseExact("9999999999999999999999999999999"));
        command.onCommand(buyer.proxy,order,"order",new String[]{"DIAMOND","7","900000000000000000000000000000.99"});
        eq("direct command accepts large total",data.orders.size(),existing+1);
        BuyOrder direct=data.orders.values().stream().filter(o -> o.amount()==7).findFirst().orElseThrow();
        eq("direct command uses typed total, not per-item",direct.totalCents(),Money.parseExact("900000000000000000000000000000.99"));
        command.onCommand(buyer.proxy,order,"order",new String[0]);scheduler.drain();
        check("/order displays existing orders",buyer.open.getItem(0)!=null && buyer.open.getItem(0).getType()!=Material.LIME_DYE);
        check("zero total rejected",!service.create(buyer.proxy,"STONE",1,BigInteger.ZERO).ok());
        check("negative total rejected",!service.create(buyer.proxy,"STONE",1,BigInteger.valueOf(-1)).ok());

        // Every grouping of partial fills/refunds must allocate each cent once.
        for(int amount=1;amount<=20;amount++) for(int cents=1;cents<=51;cents++) {
            BuyOrder model=new BuyOrder("property",buyer.id,buyer.name,"DIAMOND",amount,0,BigInteger.valueOf(cents),1,9_999_999_999_999L,false,0);
            BigInteger sum=BigInteger.ZERO;
            for(int filled=0;filled<amount;filled++) {
                BuyOrder at=new BuyOrder(model.id(),model.buyer(),model.buyerName(),model.material(),amount,filled,model.totalCents(),1,model.expires(),false,0);
                BigInteger step=at.payoutFor(1); sum=sum.add(step);
                check("partial payout is nonnegative",step.signum()>=0);
                eq("partial payout plus refund conserves escrow",sum.add(model.totalCents().subtract(at.allocated(filled+1))),model.totalCents());
            }
            eq("all fill groupings conserve total",sum,model.totalCents());
        }
        scheduler.drain();
    }

    private static void testAuctionYaml(Path dir, TestWorld world, FakeScheduler scheduler, Messages messages) {
        Path folder=dir.resolve("auction-yaml-integration");
        Logger quiet=Logger.getLogger("auction-yaml-integration");quiet.setLevel(java.util.logging.Level.OFF);
        var store=new dev.superseller.veltryx.storage.YamlDataStore(folder.toFile(),quiet);store.load();
        VeltryxConfig config=new VeltryxConfig(new YamlConfiguration());
        var economy=new EconomyService(store,config);
        var seller=new PlayerState(world.proxy,"DurableSeller"); var buyer=new PlayerState(world.proxy,"DurableBuyer");
        Bukkit.testOnlinePlayers=List.of(seller.proxy,buyer.proxy);
        BigInteger total=Money.parseExact("9999999999999999999999999999.41");
        economy.setBalanceExact(buyer.id,buyer.name,total.multiply(BigInteger.TWO));
        ItemStack original=new ItemStack(Material.EMERALD,9); ItemMeta meta=original.getItemMeta();
        meta.displayName(new StubComponent("Persistent emerald"));meta.addEnchant(Enchantment.MENDING,1,true);
        NamespacedKey key=NamespacedKey.minecraft("auction_test");meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,"durable");
        original.setItemMeta(meta); seller.inventory.setItemInMainHand(original.clone());
        var service=new AhService(store,economy,messages,config);
        var listed=service.listHeldItem(seller.proxy,total);check("YAML integration creates held listing",listed.ok());
        String id=listed.values().get("id");store.close();
        var restarted=new dev.superseller.veltryx.storage.YamlDataStore(folder.toFile(),quiet);restarted.load();
        var afterEconomy=new EconomyService(restarted,config);var afterService=new AhService(restarted,afterEconomy,messages,config);
        check("YAML listing remains live after restart",afterService.liveListings(System.currentTimeMillis()).stream().anyMatch(a->a.id().equals(id)));
        check("YAML restarted auction purchase succeeds",afterService.buy(buyer.proxy,id).ok());scheduler.drain();
        ItemStack received=buyer.inventory.last(Material.EMERALD);
        check("YAML restart delivers original custom item",received!=null && received.getAmount()==9 && received.isSimilar(original));
        eq("YAML restart delivers persistent metadata",received.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING),"durable");
        eq("YAML restarted purchase debits exact total",afterEconomy.getBalanceExact(buyer.id),total);
        eq("YAML restarted purchase credits exact total",afterEconomy.getBalanceExact(seller.id),total);
        var orders=new OrderService(restarted,afterEconomy,messages,config);
        var created=orders.create(buyer.proxy,"STONE",3,BigInteger.valueOf(101));
        check("YAML creates exact-total order",created.ok());String orderId=created.values().get("id");
        seller.inventory.setItemInMainHand(new ItemStack(Material.STONE,1));
        check("YAML partial order fill succeeds",orders.fill(seller.proxy,orderId,1).ok());scheduler.drain();
        restarted.close();
        var again=new dev.superseller.veltryx.storage.YamlDataStore(folder.toFile(),quiet);again.load();
        check("sold auction remains closed on second restart",again.allAhListings().stream().anyMatch(a->a.id().equals(id)&&a.closed()));
        eq("seller payouts survive YAML restart",again.getBalanceExact(seller.id),total.add(BigInteger.valueOf(34)));
        var finalEconomy=new EconomyService(again,config);var resumedOrders=new OrderService(again,finalEconomy,messages,config);
        check("YAML partial order can be cancelled after restart",resumedOrders.cancel(buyer.proxy,orderId).ok());
        eq("YAML restart refunds exact remainder",finalEconomy.getBalanceExact(buyer.id),total.subtract(BigInteger.valueOf(34)));
        check("YAML partial claim survives restart and cancellation",resumedOrders.claim(buyer.proxy,orderId).ok());
        eq("YAML order buyer receives item",buyer.inventory.count(Material.STONE),1);
        var factory = new dev.superseller.veltryx.spawner.SpawnerItems(new FakePlugin(dir));
        var identity = new dev.superseller.veltryx.spawner.SpawnerItems.Identity(UUID.randomUUID(), dev.superseller.veltryx.spawner.SpawnerType.PIG);
        Path spawnerPath = dir.resolve("auction-spawner-data");
        var spawners = new dev.superseller.veltryx.spawner.SpawnerStore(spawnerPath, quiet);
        var firstPosition = new dev.superseller.veltryx.spawner.SpawnerStore.Position(UUID.randomUUID(), 1, 64, 1);
        check("auction spawner has persistent storage", spawners.create(identity) && spawners.bind(identity, firstPosition));
        spawners.toggle(identity, firstPosition);
        eq("auction spawner stores loot", spawners.deposit(identity, firstPosition, List.of(new ItemStack(Material.DIAMOND, 7))),
                dev.superseller.veltryx.spawner.SpawnerStore.Deposit.STORED);
        check("auction spawner is picked up before listing", spawners.unbind(identity, firstPosition));
        seller.inventory.setItemInMainHand(factory.create(identity));
        var finalMarket = new AhService(again, finalEconomy, messages, config);
        var spawnerListing = finalMarket.listHeldItem(seller.proxy, BigInteger.valueOf(100));
        check("YAML accepts an identity-bearing spawner", spawnerListing.ok());
        String spawnerId = spawnerListing.values().get("id"); again.close();
        var third = new dev.superseller.veltryx.storage.YamlDataStore(folder.toFile(), quiet); third.load();
        var thirdMarket = new AhService(third, new EconomyService(third, config), messages, config);
        check("YAML restart can reclaim identity-bearing spawner", thirdMarket.reclaim(seller.proxy, spawnerId).ok());
        eq("YAML restart preserves spawner identity", factory.identity(seller.inventory.last(Material.SPAWNER)), identity);
        var restartedSpawners = new dev.superseller.veltryx.spawner.SpawnerStore(spawnerPath, quiet);
        var newPosition = new dev.superseller.veltryx.spawner.SpawnerStore.Position(firstPosition.world(), 10, 64, 10);
        check("reclaimed spawner can be placed at a new position", restartedSpawners.bind(identity, newPosition));
        ItemStack retained = restartedSpawners.page(identity, newPosition, 0).items()[0];
        check("reclaimed spawner retains separately stored loot", retained != null && retained.getType() == Material.DIAMOND && retained.getAmount() == 7);
        third.close();
    }

    private static void testAuction(TestWorld world, FakeScheduler scheduler, Messages messages) {
        PlayerState seller=new PlayerState(world.proxy,"AuctionSeller"), buyer=new PlayerState(world.proxy,"AuctionBuyer");
        Bukkit.testOnlinePlayers=List.of(seller.proxy,buyer.proxy);
        BalanceData data=new BalanceData(); VeltryxConfig config=new VeltryxConfig(new YamlConfiguration());
        EconomyService economy=new EconomyService(data.proxy,config);
        AhService service=new AhService(data.proxy,economy,messages,config);
        MarketPricePrompt prompt=new MarketPricePrompt(messages);
        AhGui gui=new AhGui(service,economy,messages,prompt);
        AhCommand command=new AhCommand(gui,service,messages);
        org.bukkit.command.Command ah=new org.bukkit.command.Command(){@Override public String getName(){return "ah";}};
        Logger log=Logger.getLogger(AhService.class.getName()); var previous=log.getLevel(); log.setLevel(java.util.logging.Level.OFF);
        try {
            command.onCommand(buyer.proxy,ah,"ah",new String[0]);scheduler.drain();
            check("/ah opens the listings browser",buyer.open!=null && buyer.open.getItem(51).getType()==Material.CHEST);
            Drag drag=new Drag(buyer.open,buyer.inventory);gui.onDrag(drag);check("auction browser prevents dragging items",drag.cancelled);
            seller.inventory.setItemInMainHand(new ItemStack(Material.WATER,1));
            eq("non-item material cannot enter auction escrow",service.listHeldItem(seller.proxy,BigInteger.ONE).templateKey(),"ah.unknown-item");
            eq("rejected non-item remains in hand",seller.inventory.getItemInMainHand().getType(),Material.WATER);
            eq("rejected non-item creates no listing",data.listings.size(),0);

            ItemStack held=new ItemStack(Material.DIAMOND,17);
            ItemMeta meta=held.getItemMeta(); meta.displayName(new StubComponent("One of a kind"));
            meta.lore(List.of(new StubComponent("A custom lore line")));
            meta.addEnchant(Enchantment.SHARPNESS,4,true);
            NamespacedKey key=NamespacedKey.minecraft("quality"); meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,"rare");
            held.setItemMeta(meta); ItemStack original=held.clone();
            seller.inventory.setItemInMainHand(held);
            ItemStack other=held.clone();other.setAmount(23);seller.inventory.setItem(6,other);
            ItemStack offhand=held.clone();offhand.setAmount(4);seller.inventory.setItemInOffHand(offhand);
            String raw="123456789012345678901234567890.37"; BigInteger total=Money.parseExact(raw);
            economy.setBalanceExact(buyer.id,buyer.name,total.multiply(BigInteger.TWO));
            command.onCommand(seller.proxy,ah,"ah",new String[]{"sell",raw});
            eq("command creates one listing",data.listings.size(),1);
            AhListing listing=data.listings.values().iterator().next();
            eq("auction price is the whole stack total",listing.priceExactCents(),total);
            eq("legacy auction price view clamps",listing.priceCents(),Long.MAX_VALUE);
            eq("only main-hand stack removed",seller.inventory.getItemInMainHand(),null);
            check("other matching named stack stays untouched",seller.inventory.getItem(6).getAmount()==23 && seller.inventory.getItem(6).isSimilar(other));
            check("offhand stays untouched",seller.inventory.getItemInOffHand().getAmount()==4 && seller.inventory.getItemInOffHand().isSimilar(offhand));
            ItemStack stored=service.listingItem(listing);
            check("auction serializes the actual hand stack",stored!=null && stored.getAmount()==17 && stored.isSimilar(original));
            eq("auction retains custom PDC",stored.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING),"rare");
            eq("auction retains enchantments",stored.getEnchantmentLevel(Enchantment.SHARPNESS),4);
            eq("auction retains original lore",text(stored.getItemMeta().lore().get(0)),"A custom lore line");
            command.onCommand(buyer.proxy,ah,"ah",new String[0]);scheduler.drain();
            ItemStack preview=buyer.open.getItem(0);
            eq("auction browser previews actual custom name",text(preview.getItemMeta().displayName()),"One of a kind");
            check("auction browser shows exact total",preview.getItemMeta().lore().stream().anyMatch(c -> text(c).contains(Money.formatNumber(total))));
            buyer.allowAh=false;
            Click deniedAuction=new Click(buyer.open,buyer.open,buyer.proxy,0,false,false);gui.onClick(deniedAuction);scheduler.drain();
            check("revoked auction permission blocks GUI purchase",deniedAuction.cancelled && !data.listings.get(listing.id()).closed());
            buyer.allowAh=true;
            click(gui,buyer,scheduler,0,false,false);
            eq("buyer charged exact total once",economy.getBalanceExact(buyer.id),total);
            eq("seller paid exact total once",economy.getBalanceExact(seller.id),total);
            ItemStack received=buyer.inventory.last(Material.DIAMOND);
            check("buyer receives original custom stack",received!=null && received.getAmount()==17 && received.isSimilar(original));
            eq("sold listing cannot be purchased again",service.buy(buyer.proxy,listing.id()).templateKey(),"ah.gone");

            seller.inventory.setItemInMainHand(new ItemStack(Material.STONE,10));
            check("zero auction total rejected",!service.listHeldItem(seller.proxy,BigInteger.ZERO).ok());
            eq("rejected zero total leaves hand alone",seller.inventory.getItemInMainHand().getAmount(),10);
            AhService.Result made=service.listHeldItem(seller.proxy,BigInteger.valueOf(12345));
            check("second listing created",made.ok()); String id=made.values().get("id");
            BigInteger beforeBuyer=economy.getBalanceExact(buyer.id), beforeSeller=economy.getBalanceExact(seller.id);
            buyer.inventory.capacity=3;
            eq("partial inventory insertion is refused",service.buy(buyer.proxy,id).templateKey(),"shop.no-space");
            eq("partial auction delivery is rolled back",buyer.inventory.count(Material.STONE),0);
            eq("no-space buyer not charged",economy.getBalanceExact(buyer.id),beforeBuyer);
            eq("no-space seller not paid",economy.getBalanceExact(seller.id),beforeSeller);
            check("no-space listing remains live",!data.listings.get(id).closed());
            buyer.inventory.capacity=Integer.MAX_VALUE;
            data.failAhWrites=true;
            eq("failed auction close reports storage error",service.buy(buyer.proxy,id).templateKey(),"ah.storage-error");
            eq("failed auction close rolls back items",buyer.inventory.count(Material.STONE),0);
            eq("failed auction close charges nothing",economy.getBalanceExact(buyer.id),beforeBuyer);
            data.failAhWrites=false; data.failNextTransfer=true;
            eq("failed payment reports storage error",service.buy(buyer.proxy,id).templateKey(),"ah.storage-error");
            check("failed payment reopens listing",!data.listings.get(id).closed());
            eq("failed payment rolls back item",buyer.inventory.count(Material.STONE),0);
            eq("failed payment leaves buyer funds intact",economy.getBalanceExact(buyer.id),beforeBuyer);
            eq("failed payment leaves seller funds intact",economy.getBalanceExact(seller.id),beforeSeller);
            check("auction can succeed after rollback",service.buy(buyer.proxy,id).ok());
            eq("arbitrary stack price is not multiplied",beforeBuyer.subtract(economy.getBalanceExact(buyer.id)),BigInteger.valueOf(12345));

            ItemStack namedTool=new ItemStack(Material.TRIDENT,1); ItemMeta toolMeta=namedTool.getItemMeta();
            toolMeta.displayName(new StubComponent("Damaged enchanted trident")); toolMeta.addEnchant(Enchantment.RIPTIDE,3,true);
            ((Damageable)toolMeta).setDamage(42); namedTool.setItemMeta(toolMeta); ItemStack toolCopy=namedTool.clone();
            seller.inventory.setItemInMainHand(namedTool); data.failAhWrites=true;
            eq("failed listing persistence is reported",service.listHeldItem(seller.proxy,BigInteger.TEN).templateKey(),"ah.storage-error");
            check("failed listing returns exact held item",seller.inventory.getItemInMainHand().isSimilar(toolCopy));
            data.failAhWrites=false; made=service.listHeldItem(seller.proxy,BigInteger.TEN); id=made.values().get("id");
            seller.inventory.capacity=0;
            eq("reclaim with no space fails",service.reclaim(seller.proxy,id).templateKey(),"shop.no-space");
            check("failed reclaim remains available",!data.listings.get(id).closed());
            seller.inventory.capacity=Integer.MAX_VALUE;
            check("reclaim succeeds",service.reclaim(seller.proxy,id).ok());
            check("reclaim returns the damaged enchanted item",seller.inventory.last(Material.TRIDENT).isSimilar(toolCopy));
            eq("reclaim cannot duplicate",service.reclaim(seller.proxy,id).templateKey(),"ah.gone");
            seller.inventory.setItemInMainHand(new ItemStack(Material.EMERALD,4));
            String expiredId=service.listHeldItem(seller.proxy,BigInteger.valueOf(999)).values().get("id");
            AhListing live=data.listings.get(expiredId);
            data.listings.put(expiredId,new AhListing(live.id(),live.seller(),live.sellerName(),live.material(),live.amount(),
                    live.priceExactCents(),live.listed(),System.currentTimeMillis()-1,false,live.itemData()));
            eq("expired auction is no longer purchasable",service.buy(buyer.proxy,expiredId).templateKey(),"ah.gone");
            check("expired auction remains in seller view",service.listingsOf(seller.id).stream().anyMatch(a -> a.id().equals(expiredId)));
            check("expired auction can be reclaimed",service.reclaim(seller.proxy,expiredId).ok());

            ItemStack potion=new ItemStack(Material.POTION,1);PotionMeta potionMeta=(PotionMeta)potion.getItemMeta();
            potionMeta.setBasePotionType(PotionType.STRONG_SWIFTNESS);potion.setItemMeta(potionMeta);
            seller.inventory.setItemInMainHand(potion);var potionListing=service.listHeldItem(seller.proxy,BigInteger.ONE);
            check("potion variant can be listed",potionListing.ok());
            ItemStack decodedPotion=service.listingItem(data.listings.get(potionListing.values().get("id")));
            eq("potion variant survives auction serialization",((PotionMeta)decodedPotion.getItemMeta()).getBasePotionType(),PotionType.STRONG_SWIFTNESS);
            check("potion variant can be reclaimed",service.reclaim(seller.proxy,potionListing.values().get("id")).ok());
            ItemStack book=new ItemStack(Material.ENCHANTED_BOOK,1);
            var bookMeta=(EnchantmentStorageMeta)book.getItemMeta();bookMeta.addStoredEnchant(Enchantment.MENDING,1,true);book.setItemMeta(bookMeta);
            seller.inventory.setItemInMainHand(book);var bookListing=service.listHeldItem(seller.proxy,BigInteger.ONE);
            check("enchanted book can be listed",bookListing.ok());
            ItemStack decodedBook=service.listingItem(data.listings.get(bookListing.values().get("id")));
            eq("stored book enchant survives auction serialization",((StubEnchantmentStorageMeta)decodedBook.getItemMeta()).stored.get(Enchantment.MENDING),1);
            check("enchanted book can be reclaimed",service.reclaim(seller.proxy,bookListing.values().get("id")).ok());

            var spawnerFactory = new dev.superseller.veltryx.spawner.SpawnerItems(new FakePlugin(Path.of(".")));
            var identity = new dev.superseller.veltryx.spawner.SpawnerItems.Identity(UUID.randomUUID(), dev.superseller.veltryx.spawner.SpawnerType.COW);
            ItemStack customSpawner = spawnerFactory.create(identity, 4);
            seller.inventory.setItemInMainHand(customSpawner.clone());
            var spawnerListing = service.listHeldItem(seller.proxy, BigInteger.valueOf(100));
            check("identity-bearing held spawner can be listed safely", spawnerListing.ok());
            ItemStack escrowedSpawner = service.listingItem(data.listings.get(spawnerListing.values().get("id")));
            eq("spawner identity survives auction serialization", spawnerFactory.identity(escrowedSpawner), identity);
            check("identity-bearing auction spawner can be bought", service.buy(buyer.proxy, spawnerListing.values().get("id")).ok());
            eq("buyer receives the same spawner identity", spawnerFactory.identity(buyer.inventory.last(Material.SPAWNER)), identity);
            AhListing oldSpawner = new AhListing("old-spawner", seller.id, seller.name, "SPAWNER", 1, 100, 1, System.currentTimeMillis()+100000, false);
            check("old spawner listing without metadata fails closed", service.listingItem(oldSpawner) == null);

            AhListing legacy=new AhListing("legacy",seller.id,seller.name,"IRON_INGOT",2,500,1,System.currentTimeMillis()+100000,false);
            data.listings.put(legacy.id(),legacy);
            check("legacy material-only listing remains purchasable",service.buy(buyer.proxy,"legacy").ok());
            AhListing broken=new AhListing("broken",seller.id,seller.name,"DIAMOND",1,BigInteger.TEN,1,System.currentTimeMillis()+100000,false,"not-base64!");
            data.listings.put(broken.id(),broken); beforeBuyer=economy.getBalanceExact(buyer.id);
            eq("corrupt serialized item cannot be purchased",service.buy(buyer.proxy,"broken").templateKey(),"ah.item-error");
            eq("corrupt listing charges nothing",economy.getBalanceExact(buyer.id),beforeBuyer);
            check("corrupt listing is not closed",!data.listings.get("broken").closed());

            seller.inventory.setItemInMainHand(new ItemStack(Material.EMERALD,5));
            command.onCommand(seller.proxy,ah,"ah",new String[0]);scheduler.drain();
            click(gui,seller,scheduler,51,false,false); check("auction price prompt closes menu",seller.open==null);
            seller.inventory.setItemInMainHand(new ItemStack(Material.IRON_INGOT,1));
            int beforeListings=data.listings.size();
            Chat changed=new Chat(seller.proxy,"15m");prompt.onChat(changed);scheduler.drain();
            check("auction price chat is private",changed.cancelled);
            eq("changed hand cancels pending listing",data.listings.size(),beforeListings);
            eq("changed hand item remains",seller.inventory.getItemInMainHand().getType(),Material.IRON_INGOT);
            seller.inventory.setItemInMainHand(new ItemStack(Material.EMERALD,5));
            command.onCommand(seller.proxy,ah,"ah",new String[0]);scheduler.drain(); click(gui,seller,scheduler,51,false,false);
            Chat stale=new Chat(seller.proxy,"1000");prompt.onChat(stale);
            command.onCommand(seller.proxy,ah,"ah",new String[0]);scheduler.drain();
            eq("new command cancels a captured but unprocessed price",data.listings.size(),beforeListings);
            check("cancelled queued input leaves hand intact",seller.inventory.getItemInMainHand().getAmount()==5);
            click(gui,seller,scheduler,51,false,false);
            Chat cancelled=new Chat(seller.proxy,"cancel");prompt.onChat(cancelled);scheduler.drain();
            check("cancel is private and reopens auction browser",cancelled.cancelled && seller.open!=null);
            eq("cancelling price entry leaves hand and listings alone",data.listings.size(),beforeListings);
            click(gui,seller,scheduler,51,false,false);
            Chat typed=new Chat(seller.proxy,"123456789012345678901234567890g");prompt.onChat(typed);
            Chat duplicate=new Chat(seller.proxy,"999");prompt.onChat(duplicate);scheduler.drain();
            check("typed GUI price is private",typed.cancelled);
            check("racing price inputs are also private",duplicate.cancelled);
            eq("GUI creates exactly one listing",data.listings.size(),beforeListings+1);
            AhListing fromGui=data.listings.values().stream().filter(a->a.material().equals("EMERALD") && !a.closed()).findFirst().orElseThrow();
            eq("GUI accepts very large exact totals",fromGui.priceExactCents(),Money.parseExact(typed.value));
            check("GUI removes held stack",seller.inventory.getItemInMainHand()==null);
            seller.inventory.setItemInMainHand(new ItemStack(Material.DIAMOND,2));
            String gigantic="9".repeat(160); int beforeGigantic=data.listings.size();
            command.onCommand(seller.proxy,ah,"ah",new String[]{"sell",gigantic});
            eq("160-digit auction price has no monetary cap",data.listings.size(),beforeGigantic+1);
            check("160-digit price persists exactly",data.listings.values().stream().anyMatch(a -> a.priceExactCents().equals(Money.parseExact(gigantic))));

            seller.inventory.setItemInMainHand(new ItemStack(Material.IRON_INGOT,1));
            String race=service.listHeldItem(seller.proxy,BigInteger.valueOf(10)).values().get("id");
            PlayerState otherBuyer=new PlayerState(world.proxy,"OtherBuyer");
            economy.setBalanceExact(otherBuyer.id,otherBuyer.name,BigInteger.valueOf(1000));
            Bukkit.testOnlinePlayers=List.of();
            var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var one=pool.submit(() -> service.buy(buyer.proxy,race));
                var two=pool.submit(() -> service.buy(otherBuyer.proxy,race));
                eq("competing buyers purchase once",(one.get().ok()?1:0)+(two.get().ok()?1:0),1);
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {pool.shutdownNow();}
        } finally {log.setLevel(previous);scheduler.drain();}
    }

    private static void testShardItemsAndLunge(Path dir, PlayerState state, TestWorld world, Messages messages) {
        ShardCatalog catalog = new ShardCatalog(); ShardItems items = new ShardItems(new FakePlugin(dir));
        ItemStack axe = items.create(catalog.byId("shard_axe"), 0);
        eq("new shard axe has Silk Touch", axe.getEnchantmentLevel(Enchantment.SILK_TOUCH), 1);
        eq("new shard axe has no Fortune", axe.getEnchantmentLevel(Enchantment.FORTUNE), 0);
        eq("alternate choice cannot add Fortune to shard axe", items.create(catalog.byId("shard_axe"), 1).getEnchantmentLevel(Enchantment.FORTUNE), 0);
        check("shard axe shop lore states Silk Touch", catalog.byId("shard_axe").lore().stream().anyMatch(line -> line.contains("Silk Touch")));
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
        testShardTrident(state, world, messages, items, catalog, listener);
    }

    private static void testShardTrident(PlayerState state, TestWorld world, Messages messages,
                                         ShardItems items, ShardCatalog catalog, ShardWeaponListener listener) {
        final NamespacedKey readyKey = NamespacedKey.fromString("veltryx:dry_riptide_ready");
        ItemStack trident = items.create(catalog.byId("shard_trident"), 0);
        eq("native Riptide III on trident", trident.getEnchantmentLevel(Enchantment.RIPTIDE), 3);
        check("trident lore explains charge and release", trident.getItemMeta().lore().stream()
                .map(c -> ((StubComponent)c).content).anyMatch(line -> line.contains("Hold to charge; release")));
        state.inventory.setItemInMainHand(trident);
        state.velocity = new org.bukkit.util.Vector(0,0,0);
        int sounds = world.riptideSounds;
        Interact air = new Interact(state.proxy);
        check("air block result is denied by default", air.isCancelled());
        listener.onInteract(air);
        check("dry right-click starts charging despite air block cancellation", state.active);
        eq("dry charge uses main hand", state.activeHand, EquipmentSlot.HAND);
        eq("dry click alone does not launch", state.riptides, 0);
        eq("dry click alone does not damage", state.durabilityUses, 0);
        eq("dry click alone does not start cooldown", state.pdc.map.get(readyKey), null);
        listener.onInteract(air);
        eq("repeat interaction does not reset an active charge", state.chargeStarts, 1);
        state.inventory.setItemInOffHand(new ItemStack(Material.ENDER_PEARL));
        Interact offhandClick = new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null);
        listener.onOffhandWhileCharging(offhandClick);
        eq("incidental offhand use is denied while charging dry", offhandClick.itemUse, org.bukkit.event.Event.Result.DENY);
        Interact offhandBlock = new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND, world.block);
        listener.onOffhandWhileCharging(offhandBlock);
        eq("incidental offhand block use is denied while charging dry", offhandBlock.blockUse, org.bukkit.event.Event.Result.DENY);
        state.inWater = true;
        Interact wetOffhand = new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null);
        listener.onOffhandWhileCharging(wetOffhand);
        eq("wet offhand interaction is not altered", wetOffhand.itemUse, org.bukkit.event.Event.Result.DEFAULT);
        state.inWater = false; state.inventory.setItemInOffHand(null);
        release(listener, state, 9);
        eq("nine-tick release does not launch", state.riptides, 0);
        eq("short release does not damage", state.durabilityUses, 0);
        eq("short release does not start cooldown", state.pdc.map.get(readyKey), null);

        listener.onInteract(new Interact(state.proxy));
        List<org.bukkit.event.player.PlayerRiptideEvent> observed = new ArrayList<>();
        org.bukkit.event.Event.testEventHook = event -> {
            if (event instanceof org.bukkit.event.player.PlayerRiptideEvent riptide) observed.add(riptide);
        };
        release(listener, state, 10);
        org.bukkit.event.Event.testEventHook = null;
        eq("ten-tick dry release launches", state.riptides, 1);
        eq("dry release applies velocity once", state.velocityChanges, 1);
        check("Riptide III has vanilla impulse", Math.abs(state.velocity.getZ() - 3.0) < .00001);
        eq("dry launch enters the native spin for 20 ticks", state.spinTicks, 20);
        eq("dry spin uses native damage", state.spinDamage, 8.0f);
        eq("dry release uses server durability handling", state.durabilityUses, 1);
        eq("dry release damages the correct hand", state.damagedHand, EquipmentSlot.HAND);
        eq("dry launch awards use statistic", state.itemUses, 1);
        eq("dry launch fires Riptide event", observed.size(), 1);
        check("Riptide event contains vanilla impulse", Math.abs(observed.get(0).getVelocity().getZ() - 3.0) < .00001);
        eq("dry launch broadcasts a sound", world.riptideSounds, sounds + 1);
        eq("dry launch uses Riptide III sound", world.lastRiptideSound, "item.trident.riptide_3");
        eq("dry launch uses players sound category", world.lastRiptideCategory, SoundCategory.PLAYERS);
        long ready = (Long)state.pdc.map.get(readyKey);
        long remaining = ready - System.currentTimeMillis();
        check("dry cooldown is five seconds", remaining > 4_000L && remaining <= 5_000L);

        listener.onInteract(new Interact(state.proxy));
        check("cooldown does not prevent charging", state.active);
        release(listener, state, 12);
        eq("second dry release blocked by cooldown", state.riptides, 1);
        eq("blocked release does not damage", state.durabilityUses, 1);
        eq("blocked release does not extend cooldown", state.pdc.map.get(readyKey), ready);
        eq("blocked release does not broadcast sound", world.riptideSounds, sounds + 1);

        int starts = state.chargeStarts;
        state.inWater = true;
        listener.onInteract(new Interact(state.proxy));
        check("wet right-click is not started by plugin", !state.active);
        eq("wet click ignores dry cooldown", state.chargeStarts, starts);
        state.nativeStart(EquipmentSlot.HAND);
        release(listener, state, 12);
        eq("wet release is passed through to native Riptide", state.riptides, 1);
        eq("wet release does not change dry cooldown", state.pdc.map.get(readyKey), ready);
        state.inWater = false; state.inRain = true;
        listener.onInteract(new Interact(state.proxy));
        check("rain right-click is not intercepted", !state.active);
        state.nativeStart(EquipmentSlot.HAND); release(listener, state, 12);
        eq("rain release is passed through to native Riptide", state.riptides, 1);
        eq("rain release does not change dry cooldown", state.pdc.map.get(readyKey), ready);
        state.inRain = false;

        listener.onInteract(new Interact(state.proxy));
        check("dry charge can start while cooling down", state.active);
        state.inWater = true; release(listener, state, 12); state.inWater = false;
        eq("dry-to-wet release is left to vanilla even on cooldown", state.riptides, 1);
        eq("dry-to-wet release leaves dry deadline unchanged", state.pdc.map.get(readyKey), ready);
        state.inWater = true; state.nativeStart(EquipmentSlot.HAND); state.inWater = false;
        release(listener, state, 12);
        eq("wet-to-dry release respects dry cooldown", state.riptides, 1);
        listener.onQuit(new Quit(state.proxy));
        check("relogging cannot remove dry cooldown", Objects.equals(state.pdc.map.get(readyKey), ready));
        ShardWeaponListener afterReconnect = new ShardWeaponListener(items, catalog, messages);
        afterReconnect.onInteract(new Interact(state.proxy)); release(afterReconnect, state, 12);
        eq("cooldown remains effective after reconnect", state.riptides, 1);

        state.pdc.map.put(readyKey, System.currentTimeMillis() - 1);
        state.inWater = true; state.nativeStart(EquipmentSlot.HAND); state.inWater = false;
        release(listener, state, 12);
        eq("wet-to-dry release launches when cooldown expired", state.riptides, 2);
        check("successful wet-to-dry launch sets a new dry cooldown", (Long)state.pdc.map.get(readyKey) > System.currentTimeMillis());
        state.pdc.map.put(readyKey, System.currentTimeMillis() - 1);
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        eq("expired dry cooldown permits another launch", state.riptides, 3);

        // Protection plugins can deny item use independently of the block result.
        state.pdc.map.remove(readyKey);
        starts = state.chargeStarts;
        Interact deniedAir = new Interact(state.proxy); deniedAir.itemUse = org.bukkit.event.Event.Result.DENY;
        listener.onInteract(deniedAir);
        eq("denied item use does not charge in air", state.chargeStarts, starts);
        world.blockType = Material.STONE;
        Interact deniedBlock = new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, world.block);
        deniedBlock.itemUse = org.bukkit.event.Event.Result.DENY;
        listener.onInteract(deniedBlock);
        eq("denied item use does not charge on a block", state.chargeStarts, starts);
        Interact protectedBlock = new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, world.block);
        protectedBlock.blockUse = org.bukkit.event.Event.Result.DENY;
        listener.onInteract(protectedBlock);
        eq("denied block with default item result does not charge", state.chargeStarts, starts);
        protectedBlock.itemUse = org.bukkit.event.Event.Result.ALLOW;
        listener.onInteract(protectedBlock);
        check("explicitly allowed item can charge on a denied block", state.active);
        state.interrupt();
        world.blockType = Material.CHEST;
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, world.block));
        check("ordinary chest interaction is not hijacked", !state.active);
        state.sneaking = true;
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, world.block));
        check("sneaking permits charging against a chest", state.active);
        state.interrupt(); state.sneaking = false; world.blockType = Material.STONE;
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, world.block));
        check("ordinary solid block permits charging", state.active);
        state.interrupt();
        state.externalCooldown = true;
        listener.onInteract(new Interact(state.proxy));
        check("external native item cooldown is respected", !state.active);
        state.externalCooldown = false;
        state.gameMode = GameMode.SPECTATOR;
        listener.onInteract(new Interact(state.proxy));
        check("spectators cannot start a dry charge", !state.active);
        state.gameMode = GameMode.SURVIVAL;
        state.allowShard = false;
        listener.onInteract(new Interact(state.proxy));
        check("dry charge requires shard permission", !state.active);
        state.allowShard = true;

        int launches = state.riptides, uses = state.durabilityUses;
        listener.onInteract(new Interact(state.proxy));
        state.interrupt();
        listener.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(state.proxy, trident, 20));
        eq("interrupted active item cannot launch", state.riptides, launches);
        listener.onInteract(new Interact(state.proxy));
        state.inventory.setItemInMainHand(new ItemStack(Material.TRIDENT));
        release(listener, state, 20);
        eq("a normal trident swapped into the hand cannot use the shard ability", state.riptides, launches);
        state.inventory.setItemInMainHand(trident);
        listener.onInteract(new Interact(state.proxy));
        items.toggleAbility(trident); release(listener, state, 20); items.toggleAbility(trident);
        eq("ability disabled mid-charge cannot launch", state.riptides, launches);
        listener.onInteract(new Interact(state.proxy)); state.vehicle = true;
        release(listener, state, 20); state.vehicle = false;
        eq("mounted release does not launch", state.riptides, launches);
        listener.onInteract(new Interact(state.proxy)); state.allowShard = false;
        release(listener, state, 20); state.allowShard = true;
        eq("permission revoked mid-charge cannot launch", state.riptides, launches);
        eq("interrupted releases do not damage", state.durabilityUses, uses);
        eq("interrupted releases do not start cooldown", state.pdc.map.get(readyKey), null);

        ItemStack worn = trident.clone();
        worn.setData(DataComponentTypes.MAX_DAMAGE, 50);
        worn.setData(DataComponentTypes.DAMAGE, 49);
        state.inventory.setItemInMainHand(worn);
        listener.onInteract(new Interact(state.proxy));
        check("custom max-damage trident at last durability cannot charge", !state.active);
        worn.setData(DataComponentTypes.UNBREAKABLE);
        listener.onInteract(new Interact(state.proxy));
        check("unbreakable trident can charge at last durability", state.active);
        state.interrupt();
        worn.unsetData(DataComponentTypes.UNBREAKABLE); worn.setData(DataComponentTypes.DAMAGE, 48);
        listener.onInteract(new Interact(state.proxy));
        check("custom-damage trident above the last use can charge", state.active);
        worn.setData(DataComponentTypes.DAMAGE, 49); release(listener, state, 12);
        eq("trident becoming unusable mid-charge cannot launch", state.riptides, launches);
        state.inventory.setItemInMainHand(trident);

        org.bukkit.event.Event.testEventHook = event -> {
            if (event instanceof org.bukkit.event.player.PlayerRiptideEvent riptide) riptide.setCancelled(true);
        };
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        org.bukkit.event.Event.testEventHook = null;
        eq("cancelled Riptide event prevents launch", state.riptides, launches);
        eq("cancelled Riptide event consumes no durability", state.durabilityUses, uses);
        eq("cancelled Riptide event starts no cooldown", state.pdc.map.get(readyKey), null);
        org.bukkit.event.Event.testEventHook = event -> {
            if (event instanceof org.bukkit.event.player.PlayerRiptideEvent) state.inWater = true;
        };
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        org.bukkit.event.Event.testEventHook = null; state.inWater = false;
        eq("becoming wet during Riptide event defers to vanilla", state.riptides, launches);
        eq("becoming wet during Riptide event consumes no dry durability", state.durabilityUses, uses);
        eq("becoming wet during Riptide event starts no dry cooldown", state.pdc.map.get(readyKey), null);

        org.bukkit.event.Event.testEventHook = event -> {
            if (event instanceof org.bukkit.event.player.PlayerRiptideEvent) state.inventory.setItemInMainHand(new ItemStack(Material.TRIDENT));
        };
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        org.bukkit.event.Event.testEventHook = null;
        eq("item removed during Riptide event prevents launch", state.riptides, launches);
        eq("item removed during Riptide event starts no cooldown", state.pdc.map.get(readyKey), null);
        state.inventory.setItemInMainHand(trident);
        state.damageHook = () -> state.inventory.setItemInMainHand(new ItemStack(Material.TRIDENT));
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        state.damageHook = null;
        eq("item removed during damage callback prevents launch", state.riptides, launches);
        eq("item removed during damage callback starts no cooldown", state.pdc.map.get(readyKey), null);
        state.inventory.setItemInMainHand(trident); uses = state.durabilityUses;
        int[] callbacks = {0};
        org.bukkit.event.Event.testEventHook = event -> {
            if (event instanceof org.bukkit.event.player.PlayerRiptideEvent) {
                callbacks[0]++;
                listener.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(state.proxy, trident, 12));
            }
        };
        listener.onInteract(new Interact(state.proxy)); release(listener, state, 12);
        org.bukkit.event.Event.testEventHook = null;
        eq("reentrant stop cannot cause duplicate Riptide event", callbacks[0], 1);
        eq("reentrant stop cannot launch twice", state.riptides, launches + 1);
        eq("reentrant stop cannot damage twice", state.durabilityUses, uses + 1);

        state.pdc.map.remove(readyKey);
        state.inventory.setItemInMainHand(null);
        state.inventory.setItemInOffHand(trident);
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null));
        check("offhand Shard Trident charges", state.active);
        release(listener, state, 10);
        eq("offhand release launches", state.riptides, launches + 2);
        eq("offhand release damages offhand", state.damagedHand, EquipmentSlot.OFF_HAND);
        state.pdc.map.remove(readyKey);
        state.gameMode = GameMode.CREATIVE; uses = state.durabilityUses;
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null));
        release(listener, state, 10); state.gameMode = GameMode.SURVIVAL;
        eq("creative dry launch does not force durability damage", state.durabilityUses, uses);

        state.pdc.map.remove(readyKey);
        ItemStack lowerLevel = trident.clone();
        ItemMeta lowerMeta = lowerLevel.getItemMeta(); lowerMeta.addEnchant(Enchantment.RIPTIDE, 1, true); lowerLevel.setItemMeta(lowerMeta);
        state.inventory.setItemInOffHand(lowerLevel);
        state.velocity = new org.bukkit.util.Vector(0,0,0);
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null));
        release(listener, state, 10);
        check("Riptide I uses vanilla level-one impulse", Math.abs(state.velocity.getZ() - 1.5) < .00001);
        eq("Riptide I uses level-one sound", world.lastRiptideSound, "item.trident.riptide_1");
        state.pdc.map.remove(readyKey); state.velocity = new org.bukkit.util.Vector(0,0,0); state.onGround = true;
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null));
        release(listener, state, 10); state.onGround = false;
        check("grounded dry launch receives a small upward lift", Math.abs(state.velocity.getY() - 0.2) < .00001);
        state.pdc.map.remove(readyKey);
        int beforeDamageLaunch = state.riptides;
        state.damageHook = () -> {
            ItemStack current = state.inventory.getItemInOffHand();
            current.setData(DataComponentTypes.DAMAGE, current.getData(DataComponentTypes.DAMAGE) + 1);
        };
        listener.onInteract(new Interact(state.proxy, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, null));
        release(listener, state, 10); state.damageHook = null;
        eq("normal durability change does not interrupt launch", state.riptides, beforeDamageLaunch + 1);
        PlayerState other = new PlayerState(world.proxy, "Bob");
        other.inventory.setItemInMainHand(trident.clone());
        listener.onInteract(new Interact(other.proxy)); release(listener, other, 10);
        eq("dry cooldown is per player", other.riptides, 1);
        state.inventory.setItemInOffHand(null); state.pdc.map.remove(readyKey);
    }

    private static void release(ShardWeaponListener listener, PlayerState state, int ticks) {
        ItemStack released = state.activeItem;
        listener.onStopUsing(new io.papermc.paper.event.player.PlayerStopUsingItemEvent(state.proxy, released, ticks));
        state.interrupt(); // Paper clears the active item after its stop event.
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
        if (listener instanceof ShopGui g) g.onClick(e); else if (listener instanceof ShopExtrasGui g) g.onClick(e); else if (listener instanceof OrderGui g) g.onClick(e); else if (listener instanceof AhGui g) g.onClick(e);
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
        final InventoryHolder holder; final ItemStack[] items; ItemStack offhand; int capacity = Integer.MAX_VALUE; boolean failAddAfterInsert;
        TestInventory(InventoryHolder holder, int size) { this.holder=holder; this.items=new ItemStack[size]; }
        public void setItem(int index, ItemStack item) { items[index]=item; }
        public ItemStack getItem(int index) { return items[index]; }
        public int getSize() { return items.length; }
        public ItemStack[] getContents() { return items; }
        public InventoryHolder getHolder() { return holder; }
        public ItemStack getChestplate() { return null; }
        public ItemStack getItemInMainHand() { return items[0]; }
        public void setItemInMainHand(ItemStack item) { items[0]=item; }
        public ItemStack getItemInOffHand() { return offhand; }
        public void setItemInOffHand(ItemStack item) { offhand=item; }
        public HashMap<Integer,ItemStack> addItem(ItemStack... additions) {
            HashMap<Integer,ItemStack> left = new HashMap<>();
            for (int j=0;j<additions.length;j++) {
                ItemStack stack=additions[j]; int amount=stack.getAmount(); int accept=Math.min(amount,capacity);
                int slot=-1; for(int i=0;i<items.length;i++) if(items[i]==null || items[i].getAmount()<=0 || items[i].getType()==Material.AIR) { slot=i; break; }
                if(slot<0) accept=0;
                if(accept>0) { stack.setAmount(accept); items[slot]=stack; if(capacity!=Integer.MAX_VALUE) capacity-=accept;
                    if(failAddAfterInsert) {failAddAfterInsert=false; throw new IllegalStateException("simulated partial delivery failure");} }
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
        Inventory open; ItemStack cursor; Scoreboard board; Player killer; boolean allowSell=true, allowShard=true, allowAh=true, allowOrder=true, signOpened, inWater, inRain, teleportSuccess=true, online=true;
        boolean active, sneaking, vehicle, onGround, externalCooldown, dead;
        EquipmentSlot activeHand, damagedHand; ItemStack activeItem; GameMode gameMode=GameMode.SURVIVAL;
        org.bukkit.util.Vector velocity=new org.bukkit.util.Vector(0,0,0);
        int blockChanges, riptides, durabilityUses, sounds, chargeStarts, velocityChanges, itemUses, spinTicks;
        float spinDamage; String lastSound; double health=20, absorption; Location teleportDestination;
        java.util.concurrent.CompletableFuture<Boolean> teleportFuture; Runnable damageHook;
        PlayerState(World world) { this(world, "Alice"); }
        PlayerState(World world, String name) { this.world=world; this.name=name; proxy=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},this); }
        void nativeStart(EquipmentSlot hand) { active=true; activeHand=hand; activeItem=hand==EquipmentSlot.HAND?inventory.getItemInMainHand():inventory.getItemInOffHand(); }
        void interrupt() { active=false; activeItem=null; }
        public Object invoke(Object o, Method m, Object[] a) {
            return switch(m.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "isOnline" -> online;
                case "hasPermission" -> switch ((String)a[0]) { case "veltryx.sell" -> allowSell; case "veltryx.shards" -> allowShard; case "veltryx.ah" -> allowAh; case "veltryx.order" -> allowOrder; default -> true; };
                case "getKiller" -> killer; case "setScoreboard" -> { board=(Scoreboard)a[0]; yield null; }
                case "getScoreboard" -> board; case "getGameMode" -> gameMode;
                case "playSound" -> { sounds++; lastSound=(String)a[1]; yield null; }
                case "setItemOnCursor" -> { cursor=(ItemStack)a[0]; yield null; }
                case "openVirtualSign" -> { signOpened=true; yield null; }
                case "sendBlockChange" -> { blockChanges++; yield null; }
                case "getInventory" -> inventory; case "getWorld" -> world;
                case "getLocation" -> new Location(world,100,64,200) { @Override public org.bukkit.util.Vector getDirection(){return new org.bukkit.util.Vector(0,0,1);} };
                case "getVelocity" -> velocity.clone();
                case "setVelocity" -> { velocity=((org.bukkit.util.Vector)a[0]).clone(); velocityChanges++; yield null; }
                case "isInWater" -> inWater; case "isInRain" -> inRain;
                case "isSneaking" -> sneaking; case "isInsideVehicle" -> vehicle; case "isOnGround" -> onGround; case "isDead" -> dead;
                case "hasCooldown" -> externalCooldown;
                case "startUsingItem" -> { if(!active) {nativeStart((EquipmentSlot)a[0]); chargeStarts++;} yield null; }
                case "hasActiveItem" -> active; case "getActiveItemHand" -> activeHand; case "getActiveItem" -> activeItem;
                case "clearActiveItem" -> { interrupt(); yield null; }
                case "damageItemStack" -> { durabilityUses++; damagedHand=(EquipmentSlot)a[0]; if(damageHook!=null) damageHook.run(); yield null; }
                case "incrementStatistic" -> { itemUses++; yield null; }
                case "startRiptideAttack" -> { riptides++; spinTicks=(Integer)a[0]; spinDamage=(Float)a[1]; yield null; }
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
        final Map<String,AhListing> listings = new HashMap<>();
        boolean failOrderWrites, failNextBalance, failAhWrites, failNextTransfer;
        final DataStore proxy=(DataStore)Proxy.newProxyInstance(DataStore.class.getClassLoader(),new Class<?>[]{DataStore.class},this);
        public Object invoke(Object o, Method m, Object[] a) {
            return switch (m.getName()) {
                case "getBalance" -> dev.superseller.veltryx.util.Money.legacyLong(balances.getOrDefault((UUID)a[0],java.math.BigInteger.ZERO));
                case "getBalanceExact" -> balances.getOrDefault((UUID)a[0],java.math.BigInteger.ZERO);
                case "setBalance" -> { balances.put((UUID)a[0],java.math.BigInteger.valueOf((Long)a[2])); yield null; }
                case "setBalanceExact" -> { if(failNextBalance){failNextBalance=false;throw new IllegalStateException("simulated balance failure");} balances.put((UUID)a[0],(java.math.BigInteger)a[2]); yield null; }
                case "allAhListings" -> List.copyOf(listings.values());
                case "saveAhListing" -> {if(failAhWrites)throw new IllegalStateException("simulated auction failure"); AhListing listing=(AhListing)a[0];listings.put(listing.id(),listing);yield null;}
                case "allBuyOrders" -> List.copyOf(orders.values());
                case "saveBuyOrder" -> {if(failOrderWrites)throw new IllegalStateException("simulated order failure"); BuyOrder order=(BuyOrder)a[0];orders.put(order.id(),order);yield null;}
                case "setBalancesExact" -> { if(failNextTransfer){failNextTransfer=false;throw new IllegalStateException("simulated transfer failure");} for (dev.superseller.veltryx.model.Account account : (List<dev.superseller.veltryx.model.Account>) a[0]) balances.put(account.uuid(),account.balanceExact()); yield null; }
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
        final Player player; final org.bukkit.event.block.Action action; final EquipmentSlot hand; final org.bukkit.block.Block block;
        final ItemStack item; org.bukkit.event.Event.Result itemUse=org.bukkit.event.Event.Result.DEFAULT, blockUse;
        Interact(Player p) { this(p,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND,null); }
        Interact(Player p,org.bukkit.event.block.Action action,EquipmentSlot hand,org.bukkit.block.Block block) {
            this.player=p; this.action=action; this.hand=hand; this.block=block;
            this.item=hand==EquipmentSlot.HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand();
            this.blockUse=block==null?org.bukkit.event.Event.Result.DENY:org.bukkit.event.Event.Result.ALLOW;
        }
        @Override public Player getPlayer(){return player;} @Override public org.bukkit.event.block.Action getAction(){return action;}
        @Override public EquipmentSlot getHand(){return hand;} @Override public org.bukkit.block.Block getClickedBlock(){return block;}
        @Override public ItemStack getItem(){return item;} @Override public org.bukkit.event.Event.Result useItemInHand(){return itemUse;}
        @Override public org.bukkit.event.Event.Result useInteractedBlock(){return blockUse;}
        @Override public void setCancelled(boolean value){
            if(value) {itemUse=org.bukkit.event.Event.Result.DENY; blockUse=org.bukkit.event.Event.Result.DENY;}
            else {itemUse=org.bukkit.event.Event.Result.DEFAULT; blockUse=block==null?org.bukkit.event.Event.Result.DENY:org.bukkit.event.Event.Result.ALLOW;}
        }
    }
    private static final class Quit extends org.bukkit.event.player.PlayerQuitEvent {
        final Player player; Quit(Player player){this.player=player;} @Override public Player getPlayer(){return player;}
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
        boolean findStructure=true, findUnexplored, fakeBlock, unlimitedDrop; int breaks, riptideSounds; Material blockType=Material.STONE; UUID dropOwner; ItemStack dropped;
        String lastRiptideSound; SoundCategory lastRiptideCategory;
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
            if(m.getName().equals("playSound")) { riptideSounds++; lastRiptideSound=(String)a[1]; lastRiptideCategory=(SoundCategory)a[2]; return null; }
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
