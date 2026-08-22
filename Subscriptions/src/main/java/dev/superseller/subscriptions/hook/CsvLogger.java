package dev.superseller.subscriptions.hook;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import dev.superseller.subscriptions.config.PluginSettings;

/** Daily CSV charge log. Path separators come from {@link File} so this is OS-safe. */
public final class CsvLogger {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final File folder;
    private final PluginSettings settings;
    private final Logger logger;

    public CsvLogger(File folder, PluginSettings settings, Logger logger) {
        this.folder = folder;
        this.settings = settings;
        this.logger = logger;
    }

    public void write(String subscriptionId, String planId, String subscriber, String result,
                      double amount, String detail) {
        if (!settings.csvLogs()) {
            return;
        }
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Could not create log folder " + folder);
            return;
        }
        File file = new File(folder, "charges-" + DAY.format(LocalDate.now(ZoneId.systemDefault())) + ".csv");
        boolean header = !file.exists();
        try (FileWriter writer = new FileWriter(file, true)) {
            if (header) {
                writer.write("time,subscription,plan,subscriber,result,amount,detail\n");
            }
            writer.write(csv(Instant.now().toString()) + ","
                    + csv(subscriptionId) + ","
                    + csv(planId) + ","
                    + csv(subscriber) + ","
                    + csv(result) + ","
                    + amount + ","
                    + csv(detail) + "\n");
        } catch (IOException e) {
            logger.warning("Could not write CSV log: " + e.getMessage());
        }
        prune();
    }

    private void prune() {
        int days = settings.csvRetentionDays();
        if (days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        File[] files = folder.listFiles((dir, name) -> name.startsWith("charges-") && name.endsWith(".csv"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.lastModified() < cutoff && !file.delete()) {
                logger.warning("Could not prune " + file.getName());
            }
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
