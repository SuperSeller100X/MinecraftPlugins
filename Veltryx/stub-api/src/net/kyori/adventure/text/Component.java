package net.kyori.adventure.text;
public interface Component {
    Component append(Component other);
    static Component text(String content) { return null; }
}
