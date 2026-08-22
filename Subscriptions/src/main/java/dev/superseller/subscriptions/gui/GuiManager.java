package dev.superseller.subscriptions.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.economy.EconomyService;
import dev.superseller.subscriptions.input.ChatInput;
import dev.superseller.subscriptions.model.ChargePolicy;
import dev.superseller.subscriptions.model.InboxEntry;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.PlanDraft;
import dev.superseller.subscriptions.model.PlanStatus;
import dev.superseller.subscriptions.model.Reward;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.model.SubscriptionStatus;
import dev.superseller.subscriptions.service.BillingService;
import dev.superseller.subscriptions.service.InboxService;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Colors;
import dev.superseller.subscriptions.util.Ids;
import dev.superseller.subscriptions.util.ItemBuilder;
import dev.superseller.subscriptions.util.ItemSerial;
import dev.superseller.subscriptions.util.MoneyParser;
import dev.superseller.subscriptions.util.Numbers;
import dev.superseller.subscriptions.util.Text;
import dev.superseller.subscriptions.util.TimeParser;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiManager {

    private static final String[] CATEGORIES = {"ALL", "KITS", "RANKS", "MONEY", "SERVICES", "CUSTOM"};

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final Database database;
    private final EconomyService economy;
    private final BillingService billing;
    private final InboxService inbox;
    private final ChatInput chat;
    private final Map<UUID, PlanDraft> drafts = new ConcurrentHashMap<>();

    public GuiManager(JavaPlugin plugin, PluginSettings settings, Database database, EconomyService economy,
                      BillingService billing, InboxService inbox, ChatInput chat) {
        this.plugin = plugin;
        this.settings = settings;
        this.database = database;
        this.economy = economy;
        this.billing = billing;
        this.inbox = inbox;
        this.chat = chat;
    }

    public Map<UUID, PlanDraft> drafts() {
        return drafts;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.MAIN, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, title("main", "&8Subscriptions"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(10, ItemBuilder.of(Material.ENDER_CHEST, "&dMarketplace",
                "&7Browse public plans", "&eClick to open"));
        inv.setItem(11, ItemBuilder.of(Material.CLOCK, "&bMy subscriptions",
                "&7Manage, pause, cancel,", "&7change your charge policy", "&eClick to open"));
        inv.setItem(12, ItemBuilder.of(Material.ANVIL, "&aMy plans",
                "&7Create and edit plans", "&eClick to open"));
        inv.setItem(13, ItemBuilder.of(Material.CHEST, "&6Inbox",
                "&7" + inbox.size(player.getUniqueId()) + " waiting item(s)", "&eClick to claim"));
        inv.setItem(14, ItemBuilder.of(Material.BOOK, "&eHistory",
                "&7Your recent charges", "&eClick to open"));
        inv.setItem(16, ItemBuilder.of(Material.PAPER, "&fHelp",
                "&7Commands, policies, anti-scam"));
        player.openInventory(inv);
        clickSound(player);
    }

    public void openBrowse(Player player, int page, String query, String category) {
        List<Plan> plans = database.listedPlans(query, category);
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.BROWSE, player.getUniqueId());
        holder.page = Math.max(0, page);
        holder.query = query == null ? "" : query;
        holder.category = category == null ? "ALL" : category;
        Inventory inv = Bukkit.createInventory(holder, 54, title("browse", "&8Marketplace"));
        holder.inventory(inv);
        fill(inv);
        int start = holder.page * 36;
        for (int i = 0; i < 36; i++) {
            int index = start + i;
            if (index >= plans.size()) {
                break;
            }
            Plan plan = plans.get(index);
            holder.slotIds[i] = plan.id();
            inv.setItem(i, planIcon(plan, false));
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean selected = CATEGORIES[i].equalsIgnoreCase(holder.category);
            inv.setItem(45 + i, ItemBuilder.of(selected ? Material.LIME_DYE : Material.GRAY_DYE,
                    (selected ? "&a" : "&7") + CATEGORIES[i], "&7Filter category"));
        }
        inv.setItem(51, ItemBuilder.of(Material.OAK_SIGN, "&eSearch",
                holder.query.isBlank() ? "&7No filter" : "&f" + holder.query, "&eClick to type a query"));
        if (holder.page > 0) {
            inv.setItem(52, ItemBuilder.of(Material.ARROW, "&ePrevious page"));
        }
        if ((holder.page + 1) * 36 < plans.size()) {
            inv.setItem(53, ItemBuilder.of(Material.ARROW, "&eNext page"));
        }
        player.openInventory(inv);
    }

    public void openPlan(Player player, String planId) {
        Plan plan = database.plan(planId);
        if (plan == null) {
            tell(player, settings.msg("generic.unknown-plan"));
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.PLAN, player.getUniqueId());
        holder.planId = planId;
        Inventory inv = Bukkit.createInventory(holder, 27, title("plan", "&8Plan"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(4, planIcon(plan, true));
        inv.setItem(11, ItemBuilder.of(Material.EMERALD, "&aSubscribe",
                "&7Price: &e" + economy.format(plan.price()) + " &7/ " + TimeParser.format(plan.intervalMs()),
                "&7Signup: &e" + economy.format(plan.signupFee()),
                "&7First charge: &e" + economy.format(billing.firstCharge(plan)),
                "&eClick to confirm"));
        inv.setItem(13, ItemBuilder.of(Material.PAPER, "&fRewards", rewardLore(plan)));
        if (plan.ownedBy(player.getUniqueId()) || player.hasPermission("subscriptions.admin")) {
            inv.setItem(15, ItemBuilder.of(Material.CHEST, "&6Stock",
                    "&7Kits: &f" + (plan.infiniteStock() ? "∞" : plan.stockKits()),
                    "&eClick to deposit"));
            inv.setItem(16, ItemBuilder.of(Material.WRITABLE_BOOK, "&eEdit plan"));
            inv.setItem(17, ItemBuilder.of(plan.status().listed() ? Material.LIME_DYE : Material.GRAY_DYE,
                    plan.status().listed() ? "&aPublished" : "&7" + plan.status().name(),
                    "&eClick to toggle listing"));
        }
        inv.setItem(22, ItemBuilder.of(Material.ARROW, "&7Back"));
        player.openInventory(inv);
    }

    public void openCreate(Player player) {
        PlanDraft draft = drafts.computeIfAbsent(player.getUniqueId(), id -> {
            PlanDraft created = new PlanDraft();
            created.ownerId = player.getUniqueId();
            created.ownerName = player.getName();
            return created;
        });
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.CREATE, player.getUniqueId());
        holder.planId = draft.editingPlanId;
        Inventory inv = Bukkit.createInventory(holder, 54, title("create", "&8Create plan"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(10, ItemBuilder.of(Material.NAME_TAG, "&eName", "&f" + draft.name, "&eClick to type"));
        inv.setItem(11, ItemBuilder.of(Material.PAPER, "&eDescription",
                draft.description.isBlank() ? "&7None" : "&f" + draft.description, "&eClick to type"));
        inv.setItem(12, ItemBuilder.of(Material.GOLD_INGOT, "&ePrice / cycle",
                "&f" + Numbers.compact(draft.price), "&7Supports &ek &7/ &em &7/ &eb &7/ &et", "&eClick to type"));
        inv.setItem(13, ItemBuilder.of(Material.CLOCK, "&eInterval",
                "&f" + TimeParser.format(draft.intervalMs), "&7Real-world time", "&eClick to type"));
        inv.setItem(14, ItemBuilder.of(Material.SUNFLOWER, "&eSignup fee",
                "&f" + Numbers.compact(draft.signupFee), "&eClick to type"));
        inv.setItem(15, ItemBuilder.of(Material.EXPERIENCE_BOTTLE, "&eTrial cycles",
                "&f" + draft.trialCycles, "&eClick to type"));
        inv.setItem(16, ItemBuilder.of(policyMaterial(draft.policy), "&eDefault policy",
                "&f" + draft.policy.display(),
                "&7Subscribers can override this", "&eClick to cycle"));
        inv.setItem(19, ItemBuilder.of(Material.PLAYER_HEAD, "&eMax subscribers",
                "&f" + (draft.maxSubscribers <= 0 ? "unlimited" : draft.maxSubscribers)));
        inv.setItem(20, ItemBuilder.of(Material.REPEATER, "&eMax cycles",
                "&f" + (draft.maxCycles <= 0 ? "until cancelled" : draft.maxCycles)));
        inv.setItem(21, ItemBuilder.of(Material.MAP, "&eCategory", "&f" + draft.category, "&eClick to cycle"));
        inv.setItem(22, ItemBuilder.of(draft.infiniteStock ? Material.BEACON : Material.BARRIER,
                draft.infiniteStock ? "&aInfinite stock" : "&7Finite stock",
                "&eClick to toggle (permission)"));
        inv.setItem(28, ItemBuilder.of(Material.CHEST, "&aAdd item reward",
                "&7Click an item in your inventory"));
        inv.setItem(29, ItemBuilder.of(Material.GOLD_NUGGET, "&aAdd money payout"));
        inv.setItem(30, ItemBuilder.of(Material.COMMAND_BLOCK, "&aAdd command"));
        inv.setItem(31, ItemBuilder.of(Material.NAME_TAG, "&aAdd permission"));
        inv.setItem(32, ItemBuilder.of(Material.TOTEM_OF_UNDYING, "&aAdd rank / group"));
        inv.setItem(33, ItemBuilder.of(Material.BIRCH_SIGN, "&aAdd message"));
        for (int i = 0; i < Math.min(9, draft.rewards.size()); i++) {
            Reward reward = draft.rewards.get(i);
            holder.slotIds[37 + i] = String.valueOf(i);
            inv.setItem(37 + i, ItemBuilder.of(rewardIcon(reward), "&f" + reward.describe(),
                    "&7" + reward.type().name(), "&cRight-click to remove"));
        }
        inv.setItem(49, ItemBuilder.of(Material.EMERALD_BLOCK, "&aSave & publish",
                "&7Writes the plan and lists it"));
        inv.setItem(48, ItemBuilder.of(Material.WRITABLE_BOOK, "&eSave as draft"));
        inv.setItem(50, ItemBuilder.of(Material.ARROW, "&7Back"));
        player.openInventory(inv);
    }

    public void openMySubs(Player player, int page) {
        List<Subscription> list = database.subscriptionsOf(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.MY_SUBS, player.getUniqueId());
        holder.page = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title("mysubs", "&8My subscriptions"));
        holder.inventory(inv);
        fill(inv);
        int start = holder.page * 45;
        for (int i = 0; i < 45; i++) {
            int index = start + i;
            if (index >= list.size()) {
                break;
            }
            Subscription sub = list.get(index);
            holder.slotIds[i] = sub.id();
            Plan plan = database.plan(sub.planId());
            inv.setItem(i, ItemBuilder.of(statusMaterial(sub.status()),
                    "&f" + (plan == null ? sub.planId() : plan.name()),
                    "&7Id: &f" + sub.id(),
                    "&7Status: &f" + sub.status().name(),
                    "&7Policy: &f" + sub.effectivePolicy(plan).display(),
                    "&7Cycles: &f" + sub.cycles(),
                    "&7Next: &f" + TimeParser.formatRemaining(sub.nextChargeAt()),
                    "&eLeft-click &7policy  &eRight-click &7cancel",
                    "&eShift-click &7pause/resume"));
        }
        nav(inv, holder.page, list.size(), 45);
        player.openInventory(inv);
    }

    public void openMyPlans(Player player, int page) {
        List<Plan> list = database.plansByOwner(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.MY_PLANS, player.getUniqueId());
        holder.page = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title("myplans", "&8My plans"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(45, ItemBuilder.of(Material.LIME_CONCRETE, "&aCreate a plan",
                "&7Everyone can sell subscriptions"));
        int start = holder.page * 36;
        for (int i = 0; i < 36; i++) {
            int index = start + i;
            if (index >= list.size()) {
                break;
            }
            Plan plan = list.get(index);
            holder.slotIds[i] = plan.id();
            inv.setItem(i, planIcon(plan, true));
        }
        if (holder.page > 0) {
            inv.setItem(52, ItemBuilder.of(Material.ARROW, "&ePrevious"));
        }
        if ((holder.page + 1) * 36 < list.size()) {
            inv.setItem(53, ItemBuilder.of(Material.ARROW, "&eNext"));
        }
        player.openInventory(inv);
    }

    public void openInbox(Player player, int page) {
        List<InboxEntry> items = inbox.list(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.INBOX, player.getUniqueId());
        holder.page = Math.max(0, page);
        holder.inboxIds = new long[45];
        Inventory inv = Bukkit.createInventory(holder, 54, title("inbox", "&8Subscription inbox"));
        holder.inventory(inv);
        int start = holder.page * 45;
        for (int i = 0; i < 45; i++) {
            int index = start + i;
            if (index >= items.size()) {
                break;
            }
            InboxEntry entry = items.get(index);
            holder.inboxIds[i] = entry.id();
            ItemStack stack = ItemSerial.decode(entry.itemBase64());
            if (stack == null) {
                stack = ItemBuilder.of(Material.BARRIER, "&cBroken item", "&7Click to discard");
            }
            inv.setItem(i, stack);
        }
        inv.setItem(49, ItemBuilder.of(Material.HOPPER, "&aClaim all",
                items.isEmpty() ? "&7Empty" : "&7" + items.size() + " waiting"));
        nav(inv, holder.page, items.size(), 45);
        player.openInventory(inv);
    }

    public void openHistory(Player player, int page) {
        List<String> rows = database.history(player.getUniqueId(), 180);
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.HISTORY, player.getUniqueId());
        holder.page = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title("history", "&8Charge history"));
        holder.inventory(inv);
        fill(inv);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM HH:mm");
        int start = holder.page * 45;
        for (int i = 0; i < 45; i++) {
            int index = start + i;
            if (index >= rows.size()) {
                break;
            }
            String[] p = rows.get(index).split("\\|", 5);
            long time = 0L;
            try {
                time = Long.parseLong(p[0]);
            } catch (NumberFormatException ignored) {
                // leave 0
            }
            String result = p.length > 1 ? p[1] : "?";
            String amount = p.length > 2 ? p[2] : "0";
            String plan = p.length > 3 ? p[3] : "";
            String detail = p.length > 4 ? p[4] : "";
            inv.setItem(i, ItemBuilder.of(resultMaterial(result),
                    "&f" + result + " &7" + plan,
                    "&7" + fmt.format(new Date(time)),
                    "&7Amount: &e" + amount,
                    "&7" + detail));
        }
        nav(inv, holder.page, rows.size(), 45);
        player.openInventory(inv);
    }

    public void openStock(Player player, String planId) {
        Plan plan = database.plan(planId);
        if (plan == null) {
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.STOCK, player.getUniqueId());
        holder.planId = planId;
        Inventory inv = Bukkit.createInventory(holder, 54, title("stock", "&8Deposit kits"));
        holder.inventory(inv);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, ItemBuilder.pane());
        }
        inv.setItem(49, ItemBuilder.of(Material.CHEST, "&6Stocked kits: &f"
                + (plan.infiniteStock() ? "∞" : plan.stockKits()),
                "&7Drop a full kit matching the item rewards",
                "&7into the empty slots, then click here.",
                "&eEach complete kit = 1 billing cycle."));
        inv.setItem(45, ItemBuilder.of(Material.ARROW, "&7Back"));
        player.openInventory(inv);
    }

    public void openPolicy(Player player, String subscriptionId) {
        Subscription sub = database.subscription(subscriptionId);
        if (sub == null) {
            return;
        }
        Plan plan = database.plan(sub.planId());
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.POLICY, player.getUniqueId());
        holder.subscriptionId = subscriptionId;
        Inventory inv = Bukkit.createInventory(holder, 27, title("policy", "&8Charge policy"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(11, policyButton(ChargePolicy.PAUSE, sub, plan));
        inv.setItem(13, policyButton(ChargePolicy.SKIP, sub, plan));
        inv.setItem(15, policyButton(ChargePolicy.CANCEL, sub, plan));
        inv.setItem(22, ItemBuilder.of(Material.ARROW, "&7Back"));
        player.openInventory(inv);
    }

    public void openConfirm(Player player, String action, String targetId) {
        MenuHolder holder = new MenuHolder(MenuHolder.Menu.CONFIRM, player.getUniqueId());
        holder.confirmAction = action;
        holder.planId = targetId;
        holder.subscriptionId = targetId;
        Inventory inv = Bukkit.createInventory(holder, 27, title("confirm", "&8Confirm"));
        holder.inventory(inv);
        fill(inv);
        inv.setItem(11, ItemBuilder.of(Material.LIME_CONCRETE, "&aConfirm", "&7" + action));
        inv.setItem(15, ItemBuilder.of(Material.RED_CONCRETE, "&cCancel"));
        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (!holder.viewer.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (holder.menu == MenuHolder.Menu.CREATE && raw >= 54) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                addItemReward(player, clicked);
            }
            return;
        }
        if (holder.menu == MenuHolder.Menu.STOCK && raw >= 54) {
            // allow taking items from own inventory onto the cursor
            return;
        }
        if (holder.menu == MenuHolder.Menu.STOCK && raw < 45) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        if (raw < 0) {
            return;
        }
        switch (holder.menu) {
            case MAIN -> clickMain(player, raw);
            case BROWSE -> clickBrowse(player, holder, raw);
            case PLAN -> clickPlan(player, holder, raw);
            case CREATE -> clickCreate(player, holder, raw, event.getClick());
            case MY_SUBS -> clickMySubs(player, holder, raw, event.getClick());
            case MY_PLANS -> clickMyPlans(player, holder, raw);
            case INBOX -> clickInbox(player, holder, raw);
            case HISTORY -> clickHistory(player, holder, raw);
            case STOCK -> clickStock(player, holder, raw, event.getInventory());
            case POLICY -> clickPolicy(player, holder, raw);
            case CONFIRM -> clickConfirm(player, holder, raw);
        }
    }

    public void handlePrompt(Player player, ChatInput.Result result) {
        PlanDraft draft = drafts.get(player.getUniqueId());
        switch (result.kind()) {
            case SEARCH -> openBrowse(player, 0, result.value(), "ALL");
            case NAME -> {
                if (draft != null) {
                    draft.name = clip(result.value(), settings.nameMax());
                }
                openCreate(player);
            }
            case DESCRIPTION -> {
                if (draft != null) {
                    draft.description = clip(result.value(), settings.descriptionMax());
                }
                openCreate(player);
            }
            case PRICE -> {
                double parsed = MoneyParser.parse(result.value());
                if (parsed < 0d) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    if (!player.hasPermission("subscriptions.bypass.price")
                            && (parsed < settings.minPrice() || parsed > settings.maxPrice())) {
                        tell(player, Text.apply(settings.msg("plan.price-denied"), Map.of(
                                "min", Numbers.compact(settings.minPrice()),
                                "max", Numbers.compact(settings.maxPrice()))));
                    } else {
                        draft.price = parsed;
                    }
                }
                openCreate(player);
            }
            case INTERVAL -> {
                long ms = TimeParser.parseMillis(result.value());
                if (ms < 0L) {
                    tell(player, settings.msg("generic.time-invalid"));
                } else if (draft != null) {
                    if (!player.hasPermission("subscriptions.bypass.interval")
                            && (ms < settings.minIntervalMs() || ms > settings.maxIntervalMs())) {
                        tell(player, Text.apply(settings.msg("plan.interval-denied"), Map.of(
                                "min", TimeParser.format(settings.minIntervalMs()),
                                "max", TimeParser.format(settings.maxIntervalMs()))));
                    } else {
                        draft.intervalMs = ms;
                    }
                }
                openCreate(player);
            }
            case COMMAND -> {
                if (!player.hasPermission("subscriptions.plan.command")) {
                    tell(player, settings.msg("plan.command-denied"));
                } else if (draft != null) {
                    String value = result.value();
                    boolean console = value.toLowerCase().startsWith("console:");
                    String cmd = console ? value.substring(8).trim() : value;
                    addReward(player, draft, Reward.command(console, cmd));
                }
                openCreate(player);
            }
            case PERMISSION -> {
                if (!player.hasPermission("subscriptions.plan.permission")) {
                    tell(player, settings.msg("plan.permission-denied"));
                } else if (draft != null) {
                    addReward(player, draft, Reward.permission(result.value()));
                }
                openCreate(player);
            }
            case GROUP -> {
                if (!player.hasPermission("subscriptions.plan.group")) {
                    tell(player, settings.msg("plan.group-denied"));
                } else if (draft != null) {
                    addReward(player, draft, Reward.group(result.value()));
                }
                openCreate(player);
            }
            case MONEY_REWARD -> {
                double parsed = MoneyParser.parse(result.value());
                if (parsed < 0d) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    addReward(player, draft, Reward.money(parsed));
                }
                openCreate(player);
            }
            case MAX_SUBS -> {
                int n = parseInt(result.value(), -1);
                if (n < 0) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    draft.maxSubscribers = n;
                }
                openCreate(player);
            }
            case MAX_CYCLES -> {
                int n = parseInt(result.value(), -1);
                if (n < 0) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    draft.maxCycles = n;
                }
                openCreate(player);
            }
            case SIGNUP_FEE -> {
                double parsed = MoneyParser.parse(result.value());
                if (parsed < 0d) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    draft.signupFee = parsed;
                }
                openCreate(player);
            }
            case TRIAL -> {
                int n = parseInt(result.value(), -1);
                if (n < 0) {
                    tell(player, settings.msg("generic.number-invalid"));
                } else if (draft != null) {
                    draft.trialCycles = n;
                }
                openCreate(player);
            }
        }
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openBrowse(player, 0, "", "ALL");
            case 11 -> openMySubs(player, 0);
            case 12 -> openMyPlans(player, 0);
            case 13 -> openInbox(player, 0);
            case 14 -> openHistory(player, 0);
            case 16 -> {
                for (String line : settings.msgList("help")) {
                    player.sendMessage(Colors.parse(line));
                }
            }
            default -> {
            }
        }
    }

    private void clickBrowse(Player player, MenuHolder holder, int slot) {
        if (slot < 36 && holder.slotIds[slot] != null) {
            openPlan(player, holder.slotIds[slot]);
            return;
        }
        if (slot >= 45 && slot <= 50) {
            openBrowse(player, 0, holder.query, CATEGORIES[slot - 45]);
            return;
        }
        if (slot == 51) {
            chat.prompt(player, ChatInput.Kind.SEARCH, "");
            return;
        }
        if (slot == 52 && holder.page > 0) {
            openBrowse(player, holder.page - 1, holder.query, holder.category);
        } else if (slot == 53) {
            openBrowse(player, holder.page + 1, holder.query, holder.category);
        }
    }

    private void clickPlan(Player player, MenuHolder holder, int slot) {
        Plan plan = database.plan(holder.planId);
        if (plan == null) {
            return;
        }
        switch (slot) {
            case 11 -> {
                if (!player.hasPermission("subscriptions.subscribe")) {
                    tell(player, settings.msg("generic.no-permission"));
                    return;
                }
                String error = billing.subscribe(player, plan, false);
                if (error != null) {
                    tell(player, error);
                } else {
                    success(player);
                    player.closeInventory();
                }
            }
            case 15 -> openStock(player, plan.id());
            case 16 -> {
                drafts.put(player.getUniqueId(), PlanDraft.from(plan));
                openCreate(player);
            }
            case 17 -> {
                if (!plan.ownedBy(player.getUniqueId()) && !player.hasPermission("subscriptions.admin")) {
                    tell(player, settings.msg("plan.not-owner"));
                    return;
                }
                plan.status(plan.status().listed() ? PlanStatus.UNLISTED : PlanStatus.PUBLISHED);
                database.savePlan(plan);
                tell(player, settings.msg(plan.status().listed() ? "plan.published" : "plan.unpublished")
                        .replace("{name}", plan.name()).replace("{id}", plan.id()));
                openPlan(player, plan.id());
            }
            case 22 -> openMain(player);
            default -> {
            }
        }
    }

    private void clickCreate(Player player, MenuHolder holder, int slot, ClickType click) {
        PlanDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openCreate(player);
            return;
        }
        if (slot >= 37 && slot <= 45 && click.isRightClick() && holder.slotIds[slot] != null) {
            int index = parseInt(holder.slotIds[slot], -1);
            if (index >= 0 && index < draft.rewards.size()) {
                draft.rewards.remove(index);
            }
            openCreate(player);
            return;
        }
        switch (slot) {
            case 10 -> chat.prompt(player, ChatInput.Kind.NAME, "");
            case 11 -> chat.prompt(player, ChatInput.Kind.DESCRIPTION, "");
            case 12 -> chat.prompt(player, ChatInput.Kind.PRICE, "");
            case 13 -> chat.prompt(player, ChatInput.Kind.INTERVAL, "");
            case 14 -> chat.prompt(player, ChatInput.Kind.SIGNUP_FEE, "");
            case 15 -> chat.prompt(player, ChatInput.Kind.TRIAL, "");
            case 16 -> {
                draft.policy = draft.policy.next();
                openCreate(player);
            }
            case 19 -> chat.prompt(player, ChatInput.Kind.MAX_SUBS, "");
            case 20 -> chat.prompt(player, ChatInput.Kind.MAX_CYCLES, "");
            case 21 -> {
                draft.category = nextCategory(draft.category);
                openCreate(player);
            }
            case 22 -> {
                if (!player.hasPermission("subscriptions.plan.infinite")) {
                    tell(player, settings.msg("plan.infinite-denied"));
                    return;
                }
                draft.infiniteStock = !draft.infiniteStock;
                openCreate(player);
            }
            case 28 -> tell(player, "&7Click an item in your own inventory to add it as a kit reward.");
            case 29 -> {
                if (!player.hasPermission("subscriptions.plan.money")) {
                    tell(player, settings.msg("generic.no-permission"));
                    return;
                }
                chat.prompt(player, ChatInput.Kind.MONEY_REWARD, "");
            }
            case 30 -> chat.prompt(player, ChatInput.Kind.COMMAND, "");
            case 31 -> chat.prompt(player, ChatInput.Kind.PERMISSION, "");
            case 32 -> chat.prompt(player, ChatInput.Kind.GROUP, "");
            case 33 -> {
                addReward(player, draft, Reward.message("&aThanks for subscribing to {plan}!"));
                openCreate(player);
            }
            case 48 -> saveDraft(player, draft, false);
            case 49 -> saveDraft(player, draft, true);
            case 50 -> openMyPlans(player, 0);
            default -> {
            }
        }
    }

    private void clickMySubs(Player player, MenuHolder holder, int slot, ClickType click) {
        if (slot == 45 && holder.page > 0) {
            openMySubs(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            openMySubs(player, holder.page + 1);
            return;
        }
        if (slot >= 45 || holder.slotIds[slot] == null) {
            return;
        }
        Subscription sub = database.subscription(holder.slotIds[slot]);
        if (sub == null) {
            return;
        }
        Plan plan = database.plan(sub.planId());
        if (click.isShiftClick()) {
            if (!player.hasPermission("subscriptions.pause")) {
                tell(player, settings.msg("generic.no-permission"));
                return;
            }
            boolean pause = sub.status() != SubscriptionStatus.PAUSED;
            billing.pause(sub, pause);
            tell(player, Text.apply(settings.msg(pause ? "sub.paused" : "sub.resumed"),
                    Map.of("name", plan == null ? sub.planId() : plan.name())));
            openMySubs(player, holder.page);
        } else if (click.isRightClick()) {
            if (!player.hasPermission("subscriptions.cancel")) {
                tell(player, settings.msg("generic.no-permission"));
                return;
            }
            openConfirm(player, "cancel-sub", sub.id());
        } else {
            openPolicy(player, sub.id());
        }
    }

    private void clickMyPlans(Player player, MenuHolder holder, int slot) {
        if (slot == 45) {
            if (!player.hasPermission("subscriptions.create")) {
                tell(player, settings.msg("generic.no-permission"));
                return;
            }
            drafts.remove(player.getUniqueId());
            openCreate(player);
            return;
        }
        if (slot == 52 && holder.page > 0) {
            openMyPlans(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            openMyPlans(player, holder.page + 1);
            return;
        }
        if (slot < 36 && holder.slotIds[slot] != null) {
            openPlan(player, holder.slotIds[slot]);
        }
    }

    private void clickInbox(Player player, MenuHolder holder, int slot) {
        if (slot == 49) {
            int n = inbox.claimAll(player);
            tell(player, n == 0 ? settings.msg("inbox.empty") : Text.apply(settings.msg("inbox.claimed-all"), Map.of()));
            if (n > 0) {
                success(player);
            }
            openInbox(player, holder.page);
            return;
        }
        if (slot == 45 && holder.page > 0) {
            openInbox(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            openInbox(player, holder.page + 1);
            return;
        }
        if (slot >= 45 || holder.inboxIds == null || slot >= holder.inboxIds.length || holder.inboxIds[slot] == 0L) {
            return;
        }
        boolean ok = inbox.claim(player, holder.inboxIds[slot]);
        tell(player, ok ? Text.apply(settings.msg("inbox.claimed"), Map.of("amount", "1")) : settings.msg("inbox.full"));
        if (ok) {
            success(player);
        } else {
            fail(player);
        }
        openInbox(player, holder.page);
    }

    private void clickHistory(Player player, MenuHolder holder, int slot) {
        if (slot == 45 && holder.page > 0) {
            openHistory(player, holder.page - 1);
        } else if (slot == 53) {
            openHistory(player, holder.page + 1);
        }
    }

    private void clickStock(Player player, MenuHolder holder, int slot, Inventory inventory) {
        if (slot == 45) {
            collectStock(player, holder, inventory, false);
            openPlan(player, holder.planId);
            return;
        }
        if (slot != 49) {
            return;
        }
        collectStock(player, holder, inventory, true);
        openStock(player, holder.planId);
    }

    private void collectStock(Player player, MenuHolder holder, Inventory inventory, boolean deposit) {
        Plan plan = database.plan(holder.planId);
        if (plan == null) {
            return;
        }
        if (!plan.ownedBy(player.getUniqueId()) && !player.hasPermission("subscriptions.admin")) {
            tell(player, settings.msg("plan.not-owner"));
            return;
        }
        if (!deposit) {
            return;
        }
        int kits = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (settings.blacklist().contains(item.getType())) {
                tell(player, settings.msg("plan.blacklisted"));
                giveBack(player, item);
                inventory.setItem(i, null);
                continue;
            }
            kits++;
            if (plan.rewards().stream().noneMatch(r -> r.type().name().equals("ITEM"))) {
                plan.rewards().add(Reward.item(ItemSerial.encode(item), item.getAmount()));
            }
            inventory.setItem(i, null);
        }
        if (kits > 0) {
            plan.addKits(kits);
            database.savePlan(plan);
            tell(player, Text.apply(settings.msg("plan.stock-added"), Map.of(
                    "amount", String.valueOf(kits),
                    "stock", plan.infiniteStock() ? "∞" : String.valueOf(plan.stockKits()))));
            success(player);
            billing.tryResumePaused();
        }
    }

    private void clickPolicy(Player player, MenuHolder holder, int slot) {
        Subscription sub = database.subscription(holder.subscriptionId);
        if (sub == null) {
            return;
        }
        ChargePolicy policy = switch (slot) {
            case 11 -> ChargePolicy.PAUSE;
            case 13 -> ChargePolicy.SKIP;
            case 15 -> ChargePolicy.CANCEL;
            case 22 -> {
                openMySubs(player, 0);
                yield null;
            }
            default -> null;
        };
        if (policy == null) {
            return;
        }
        if (!player.hasPermission("subscriptions.policy")) {
            tell(player, settings.msg("generic.no-permission"));
            return;
        }
        billing.setPolicy(sub, policy);
        Plan plan = database.plan(sub.planId());
        tell(player, Text.apply(settings.msg("sub.policy"), Map.of(
                "name", plan == null ? sub.planId() : plan.name(),
                "policy", policy.display())));
        openPolicy(player, sub.id());
    }

    private void clickConfirm(Player player, MenuHolder holder, int slot) {
        if (slot == 15) {
            openMain(player);
            return;
        }
        if (slot != 11) {
            return;
        }
        if ("cancel-sub".equals(holder.confirmAction)) {
            Subscription sub = database.subscription(holder.subscriptionId);
            String error = billing.cancel(sub, "Cancelled by player", false);
            if (error != null) {
                tell(player, error);
            }
            openMySubs(player, 0);
        }
    }

    private void saveDraft(Player player, PlanDraft draft, boolean publish) {
        if (draft.rewards.isEmpty() && publish) {
            tell(player, settings.msg("plan.no-rewards"));
            return;
        }
        if (!player.hasPermission("subscriptions.bypass.limit")
                && draft.editingPlanId == null
                && database.plansByOwner(player.getUniqueId()).size() >= settings.maxPlansPerPlayer()) {
            tell(player, Text.apply(settings.msg("plan.too-many-plans"),
                    Map.of("max", String.valueOf(settings.maxPlansPerPlayer()))));
            return;
        }
        Plan plan = draft.editingPlanId == null ? new Plan(Ids.planId(draft.name)) : database.plan(draft.editingPlanId);
        if (plan == null) {
            plan = new Plan(Ids.planId(draft.name));
        }
        boolean created = draft.editingPlanId == null;
        draft.applyTo(plan);
        plan.ownerId(player.getUniqueId());
        plan.ownerName(player.getName());
        if (publish) {
            plan.status(PlanStatus.PUBLISHED);
        } else if (plan.status() == PlanStatus.PUBLISHED) {
            // keep published if they only saved
        } else {
            plan.status(PlanStatus.DRAFT);
        }
        database.savePlan(plan);
        if (created) {
            Bukkit.getPluginManager().callEvent(new dev.superseller.subscriptions.api.event.PlanCreateEvent(plan));
            tell(player, Text.apply(settings.msg("plan.created"), Map.of("id", plan.id(), "name", plan.name())));
        }
        if (publish) {
            tell(player, Text.apply(settings.msg("plan.published"), Map.of("name", plan.name(), "id", plan.id())));
        }
        drafts.remove(player.getUniqueId());
        success(player);
        openPlan(player, plan.id());
    }

    private void addItemReward(Player player, ItemStack stack) {
        PlanDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        if (!player.hasPermission("subscriptions.plan.items")) {
            tell(player, settings.msg("generic.no-permission"));
            return;
        }
        if (settings.blacklist().contains(stack.getType())) {
            tell(player, settings.msg("plan.blacklisted"));
            return;
        }
        addReward(player, draft, Reward.item(ItemSerial.encode(stack), stack.getAmount()));
        draft.icon = stack.getType().name();
        openCreate(player);
    }

    private void addReward(Player player, PlanDraft draft, Reward reward) {
        if (draft.rewards.size() >= settings.maxRewardsPerPlan()) {
            tell(player, settings.msg("plan.too-many-rewards"));
            return;
        }
        draft.rewards.add(reward);
    }

    private ItemStack planIcon(Plan plan, boolean detailed) {
        List<String> lore = new ArrayList<>();
        lore.add("&7by &f" + plan.ownerName());
        lore.add("&7" + plan.category() + " &8• &7" + plan.status().name());
        lore.add("&7Price: &e" + economy.format(plan.price()) + " &7/ &f" + TimeParser.format(plan.intervalMs()));
        if (plan.signupFee() > 0d) {
            lore.add("&7Signup: &e" + economy.format(plan.signupFee()));
        }
        if (plan.trialCycles() > 0) {
            lore.add("&7Trial: &f" + plan.trialCycles() + " cycle(s)");
        }
        lore.add("&7Subscribers: &f" + plan.subscriberCount()
                + (plan.maxSubscribers() > 0 ? "&7/&f" + plan.maxSubscribers() : ""));
        lore.add("&7Stock: &f" + (plan.infiniteStock() ? "∞" : plan.stockKits()));
        lore.add("&7Policy: &f" + plan.policy().display());
        if (!plan.description().isBlank()) {
            lore.add("&8" + plan.description());
        }
        if (detailed) {
            lore.add("&7Id: &f" + plan.id());
            lore.addAll(rewardLore(plan));
        } else {
            lore.add("&eClick to view");
        }
        return ItemBuilder.of(ItemBuilder.material(plan.icon(), Material.CHEST), "&f" + plan.name(), lore);
    }

    private List<String> rewardLore(Plan plan) {
        List<String> lore = new ArrayList<>();
        if (plan.rewards().isEmpty()) {
            lore.add("&cNo rewards yet");
            return lore;
        }
        for (Reward reward : plan.rewards()) {
            lore.add("&7- &f" + reward.describe());
        }
        return lore;
    }

    private ItemStack policyButton(ChargePolicy policy, Subscription sub, Plan plan) {
        boolean selected = sub.effectivePolicy(plan) == policy;
        return ItemBuilder.of(selected ? Material.LIME_CONCRETE : policyMaterial(policy),
                (selected ? "&a" : "&7") + policy.display(),
                "&7Applies only to this subscription",
                "&7when a cycle cannot be fulfilled.");
    }

    private static Material policyMaterial(ChargePolicy policy) {
        return switch (policy) {
            case PAUSE -> Material.YELLOW_CONCRETE;
            case SKIP -> Material.ORANGE_CONCRETE;
            case CANCEL -> Material.RED_CONCRETE;
        };
    }

    private static Material statusMaterial(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> Material.LIME_DYE;
            case PAUSED -> Material.GRAY_DYE;
            case PAUSED_STOCK -> Material.ORANGE_DYE;
            case PAUSED_FUNDS -> Material.RED_DYE;
            case CANCELLED -> Material.BARRIER;
            case EXPIRED -> Material.CLOCK;
        };
    }

    private static Material resultMaterial(String result) {
        return switch (result) {
            case "CHARGE", "TRIAL" -> Material.EMERALD;
            case "SKIP" -> Material.ORANGE_DYE;
            case "PAUSE" -> Material.YELLOW_DYE;
            default -> Material.BARRIER;
        };
    }

    private static Material rewardIcon(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> Material.CHEST;
            case MONEY -> Material.GOLD_INGOT;
            case COMMAND -> Material.COMMAND_BLOCK;
            case PERMISSION -> Material.NAME_TAG;
            case GROUP -> Material.TOTEM_OF_UNDYING;
            case MESSAGE -> Material.BIRCH_SIGN;
        };
    }

    private void fill(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.pane());
            }
        }
    }

    private void nav(Inventory inv, int page, int total, int perPage) {
        if (page > 0) {
            inv.setItem(45, ItemBuilder.of(Material.ARROW, "&ePrevious page"));
        }
        if ((page + 1) * perPage < total) {
            inv.setItem(53, ItemBuilder.of(Material.ARROW, "&eNext page"));
        }
    }

    private String title(String key, String fallback) {
        return Colors.parse(settings.menuTitle(key, fallback));
    }

    private void tell(Player player, String message) {
        player.sendMessage(Colors.parse(settings.prefix() + message));
    }

    private void clickSound(Player player) {
        sound(player, "click", Sound.UI_BUTTON_CLICK);
    }

    private void success(Player player) {
        sound(player, "success", Sound.ENTITY_PLAYER_LEVELUP);
    }

    private void fail(Player player) {
        sound(player, "fail", Sound.ENTITY_VILLAGER_NO);
    }

    private void sound(Player player, String key, Sound fallback) {
        if (!settings.sounds()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(settings.raw().getString("sounds." + key, fallback.name()));
            player.playSound(player.getLocation(), sound, 0.7f, 1f);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), fallback, 0.7f, 1f);
        }
    }

    private void giveBack(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String nextCategory(String current) {
        for (int i = 1; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(current)) {
                return CATEGORIES[i + 1 == CATEGORIES.length ? 1 : i + 1];
            }
        }
        return "CUSTOM";
    }

    public void clear(UUID uuid) {
        drafts.remove(uuid);
        chat.clear(uuid);
    }
}
