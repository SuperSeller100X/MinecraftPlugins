package net.kyori.adventure.text;
public interface Component {
    static Component empty() { return null; }
    Component append(Component other);
    static Component text(String content) { return null; }
}
