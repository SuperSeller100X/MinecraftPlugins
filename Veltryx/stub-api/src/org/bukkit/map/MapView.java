package org.bukkit.map;
public interface MapView {
    enum Scale { CLOSEST, CLOSE, NORMAL, FAR, FARTHEST }
    void setScale(Scale scale);
    void setCenterX(int x);
    void setCenterZ(int z);
}
