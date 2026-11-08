package net.kyori.adventure.text;
/** Minimal in-memory component used only by the offline smoke harness. */
public final class StubComponent implements Component {
    public final String content;
    public boolean italicDisabled;
    public StubComponent(String content) { this.content = content; }
    @Override public Component append(Component other) { return this; }
    @Override public Component decoration(net.kyori.adventure.text.format.TextDecoration decoration, boolean value) {
        if (decoration == net.kyori.adventure.text.format.TextDecoration.ITALIC && !value) italicDisabled = true;
        return this;
    }
}
