package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Player reports: submitted with /report, reviewed with /reports. */
public final class ReportService {

    private final DataStore store;
    private final Messages messages;

    public ReportService(DataStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    /** Files a report and notifies online staff. */
    public ReportEntry report(UUID target, String targetName, Player reporter, String reason) {
        ReportEntry entry = new ReportEntry(Ids.next(), target, targetName,
                reporter.getUniqueId(), reporter.getName(), reason,
                System.currentTimeMillis(), false);
        store.saveReport(entry);

        Component notification = messages.prefixed("report.filed-staff", Map.of(
                "reporter", reporter.getName(),
                "player", targetName,
                "reason", reason,
                "id", entry.id()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("veltryx.moderation.reports")) {
                staff.sendMessage(notification);
            }
        }
        Bukkit.getConsoleSender().sendMessage(notification);
        return entry;
    }

    /** Open reports, newest first. */
    public List<ReportEntry> openReports() {
        List<ReportEntry> open = new ArrayList<>();
        for (ReportEntry entry : store.allReports()) {
            if (!entry.resolved()) {
                open.add(entry);
            }
        }
        open.sort(Comparator.comparingLong(ReportEntry::issued).reversed());
        return open;
    }

    /** Marks a report resolved. */
    public Optional<ReportEntry> resolve(String id) {
        for (ReportEntry entry : store.allReports()) {
            if (entry.id().equalsIgnoreCase(id) && !entry.resolved()) {
                store.setReportResolved(entry.id(), true);
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
