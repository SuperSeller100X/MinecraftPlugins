package net.kyori.adventure.text;
public interface Component {
    static Component text(String content) { return new Component() {}; }
    static Component empty() { return new Component() {}; }
}
