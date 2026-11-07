package dev.superseller.veltryx.storage;

import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.model.NoteEntry;
import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.model.WarnEntry;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for every Veltryx subsystem: economy accounts, player profiles,
 * punishments, staff notes, reports and anti-cheat flags.
 *
 * <p>Implementations keep an in-memory cache as the source of truth at runtime
 * and write through to disk (YAML) or database (SQLite). All methods must be
 * safe to call from asynchronous threads. Snapshots returned by the
 * {@code all*} / {@code top} methods are copies.
 */
public interface DataStore {

    /** Loads every record into memory. */
    void load();

    /** Flushes pending state to persistent storage. */
    void save();

    /** Closes underlying resources (database connections, etc.). */
    void close();

    // ------------------------------------------------------------- economy

    /** Stored balance in cents (0 when the account is unknown). */
    long getBalance(UUID uuid);

    /** Overwrites the stored balance and remembers the player's last-known name. */
    void setBalance(UUID uuid, String name, long cents);

    /** Top accounts by balance, highest first. */
    List<Account> top(int limit);

    // ------------------------------------------------------------- profile

    /** Profile record, or {@code null} when the player is unknown. */
    Profile getProfile(UUID uuid);

    /**
     * Creates or updates a player profile.
     *
     * @param now            current epoch millis
     * @param sessionMillis  playtime to add (0 on join)
     * @param joined         true on join (bumps join counter and last-seen)
     */
    void upsertProfile(UUID uuid, String name, long now, long sessionMillis, boolean joined);

    // --------------------------------------------------------------- bans

    void saveBan(BanEntry entry);

    void setBanActive(String id, boolean active);

    List<BanEntry> allBans();

    // --------------------------------------------------------------- mutes

    void saveMute(MuteEntry entry);

    void setMuteActive(String id, boolean active);

    List<MuteEntry> allMutes();

    // -------------------------------------------------------------- warns

    void saveWarn(WarnEntry entry);

    void setWarnActive(String id, boolean active);

    List<WarnEntry> allWarns();

    // -------------------------------------------------------------- notes

    void saveNote(NoteEntry entry);

    void removeNote(String id);

    List<NoteEntry> allNotes();

    // ------------------------------------------------------------- reports

    void saveReport(ReportEntry entry);

    void setReportResolved(String id, boolean resolved);

    List<ReportEntry> allReports();

    // --------------------------------------------------------------- flags

    void saveFlag(FlagEntry entry);

    /** Every flag recorded for a player, newest first. */
    List<FlagEntry> flagsFor(UUID uuid);

    /** Deletes flags older than the given timestamp. */
    void pruneFlags(long olderThanMillis);
}
