package dev.superseller.subscriptions.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Plan {

    private final String id;
    private UUID ownerId;
    private String ownerName;
    private String name;
    private String description;
    private String category;
    private double price;
    private double signupFee;
    private long intervalMs;
    private ChargePolicy policy;
    private int maxSubscribers;
    private int maxCycles;
    private int trialCycles;
    private boolean infiniteStock;
    private int stockKits;
    private PlanStatus status;
    private long createdAt;
    private final List<Reward> rewards = new ArrayList<>();
    private String icon; // material name
    private int subscriberCount;

    public Plan(String id) {
        this.id = id;
        this.ownerName = "Server";
        this.name = "Untitled";
        this.description = "";
        this.category = "CUSTOM";
        this.intervalMs = 3_600_000L;
        this.policy = ChargePolicy.PAUSE;
        this.maxSubscribers = 50;
        this.status = PlanStatus.DRAFT;
        this.createdAt = System.currentTimeMillis();
        this.icon = "CHEST";
    }

    public String id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void ownerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String ownerName() {
        return ownerName == null ? "Server" : ownerName;
    }

    public void ownerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean serverOwned() {
        return ownerId == null;
    }

    public boolean ownedBy(UUID uuid) {
        return uuid != null && uuid.equals(ownerId);
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name == null || name.isBlank() ? "Untitled" : name;
    }

    public String description() {
        return description == null ? "" : description;
    }

    public void description(String description) {
        this.description = description == null ? "" : description;
    }

    public String category() {
        return category == null ? "CUSTOM" : category.toUpperCase(Locale.ROOT);
    }

    public void category(String category) {
        this.category = category == null ? "CUSTOM" : category.toUpperCase(Locale.ROOT);
    }

    public double price() {
        return price;
    }

    public void price(double price) {
        this.price = Math.max(0d, price);
    }

    public double signupFee() {
        return signupFee;
    }

    public void signupFee(double signupFee) {
        this.signupFee = Math.max(0d, signupFee);
    }

    public long intervalMs() {
        return intervalMs;
    }

    public void intervalMs(long intervalMs) {
        this.intervalMs = Math.max(1_000L, intervalMs);
    }

    public ChargePolicy policy() {
        return policy == null ? ChargePolicy.PAUSE : policy;
    }

    public void policy(ChargePolicy policy) {
        this.policy = policy == null ? ChargePolicy.PAUSE : policy;
    }

    public int maxSubscribers() {
        return maxSubscribers;
    }

    public void maxSubscribers(int maxSubscribers) {
        this.maxSubscribers = Math.max(0, maxSubscribers);
    }

    public int maxCycles() {
        return maxCycles;
    }

    public void maxCycles(int maxCycles) {
        this.maxCycles = Math.max(0, maxCycles);
    }

    public int trialCycles() {
        return trialCycles;
    }

    public void trialCycles(int trialCycles) {
        this.trialCycles = Math.max(0, trialCycles);
    }

    public boolean infiniteStock() {
        return infiniteStock;
    }

    public void infiniteStock(boolean infiniteStock) {
        this.infiniteStock = infiniteStock;
    }

    public int stockKits() {
        return stockKits;
    }

    public void stockKits(int stockKits) {
        this.stockKits = Math.max(0, stockKits);
    }

    public boolean consumeKit() {
        if (infiniteStock || !needsItemStock()) {
            return true;
        }
        if (stockKits <= 0) {
            return false;
        }
        stockKits--;
        return true;
    }

    public void addKits(int amount) {
        if (amount > 0) {
            stockKits += amount;
        }
    }

    public boolean takeKits(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (stockKits < amount) {
            return false;
        }
        stockKits -= amount;
        return true;
    }

    public PlanStatus status() {
        return status == null ? PlanStatus.DRAFT : status;
    }

    public void status(PlanStatus status) {
        this.status = status == null ? PlanStatus.DRAFT : status;
    }

    public long createdAt() {
        return createdAt;
    }

    public void createdAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<Reward> rewards() {
        return rewards;
    }

    public String encodeRewards() {
        StringBuilder out = new StringBuilder();
        for (Reward reward : rewards) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(reward.encode());
        }
        return out.toString();
    }

    public void decodeRewards(String blob) {
        rewards.clear();
        if (blob == null || blob.isBlank()) {
            return;
        }
        for (String line : blob.split("\n")) {
            Reward reward = Reward.decode(line);
            if (reward != null) {
                rewards.add(reward);
            }
        }
    }

    public String icon() {
        return icon == null || icon.isBlank() ? "CHEST" : icon;
    }

    public void icon(String icon) {
        this.icon = icon;
    }

    public int subscriberCount() {
        return subscriberCount;
    }

    public void subscriberCount(int subscriberCount) {
        this.subscriberCount = Math.max(0, subscriberCount);
    }

    public boolean hasCapacity() {
        return maxSubscribers <= 0 || subscriberCount < maxSubscribers;
    }

    public boolean needsItemStock() {
        for (Reward reward : rewards) {
            if (reward.type().requiresSellerStock()) {
                return true;
            }
        }
        return false;
    }

    public boolean needsSellerFunds() {
        for (Reward reward : rewards) {
            if (reward.type().requiresSellerFunds()) {
                return true;
            }
        }
        return false;
    }

    public double sellerPayoutCost() {
        double total = 0d;
        for (Reward reward : rewards) {
            if (reward.type() == RewardType.MONEY) {
                total += reward.amount();
            }
        }
        return total;
    }

    public boolean canFulfillNow() {
        if (needsItemStock() && !infiniteStock && stockKits <= 0) {
            return false;
        }
        return !rewards.isEmpty();
    }
}
