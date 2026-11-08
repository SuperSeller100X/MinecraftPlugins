package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.market.AhService;
import dev.superseller.veltryx.market.OrderService;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.shards.ShardService;
import dev.superseller.veltryx.spawner.*;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.PlatformScheduler;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.StubComponent;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.spawner.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.loot.LootContext;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;

/** Stateful offline tests; these doubles do not prove real Paper compatibility. */
public final class SpawnerTest {
    private static int passed, failed;
    private static final Logger LOG = Logger.getLogger("spawner-test");
    private static final Path RES = Path.of("src/main/resources");

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("veltryx-spawners-");
        try { run(dir); }
        finally {
            try (var files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
            }
        }
        System.out.println("Spawner: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }

    private static void run(Path dir) throws Exception {
        FakePlugin plugin = new FakePlugin(dir);
        FakeScheduler scheduler = new FakeScheduler();
        Bukkit.testScheduler = scheduler;
        Bukkit.testInventoryFactory = (holder, size) -> new TestInventory(holder, size);
        PlatformScheduler.init(plugin);
        Messages messages = new Messages(plugin); messages.reload();
        BalanceData data = new BalanceData();
        ShardService shards = new ShardService(data.proxy);
        SpawnerItems items = new SpawnerItems(plugin);
        SpawnerStore store = new SpawnerStore(dir.resolve("spawners"), LOG);
        SpawnerLootGui gui = new SpawnerLootGui(store, messages);
        SpawnerListener listener = new SpawnerListener(items, store, gui, messages, LOG);
        SpawnerShopGui shop = new SpawnerShopGui(shards, items, store, messages, LOG, p -> {});
        TestWorld world = new TestWorld();
        PlayerState state = new PlayerState(world.proxy);
        Player player = state.proxy;
        shards.give(state.id, 10_000);

        shop.open(player); scheduler.drain();
        eq("shop has seven types", SpawnerType.values().length, 7);
        eq("price per spawner", SpawnerType.PRICE_SHARDS, 1000L);
        eq("shop inventory size", state.open.getSize(), 27);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < SpawnerType.values().length; i++) {
            SpawnerType type = SpawnerType.values()[i];
            ItemStack icon = state.open.getItem(10 + i);
            check("shop icon " + type, icon != null && icon.getType() == Material.SPAWNER && items.identity(icon) == null);
            check("shop shows exact price " + type, icon.getItemMeta().lore().stream().map(c -> ((StubComponent)c).content).anyMatch(s -> s.contains("1,000 shards")));
            Click click = new Click(state.open, state.open, player, 10 + i, false, false);
            shop.onClick(click); scheduler.drain();
            check("shop click cancelled " + type, click.cancelled);
            ItemStack bought = state.inventory.getItem(i);
            SpawnerItems.Identity identity = items.identity(bought);
            check("purchased correct unique spawner " + type, identity != null && identity.type() == type && ids.add(identity.id()));
            eq("charges exactly 1000 for " + type, shards.balance(state.id), 10_000L - 1000L * (i + 1));
        }
        eq("seven unique spawners", ids.size(), 7);
        state.allowShards = false;
        long before = shards.balance(state.id);
        Click denied = new Click(state.open, state.open, player, 10, false, false);
        shop.onClick(denied); scheduler.drain();
        check("permission denied without purchase", denied.cancelled && shards.balance(state.id) == before);
        state.allowShards = true;
        shards.take(state.id, before - 999);
        Click poor = new Click(state.open, state.open, player, 10, false, false);
        shop.onClick(poor); scheduler.drain();
        eq("insufficient shards are not charged", shards.balance(state.id), 999L);

        ItemStack purchased = state.inventory.getItem(0);
        SpawnerItems.Identity pig = items.identity(purchased);
        TestBlock block = new TestBlock(world.proxy, 4, 70, 8);
        Place placed = new Place(player, purchased, block);
        listener.onPlace(placed); listener.onPlaceFinished(placed);
        check("custom item places", !placed.cancelled);
        SpawnerStore.Position position = SpawnerStore.Position.of(block);
        eq("default spawner mode", store.modeAt(pig, position), SpawnerStore.Mode.SPAWNER);
        check("configuration waits for captured tile entity to commit", block.spawner.entry == null);
        // Simulate Paper replacing the event-time tile with its captured tile.
        block.spawner = new TestSpawner(block);
        scheduler.drain();
        eq("configured pig type after placement commits", block.spawner.type, EntityType.PIG);
        SpawnRule current = block.spawner.entry.getSpawnRule();
        check("current spawn ignores block and sky light", current.getMinBlockLight()==0 && current.getMaxBlockLight()==15
                && current.getMinSkyLight()==0 && current.getMaxSkyLight()==15);
        check("potential spawn retains light override", block.spawner.potentials.size()==1
                && block.spawner.potentials.get(0).getSpawnRule().getMaxBlockLight()==15);
        eq("block and item identity match", items.identity(block), pig);

        Place duplicate = new Place(player, purchased, new TestBlock(world.proxy, 5, 70, 8));
        listener.onPlace(duplicate); listener.onPlaceFinished(duplicate);
        check("copied identity cannot be placed twice", duplicate.cancelled);
        eq("duplicate rejection leaves original bound", store.modeAt(pig, position), SpawnerStore.Mode.SPAWNER);
        Inventory previouslyOpen = state.open;
        Interact deniedInteract = new Interact(player, block, EquipmentSlot.HAND); deniedInteract.cancelled = true;
        listener.onInteract(deniedInteract); scheduler.drain();
        check("cancelled interaction cannot open spawner storage", state.open == previouslyOpen);
        Interact open = new Interact(player, block, EquipmentSlot.HAND);
        listener.onInteract(open); scheduler.drain();
        check("right click opens spawner storage", open.cancelled && state.open.getHolder() instanceof SpawnerLootGui.Holder);
        eq("storage page is 54 slots", state.open.getSize(), 54);
        check("page one has next but no previous", state.open.getItem(53).getType()==Material.ARROW && state.open.getItem(45).getType()!=Material.ARROW);
        Interact offhand = new Interact(player, block, EquipmentSlot.OFF_HAND);
        listener.onInteract(offhand); check("offhand use cannot alter spawner", offhand.cancelled);

        Entity pigEntity = entity(world.proxy, EntityType.PIG);
        Spawn vanilla = new Spawn(block.spawner, pigEntity);
        listener.onSpawn(vanilla);
        check("spawner mode allows normal native spawning", !vanilla.cancelled);
        Click toggle = new Click(state.open, state.open, player, 49, false, false);
        gui.onClick(toggle); scheduler.drain();
        eq("GUI switches to inventory mode", store.modeAt(pig, position), SpawnerStore.Mode.INVENTORY);
        final LootContext[] seenContext = {null}; final String[] seenKey = {null};
        Bukkit.testLootFactory = key -> {
            seenKey[0] = key.toString();
            return (random, context) -> {
                seenContext[0] = context;
                ItemStack meat = new ItemStack(Material.PORKCHOP, 3);
                ItemMeta meta = meat.getItemMeta();
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "loot_marker"), PersistentDataType.STRING, "kept");
                meat.setItemMeta(meta);
                return List.of(meat, new ItemStack(Material.BONE, 2));
            };
        };
        Spawn deniedSpawn = new Spawn(block.spawner, pigEntity); deniedSpawn.cancelled = true;
        listener.onSpawn(deniedSpawn); scheduler.drain();
        check("cancelled spawn cannot generate virtual loot", store.page(pig, position, 0).items()[0] == null);
        Spawn virtual = new Spawn(block.spawner, pigEntity);
        listener.onSpawn(virtual); scheduler.drain();
        check("inventory mode cancels the physical mob", virtual.cancelled);
        eq("uses the mob's actual loot-table key", seenKey[0], "minecraft:entities/pig");
        check("loot context contains the real spawnee", seenContext[0] != null && seenContext[0].looted == pigEntity);
        eq("first loot stored", store.page(pig, position, 0).items()[0].getAmount(), 3);
        check("open view refreshes when loot arrives", state.open.getItem(0) != null && state.open.getItem(0).getAmount()==3);
        eq("different loot stacks stored", store.page(pig, position, 0).items()[1].getType(), Material.BONE);
        Spawn wrongMob = new Spawn(block.spawner, entity(world.proxy, EntityType.COW));
        listener.onSpawn(wrongMob);
        check("changed spawner mob is rejected", wrongMob.cancelled);
        eq("changed mob did not generate loot", store.page(pig, position, 0).items()[0].getAmount(), 3);

        Click takeOne = new Click(state.open, state.open, player, 0, true, false);
        gui.onClick(takeOne); scheduler.drain();
        check("loot click cancelled", takeOne.cancelled);
        eq("right-click withdraws one", store.page(pig, position, 0).items()[0].getAmount(), 2);
        check("withdrawal keeps loot metadata", state.inventory.last(Material.PORKCHOP).getItemMeta()
                .getPersistentDataContainer().get(new NamespacedKey(plugin, "loot_marker"), PersistentDataType.STRING).equals("kept"));
        Click next = new Click(state.open, state.open, player, 53, false, false);
        for (int page = 1; page < SpawnerStore.PAGES; page++) {
            gui.onClick(next); scheduler.drain();
            check("page " + (page+1) + " reachable", state.open.getHolder() instanceof SpawnerLootGui.Holder);
            next = new Click(state.open, state.open, player, 53, false, false);
        }
        check("page 20 has previous and no next", state.open.getItem(45).getType()==Material.ARROW && state.open.getItem(53).getType()!=Material.ARROW);
        Drag drag = new Drag(state.open, state.inventory); gui.onDrag(drag);
        check("dragging into storage is blocked", drag.cancelled);
        Click bottom = new Click(state.open, state.inventory, player, 0, false, true); gui.onClick(bottom);
        check("shift-clicking player inventory into storage is blocked", bottom.cancelled);

        // Reload preserves type, position, mode, all slots and item metadata.
        SpawnerStore restarted = new SpawnerStore(dir.resolve("spawners"), LOG);
        eq("mode survives restart", restarted.modeAt(pig, position), SpawnerStore.Mode.INVENTORY);
        ItemStack reloaded = restarted.page(pig, position, 0).items()[0];
        eq("loot survives restart", reloaded.getAmount(), 2);
        eq("loot metadata survives restart", reloaded.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "loot_marker"), PersistentDataType.STRING), "kept");
        reloaded.setAmount(60);
        eq("page snapshot cannot mutate stored items", restarted.page(pig, position, 0).items()[0].getAmount(), 2);

        // Protect against other plugins cancelling placement or breaking.
        SpawnerItems.Identity cow = items.identity(state.inventory.getItem(2));
        TestBlock cancelledBlock = new TestBlock(world.proxy, 50, 70, 8);
        Place cancelledPlace = new Place(player, state.inventory.getItem(2), cancelledBlock);
        listener.onPlace(cancelledPlace); cancelledPlace.cancelled = true; listener.onPlaceFinished(cancelledPlace);
        check("cancelled placement rolls back binding", store.bind(cow, SpawnerStore.Position.of(cancelledBlock)));
        check("test rollback can be unbound", store.unbind(cow, SpawnerStore.Position.of(cancelledBlock)));
        SpawnerItems.Identity sheep = items.identity(state.inventory.getItem(1));
        TestBlock lateBlock = new TestBlock(world.proxy, 51, 70, 8);
        Place late = new Place(player, state.inventory.getItem(1), lateBlock);
        listener.onPlace(late); listener.onPlaceFinished(late); late.cancelled = true; scheduler.drain();
        check("late placement cancellation releases binding", store.bind(sheep, SpawnerStore.Position.of(lateBlock)));
        check("late cancellation does not create a duplicate refund", world.lastDrop == null);
        check("test late binding can be released", store.unbind(sheep, SpawnerStore.Position.of(lateBlock)));
        TestBlock removedBlock = new TestBlock(world.proxy, 52, 70, 8);
        Place removed = new Place(player, state.inventory.getItem(1), removedBlock);
        listener.onPlace(removed); listener.onPlaceFinished(removed);
        removedBlock.type = Material.AIR; scheduler.drain();
        check("removed-before-commit spawner is refunded with identity", world.lastDrop != null
                && sheep.equals(items.identity(world.lastDrop.stack)));
        check("placement refund is owner-only and does not expire", state.id.equals(world.lastDrop.owner)
                && world.lastDrop.unlimited && world.lastDrop.invulnerable);
        check("refunded spawner can be placed again", store.bind(sheep, SpawnerStore.Position.of(removedBlock)));
        check("test refund binding can be released", store.unbind(sheep, SpawnerStore.Position.of(removedBlock)));

        testMarketGuards(data, player, messages);
        state.inventory.setItem(0, new ItemStack(Material.STONE));
        Break noSilk = new Break(player, block); listener.onBreak(noSilk); listener.onBreakFinished(noSilk);
        check("non-Silk break is cancelled", noSilk.cancelled);
        eq("non-Silk break retains mode and storage", store.modeAt(pig, position), SpawnerStore.Mode.INVENTORY);
        ItemStack silk = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta silkMeta = silk.getItemMeta(); silkMeta.addEnchant(Enchantment.SILK_TOUCH, 1, true); silk.setItemMeta(silkMeta);
        state.inventory.setItem(0, silk);
        Break protectedBreak = new Break(player, block); listener.onBreak(protectedBreak); protectedBreak.cancelled=true;
        listener.onBreakFinished(protectedBreak);
        eq("another plugin cancelling break retains binding", store.modeAt(pig, position), SpawnerStore.Mode.INVENTORY);
        TestInventory full = state.inventory;
        ItemStack[] snapshot = cloneItems(full.items);
        for (int i = 1; i < full.items.length; i++) full.setItem(i, new ItemStack(Material.STONE, 64));
        Break fullBreak = new Break(player, block); listener.onBreak(fullBreak); listener.onBreakFinished(fullBreak);
        check("full inventory prevents break", fullBreak.cancelled);
        full.setStorageContents(snapshot);
        Break pickup = new Break(player, block); listener.onBreak(pickup);
        pickup.dropItems = true; pickup.xp = 10; // another listener changed the flags
        listener.onBreakFinished(pickup); scheduler.drain();
        check("Silk Touch pickup succeeds without vanilla drops", !pickup.cancelled && !pickup.dropItems && pickup.xp==0);
        ItemStack returned = state.inventory.findSpawner(pig, items);
        check("spawner goes directly into inventory", returned != null);
        check("open spawner view closes after pickup", state.open == null);
        TestBlock moved = new TestBlock(world.proxy, 10, 72, 12);
        Place replace = new Place(player, returned, moved); listener.onPlace(replace); listener.onPlaceFinished(replace); scheduler.drain();
        SpawnerStore.Position movedPosition = SpawnerStore.Position.of(moved);
        check("replacing picked-up spawner succeeds", !replace.cancelled);
        eq("mode follows moved spawner", store.modeAt(pig, movedPosition), SpawnerStore.Mode.INVENTORY);
        eq("loot follows moved spawner", store.page(pig, movedPosition, 0).items()[0].getAmount(), 2);
        Explode explosion = new Explode(new ArrayList<>(List.of(moved, new TestBlock(world.proxy, 11, 72, 12))));
        listener.onEntityExplosion(explosion);
        eq("explosion keeps custom spawner but not vanilla spawner", explosion.blocks.size(), 1);
        check("remaining block is vanilla", items.identity(explosion.blocks.get(0)) == null);

        testFullAndConcurrent(dir, plugin, world.proxy, store, items);
        testAllMobTables(dir, plugin, world.proxy, store, items, listener, gui, scheduler);
        testDiskFailure(dir, plugin, shards, messages, player, state, scheduler, items);
        testRegionScheduler(world.proxy);
    }

    private static void testRegionScheduler(World world) throws Exception {
        Field folia = PlatformScheduler.class.getDeclaredField("folia");
        Field getter = PlatformScheduler.class.getDeclaredField("regionSchedulerGet");
        Field run = PlatformScheduler.class.getDeclaredField("regionRun");
        folia.setAccessible(true); getter.setAccessible(true); run.setAccessible(true);
        Object oldFolia = folia.get(null), oldGetter = getter.get(null), oldRun = run.get(null);
        try {
            folia.set(null, true);
            getter.set(null, SpawnerTest.class.getMethod("testRegion"));
            run.set(null, TestRegion.class.getMethod("run", Plugin.class, Location.class, java.util.function.Consumer.class));
            Location location = new Location(world, 100, 70, -100);
            boolean[] executed = {false};
            PlatformScheduler.runForLocation(location, () -> executed[0] = true);
            check("region scheduling is deferred", !executed[0] && TEST_REGION.location == location);
            TEST_REGION.task.accept(null);
            check("region-owned task runs", executed[0]);
            getter.set(null, null);
            boolean rejected = false;
            try { PlatformScheduler.runForLocation(location, () -> {}); }
            catch (IllegalStateException expected) { rejected = true; }
            check("missing region scheduler fails closed", rejected);
        } finally {
            folia.set(null, oldFolia); getter.set(null, oldGetter); run.set(null, oldRun);
        }
    }

    private static final TestRegion TEST_REGION = new TestRegion();
    public static TestRegion testRegion() { return TEST_REGION; }
    public static final class TestRegion {
        Location location; java.util.function.Consumer<Object> task;
        public Object run(Plugin plugin, Location location, java.util.function.Consumer<Object> task) {
            this.location = location; this.task = task; return null;
        }
    }

    private static void testMarketGuards(BalanceData data, Player player, Messages messages) {
        VeltryxConfig config = new VeltryxConfig(new org.bukkit.configuration.file.YamlConfiguration());
        EconomyService economy = new EconomyService(data.proxy, config);
        AhService ah = new AhService(data.proxy, economy, messages, config);
        OrderService orders = new OrderService(data.proxy, economy, messages, config);
        eq("auction cannot strip spawner identity", ah.list(player, "SPAWNER", 1, 100).templateKey(), "spawner.market-disabled");
        eq("spawner buy orders cannot be created", orders.create(player, "SPAWNER", 1, 100).templateKey(), "spawner.market-disabled");
        long now = System.currentTimeMillis();
        data.orders.add(new BuyOrder("old-spawner-order", UUID.randomUUID(), "OldBuyer", "SPAWNER",
                1, 0, 100, now, now + 60_000, false));
        eq("existing orders cannot strip spawner identity", orders.fill(player, "old-spawner-order", 1).templateKey(), "spawner.market-disabled");
    }

    private static void testFullAndConcurrent(Path dir, FakePlugin plugin, World world, SpawnerStore store, SpawnerItems items) throws Exception {
        SpawnerItems.Identity id = new SpawnerItems.Identity(UUID.randomUUID(), SpawnerType.COW);
        SpawnerStore.Position pos = new SpawnerStore.Position(world.getUID(), 20, 70, 20);
        check("bulk storage created", store.create(id)); check("bulk storage bound", store.bind(id,pos));
        eq("bulk inventory mode", store.toggle(id,pos), SpawnerStore.Mode.INVENTORY);
        List<ItemStack> fill = new ArrayList<>();
        for (int i=0;i<SpawnerStore.CAPACITY;i++) fill.add(new ItemStack(Material.STONE,64));
        eq("all twenty pages accept loot", store.deposit(id,pos,fill), SpawnerStore.Deposit.STORED);
        eq("last slot of page 20 populated", store.page(id,pos,19).items()[44].getAmount(),64);
        eq("full inventory rejects entire roll", store.deposit(id,pos,List.of(new ItemStack(Material.STONE,1))), SpawnerStore.Deposit.FULL);
        TestInventory receiver = new TestInventory(null,36);
        ItemStack displayed = store.page(id,pos,19).items()[44];
        eq("single item withdrawal from page 20", store.withdraw(id,pos,899,displayed,true,receiver).amount(),1);
        eq("overflowing multi-item roll is atomic", store.deposit(id,pos,List.of(new ItemStack(Material.STONE,1),new ItemStack(Material.DIAMOND,1))),SpawnerStore.Deposit.FULL);
        eq("rejected roll did not partially fill", store.page(id,pos,19).items()[44].getAmount(),63);
        eq("available space accepts a fitting roll", store.deposit(id,pos,List.of(new ItemStack(Material.STONE,1))),SpawnerStore.Deposit.STORED);
        SpawnerStore reopened = new SpawnerStore(dir.resolve("spawners"), LOG);
        eq("last page survives restart", reopened.page(id,pos,19).items()[44].getAmount(),64);

        SpawnerItems.Identity concurrent = new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.ZOMBIE);
        SpawnerStore.Position p = new SpawnerStore.Position(world.getUID(),21,70,20);
        check("concurrent test created", store.create(concurrent) && store.bind(concurrent,p)); store.toggle(concurrent,p);
        eq("concurrent stack stored",store.deposit(concurrent,p,List.of(new ItemStack(Material.BONE,64))),SpawnerStore.Deposit.STORED);
        ItemStack sameDisplay = store.page(concurrent,p,0).items()[0];
        TestInventory a=new TestInventory(null,36), b=new TestInventory(null,36);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go=new CountDownLatch(1);
            Future<SpawnerStore.Withdrawal> one=executor.submit(() -> {go.await(); return store.withdraw(concurrent,p,0,sameDisplay,false,a);});
            Future<SpawnerStore.Withdrawal> two=executor.submit(() -> {go.await(); return store.withdraw(concurrent,p,0,sameDisplay,false,b);});
            go.countDown();
            eq("concurrent viewers cannot duplicate loot",one.get().amount()+two.get().amount(),64);
            eq("stored stack removed once",store.page(concurrent,p,0).items()[0],null);
        } finally { executor.shutdownNow(); }
    }

    private static void testAllMobTables(Path dir, FakePlugin plugin, World world, SpawnerStore store,
                                         SpawnerItems items, SpawnerListener listener, SpawnerLootGui gui,
                                         FakeScheduler scheduler) {
        final String[] key = {null};
        Bukkit.testLootFactory = k -> { key[0]=k.toString(); return (random,context) -> List.of(new ItemStack(Material.DIAMOND,1)); };
        int x=30;
        for (SpawnerType type : SpawnerType.values()) {
            SpawnerItems.Identity id=new SpawnerItems.Identity(UUID.randomUUID(),type);
            TestBlock block=new TestBlock(world,x++,70,40); SpawnerStore.Position position=SpawnerStore.Position.of(block);
            check("create and bind " + type,store.create(id) && store.bind(id,position) && items.configure(block,id));
            store.toggle(id,position);
            Spawn event=new Spawn(block.spawner,entity(world,type.entityType())); listener.onSpawn(event); scheduler.drain();
            check("inventory mode intercepts " + type,event.cancelled);
            eq("correct table " + type,key[0],"minecraft:"+type.lootTable());
            eq("generated loot stored " + type,store.page(id,position,0).items()[0].getType(),Material.DIAMOND);
        }
    }

    private static void testDiskFailure(Path dir, FakePlugin plugin, ShardService shards, Messages messages,
                                        Player player, PlayerState state, FakeScheduler scheduler, SpawnerItems items) throws Exception {
        Path regularFile=dir.resolve("not-a-directory"); Files.writeString(regularFile,"x");
        Logger expectedFailure = Logger.getLogger("spawner-test-expected-disk-failure");
        expectedFailure.setLevel(java.util.logging.Level.OFF);
        SpawnerStore failing=new SpawnerStore(regularFile,expectedFailure);
        SpawnerShopGui broken=new SpawnerShopGui(shards,items,failing,messages,expectedFailure,p -> {});
        shards.give(state.id,5000); long before=shards.balance(state.id);
        broken.open(player); scheduler.drain();
        int count=state.inventory.count(Material.SPAWNER);
        Click click=new Click(state.open,state.open,player,10,false,false); broken.onClick(click); scheduler.drain();
        eq("failed storage refunds shards",shards.balance(state.id),before);
        eq("failed storage gives no spawner",state.inventory.count(Material.SPAWNER),count);
    }

    private static Entity entity(World world, EntityType type) {
        return (Entity)Proxy.newProxyInstance(Entity.class.getClassLoader(),new Class<?>[]{Entity.class},(o,m,a) -> switch(m.getName()) {
            case "getType" -> type; case "getWorld" -> world; case "getLocation" -> new Location(world,4,70,8); default -> defaults(m.getReturnType());
        });
    }
    private static ItemStack[] cloneItems(ItemStack[] input) { ItemStack[] out=new ItemStack[input.length]; for(int i=0;i<input.length;i++)out[i]=input[i]==null?null:input[i].clone(); return out; }
    private static void check(String name, boolean condition){if(condition)passed++;else{failed++;System.out.println("FAIL "+name);}}
    private static void eq(String name,Object actual,Object expected){check(name+" expected="+expected+" actual="+actual,Objects.equals(actual,expected));}
    private static Object defaults(Class<?> t){if(!t.isPrimitive() || t==void.class)return null;if(t==boolean.class)return false;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;return 0;}

    private static final class FakePlugin extends JavaPlugin {
        final Path dir; FakePlugin(Path dir){this.dir=dir;}
        @Override public java.io.File getDataFolder(){return dir.toFile();}
        @Override public Logger getLogger(){return LOG;}
        @Override public java.io.InputStream getResource(String name){try{return Files.newInputStream(RES.resolve(name));}catch(Exception e){return null;}}
    }
    private static final class FakeScheduler implements BukkitScheduler {
        final Deque<Runnable> tasks=new ArrayDeque<>();
        BukkitTask add(Runnable r){tasks.add(r);return () -> {};}
        public BukkitTask runTask(Plugin p,Runnable r){return add(r);} public BukkitTask runTaskAsynchronously(Plugin p,Runnable r){return add(r);}
        public BukkitTask runTaskLater(Plugin p,Runnable r,long delay){return add(r);}
        public BukkitTask runTaskTimer(Plugin p,Runnable r,long delay,long period){return () -> {};}
        void drain(){int count=0;while(!tasks.isEmpty()){if(++count>10000)throw new IllegalStateException("scheduler loop");tasks.removeFirst().run();}}
    }
    private static final class TestInventory implements PlayerInventory {
        final InventoryHolder holder; final ItemStack[] items;
        TestInventory(InventoryHolder holder,int size){this.holder=holder;items=new ItemStack[size];}
        public void setItem(int slot,ItemStack item){items[slot]=item;} public ItemStack getItem(int slot){return items[slot];}
        public int getSize(){return items.length;} public ItemStack[] getContents(){return items;} public InventoryHolder getHolder(){return holder;}
        public ItemStack getChestplate(){return null;} public ItemStack getItemInMainHand(){return items[0];} public void setItemInMainHand(ItemStack item){items[0]=item;}
        public HashMap<Integer,ItemStack> addItem(ItemStack... added){
            HashMap<Integer,ItemStack> left=new HashMap<>();
            for(int n=0;n<added.length;n++){
                ItemStack item=added[n]; int amount=item.getAmount(); int max=item.getMaxStackSize();
                for(ItemStack current:items) if(current!=null && current.getType()!=Material.AIR && current.isSimilar(item) && current.getAmount()<max){
                    int add=Math.min(amount,max-current.getAmount());current.setAmount(current.getAmount()+add);amount-=add;if(amount==0)break;
                }
                for(int i=0;i<items.length && amount>0;i++)if(items[i]==null || items[i].getType()==Material.AIR || items[i].getAmount()<=0){
                    int add=Math.min(amount,max);items[i]=item.clone();items[i].setAmount(add);amount-=add;
                }
                if(amount>0){ItemStack remainder=item.clone();remainder.setAmount(amount);left.put(n,remainder);}
            }
            return left;
        }
        int count(Material material){int n=0;for(ItemStack item:items)if(item!=null&&item.getType()==material)n+=item.getAmount();return n;}
        ItemStack last(Material material){ItemStack result=null;for(ItemStack item:items)if(item!=null&&item.getType()==material)result=item;return result;}
        ItemStack findSpawner(SpawnerItems.Identity id,SpawnerItems factory){for(ItemStack item:items)if(id.equals(factory.identity(item)))return item;return null;}
    }
    private static final class TestView implements InventoryView {
        final Inventory top,bottom; TestView(Inventory top,Inventory bottom){this.top=top;this.bottom=bottom;}
        public Inventory getTopInventory(){return top;} public Inventory getBottomInventory(){return bottom;}
        public ItemStack getCursor(){return null;} public void setCursor(ItemStack stack){}
    }
    private static final class PlayerState implements InvocationHandler {
        final UUID id=UUID.randomUUID(); final TestInventory inventory=new TestInventory(null,36); final World world;
        final Player proxy; Inventory open; boolean allowShards=true;
        PlayerState(World world){this.world=world;proxy=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},this);}
        public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()){
            case "getUniqueId" -> id; case "getName" -> "Alex"; case "isOnline" -> true;
            case "hasPermission" -> !"veltryx.shards".equals(a[0]) || allowShards;
            case "getInventory" -> inventory; case "getWorld" -> world; case "getLocation" -> new Location(world,4,70,8);
            case "openInventory" -> {open=(Inventory)a[0];yield new TestView(open,inventory);}
            case "getOpenInventory" -> new TestView(open,inventory); case "closeInventory" -> {open=null;yield null;}
            case "equals" -> o==a[0]; case "hashCode" -> id.hashCode(); default -> defaults(m.getReturnType());
        };}
    }
    private static final class BalanceData implements InvocationHandler {
        final Map<UUID,Long> balances=new HashMap<>(); final List<BuyOrder> orders=new ArrayList<>();
        final DataStore proxy=(DataStore)Proxy.newProxyInstance(DataStore.class.getClassLoader(),new Class<?>[]{DataStore.class},this);
        public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()){
            case "shards" -> balances.getOrDefault((UUID)a[0],0L); case "setShards" -> {balances.put((UUID)a[0],(Long)a[1]);yield null;}
            case "allBuyOrders" -> orders;
            default -> defaults(m.getReturnType());
        };}
    }
    private static final class TestWorld implements InvocationHandler {
        final UUID id=UUID.randomUUID();
        final World proxy=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},this);
        DropState lastDrop;
        public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()) {
            case "getUID" -> id;
            case "dropItemNaturally" -> {lastDrop=new DropState((ItemStack)a[1]);yield lastDrop.proxy;}
            default -> defaults(m.getReturnType());
        };}
    }
    private static final class DropState implements InvocationHandler {
        final ItemStack stack; UUID owner; boolean unlimited, invulnerable;
        final org.bukkit.entity.Item proxy=(org.bukkit.entity.Item)Proxy.newProxyInstance(
                org.bukkit.entity.Item.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Item.class},this);
        DropState(ItemStack stack){this.stack=stack;}
        public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()) {
            case "getItemStack" -> stack; case "setOwner" -> {owner=(UUID)a[0];yield null;}
            case "setUnlimitedLifetime" -> {unlimited=(boolean)a[0];yield null;}
            case "setInvulnerable" -> {invulnerable=(boolean)a[0];yield null;}
            default -> defaults(m.getReturnType());
        };}
    }
    private static final class TestBlock implements Block {
        final World world; final int x,y,z; Material type=Material.SPAWNER; TestSpawner spawner;
        TestBlock(World world,int x,int y,int z){this.world=world;this.x=x;this.y=y;this.z=z;spawner=new TestSpawner(this);}
        public Material getType(){return type;} public Block getRelative(BlockFace face){return null;} public Location getLocation(){return new Location(world,x,y,z);}
        public BlockData getBlockData(){return null;} public BlockState getState(){return spawner;} public void setType(Material t){type=t;}
        public boolean breakNaturally(){return false;} public boolean breakNaturally(ItemStack tool,boolean effect){return false;}
        public World getWorld(){return world;} public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
    }
    private static final class TestSpawner implements CreatureSpawner {
        final TestBlock block; final StubPersistentDataContainer pdc=new StubPersistentDataContainer();
        EntityType type; SpawnerEntry entry; List<SpawnerEntry> potentials=List.of();
        TestSpawner(TestBlock block){this.block=block;} public Block getBlock(){return block;} public boolean update(boolean force,boolean physics){return true;}
        public void setSpawnedType(EntityType type){this.type=type;} public EntityType getSpawnedType(){return type;}
        public EntitySnapshot getSpawnedEntity(){return () -> type;} public void setSpawnedEntity(SpawnerEntry entry){this.entry=entry;}
        public void setPotentialSpawns(Collection<SpawnerEntry> entries){potentials=new ArrayList<>(entries);}
        public PersistentDataContainer getPersistentDataContainer(){return pdc;}
    }
    private static final class Click extends InventoryClickEvent {
        final Inventory top,clicked;final Player player;final int slot;final boolean right,shift;boolean cancelled;
        Click(Inventory top,Inventory clicked,Player player,int slot,boolean right,boolean shift){this.top=top;this.clicked=clicked;this.player=player;this.slot=slot;this.right=right;this.shift=shift;}
        public Inventory getInventory(){return top;}public Inventory getClickedInventory(){return clicked;}public HumanEntity getWhoClicked(){return player;}
        public int getSlot(){return slot;}public boolean isRightClick(){return right;}public boolean isShiftClick(){return shift;}
        public void setCancelled(boolean value){cancelled=value;}public boolean isCancelled(){return cancelled;}
    }
    private static final class Drag extends InventoryDragEvent {
        final Inventory top,bottom;boolean cancelled;Drag(Inventory top,Inventory bottom){this.top=top;this.bottom=bottom;}
        public InventoryView getView(){return new TestView(top,bottom);}public void setCancelled(boolean value){cancelled=value;}
    }
    private static final class Place extends BlockPlaceEvent {
        final Player player;final ItemStack item;final Block block;boolean cancelled,canBuild=true;
        Place(Player player,ItemStack item,Block block){this.player=player;this.item=item;this.block=block;}
        public Player getPlayer(){return player;}public ItemStack getItemInHand(){return item;}public Block getBlockPlaced(){return block;}
        public boolean canBuild(){return canBuild;}public void setCancelled(boolean value){cancelled=value;}public boolean isCancelled(){return cancelled;}
    }
    private static final class Break extends BlockBreakEvent {
        final Player player;final Block block;boolean cancelled,dropItems=true;int xp=10;
        Break(Player player,Block block){this.player=player;this.block=block;}public Player getPlayer(){return player;}public Block getBlock(){return block;}
        public void setDropItems(boolean b){dropItems=b;}public void setExpToDrop(int n){xp=n;}
        public void setCancelled(boolean b){cancelled=b;}public boolean isCancelled(){return cancelled;}
    }
    private static final class Interact extends PlayerInteractEvent {
        final Player player;final Block block;final EquipmentSlot hand;boolean cancelled;
        Interact(Player player,Block block,EquipmentSlot hand){this.player=player;this.block=block;this.hand=hand;}
        public Player getPlayer(){return player;}public Block getClickedBlock(){return block;}public Action getAction(){return Action.RIGHT_CLICK_BLOCK;}
        public EquipmentSlot getHand(){return hand;}public void setCancelled(boolean b){cancelled=b;}public boolean isCancelled(){return cancelled;}
    }
    private static final class Spawn extends SpawnerSpawnEvent {
        final CreatureSpawner spawner;final Entity entity;boolean cancelled;
        Spawn(CreatureSpawner spawner,Entity entity){this.spawner=spawner;this.entity=entity;}
        public CreatureSpawner getSpawner(){return spawner;}public Entity getEntity(){return entity;}
        public void setCancelled(boolean b){cancelled=b;}public boolean isCancelled(){return cancelled;}
    }
    private static final class Explode extends EntityExplodeEvent {
        final List<Block> blocks; Explode(List<Block> blocks){this.blocks=blocks;} public List<Block> blockList(){return blocks;}
    }
}
