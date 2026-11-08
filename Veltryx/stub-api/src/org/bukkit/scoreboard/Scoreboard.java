package org.bukkit.scoreboard;

public interface Scoreboard {
    Objective registerNewObjective(String name, Criteria criteria,
            net.kyori.adventure.text.Component displayName, RenderType renderType);
    Objective getObjective(String name);
    Team registerNewTeam(String name);
    Team getTeam(String teamName);
    void resetScores(String entry);
}
