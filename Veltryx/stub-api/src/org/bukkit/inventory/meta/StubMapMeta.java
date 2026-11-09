package org.bukkit.inventory.meta;
public class StubMapMeta extends StubItemMeta implements MapMeta {
    public org.bukkit.map.MapView view;
    public void setMapView(org.bukkit.map.MapView view) { this.view=view; }
}
