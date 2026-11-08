package io.papermc.paper.scoreboard.numbers;

public interface NumberFormat {
    NumberFormat BLANK = new NumberFormat() { };
    static NumberFormat blank() { return BLANK; }
}
