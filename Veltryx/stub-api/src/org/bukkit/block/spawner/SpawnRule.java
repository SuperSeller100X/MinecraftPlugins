package org.bukkit.block.spawner;
public class SpawnRule {
    private final int minBlock, maxBlock, minSky, maxSky;
    public SpawnRule(int minBlock, int maxBlock, int minSky, int maxSky) { this.minBlock=minBlock;this.maxBlock=maxBlock;this.minSky=minSky;this.maxSky=maxSky; }
    public int getMinBlockLight(){return minBlock;} public int getMaxBlockLight(){return maxBlock;}
    public int getMinSkyLight(){return minSky;} public int getMaxSkyLight(){return maxSky;}
}
