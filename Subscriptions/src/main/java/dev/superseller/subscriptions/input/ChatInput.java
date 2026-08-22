package dev.superseller.subscriptions.input;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.scheduler.PlatformScheduler;
import dev.superseller.subscriptions.util.Colors;

import org.bukkit.entity.Player;

public final class ChatInput {

    public enum Kind {
        NAME, DESCRIPTION, PRICE, INTERVAL, COMMAND, PERMISSION, GROUP, MONEY_REWARD,
        SEARCH, MAX_SUBS, MAX_CYCLES, SIGNUP_FEE, TRIAL
    }

    private static final long TIMEOUT_MS = 60_000L;

    private final PluginSettings settings;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();
    private BiConsumer<Player, Result> handler;

    public ChatInput(PluginSettings settings, PlatformScheduler scheduler) {
        this.settings = settings;
        this.scheduler = scheduler;
    }

    public void handler(BiConsumer<Player, Result> handler) {
        this.handler = handler;
    }

    public boolean has(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void prompt(Player player, Kind kind, String context) {
        pending.put(player.getUniqueId(), new Prompt(kind, context, System.currentTimeMillis()));
        player.closeInventory();
        String key = switch (kind) {
            case NAME -> "prompt.name";
            case DESCRIPTION -> "prompt.description";
            case PRICE -> "prompt.price";
            case INTERVAL -> "prompt.interval";
            case COMMAND -> "prompt.command";
            case PERMISSION -> "prompt.permission";
            case GROUP -> "prompt.group";
            case MONEY_REWARD -> "prompt.money-reward";
            case SEARCH -> "prompt.search";
            case MAX_SUBS -> "prompt.max-subs";
            case MAX_CYCLES -> "prompt.max-cycles";
            case SIGNUP_FEE -> "prompt.signup-fee";
            case TRIAL -> "prompt.trial";
        };
        player.sendMessage(Colors.parse(settings.prefix() + settings.msg(key)));
    }

    public void onChatAsync(Player player, String message) {
        Prompt prompt = pending.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        scheduler.runEntity(player, () -> complete(player, prompt, message));
    }

    public void tick() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> {
            if (now - entry.getValue().started <= TIMEOUT_MS) {
                return false;
            }
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                scheduler.runEntity(player, () ->
                        player.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.timeout-input"))));
            }
            return true;
        });
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    private void complete(Player player, Prompt prompt, String message) {
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("c") || input.equalsIgnoreCase("exit")) {
            player.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.cancelled-input")));
            return;
        }
        if (handler != null) {
            handler.accept(player, new Result(prompt.kind, prompt.context, input));
        }
    }

    public record Result(Kind kind, String context, String value) {
    }

    private record Prompt(Kind kind, String context, long started) {
    }
}
