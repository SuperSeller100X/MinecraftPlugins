package org.bukkit.scoreboard;

public interface Objective {
    String getName();
    net.kyori.adventure.text.Component displayName();
    void displayName(net.kyori.adventure.text.Component displayName);
    String getCriteria();
    Scoreboard getScoreboard();
    void setDisplaySlot(DisplaySlot slot);
    DisplaySlot getDisplaySlot();
    RenderType getRenderType();
    Score getScore(String entry);
    void unregister();
}
