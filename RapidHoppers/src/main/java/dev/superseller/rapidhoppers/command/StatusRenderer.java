package dev.superseller.rapidhoppers.command;

import java.util.Map;

import dev.superseller.rapidhoppers.RapidHoppersPlugin;
import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.engine.ThrottleMonitor;
import dev.superseller.rapidhoppers.engine.TransferMath;
import dev.superseller.rapidhoppers.scheduler.PlatformScheduler;

import org.bukkit.command.CommandSender;

/** Shared renderer for {@code /rh info} and {@code /rh stats}. */
public final class StatusRenderer {

    private final RapidHoppersPlugin plugin;

    public StatusRenderer(RapidHoppersPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendInfo(CommandSender sender) {
        Settings settings = plugin.settings();
        plugin.messages().sendPlain(sender, "status.header", Map.of());
        plugin.messages().sendPlain(sender, "status.engine", Map.of("state", stateTag()));
        plugin.messages().sendPlain(sender, "status.interval", Map.of(
                "interval", String.valueOf(settings.getIntervalTicks()),
                "speed", String.valueOf(TransferMath.round1(settings.speedFactor()))));
        plugin.messages().sendPlain(sender, "status.rate",
                Map.of("rate", String.valueOf(TransferMath.round1(settings.itemsPerSecond()))));
        plugin.messages().sendPlain(sender, "status.worlds", Map.of(
                "mode", settings.getWorldMode().name(),
                "count", String.valueOf(settings.getWorldList().size())));
        plugin.messages().sendPlain(sender, "status.version", Map.of(
                "version", RapidHoppersPlugin.VERSION,
                "api", RapidHoppersPlugin.API_VERSION,
                "platform", PlatformScheduler.platformName()));
    }

    public void sendStats(CommandSender sender) {
        plugin.messages().sendPlain(sender, "status.header", Map.of());
        plugin.messages().sendPlain(sender, "status.engine", Map.of("state", stateTag()));
        plugin.messages().sendPlain(sender, "status.tracked",
                Map.of("tracked", String.valueOf(plugin.stats().trackedContainers())));
        plugin.messages().sendPlain(sender, "status.transfers", Map.of(
                "total", String.valueOf(plugin.stats().totalTransfers()),
                "rate", String.valueOf(TransferMath.round1(plugin.stats().ratePerSecond()))));
        plugin.messages().sendPlain(sender, "status.tps",
                Map.of("tps", String.valueOf(plugin.throttle().tps())));
    }

    /** Coloured engine state tag, reflecting the throttle. */
    public String stateTag() {
        if (!plugin.settings().isEnabled()) {
            return plugin.messages().raw("status.disabled");
        }
        ThrottleMonitor.State state = plugin.throttle().state();
        return switch (state) {
            case PAUSED -> plugin.messages().raw("status.paused");
            case THROTTLED -> plugin.messages().raw("status.throttled");
            case NORMAL -> plugin.messages().raw("status.enabled");
        };
    }
}
