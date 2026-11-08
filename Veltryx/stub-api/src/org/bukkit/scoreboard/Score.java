package org.bukkit.scoreboard;

public interface Score {
    io.papermc.paper.scoreboard.numbers.NumberFormat numberFormat();
    void numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat format);
    String getEntry();
    Objective getObjective();
    int getScore();
    void setScore(int score);
    void resetScore();
    boolean isScoreSet();
}
