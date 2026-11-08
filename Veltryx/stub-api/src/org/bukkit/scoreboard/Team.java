package org.bukkit.scoreboard;

public interface Team {
    String getName();
    net.kyori.adventure.text.Component displayName();
    void displayName(net.kyori.adventure.text.Component displayName);
    net.kyori.adventure.text.Component prefix();
    void prefix(net.kyori.adventure.text.Component prefix);
    net.kyori.adventure.text.Component suffix();
    void suffix(net.kyori.adventure.text.Component suffix);
    void addEntry(String entry);
    void removeEntry(String entry);
    boolean hasEntry(String entry);
    java.util.Set<String> getEntries();
    Scoreboard getScoreboard();
    void unregister();
}
