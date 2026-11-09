package io.papermc.paper.event.packet;
public class UncheckedSignChangeEvent extends org.bukkit.event.player.PlayerEvent implements org.bukkit.event.Cancellable {
 public io.papermc.paper.math.BlockPosition getEditedBlockPosition(){return null;}
 public org.bukkit.block.sign.Side getSide(){return org.bukkit.block.sign.Side.FRONT;}
 public java.util.List<net.kyori.adventure.text.Component> lines(){return java.util.List.of();}
 public boolean isCancelled(){return false;} public void setCancelled(boolean value){}
}
