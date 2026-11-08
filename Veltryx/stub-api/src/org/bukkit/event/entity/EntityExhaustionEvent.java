package org.bukkit.event.entity;
public class EntityExhaustionEvent extends EntityEvent implements org.bukkit.event.Cancellable {
    public enum ExhaustionReason { BLOCK_MINED, HUNGER_EFFECT, DAMAGED, ATTACK, JUMP_SPRINT, JUMP, SWIM, WALK_UNDERWATER, WALK_ON_WATER, SPRINT, CROUCH, WALK, REGEN, ENCHANTMENT_EFFECT, UNKNOWN }
    @Override public org.bukkit.entity.HumanEntity getEntity(){return null;}
    public ExhaustionReason getExhaustionReason(){return ExhaustionReason.UNKNOWN;}
    public float getExhaustion(){return 0;} public void setExhaustion(float value){}
    public boolean isCancelled(){return false;} public void setCancelled(boolean value){}
}
