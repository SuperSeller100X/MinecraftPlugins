package dev.superseller.veltryx.storage;

import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.model.NoteEntry;
import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.model.WarnEntry;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * YAML-backed {@link DataStore}: four flat files under the plugin data folder
 * ({@code economy.yml}, {@code profiles.yml}, {@code moderation.yml},
 * {@code flags.yml}). Simple and portable; ideal for small and mid-size servers.
 */
public final class YamlDataStore implements DataStore {

    private final File folder;
    private final Logger logger;

    private final ConcurrentMap<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BanEntry> bans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WarnEntry> warns = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NoteEntry> notes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReportEntry> reports = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FlagEntry> flags = new ConcurrentHashMap<>();

    public YamlDataStore(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    @Override
    public void load() {
        folder.mkdirs();
        accounts.clear();
        YamlConfiguration economy = read("economy.yml");
        for (String key : economy.getKeys(false)) {
            ConfigurationSection section = economy.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            accounts.put(uuid, new Account(uuid,
                    section.getString("name", "?"),
                    section.getLong("balance", 0L)));
        }

        profiles.clear();
        YamlConfiguration prof = read("profiles.yml");
        for (String key : prof.getKeys(false)) {
            ConfigurationSection section = prof.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            profiles.put(uuid, new Profile(uuid,
                    section.getString("name", "?"),
                    section.getLong("first-seen", 0L),
                    section.getLong("last-seen", 0L),
                    section.getLong("playtime", 0L),
                    section.getInt("joins", 0)));
        }

        bans.clear();
        mutes.clear();
        warns.clear();
        notes.clear();
        reports.clear();
        YamlConfiguration moderation = read("moderation.yml");
        loadPunishments(moderation.getConfigurationSection("bans"));
        loadMutes(moderation.getConfigurationSection("mutes"));
        loadWarns(moderation.getConfigurationSection("warns"));
        loadNotes(moderation.getConfigurationSection("notes"));
        loadReports(moderation.getConfigurationSection("reports"));

        flags.clear();
        YamlConfiguration flagFile = read("flags.yml");
        ConfigurationSection flagSection = flagFile.getConfigurationSection("flags");
        if (flagSection != null) {
            for (String key : flagSection.getKeys(false)) {
                ConfigurationSection s = flagSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                UUID uuid = parseUuid(s.getString("uuid", ""));
                if (uuid == null) {
                    continue;
                }
                flags.put(key, new FlagEntry(key, uuid, s.getString("name", "?"),
                        s.getString("check", "?"), s.getDouble("vl", 0.0),
                        s.getString("detail", ""), s.getLong("issued", 0L)));
            }
        }
    }

    private void loadPunishments(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID uuid = parseUuid(s.getString("uuid", ""));
            if (uuid == null) {
                continue;
            }
            bans.put(key, new BanEntry(key, uuid, s.getString("name", "?"),
                    s.getString("category", "custom"), s.getString("reason", ""),
                    s.getString("details", ""), s.getString("staff", "?"),
                    s.getLong("issued", 0L), s.getLong("expires", -1L),
                    s.getBoolean("active", true), s.getBoolean("auto", false)));
        }
    }

