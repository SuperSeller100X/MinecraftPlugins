package io.papermc.paper.scoreboard.numbers;

public interface NumberFormat {
    NumberFormat BLANK = new NumberFormat() { };
    static NumberFormat blank() { return BLANK; }
    static FixedFormat fixed(net.kyori.adventure.text.ComponentLike component) {
        return new TestFixed(component.asComponent());
    }
    class TestFixed implements FixedFormat {
        public final net.kyori.adventure.text.Component component;
        TestFixed(net.kyori.adventure.text.Component component) { this.component = component; }
        @Override public net.kyori.adventure.text.Component asComponent() { return component; }
    }
}
