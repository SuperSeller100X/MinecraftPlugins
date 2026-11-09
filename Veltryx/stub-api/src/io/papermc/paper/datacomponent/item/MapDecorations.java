package io.papermc.paper.datacomponent.item;
import java.util.Map;
import org.bukkit.map.MapCursor;
public interface MapDecorations {
    interface DecorationEntry { MapCursor.Type type(); double x(); double z(); float rotation(); }
    record TestEntry(MapCursor.Type type, double x, double z, float rotation) implements DecorationEntry { }
    record TestDecorations(Map<String, DecorationEntry> decorations) implements MapDecorations { }
    Map<String, DecorationEntry> decorations();
    static DecorationEntry decorationEntry(MapCursor.Type type, double x, double z, float rotation) { return new TestEntry(type,x,z,rotation); }
    static MapDecorations mapDecorations(Map<String, DecorationEntry> entries) { return new TestDecorations(Map.copyOf(entries)); }
}