    private void loadMutes(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID uuid = parseUuid(s.getString("uuid", ""));
            if (uuid == null) {
                continue;
            }
            mutes.put(key, new MuteEntry(key, uuid, s.getString("name", "?"),
                    s.getString("reason", ""), s.getString("staff", "?"),
                    s.getLong("issued", 0L), s.getLong("expires", -1L),
                    s.getBoolean("active", true)));
        }
    }

    private void loadWarns(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID uuid = parseUuid(s.getString("uuid", ""));
            if (uuid == null) {
                continue;
            }
            warns.put(key, new WarnEntry(key, uuid, s.getString("name", "?"),
                    s.getString("reason", ""), s.getString("staff", "?"),
                    s.getLong("issued", 0L), s.getBoolean("active", true)));
        }
    }

    private void loadNotes(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID uuid = parseUuid(s.getString("uuid", ""));
            if (uuid == null) {
                continue;
            }
            notes.put(key, new NoteEntry(key, uuid, s.getString("name", "?"),
                    s.getString("note", ""), s.getString("staff", "?"),
                    s.getLong("issued", 0L)));
        }
    }

    private void loadReports(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID target = parseUuid(s.getString("target", ""));
            UUID reporter = parseUuid(s.getString("reporter", ""));
            if (target == null || reporter == null) {
                continue;
            }
            reports.put(key, new ReportEntry(key, target, s.getString("target-name", "?"),
                    reporter, s.getString("reporter-name", "?"),
                    s.getString("reason", ""), s.getLong("issued", 0L),
                    s.getBoolean("resolved", false)));
        }
    }

    @Override
    public void save() {
        YamlConfiguration economy = new YamlConfiguration();
        for (Account account : accounts.values()) {
            String path = account.uuid().toString();
            economy.set(path + ".name", account.name());
            economy.set(path + ".balance", account.balance());
        }
        write("economy.yml", economy);

        YamlConfiguration prof = new YamlConfiguration();
        for (Profile profile : profiles.values()) {
            String path = profile.uuid().toString();
            prof.set(path + ".name", profile.name());
            prof.set(path + ".first-seen", profile.firstSeen());
            prof.set(path + ".last-seen", profile.lastSeen());
            prof.set(path + ".playtime", profile.playtimeMs());
            prof.set(path + ".joins", profile.joins());
        }
        write("profiles.yml", prof);

        YamlConfiguration moderation = new YamlConfiguration();
        for (BanEntry b : bans.values()) {
            String path = "bans." + b.id();
            moderation.set(path + ".uuid", b.uuid().toString());
            moderation.set(path + ".name", b.name());
            moderation.set(path + ".category", b.category());
            moderation.set(path + ".reason", b.reason());
            moderation.set(path + ".details", b.details());
            moderation.set(path + ".staff", b.staff());
            moderation.set(path + ".issued", b.issued());
            moderation.set(path + ".expires", b.expires());
            moderation.set(path + ".active", b.active());
            moderation.set(path + ".auto", b.auto());
        }
        for (MuteEntry m : mutes.values()) {
            String path = "mutes." + m.id();
            moderation.set(path + ".uuid", m.uuid().toString());
            moderation.set(path + ".name", m.name());
            moderation.set(path + ".reason", m.reason());
            moderation.set(path + ".staff", m.staff());
            moderation.set(path + ".issued", m.issued());
            moderation.set(path + ".expires", m.expires());
            moderation.set(path + ".active", m.active());
        }
        for (WarnEntry w : warns.values()) {
            String path = "warns." + w.id();
            moderation.set(path + ".uuid", w.uuid().toString());
            moderation.set(path + ".name", w.name());
            moderation.set(path + ".reason", w.reason());
            moderation.set(path + ".staff", w.staff());
            moderation.set(path + ".issued", w.issued());
            moderation.set(path + ".active", w.active());
        }
        for (NoteEntry n : notes.values()) {
            String path = "notes." + n.id();
            moderation.set(path + ".uuid", n.uuid().toString());
            moderation.set(path + ".name", n.name());
            moderation.set(path + ".note", n.note());
            moderation.set(path + ".staff", n.staff());
            moderation.set(path + ".issued", n.issued());
        }
        for (ReportEntry r : reports.values()) {
            String path = "reports." + r.id();
            moderation.set(path + ".target", r.target().toString());
            moderation.set(path + ".target-name", r.targetName());
            moderation.set(path + ".reporter", r.reporter().toString());
            moderation.set(path + ".reporter-name", r.reporterName());
            moderation.set(path + ".reason", r.reason());
            moderation.set(path + ".issued", r.issued());
            moderation.set(path + ".resolved", r.resolved());
        }
        write("moderation.yml", moderation);

        YamlConfiguration flagFile = new YamlConfiguration();
        for (FlagEntry f : flags.values()) {
            String path = "flags." + f.id();
            flagFile.set(path + ".uuid", f.uuid().toString());
            flagFile.set(path + ".name", f.name());
            flagFile.set(path + ".check", f.check());
            flagFile.set(path + ".vl", f.vl());
            flagFile.set(path + ".detail", f.detail());
            flagFile.set(path + ".issued", f.issued());
        }
        write("flags.yml", flagFile);
    }

    @Override
    public void close() {
        save();
    }

    // ------------------------------------------------------------- economy

    @Override
    public long getBalance(UUID uuid) {
        Account account = accounts.get(uuid);
        return account == null ? 0L : account.balance();
    }

    @Override
    public void setBalance(UUID uuid, String name, long cents) {
        accounts.put(uuid, new Account(uuid, name, Math.max(0L, cents)));
        save();
    }

    @Override
    public List<Account> top(int limit) {
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparingLong(Account::balance).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ------------------------------------------------------------- profile

    @Override
    public Profile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    @Override
    public void upsertProfile(UUID uuid, String name, long now, long sessionMillis, boolean joined) {
        Profile existing = profiles.get(uuid);
        if (existing == null) {
            profiles.put(uuid, new Profile(uuid, name, now, now, Math.max(0L, sessionMillis), joined ? 1 : 0));
        } else {
            profiles.put(uuid, new Profile(uuid, name,
                    existing.firstSeen() == 0L ? now : existing.firstSeen(),
                    joined ? now : Math.max(existing.lastSeen(), now),
                    existing.playtimeMs() + Math.max(0L, sessionMillis),
                    existing.joins() + (joined ? 1 : 0)));
        }
        save();
    }

    // --------------------------------------------------------------- bans

    @Override
    public void saveBan(BanEntry entry) {
        bans.put(entry.id(), entry);
        save();
    }

    @Override
    public void setBanActive(String id, boolean active) {
        BanEntry entry = bans.get(id);
        if (entry != null) {
            bans.put(id, new BanEntry(entry.id(), entry.uuid(), entry.name(), entry.category(),
                    entry.reason(), entry.details(), entry.staff(), entry.issued(),
                    entry.expires(), active, entry.auto()));
            save();
        }
    }

    @Override
    public List<BanEntry> allBans() {
        return List.copyOf(bans.values());
    }

    // --------------------------------------------------------------- mutes

    @Override
    public void saveMute(MuteEntry entry) {
        mutes.put(entry.id(), entry);
        save();
    }

    @Override
    public void setMuteActive(String id, boolean active) {
        MuteEntry entry = mutes.get(id);
        if (entry != null) {
            mutes.put(id, new MuteEntry(entry.id(), entry.uuid(), entry.name(), entry.reason(),
                    entry.staff(), entry.issued(), entry.expires(), active));
            save();
        }
    }

    @Override
    public List<MuteEntry> allMutes() {
        return List.copyOf(mutes.values());
    }

    // -------------------------------------------------------------- warns

    @Override
    public void saveWarn(WarnEntry entry) {
        warns.put(entry.id(), entry);
        save();
    }

    @Override
    public void setWarnActive(String id, boolean active) {
        WarnEntry entry = warns.get(id);
        if (entry != null) {
            warns.put(id, new WarnEntry(entry.id(), entry.uuid(), entry.name(), entry.reason(),
                    entry.staff(), entry.issued(), active));
            save();
        }
    }

    @Override
    public List<WarnEntry> allWarns() {
        return List.copyOf(warns.values());
    }

    // -------------------------------------------------------------- notes

    @Override
    public void saveNote(NoteEntry entry) {
        notes.put(entry.id(), entry);
        save();
    }

    @Override
    public void removeNote(String id) {
        notes.remove(id);
        save();
    }

    @Override
    public List<NoteEntry> allNotes() {
        return List.copyOf(notes.values());
    }

    // ------------------------------------------------------------- reports

    @Override
    public void saveReport(ReportEntry entry) {
        reports.put(entry.id(), entry);
        save();
    }

    @Override
    public void setReportResolved(String id, boolean resolved) {
        ReportEntry entry = reports.get(id);
        if (entry != null) {
            reports.put(id, new ReportEntry(entry.id(), entry.target(), entry.targetName(),
                    entry.reporter(), entry.reporterName(), entry.reason(), entry.issued(), resolved));
            save();
        }
    }

    @Override
    public List<ReportEntry> allReports() {
        return List.copyOf(reports.values());
    }

    // --------------------------------------------------------------- flags

    @Override
    public void saveFlag(FlagEntry entry) {
        flags.put(entry.id(), entry);
        save();
    }

    @Override
    public List<FlagEntry> flagsFor(UUID uuid) {
        List<FlagEntry> matching = new ArrayList<>();
        for (FlagEntry flag : flags.values()) {
            if (flag.uuid().equals(uuid)) {
                matching.add(flag);
            }
        }
        matching.sort(java.util.Comparator.comparingLong(FlagEntry::issued).reversed());
        return matching;
    }

    @Override
    public void pruneFlags(long olderThanMillis) {
        flags.values().removeIf(f -> f.issued() < olderThanMillis);
        save();
    }

    // -------------------------------------------------------------- helper

    private YamlConfiguration read(String name) {
        File file = new File(folder, name);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void write(String name, YamlConfiguration yaml) {
        try {
            yaml.save(new File(folder, name));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + name + ": " + e.getMessage());
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
