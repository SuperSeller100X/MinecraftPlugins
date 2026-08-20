package dev.superseller.attag.smoke;

import dev.superseller.attag.AtTagPlugin;
import dev.superseller.attag.config.AtTagConfig;
import dev.superseller.attag.listener.ChatListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * End-to-end offline test: boots the real plugin against an in-memory mock
 * of the Bukkit API and asserts chat rewriting and ping-sound delivery.
 * Run by build.sh — exit code 1 on any failure.
 */
public final class MockFlowTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        MockServer server = new MockServer();
        Bukkit.setServer(server);
        MockPlayer alex = server.add("Alex", new Location(100.5, 64.0, 100.5));
        MockPlayer bob = server.add("Bob", new Location(-3.2, 70.0, 12.0));
        MockPlayer carol = server.add("Carol", new Location(0, 0, 0));

        AtTagPlugin plugin = new AtTagPlugin();
        plugin.onEnable(); // registers the ChatListener on the mock server
        AtTagConfig config = plugin.getAtTagConfig();
        ChatListener listener = server.listener;

        // ---- 1. @player mention -------------------------------------------
        section("@player");
        AsyncPlayerChatEvent e1 = chat(alex, "@Bob hello");
        listener.onChat(e1);
        check("message unchanged", e1.getMessage().equals("@Bob hello"));
        check("bob hears exactly one player-sound",
                bob.sounds().equals(List.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)));
        check("sender silent", alex.sounds().isEmpty());
        check("bystander silent", carol.sounds().isEmpty());

        // ---- 2. @here coordinates ------------------------------------------
        section("@here");
        clear(alex, bob, carol);
        AsyncPlayerChatEvent e2 = chat(alex, "meet @here now");
        listener.onChat(e2);
        check("@here replaced with block coords",
                e2.getMessage().equals("meet [100, 64, 100] now"));
        check("no sound for @here", alex.sounds().isEmpty()
                && bob.sounds().isEmpty() && carol.sounds().isEmpty());

        // ---- 3. @everyone ---------------------------------------------------
        section("@everyone");
        clear(alex, bob, carol);
        AsyncPlayerChatEvent e3 = chat(alex, "@everyone raid!");
        listener.onChat(e3);
        check("message unchanged", e3.getMessage().equals("@everyone raid!"));
        check("bob pinged", bob.sounds().equals(List.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)));
        check("carol pinged", carol.sounds().equals(List.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)));
        check("sender not pinged", alex.sounds().isEmpty());

        // ---- 4. @all + explicit mention dedupe ------------------------------
        section("@all + @name dedupe");
        clear(alex, bob, carol);
        AsyncPlayerChatEvent e4 = chat(alex, "@all @Bob @bob");
        listener.onChat(e4);
        check("bob single sound despite double mention + @all",
                bob.sounds().equals(List.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)));
        check("carol everyone-sound", carol.sounds().equals(List.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)));

        // ---- 5. unknown name ------------------------------------------------
        section("unknown name");
        clear(alex, bob, carol);
        AsyncPlayerChatEvent e5 = chat(alex, "@Nobody hello");
        listener.onChat(e5);
        check("message unchanged", e5.getMessage().equals("@Nobody hello"));
        check("nobody pinged", bob.sounds().isEmpty() && carol.sounds().isEmpty());

        // ---- 6. no @ in message ---------------------------------------------
        section("plain message");
        clear(alex, bob, carol);
        AsyncPlayerChatEvent e6 = chat(alex, "just chatting");
        listener.onChat(e6);
        check("message untouched", e6.getMessage().equals("just chatting"));
        check("nobody pinged", bob.sounds().isEmpty() && carol.sounds().isEmpty());

        // ---- 7. config defaults from bundled config.yml ----------------------
        section("config defaults");
        check("default player-sound", config.playerSound() == Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        check("default everyone-sound", config.everyoneSound() == Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        check("default volume 1.0", config.volume() == 1.0f);
        check("default pitch 1.0", config.pitch() == 1.0f);

        if (failed > 0) {
            System.err.println("MockFlowTest FAILED: " + failed + " failure(s), " + passed + " passed.");
            System.exit(1);
        }
        System.out.println("MockFlowTest: all " + passed + " checks passed.");
    }

    private static void section(String name) {
        System.out.println("== " + name + " ==");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok: " + name);
        } else {
            failed++;
            System.err.println("  FAIL: " + name);
        }
    }

    private static void clear(MockPlayer... players) {
        for (MockPlayer p : players) {
            p.sounds.clear();
        }
    }

    private static AsyncPlayerChatEvent chat(MockPlayer from, String message) {
        return new AsyncPlayerChatEvent(true, from, message, new LinkedHashSet<>());
    }

    // ------------------------------------------------------------ mock API

    static final class MockServer implements Server {

        final List<MockPlayer> players = new ArrayList<>();
        ChatListener listener;

        MockPlayer add(String name, Location location) {
            MockPlayer p = new MockPlayer(name, location, this);
            players.add(p);
            return p;
        }

        @Override
        public Collection<? extends Player> getOnlinePlayers() {
            return new ArrayList<>(players);
        }

        @Override
        public PluginManager getPluginManager() {
            return new PluginManager() {
                @Override
                public void registerEvents(Listener listener, Plugin plugin) {
                    MockServer.this.listener = (ChatListener) listener;
                }
            };
        }

        @Override
        public BukkitScheduler getScheduler() {
            return (plugin, task) -> {
                task.run(); // run synchronously so tests can assert sounds
                return new BukkitTask() {
                };
            };
        }
    }

    static final class MockPlayer implements Player {

        final String name;
        final Location location;
        final Server server;
        final List<Sound> sounds = new ArrayList<>();

        MockPlayer(String name, Location location, Server server) {
            this.name = name;
            this.location = location;
            this.server = server;
        }

        List<Sound> sounds() {
            return Collections.unmodifiableList(sounds);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public void playSound(Location location, Sound sound, float volume, float pitch) {
            sounds.add(sound);
        }
    }
}
