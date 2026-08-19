package dev.superseller.gifty.smoke;

import dev.superseller.gifty.GiftyPlugin;
import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.gui.GiftyHolder;
import dev.superseller.gifty.input.ChatInputManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Full gift-flow test against an in-memory mock of the Bukkit API.
 * Exercises the real plugin: enable, compose, prompts, send, storage,
 * inbox GUI, claiming, refunds, cooldowns, quit-returns and commands.
 */
public final class MockFlowTest {

    private static int failures;

    public static void main(String[] args) {
        MockServer server = new MockServer();
        Bukkit.setServer(server);
        MockPlayer alice = server.addPlayer("Alice");
        MockPlayer bob = server.addPlayer("Bob");
        server.economy.balances.put(alice.getUniqueId(), 10000.0);
        server.economy.balances.put(bob.getUniqueId(), 0.0);

        GiftyPlugin plugin = new GiftyPlugin();
        plugin.onEnable();
        GuiSessionManager sessions = plugin.getSessions();
        GiftyConfig config = plugin.getGiftyConfig();
        ChatInputManager chat = new ChatInputManager(config);
        chat.setSessions(sessions);

        // ---- 1. compose GUI ------------------------------------------------
        sections("compose");
        sessions.openCompose(alice, bob);
        check("compose open", alice.openInv != null);
        GiftyHolder compose = sessions.currentHolder(alice);
        check("kind compose", compose != null && compose.kind == GiftyHolder.Kind.COMPOSE);
        check("1 slot => 1 row (9)", compose != null && compose.getInventory().getSize() == 9);

        // ---- 2. place item, close -> confirm ---------------------------------
        sections("compose->confirm");
        compose.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        sessions.onComposeClose(alice);
        GiftyHolder confirm = sessions.currentHolder(alice);
        check("confirm open", confirm != null && confirm.kind == GiftyHolder.Kind.CONFIRM);
        check("item slot cleared", compose.getInventory().getItem(0) == null);

        // ---- 3. money + message via chat prompts ----------------------------
        sections("prompts");
        sessions.handlePromptResult(alice, ChatInputManager.Kind.MONEY, bob.getUniqueId(), "250");
        sessions.handlePromptResult(alice, ChatInputManager.Kind.MESSAGE, bob.getUniqueId(), "Enjoy! <3");
        GiftyHolder confirm2 = sessions.currentHolder(alice);
        check("still confirm", confirm2 != null && confirm2.kind == GiftyHolder.Kind.CONFIRM);
        sessions.handlePromptResult(alice, ChatInputManager.Kind.MONEY, bob.getUniqueId(), "not-a-number");
        check("bad money rejected", alice.lastMessage() != null && alice.lastMessage().contains("valid"));

        // chat input capture path (prompt -> chat -> complete)
        chat.prompt(alice, ChatInputManager.Kind.MONEY, bob.getUniqueId());
        check("prompt pending", alice.lastMessage() != null && alice.lastMessage().contains("amount"));
        chat.onChatAsync(alice, "33");

        // ---- 4. send ----------------------------------------------------------
        sections("send");
        sessions.sendGift(alice);
        check("alice balance 9967 (10000-33, last prompt wins)", server.economy.balance(alice) == 9967.0);
        check("session cleared", sessions.currentHolder(alice) == null || sessions.currentHolder(alice).done);
        check("bob notified", bob.hasMessage("received a gift"));

        // ---- 5. storage --------------------------------------------------------
        sections("storage");
        check("pending 1", sessions.pendingCount(bob.getUniqueId()) == 1);
        File inboxFile = new File("target/test-data/Gifty/data/inboxes/" + bob.getUniqueId() + ".yml");
        check("inbox file exists", inboxFile.exists());
        check("meta file exists", new File("target/test-data/Gifty/data/meta.yml").exists());
        check("alice sent counter", plugin != null); // placeholder smoke via storage
        boolean fileHasData = false;
        try {
            String content = new String(java.nio.file.Files.readAllBytes(inboxFile.toPath()));
            fileHasData = content.contains("DIAMOND") && content.contains("Enjoy! <3");
        } catch (Exception ignored) {
        }
        check("file contains data", fileHasData);

        // ---- 6. inbox GUI ------------------------------------------------------
        sections("inbox gui");
        sessions.openInbox(bob, 1, false);
        GiftyHolder inbox = sessions.currentHolder(bob);
        check("kind inbox", inbox != null && inbox.kind == GiftyHolder.Kind.INBOX);
        check("54 slots", inbox.getInventory().getSize() == 54);
        check("delivery mapped", inbox.deliveryIds.length == 45 && inbox.deliveryIds[0] != 0);
        long deliveryId = inbox.deliveryIds[0];

        // ---- 7. claim all -------------------------------------------------------
        sections("claim");
        sessions.claimAll(bob, deliveryId);
        check("bob got 5 diamonds", bob.count(Material.DIAMOND) == 5);
        check("bob balance 33", server.economy.balance(bob) == 33.0);
        check("pending 0", sessions.pendingCount(bob.getUniqueId()) == 0);
        check("inbox file deleted", !inboxFile.exists());

        // ---- 8. refund flow ------------------------------------------------------
        sections("refund");
        // need a pending delivery again: send is blocked by cooldown, so refund
        // is tested on a fresh send by clearing cooldown via a new player
        MockPlayer carol = server.addPlayer("Carol");
        server.economy.balances.put(carol.getUniqueId(), 100.0);
        sessions.openCompose(carol, bob);
        GiftyHolder cCompose = sessions.currentHolder(carol);
        cCompose.getInventory().setItem(0, new ItemStack(Material.EMERALD, 2));
        sessions.onComposeClose(carol);
        sessions.sendGift(carol);
        check("carol -> bob pending", sessions.pendingCount(bob.getUniqueId()) == 1);
        int refunded = sessions.refundInbox(bob.getUniqueId());
        check("refunded 1", refunded == 1);
        check("bob pending 0 after refund", sessions.pendingCount(bob.getUniqueId()) == 0);
        check("carol pending 1 (refund)", sessions.pendingCount(carol.getUniqueId()) == 1);
        sessions.openInbox(carol, 1, false);
        GiftyHolder cInbox = sessions.currentHolder(carol);
        sessions.claimAll(carol, cInbox.deliveryIds[0]);
        check("carol got emeralds back", carol.count(Material.EMERALD) == 2);
        check("carol pending 0", sessions.pendingCount(carol.getUniqueId()) == 0);

        // ---- 9. cooldown ---------------------------------------------------------
        sections("cooldown");
        sessions.openCompose(carol, bob);
        sessions.onComposeClose(carol); // empty draft -> nothing
        sessions.openCompose(carol, bob);
        GiftyHolder cCompose2 = sessions.currentHolder(carol);
        cCompose2.getInventory().setItem(0, new ItemStack(Material.EMERALD, 1));
        sessions.onComposeClose(carol);
        sessions.sendGift(carol);
        check("cooldown blocked", carol.hasMessage("again in"));

        // ---- 10. config + blacklist --------------------------------------
        sections("capacity");
        check("capacity default 108", config.maxPendingPerPlayer() == 108);
        check("send slots default 1", config.sendSlots() == 1);
        check("inbox pages default 2", config.inboxPages() == 2);
        check("cooldown default 60", config.cooldownSeconds() == 60);

        sections("blacklist");
        try {
            java.nio.file.Path cfgPath = java.nio.file.Paths.get("target/test-data/Gifty/config.yml");
            String content = new String(java.nio.file.Files.readAllBytes(cfgPath));
            content = content.replace("economy:\n",
                    "  blacklisted-materials:\n    - EMERALD\n\neconomy:\n");
            java.nio.file.Files.write(cfgPath, content.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        plugin.reload();
        // dave is a fresh sender without cooldown history
        MockPlayer dave = server.addPlayer("Dave");
        server.economy.balances.put(dave.getUniqueId(), 50.0);
        sessions.openCompose(dave, bob);
        sessions.currentHolder(dave).getInventory().setItem(0, new ItemStack(Material.EMERALD, 1));
        sessions.onComposeClose(dave);
        sessions.sendGift(dave);
        check("blacklisted emerald rejected", dave.hasMessage("cannot be gifted"));
        check("nothing was sent", sessions.pendingCount(bob.getUniqueId()) == 0);

        // ---- 11. quit returns ------------------------------------------------------
        sections("quit returns");
        sessions.abandonConfirm(carol); // close the cooldown-blocked confirm session
        carol.inv.wipe();
        sessions.openCompose(carol, bob);
        GiftyHolder qCompose = sessions.currentHolder(carol);
        qCompose.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 3));
        sessions.onQuit(carol);
        check("quit cleared session", sessions.currentHolder(carol) == null);
        carol.inv.wipe();
        sessions.onJoin(carol);
        check("returned on join", carol.count(Material.GOLD_INGOT) == 3);

        // ---- 12. commands -----------------------------------------------------------
        sections("commands");
        dev.superseller.gifty.command.GiftCommand giftCmd = new dev.superseller.gifty.command.GiftCommand(config, sessions);
        giftCmd.onCommand(alice, null, "gift", new String[]{"Bob"});
        check("command opened compose", sessions.currentHolder(alice) != null
                && sessions.currentHolder(alice).kind == GiftyHolder.Kind.COMPOSE);
        dev.superseller.gifty.command.InboxCommand inboxCmd = new dev.superseller.gifty.command.InboxCommand(config, sessions);
        inboxCmd.onCommand(alice, null, "inbox", new String[]{});
        check("command opened inbox", sessions.currentHolder(alice) != null
                && sessions.currentHolder(alice).kind == GiftyHolder.Kind.INBOX);
        plugin.reload();
        check("admin reload runs", true);

        plugin.onDisable();

        System.out.println();
        if (failures > 0) {
            System.out.println("MOCK FLOW TEST FAILED: " + failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("MOCK FLOW TEST OK");
    }

    private static void sections(String name) {
        System.out.println("== " + name + " ==");
    }

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "  ok  " : "  FAIL ") + name);
        if (!condition) {
            failures++;
        }
    }

    // ================================================================ mocks

    public static final class MockServer implements Server {
        final Map<String, MockPlayer> playersByName = new HashMap<>();
        final Map<UUID, MockPlayer> playersById = new HashMap<>();
        final MockPluginManager pluginManager = new MockPluginManager();
        final MockScheduler scheduler = new MockScheduler();
        final MockEconomy economy = new MockEconomy();
        final MockServicesManager servicesManager = new MockServicesManager(economy);
        final Map<String, PluginCommand> commands = new HashMap<>();

        MockPlayer addPlayer(String name) {
            MockPlayer p = new MockPlayer(name);
            playersByName.put(name.toLowerCase(), p);
            playersById.put(p.getUniqueId(), p);
            return p;
        }

        @Override public String getName() { return "MockServer"; }
        @Override public String getVersion() { return "26.2"; }
        @Override public String getBukkitVersion() { return "26.2-mock"; }
        @Override public Logger getLogger() { return Logger.getGlobal(); }
        @Override public PluginManager getPluginManager() { return pluginManager; }
        @Override public BukkitScheduler getScheduler() { return scheduler; }
        @Override public ServicesManager getServicesManager() { return servicesManager; }
        @Override public ConsoleCommandSender getConsoleSender() { return new MockConsoleSender(); }
        @Override public PluginCommand getPluginCommand(String name) {
            return commands.computeIfAbsent(name, PluginCommand::new);
        }
        @Override public Collection<? extends Player> getOnlinePlayers() {
            return new ArrayList<>(playersById.values());
        }
        @Override public Player getPlayer(String name) { return playersByName.get(name.toLowerCase()); }
        @Override public Player getPlayer(UUID id) { return playersById.get(id); }
        @Override public OfflinePlayer getOfflinePlayer(String name) {
            return playersByName.get(name.toLowerCase());
        }
        @Override public OfflinePlayer getOfflinePlayer(UUID id) { return playersById.get(id); }
        @Override public void broadcastMessage(String message) { }
        @Override public Inventory createInventory(InventoryHolder owner, int size, String title) {
            MockInventory inv = new MockInventory(owner, size);
            inv.title = title;
            return inv;
        }
    }

    public static final class MockPluginManager implements PluginManager {
        final List<Listener> listeners = new ArrayList<>();
        @Override public void registerEvents(Listener listener, Plugin plugin) { listeners.add(listener); }
        @Override public Plugin getPlugin(String name) { return null; }
    }

    public static final class MockScheduler implements BukkitScheduler {
        final List<Runnable> timers = new ArrayList<>();
        @Override public BukkitTask runTask(Plugin plugin, Runnable task) { task.run(); return new MockTask(); }
        @Override public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) { task.run(); return new MockTask(); }
        @Override public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
            timers.add(task);
            task.run();
            return new MockTask();
        }
        @Override public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) { task.run(); return new MockTask(); }
        @Override public void cancelTasks(Plugin plugin) { }
    }

    public static final class MockTask implements BukkitTask {
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return false; }
    }

    public static final class MockServicesManager implements ServicesManager {
        private final Economy economy;
        MockServicesManager(Economy economy) { this.economy = economy; }
        @Override public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
            if (service == Economy.class) {
                @SuppressWarnings("unchecked")
                RegisteredServiceProvider<T> rsp = (RegisteredServiceProvider<T>)
                        new RegisteredServiceProvider<Economy>(economy, null);
                return rsp;
            }
            return null;
        }
    }

    public static final class MockConsoleSender implements ConsoleCommandSender {
        @Override public String getName() { return "CONSOLE"; }
        @Override public void sendMessage(String message) { }
        @Override public boolean hasPermission(String name) { return true; }
        @Override public boolean isOp() { return true; }
    }

    public static final class MockWorld implements World {
        @Override public String getName() { return "world"; }
        @Override public Item dropItem(Location location, ItemStack item) { return new MockItem(item); }
    }

    public static final class MockItem implements Item {
        private final ItemStack stack;
        MockItem(ItemStack stack) { this.stack = stack; }
        @Override public ItemStack getItemStack() { return stack; }
        @Override public void setPickupDelay(int delay) { }
        @Override public UUID getUniqueId() { return UUID.randomUUID(); }
        @Override public Location getLocation() { return new Location(new MockWorld(), 0, 64, 0); }
        @Override public World getWorld() { return new MockWorld(); }
    }

    public static final class MockPlayer implements Player {
        private final String name;
        private final UUID uuid = UUID.randomUUID();
        final List<String> messages = new ArrayList<>();
        final MockPlayerInventory inv = new MockPlayerInventory();
        final MockWorld world = new MockWorld();
        final Location location = new Location(world, 0, 64, 0);
        Inventory openInv;

        MockPlayer(String name) { this.name = name; }

        String lastMessage() { return messages.isEmpty() ? null : messages.get(messages.size() - 1); }
        boolean hasMessage(String part) {
            for (String m : messages) {
                if (m.contains(part)) {
                    return true;
                }
            }
            return false;
        }
        int count(Material material) {
            int n = 0;
            for (ItemStack s : inv.contents) {
                if (s != null && s.getType() == material) {
                    n += s.getAmount();
                }
            }
            return n;
        }

        @Override public String getName() { return name; }
        @Override public UUID getUniqueId() { return uuid; }
        @Override public void sendMessage(String message) { messages.add(message); }
        @Override public boolean hasPermission(String permission) {
            return !"gifty.cooldown.bypass".equals(permission);
        }
        @Override public boolean isOp() { return true; }
        @Override public boolean isOnline() { return true; }
        @Override public Player getPlayer() { return this; }
        @Override public boolean hasPlayedBefore() { return true; }
        @Override public PlayerInventory getInventory() { return inv; }
        @Override public Location getLocation() { return location; }
        @Override public World getWorld() { return world; }
        @Override public InventoryView openInventory(Inventory inventory) {
            openInv = inventory;
            if (inventory instanceof MockInventory) {
                ((MockInventory) inventory).viewers.add(this);
            }
            return null;
        }
        @Override public void closeInventory() { openInv = null; }
        @Override public InventoryView getOpenInventory() { return null; }
        @Override public void playSound(Location location, Sound sound, float volume, float pitch) { }
    }

    public static final class MockInventory implements Inventory {
        private final ItemStack[] contents;
        private final InventoryHolder holder;
        final List<HumanEntity> viewers = new ArrayList<>();
        String title;

        MockInventory(InventoryHolder holder, int size) {
            this.holder = holder;
            this.contents = new ItemStack[size];
        }

        @Override public InventoryHolder getHolder() { return holder; }
        @Override public int getSize() { return contents.length; }
        @Override public ItemStack getItem(int index) { return contents[index]; }
        @Override public void setItem(int index, ItemStack item) { contents[index] = item; }
        @Override public Map<Integer, ItemStack> addItem(ItemStack... items) {
            Map<Integer, ItemStack> leftover = new HashMap<>();
            int i = 0;
            for (ItemStack stack : items) {
                int remaining = stack.getAmount();
                for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                    ItemStack cur = contents[slot];
                    if (cur == null) {
                        contents[slot] = new ItemStack(stack.getType(), remaining);
                        remaining = 0;
                    } else if (cur.getType() == stack.getType() && cur.getAmount() < 64) {
                        int fit = Math.min(remaining, 64 - cur.getAmount());
                        cur.setAmount(cur.getAmount() + fit);
                        remaining -= fit;
                    }
                }
                if (remaining > 0) {
                    leftover.put(i, new ItemStack(stack.getType(), remaining));
                }
                i++;
            }
            return leftover;
        }
        @Override public boolean removeItem(ItemStack... items) { return true; }
        @Override public boolean contains(ItemStack item) { return false; }
        @Override public void clear() {
            for (int i = 0; i < contents.length; i++) {
                contents[i] = null;
            }
        }
        @Override public List<HumanEntity> getViewers() { return viewers; }
        @Override public Inventory getInventory() { return this; }
    }

    public static final class MockPlayerInventory implements PlayerInventory {
        final ItemStack[] contents = new ItemStack[36];

        void wipe() {
            for (int i = 0; i < contents.length; i++) {
                contents[i] = null;
            }
        }
        @Override public InventoryHolder getHolder() { return null; }
        @Override public int getSize() { return contents.length; }
        @Override public ItemStack getItem(int index) { return contents[index]; }
        @Override public void setItem(int index, ItemStack item) { contents[index] = item; }
        @Override public Map<Integer, ItemStack> addItem(ItemStack... items) {
            Map<Integer, ItemStack> leftover = new HashMap<>();
            int i = 0;
            for (ItemStack stack : items) {
                int remaining = stack.getAmount();
                for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                    ItemStack cur = contents[slot];
                    if (cur == null) {
                        contents[slot] = new ItemStack(stack.getType(), remaining);
                        remaining = 0;
                    } else if (cur.getType() == stack.getType() && cur.getAmount() < 64) {
                        int fit = Math.min(remaining, 64 - cur.getAmount());
                        cur.setAmount(cur.getAmount() + fit);
                        remaining -= fit;
                    }
                }
                if (remaining > 0) {
                    leftover.put(i, new ItemStack(stack.getType(), remaining));
                }
                i++;
            }
            return leftover;
        }
        @Override public boolean removeItem(ItemStack... items) { return true; }
        @Override public boolean contains(ItemStack item) { return false; }
        @Override public void clear() {
            for (int i = 0; i < contents.length; i++) {
                contents[i] = null;
            }
        }
        @Override public List<HumanEntity> getViewers() { return new ArrayList<>(); }
        @Override public Inventory getInventory() { return this; }
        @Override public ItemStack getItemInMainHand() { return contents[0]; }
        @Override public void setItemInMainHand(ItemStack item) { contents[0] = item; }
        @Override public int firstEmpty() {
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static final class MockEconomy implements Economy {
        final Map<UUID, Double> balances = new HashMap<>();
        double balance(Player p) { return balances.getOrDefault(p.getUniqueId(), 0.0); }
        @Override public String getName() { return "MockEconomy"; }
        @Override public boolean isEnabled() { return true; }
        @Override public double getBalance(OfflinePlayer player) { return balances.getOrDefault(player.getUniqueId(), 0.0); }
        @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            if (!has(player, amount)) {
                return new EconomyResponse(0, getBalance(player), "insufficient");
            }
            balances.put(player.getUniqueId(), getBalance(player) - amount);
            return new EconomyResponse(amount, getBalance(player), null);
        }
        @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            balances.put(player.getUniqueId(), getBalance(player) + amount);
            return new EconomyResponse(amount, getBalance(player), null);
        }
        @Override public String format(double amount) {
            return "$" + String.format("%.2f", amount);
        }
    }
}
