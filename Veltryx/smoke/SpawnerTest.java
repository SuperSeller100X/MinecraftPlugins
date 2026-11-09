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
        state.inventory.setItem(0, null); // vanilla consumed the placed item

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

        testMarketGuards(data, player, messages, items);
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
        testStacks(dir, plugin, scheduler, items, messages);
    }

    private static void testStacks(Path dir, FakePlugin plugin, FakeScheduler scheduler,
                                   SpawnerItems items, Messages messages) throws Exception {
        Path path=dir.resolve("stack-tests");
        SpawnerStore store=new SpawnerStore(path,LOG);
        SpawnerLootGui gui=new SpawnerLootGui(store,messages);
        SpawnerListener listener=new SpawnerListener(items,store,gui,messages,LOG);
        TestWorld world=new TestWorld(); PlayerState player=new PlayerState(world.proxy);
        TestBlock target=new TestBlock(world.proxy,3,70,3);
        SpawnerStore.Position pos=SpawnerStore.Position.of(target);
        SpawnerItems.Identity root=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity child=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity wrong=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.COW);
        check("stack identities created",store.create(root)&&store.create(child)&&store.create(wrong));
        check("stack root placed",store.bind(root,pos)&&items.configure(target,root));
        SpawnerStore.Position childPos=new SpawnerStore.Position(world.id,4,70,3);
        check("child bound before acquiring loot",store.bind(child,childPos));
        eq("child inventory mode",store.toggle(child,childPos),SpawnerStore.Mode.INVENTORY);
        eq("child saves pre-existing loot",store.deposit(child,childPos,List.of(new ItemStack(Material.PORKCHOP,7))),SpawnerStore.Deposit.STORED);
        check("child picked up before stacking",store.unbind(child,childPos));
        ItemStack source=items.create(child);
        player.inventory.setItemInMainHand(source);
        Interact protectedUse=new Interact(player.proxy,target,EquipmentSlot.HAND); protectedUse.cancelled=true;
        listener.onInteract(protectedUse);
        eq("cancelled use cannot stack",store.stackSizeAt(root,pos),1);
        check("cancelled use doesn't consume item",player.inventory.getItemInMainHand()!=null);
        player.inventory.setItemInMainHand(items.create(wrong));
        Interact wrongUse=new Interact(player.proxy,target,EquipmentSlot.HAND); listener.onInteract(wrongUse);
        check("different types are not consumed",wrongUse.cancelled && items.identity(player.inventory.getItemInMainHand()).equals(wrong));
        eq("different types never merge",store.stackSizeAt(root,pos),1);
        player.inventory.setItemInMainHand(source);
        Interact stack=new Interact(player.proxy,target,EquipmentSlot.HAND); listener.onInteract(stack); scheduler.drain();
        check("right clicking with same type consumes source",stack.cancelled && player.inventory.getItemInMainHand()==null);
        eq("two spawners now stacked",store.stackSizeAt(root,pos),2);
        eq("native spawn attempts scale",target.spawner.spawnCount,8);
        eq("nearby limit scales",target.spawner.nearby,12);
        check("consumed identity cannot be placed twice",!store.bind(child,childPos));
        eq("each component contributes 20 pages",store.page(root,pos,0).totalPages(),40);
        eq("child's stored loot is preserved",store.page(root,pos,20).items()[0].getAmount(),7);

        Interact open=new Interact(player.proxy,target,EquipmentSlot.HAND); listener.onInteract(open); scheduler.drain();
        check("empty hand opens stacked storage",player.open!=null && player.open.getHolder() instanceof SpawnerLootGui.Holder);
        Click toggle=new Click(player.open,player.open,player.proxy,49,false,false); gui.onClick(toggle);
        eq("mode change waits for block region",store.modeAt(root,pos),SpawnerStore.Mode.SPAWNER);
        scheduler.drain();
        eq("mode changed on region",store.modeAt(root,pos),SpawnerStore.Mode.INVENTORY);
        eq("inventory mode restores baseline spawn attempts",target.spawner.spawnCount,4);
        eq("inventory mode restores baseline nearby limit",target.spawner.nearby,6);
        int[] rolls={0};
        Bukkit.testLootFactory=key -> (random,context) -> {rolls[0]++;return List.of(new ItemStack(Material.PORKCHOP,3),new ItemStack(Material.BONE,2));};
        Spawn generated=new Spawn(target.spawner,entity(world.proxy,EntityType.PIG)); listener.onSpawn(generated); scheduler.drain();
        check("stacked virtual spawn cancels mob",generated.cancelled);
        eq("inventory generates one independent roll per spawner",rolls[0],2);
        eq("root stores proportional pork",store.page(root,pos,0).items()[0].getAmount(),6);
        eq("root stores proportional bone",store.page(root,pos,0).items()[1].getAmount(),4);
        eq("previous child loot remains separate",store.page(root,pos,20).items()[0].getAmount(),7);
        target.spawner.spawnCount=8; // simulate stale parameters after interrupted configuration
        Spawn stale=new Spawn(target.spawner,entity(world.proxy,EntityType.PIG)); listener.onSpawn(stale);
        check("stale counts fail closed",stale.cancelled && rolls[0]==2);
        scheduler.drain(); eq("stale counts repaired on region",target.spawner.spawnCount,4);
        toggle=new Click(player.open,player.open,player.proxy,49,false,false); gui.onClick(toggle); scheduler.drain();
        eq("normal mode scales attempts again",target.spawner.spawnCount,8);
        Spawn normal=new Spawn(target.spawner,entity(world.proxy,EntityType.PIG));listener.onSpawn(normal);
        check("normal mode permits vanilla spawn",!normal.cancelled);
        eq("switch to inventory for joined storage test",store.toggle(root,pos),SpawnerStore.Mode.INVENTORY);
        List<ItemStack> fill=new ArrayList<>();
        for(int i=0;i<SpawnerStore.CAPACITY-2;i++) fill.add(new ItemStack(Material.STONE,64));
        eq("root component can fill completely",store.deposit(root,pos,fill),SpawnerStore.Deposit.STORED);
        eq("overflow proceeds to next component",store.deposit(root,pos,List.of(new ItemStack(Material.DIAMOND,2))),SpawnerStore.Deposit.STORED);
        eq("child existing loot preserved beside new loot",store.page(root,pos,20).items()[0].getAmount(),7);
        eq("child accepts loot when root full",store.page(root,pos,20).items()[1].getType(),Material.DIAMOND);
        TestInventory receiver=new TestInventory(null,36);
        ItemStack displayed=store.page(root,pos,20).items()[0];
        eq("can withdraw from attached storage",store.withdraw(root,pos,SpawnerStore.CAPACITY,displayed,true,receiver).amount(),1);
        eq("attached withdrawal changes correct record",store.page(root,pos,20).items()[0].getAmount(),6);
        eq("can store the withdrawn loot again",store.deposit(root,pos,List.of(new ItemStack(Material.PORKCHOP,1))),SpawnerStore.Deposit.STORED);
        eq("back to normal mode",store.toggle(root,pos),SpawnerStore.Mode.SPAWNER);

        SpawnerStore restarted=new SpawnerStore(path,LOG);
        eq("stack count survives restart",restarted.stackSizeAt(root,pos),2);
        eq("stacked child loot survives restart",restarted.page(root,pos,20).items()[0].getAmount(),6);
        check("child copy remains unusable after restart",!restarted.bind(child,childPos));
        ItemStack silk=new ItemStack(Material.DIAMOND_PICKAXE); ItemMeta meta=silk.getItemMeta();
        meta.addEnchant(Enchantment.SILK_TOUCH,1,true);silk.setItemMeta(meta);player.inventory.setItemInMainHand(silk);
        Break pickup=new Break(player.proxy,target);listener.onBreak(pickup);listener.onBreakFinished(pickup);scheduler.drain();
        ItemStack returned=player.inventory.findSpawner(root,items);
        check("whole stack can be silk picked up",!pickup.cancelled && returned!=null && store.stackSize(root)==2);
        TestBlock moved=new TestBlock(world.proxy,15,72,15); Place placed=new Place(player.proxy,returned,moved);
        listener.onPlace(placed);listener.onPlaceFinished(placed);scheduler.drain();
        SpawnerStore.Position movedPos=SpawnerStore.Position.of(moved);
        check("whole stack replaces at a new location",!placed.cancelled && store.stackSizeAt(root,movedPos)==2);
        eq("replaced stack has scaled native count",moved.spawner.spawnCount,8);
        eq("replaced stack keeps child storage",store.page(root,movedPos,20).items()[0].getAmount(),6);

        // Competing interactions on the same source may attach it only once.
        SpawnerItems.Identity race=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        check("race source created",store.create(race));
        ExecutorService threads=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go=new CountDownLatch(1);
            Future<SpawnerStore.Merge> a=threads.submit(() -> {go.await();return store.merge(root,movedPos,race);});
            Future<SpawnerStore.Merge> b=threads.submit(() -> {go.await();return store.merge(root,movedPos,race);});
            go.countDown(); SpawnerStore.Merge x=a.get(), y=b.get();
            check("concurrent source cannot be attached twice",(x==SpawnerStore.Merge.OK && y==SpawnerStore.Merge.INVALID)||(y==SpawnerStore.Merge.OK && x==SpawnerStore.Merge.INVALID));
            eq("race adds exactly one",store.stackSizeAt(root,movedPos),3);
        } finally {threads.shutdownNow();}
        testStackCap(dir,plugin,world.proxy,items,messages,scheduler);
        testNestedStacks(dir,world.proxy);
        testPartialDepositFailure(dir,world.proxy);
        testFailedAttach(dir,world.proxy);
    }

    private static void testNestedStacks(Path dir, World world) {
        Path path=dir.resolve("nested-stacks");
        SpawnerStore store=new SpawnerStore(path,LOG);
        SpawnerStore.Position aPos=new SpawnerStore.Position(world.getUID(),40,70,40);
        SpawnerStore.Position bPos=new SpawnerStore.Position(world.getUID(),41,70,40);
        SpawnerItems.Identity a=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity ac=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity b=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity bc=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        check("nested test records created",store.create(a)&&store.create(ac)&&store.create(b)&&store.create(bc));
        check("nested roots bound",store.bind(a,aPos)&&store.bind(b,bPos));
        eq("nested first pair merges",store.merge(a,aPos,ac),SpawnerStore.Merge.OK);
        eq("nested second pair merges",store.merge(b,bPos,bc),SpawnerStore.Merge.OK);
        eq("nested source in inventory mode",store.toggle(b,bPos),SpawnerStore.Mode.INVENTORY);
        eq("nested source stores items",store.deposit(b,bPos,List.of(new ItemStack(Material.DIAMOND,4))),SpawnerStore.Deposit.STORED);
        check("nested source can be picked up",store.unbind(b,bPos));
        eq("whole substack merges into root",store.merge(a,aPos,b),SpawnerStore.Merge.OK);
        eq("nested merge preserves all members",store.stackSizeAt(a,aPos),4);
        eq("nested stack has 80 pages",store.page(a,aPos,0).totalPages(),80);
        eq("target mode wins",store.modeAt(a,aPos),SpawnerStore.Mode.SPAWNER);
        int diamonds=0;for(int page=0;page<80;page++) for(ItemStack item:store.page(a,aPos,page).items())
            if(item!=null && item.getType()==Material.DIAMOND) diamonds+=item.getAmount();
        eq("nested source loot survives merge",diamonds,4);
        SpawnerStore restarted=new SpawnerStore(path,LOG);
        eq("nested parent chains survive restart",restarted.stackSizeAt(a,aPos),4);
        check("nested source copy cannot bind after restart",!restarted.bind(b,bPos));
    }

    private static void testPartialDepositFailure(Path dir, World world) throws Exception {
        Path path=dir.resolve("partial-deposit");
        Logger quiet=Logger.getLogger("expected-partial-deposit");quiet.setLevel(java.util.logging.Level.OFF);
        SpawnerStore store=new SpawnerStore(path,quiet);
        SpawnerItems.Identity root=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity child=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerStore.Position a=new SpawnerStore.Position(world.getUID(),50,70,50);
        SpawnerStore.Position b=new SpawnerStore.Position(world.getUID(),51,70,50);
        check("partial deposit records created",store.create(root)&&store.create(child)&&store.bind(root,a)&&store.bind(child,b));
        eq("partial deposit child inventory mode",store.toggle(child,b),SpawnerStore.Mode.INVENTORY);
        eq("pre-existing child contents stored",store.deposit(child,b,List.of(new ItemStack(Material.PORKCHOP,7))),SpawnerStore.Deposit.STORED);
        check("partial deposit child unbound",store.unbind(child,b));
        eq("partial deposit pair merged",store.merge(root,a,child),SpawnerStore.Merge.OK);
        eq("partial deposit root inventory mode",store.toggle(root,a),SpawnerStore.Mode.INVENTORY);
        List<ItemStack> almostFull=new ArrayList<>();
        for(int i=0;i<SpawnerStore.CAPACITY-1;i++)almostFull.add(new ItemStack(Material.STONE,64));
        almostFull.add(new ItemStack(Material.STONE,63));
        eq("root filled except one item",store.deposit(root,a,almostFull),SpawnerStore.Deposit.STORED);
        Files.createDirectory(path.resolve(child.id()+".tmp"));
        eq("multi-file batch reports failure",store.deposit(root,a,List.of(new ItemStack(Material.STONE,2))),SpawnerStore.Deposit.ERROR);
        Files.delete(path.resolve(child.id()+".tmp"));
        SpawnerStore restarted=new SpawnerStore(path,quiet);
        eq("first component may retain partial new loot",restarted.page(root,a,19).items()[44].getAmount(),64);
        eq("later component retains pre-existing loot",restarted.page(root,a,20).items()[0].getAmount(),7);
        check("failed later write did not invent loot",restarted.page(root,a,20).items()[1]==null);
    }

    private static void testFailedAttach(Path dir, World world) throws Exception {
        Path path=dir.resolve("stack-fail");
        Logger quiet=Logger.getLogger("expected-stack-io-failure");quiet.setLevel(java.util.logging.Level.OFF);
        SpawnerStore store=new SpawnerStore(path,quiet);
        SpawnerItems.Identity root=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerItems.Identity child=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.PIG);
        SpawnerStore.Position pos=new SpawnerStore.Position(world.getUID(),1,65,1);
        SpawnerStore.Position other=new SpawnerStore.Position(world.getUID(),2,65,1);
        check("failure test stack created",store.create(root)&&store.bind(root,pos)&&store.create(child));
        Files.createDirectory(path.resolve(child.id()+".tmp"));
        eq("failed parent write refuses merge",store.merge(root,pos,child),SpawnerStore.Merge.ERROR);
        eq("failed merge leaves count untouched",store.stackSizeAt(root,pos),1);
        Files.delete(path.resolve(child.id()+".tmp"));
        check("failed merge leaves source usable",store.bind(child,other));
        check("source can be unbound",store.unbind(child,other));
        check("merge succeeds once disk recovers",store.merge(root,pos,child)==SpawnerStore.Merge.OK);
        java.util.Properties corrupt=new java.util.Properties();
        try(var in=Files.newInputStream(path.resolve(child.id()+".properties"))){corrupt.load(in);}
        corrupt.setProperty("parent",UUID.randomUUID().toString());
        try(var out=Files.newOutputStream(path.resolve(child.id()+".properties"))){corrupt.store(out,"bad parent");}
        SpawnerStore broken=new SpawnerStore(path,quiet);
        eq("corrupt parent graph fails closed",broken.stackSizeAt(root,pos),0);
        check("corrupt graph cannot permit duplicate placement",!broken.bind(child,other));
    }

    private static void testStackCap(Path dir, FakePlugin plugin, World world, SpawnerItems items,
                                     Messages messages, FakeScheduler scheduler) throws Exception {
        Path path=dir.resolve("stack-cap");
        SpawnerStore store=new SpawnerStore(path,LOG);
        SpawnerLootGui gui=new SpawnerLootGui(store,messages);
        SpawnerListener listener=new SpawnerListener(items,store,gui,messages,LOG);
        TestBlock block=new TestBlock(world,200,80,200); SpawnerStore.Position pos=SpawnerStore.Position.of(block);
        SpawnerItems.Identity root=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
        check("cap root created",store.create(root)&&store.bind(root,pos));
        boolean all=true;
        for(int n=1;n<SpawnerStore.MAX_STACK-1;n++) {
            SpawnerItems.Identity id=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
            all &= store.create(id) && store.merge(root,pos,id)==SpawnerStore.Merge.OK;
        }
        check("can stack through 1023",all);
        eq("one slot remains",store.stackSizeAt(root,pos),1023);
        SpawnerItems.Identity pair=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
        SpawnerItems.Identity pairChild=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
        SpawnerStore.Position pairPos=new SpawnerStore.Position(world.getUID(),202,80,200);
        check("two-stack source created",store.create(pair)&&store.create(pairChild)&&store.bind(pair,pairPos));
        eq("two-stack source assembled",store.merge(pair,pairPos,pairChild),SpawnerStore.Merge.OK);
        check("two-stack source picked up",store.unbind(pair,pairPos));
        eq("partial source group rejected at cap",store.merge(root,pos,pair),SpawnerStore.Merge.FULL);
        eq("rejected source group intact",store.stackSize(pair),2);
        eq("target untouched by rejection",store.stackSizeAt(root,pos),1023);
        SpawnerItems.Identity last=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
        check("last source created",store.create(last));
        eq("single source reaches exact cap",store.merge(root,pos,last),SpawnerStore.Merge.OK);
        eq("exact stack cap",store.stackSizeAt(root,pos),1024);
        SpawnerItems.Identity extra=new SpawnerItems.Identity(UUID.randomUUID(),SpawnerType.SHEEP);
        check("extra item created",store.create(extra));
        eq("1025th is refused",store.merge(root,pos,extra),SpawnerStore.Merge.FULL);
        SpawnerStore.Position other=new SpawnerStore.Position(world.getUID(),201,80,200);
        check("rejected item remains usable",store.bind(extra,other));
        check("max stack parameters configured",items.configure(block,root,1024,SpawnerStore.Mode.SPAWNER));
        eq("1024 stack attempts are proportional",block.spawner.spawnCount,4096);
        eq("1024 stack nearby limit is proportional",block.spawner.nearby,6144);
        eq("1024 stack inventory mode",store.toggle(root,pos),SpawnerStore.Mode.INVENTORY);
        check("inventory counts configured",items.updateCounts(block,root,1024,SpawnerStore.Mode.INVENTORY));
        int[] rolls={0}; Bukkit.testLootFactory=key -> (random,context) -> {rolls[0]++;return List.of(new ItemStack(Material.DIAMOND,1));};
        Spawn event=new Spawn(block.spawner,entity(world,EntityType.SHEEP));listener.onSpawn(event);scheduler.drain();
        int diamonds=0;for(ItemStack stack:store.page(root,pos,0).items())if(stack!=null && stack.getType()==Material.DIAMOND)diamonds+=stack.getAmount();
        eq("1024 independent virtual rolls",rolls[0],1024);
        eq("1024 generated items stored without overflow",diamonds,1024);
        SpawnerStore restarted=new SpawnerStore(path,LOG);
        eq("1024 stack survives restart",restarted.stackSizeAt(root,pos),1024);
        eq("1024 stack storage pages survive",restarted.page(root,pos,0).totalPages(),20480);
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

    private static void testMarketGuards(BalanceData data, Player player, Messages messages, SpawnerItems items) {
        VeltryxConfig config = new VeltryxConfig(new org.bukkit.configuration.file.YamlConfiguration());
        EconomyService economy = new EconomyService(data.proxy, config);
        AhService ah = new AhService(data.proxy, economy, messages, config);
        OrderService orders = new OrderService(data.proxy, economy, messages, config);
        eq("legacy material-only auction cannot strip spawner identity", ah.list(player, "SPAWNER", 1, 100).templateKey(), "spawner.market-disabled");
        ItemStack previous = player.getInventory().getItem(35);
        ItemStack custom = items.create(new SpawnerItems.Identity(UUID.randomUUID(), SpawnerType.COW));
        player.getInventory().setItem(35, custom);
        long now = System.currentTimeMillis();
        data.orders.add(new BuyOrder("old-spawner-order", UUID.randomUUID(), "OldBuyer", "SPAWNER",
                1, 0, 100, now, now + 60_000, false));
        eq("material-only orders cannot strip custom spawner identity", orders.fill(player, "old-spawner-order", 1).templateKey(), "order.no-metadata");
        check("rejected spawner retains its identity", items.identity(player.getInventory().getItem(35)) != null);
        player.getInventory().setItem(35, previous);
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
        DropState lastDrop; final Map<String, Block> blocks = new HashMap<>();
        public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()) {
            case "getUID" -> id;
            case "getBlockAt" -> blocks.get(a[0] + ":" + a[1] + ":" + a[2]);
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
        TestBlock(World world,int x,int y,int z){this.world=world;this.x=x;this.y=y;this.z=z;spawner=new TestSpawner(this);
            if (Proxy.isProxyClass(world.getClass()) && Proxy.getInvocationHandler(world) instanceof TestWorld test) test.blocks.put(x+":"+y+":"+z,this); }
        public Material getType(){return type;} public Block getRelative(BlockFace face){return null;} public Location getLocation(){return new Location(world,x,y,z);}
        public BlockData getBlockData(){return null;} public BlockState getState(){return spawner;} public void setType(Material t){type=t;}
        public boolean breakNaturally(){return false;} public boolean breakNaturally(ItemStack tool,boolean effect){return false;}
        public World getWorld(){return world;} public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
    }
    private static final class TestSpawner implements CreatureSpawner {
        final TestBlock block; final StubPersistentDataContainer pdc=new StubPersistentDataContainer();
        EntityType type; int spawnCount=4, nearby=6; SpawnerEntry entry; List<SpawnerEntry> potentials=List.of();
        TestSpawner(TestBlock block){this.block=block;} public Block getBlock(){return block;} public boolean update(boolean force,boolean physics){return true;}
        public int getSpawnCount(){return spawnCount;} public void setSpawnCount(int n){spawnCount=n;}
        public int getMaxNearbyEntities(){return nearby;} public void setMaxNearbyEntities(int n){nearby=n;}
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
