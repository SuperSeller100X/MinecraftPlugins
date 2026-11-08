package net.kyori.adventure.text;
public interface Component {
    static Component empty() { return new StubComponent(""); }
    Component append(Component other);
    Component decoration(net.kyori.adventure.text.format.TextDecoration decoration, boolean value);
    static Component text(String content) { return new StubComponent(content); }
}
