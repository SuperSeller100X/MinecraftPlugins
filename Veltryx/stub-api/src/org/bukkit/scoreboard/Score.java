package org.bukkit.scoreboard;

public interface Score {
    String getEntry();
    Objective getObjective();
    int getScore();
    void setScore(int score);
    void resetScore();
    boolean isScoreSet();
}
