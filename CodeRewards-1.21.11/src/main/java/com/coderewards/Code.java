package com.coderewards;

public class Code {
    private String code;
    private String rewardType;
    private double amount;
    private boolean active;
    
    public Code(String code, String rewardType, double amount, boolean active) {
        this.code = code;
        this.rewardType = rewardType;
        this.amount = amount;
        this.active = active;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getRewardType() {
        return rewardType;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
}
