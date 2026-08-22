package dev.superseller.subscriptions.hook;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.scheduler.PlatformScheduler;

public final class DiscordHook {

    private final PluginSettings settings;
    private final PlatformScheduler scheduler;
    private final Logger logger;

    public DiscordHook(PluginSettings settings, PlatformScheduler scheduler, Logger logger) {
        this.settings = settings;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public boolean configured() {
        String url = settings.webhookUrl();
        return url != null && !url.isBlank();
    }

    public void charge(String player, String plan, String amount, String result) {
        if (!settings.webhookCharges()) {
            return;
        }
        send("Charge", player + " / " + plan + " / " + amount + " / " + result);
    }

    public void cancel(String player, String plan, String reason) {
        if (!settings.webhookCancels()) {
            return;
        }
        send("Cancelled", player + " / " + plan + " / " + reason);
    }

    public void stock(String plan, String owner) {
        if (!settings.webhookStock()) {
            return;
        }
        send("Out of stock", plan + " by " + owner + " ran out of kits. Subscribers will not be charged.");
    }

    public boolean test() {
        if (!configured()) {
            return false;
        }
        send("Test", "Subscriptions webhook is working.");
        return true;
    }

    private void send(String title, String description) {
        if (!configured()) {
            return;
        }
        String url = settings.webhookUrl();
        String payload = "{\"embeds\":[{\"title\":\"" + escape(title)
                + "\",\"description\":\"" + escape(description) + "\",\"color\":10027263}]}";
        scheduler.runAsync(() -> post(url, payload));
    }

    private void post(String url, String payload) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.warning("Discord webhook returned HTTP " + code);
            }
            connection.disconnect();
        } catch (Exception e) {
            logger.warning("Discord webhook failed: " + e.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
