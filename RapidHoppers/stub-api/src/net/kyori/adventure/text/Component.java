package net.kyori.adventure.text;
public interface Component {
    static Component text(String value) { return new Component() { public String toString() { return value; } }; }
}
