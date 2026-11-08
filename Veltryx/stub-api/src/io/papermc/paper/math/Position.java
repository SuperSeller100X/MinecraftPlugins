package io.papermc.paper.math;
public interface Position {
    int blockX(); int blockY(); int blockZ();
    static BlockPosition block(int x, int y, int z) { return new StubBlockPosition(x, y, z); }
}
