package dev.superseller.gifty;

import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.economy.EconomyHook;
import dev.superseller.gifty.gui.GiftyHolder;
import dev.superseller.gifty.input.ChatInputManager;
import dev.superseller.gifty.model.Delivery;
import dev.superseller.gifty.model.Inbox;
import dev.superseller.gifty.model.SerialItem;
import dev.superseller.gifty.scheduler.PlatformScheduler;
import dev.superseller.gifty.storage.FileStorage;
import dev.superseller.gifty.util.Colors;
import dev.superseller.gifty.util.ItemBuilder;
import dev.superseller.gifty.util.Numbers;
import dev.superseller.gifty.util.Sounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Owns all GUI sessions: compose -> confirm -> send, the inbox viewer and
 * claiming. Methods touching players/worlds must run on the player's owning
 * thread (they are called from GUI events, commands and entity-scheduled
 * tasks, which is exactly that).
 */
public final class GuiSessionManager {

    private final GiftyConfig config;
    private final FileStorage storage;
    private final EconomyHook economy;
    private final ChatInputManager chatInput;

    private static final class Draft {
        UUID targetId;
        String targetName;
        double money;
        String message; // raw text, color-translated on render
        List<SerialItem> items = new ArrayList<>();
    }

    private static final class Session {
        GiftyHolder holder;
        Draft draft;
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();

    public GuiSessionManager(GiftyConfig config, FileStorage storage, EconomyHook economy,
                             ChatInputManager chatInput) {
        this.config = config;
        this.storage = storage;
        this.economy = economy;
        this.chatInput = chatInput;
    }

    // ================================================================ compose

    public void openCompose(Player sender, OfflinePlayer target) {
        Session existing = sessions.get(sender.getUniqueId());
        if (existing != null && existing.holder != null && !existing.holder.done) {
            if (existing.holder.kind == GiftyHolder.Kind.INBOX) {
                // harmless to close — no items can be lost
                existing.holder.done = true;
                sender.closeInventory();
                sessions.remove(sender.getUniqueId());
            } else {
                sender.sendMessage(config.format("send.session-open"));
                return;
            }
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(config.format("send.self"));
            return;
        }
        int slots = config.sendSlots();
        int rows = Math.min(6, Math.max(1, (slots + 8) / 9));
        GiftyHolder holder = new GiftyHolder(GiftyHolder.Kind.COMPOSE, sender.getUniqueId(),
                target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Colors.parse(config.msg("gui.compose-title", "player", target.getName())));
        holder.setInventory(inv);

        for (int i = slots; i < inv.getSize(); i++) {
            inv.setItem(i, backdrop());
        }
        int hintSlot = slots;
        if (hintSlot < inv.getSize()) {
            inv.setItem(hintSlot, ItemBuilder.of(Material.OAK_SIGN, config.msg("gui.compose-hint-name"),
                    split(config.msg("gui.compose-hint-lore"))));
        }

        Draft draft = new Draft();
        draft.targetId = target.getUniqueId();
        draft.targetName = target.getName();
        Session session = new Session();
        session.holder = holder;
        session.draft = draft;
        sessions.put(sender.getUniqueId(), session);
        sender.openInventory(inv);
        if (config.playSounds()) {
            Sounds.click(sender);
        }
    }

    /** Compose inventory closed by the player: move contents into the draft. */
    public void onComposeClose(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.holder == null || session.holder.kind != GiftyHolder.Kind.COMPOSE) {
            return;
        }
        GiftyHolder holder = session.holder;
        if (holder.done) {
            return;
        }
        Inventory inv = holder.getInventory();
        List<SerialItem> items = new ArrayList<>();
        for (int i = 0; i < config.sendSlots() && i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(SerialItem.fromItemStack(stack));
                inv.setItem(i, null);
            }
        }
        holder.done = true;
        if (items.isEmpty() && session.draft.money <= 0 && session.draft.message == null) {
            sessions.remove(player.getUniqueId());
            return;
        }
        session.draft.items = items;
        // open on the next tick — safest way to open a GUI from a close event
        PlatformScheduler.runEntitySync(player, () -> openConfirm(player));
    }

    // ================================================================ confirm

    public void openConfirm(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.draft == null) {
            return;
        }
        Draft draft = session.draft;
        GiftyHolder holder = new GiftyHolder(GiftyHolder.Kind.CONFIRM, player.getUniqueId(),
                draft.targetId, draft.targetName);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Colors.parse(config.msg("gui.confirm-title", "player", draft.targetName)));
        holder.setInventory(inv);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, backdrop());
        }
        int itemCount = 0;
        for (SerialItem s : draft.items) {
            itemCount += s.amount();
        }
        List<String> itemLore = new ArrayList<>();
        itemLore.add(config.msg("gui.confirm-items-count", "count", Numbers.format(itemCount)));
        inv.setItem(0, ItemBuilder.of(Material.CHEST, config.msg("gui.confirm-items-name"), itemLore));

        List<String> moneyLore = new ArrayList<>();
        moneyLore.add(config.msg("gui.confirm-money-value", "money", economy.format(draft.money)));
        moneyLore.add(config.msg("gui.confirm-money-hint"));
        inv.setItem(2, ItemBuilder.of(Material.EMERALD, config.msg("gui.confirm-money-name"), moneyLore));

        List<String> msgLore = new ArrayList<>();
        msgLore.add(draft.message == null
                ? config.msg("gui.confirm-message-none")
                : config.msg("gui.confirm-message-set", "message", preview(draft.message)));
        msgLore.add(config.msg("gui.confirm-message-hint"));
        inv.setItem(4, ItemBuilder.of(Material.WRITABLE_BOOK, config.msg("gui.confirm-message-name"), msgLore));

        inv.setItem(6, ItemBuilder.of(Material.BARRIER, config.msg("gui.confirm-cancel-name"),
                split(config.msg("gui.confirm-cancel-lore"))));
        inv.setItem(8, ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE, config.msg("gui.confirm-send-name"),
                split(config.msg("gui.confirm-send-lore"))));

        session.holder = holder;
        player.openInventory(inv);
        if (config.playSounds()) {
            Sounds.click(player);
        }
    }

    public void onConfirmClose(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.holder == null || session.holder.kind != GiftyHolder.Kind.CONFIRM) {
            return;
        }
        if (session.holder.done) {
            return;
        }
        abandonConfirm(player);
    }

    /** Cancels the draft: returns items and clears the session. */
    public void abandonConfirm(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        returnDraftItems(player, session);
        sessions.remove(player.getUniqueId());
        player.sendMessage(config.format("gui.confirm-abandoned"));
    }

    // ================================================================ prompts

    public void handlePromptResult(Player player, ChatInputManager.Kind kind, UUID targetId, String input) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.draft == null) {
            return;
        }
        Draft draft = session.draft;
        if (kind == ChatInputManager.Kind.MONEY) {
            double amount;
            try {
                amount = Double.parseDouble(input.replace(",", ".").replace("$", "").trim());
            } catch (NumberFormatException e) {
                player.sendMessage(config.format("prompt.invalid"));
                return;
            }
            if (amount < 0) {
                player.sendMessage(config.format("prompt.invalid"));
                return;
            }
            amount = Math.floor(amount * 100) / 100d;
            draft.money = amount;
        } else {
            if (input.equalsIgnoreCase("none") || input.equalsIgnoreCase("remove")) {
                draft.message = null;
            } else {
                if (input.length() > config.maxMessageLength()) {
                    input = input.substring(0, config.maxMessageLength());
                }
                draft.message = input;
            }
        }
        openConfirm(player);
    }

    // ================================================================ send

    public void sendGift(Player sender) {
        Session session = sessions.get(sender.getUniqueId());
        if (session == null || session.draft == null) {
            return;
        }
        Draft draft = session.draft;

        if (draft.items.isEmpty() && draft.money <= 0 && draft.message == null) {
            sender.sendMessage(config.format("send.no-content"));
            if (config.playSounds()) {
                Sounds.error(sender);
            }
            return;
        }
        if (draft.money > 0) {
            if (!sender.hasPermission("gifty.money")) {
                sender.sendMessage(config.format("send.no-money-perm"));
                return;
            }
            if (!economy.isEnabled()) {
                sender.sendMessage(config.format("send.money-disabled"));
                return;
            }
            if (economy.balance(sender) < draft.money) {
                sender.sendMessage(config.format("send.insufficient", "balance", economy.format(economy.balance(sender))));
                if (config.playSounds()) {
                    Sounds.error(sender);
                }
                return;
            }
        }
        if (draft.message != null && !sender.hasPermission("gifty.message")) {
            sender.sendMessage(config.format("send.no-message-perm"));
            return;
        }
        java.util.List<String> blacklist = config.blacklistedMaterials();
        if (!blacklist.isEmpty()) {
            for (SerialItem item : draft.items) {
                if (blacklist.contains(item.material().toUpperCase())) {
                    sender.sendMessage(config.format("send.blacklisted", "item", item.material()));
                    if (config.playSounds()) {
                        Sounds.error(sender);
                    }
                    return;
                }
            }
        }

        long cooldown = config.cooldownSeconds();
        if (cooldown > 0 && !sender.hasPermission("gifty.cooldown.bypass")) {
            long remaining = cooldownLeft(sender.getUniqueId());
            if (remaining > 0) {
                sender.sendMessage(config.format("send.cooldown", "time", Numbers.duration(remaining)));
                if (config.playSounds()) {
                    Sounds.error(sender);
                }
                return;
            }
        }

        UUID targetId = draft.targetId;
        int pending = storage.pendingCount(targetId);
        if (pending >= config.maxPendingPerPlayer() && !sender.hasPermission("gifty.full.bypass")) {
            sender.sendMessage(config.format("send.full", "player", draft.targetName));
            if (config.playSounds()) {
                Sounds.error(sender);
            }
            return;
        }

        if (draft.money > 0 && !economy.withdraw(sender, draft.money)) {
            sender.sendMessage(config.format("send.insufficient", "balance", economy.format(economy.balance(sender))));
            if (config.playSounds()) {
                Sounds.error(sender);
            }
            return;
        }

        Delivery delivery = new Delivery(storage.nextId(), sender.getUniqueId(), sender.getName(),
                System.currentTimeMillis(), draft.message, draft.money, draft.items);
        Inbox inbox = storage.loadInbox(targetId);
        inbox.add(delivery);
        storage.markDirty(targetId);
        storage.saveInbox(targetId);
        storage.bumpSent(sender.getUniqueId(), 1);
        storage.bumpReceived(targetId, 1);
        lastSend.put(sender.getUniqueId(), System.currentTimeMillis());

        if (session.holder != null) {
            session.holder.done = true;
        }
        sessions.remove(sender.getUniqueId());
        sender.closeInventory();
        sender.sendMessage(config.format("send.success", "player", draft.targetName));
        if (config.playSounds()) {
            Sounds.success(sender);
        }

        Player recipient = Bukkit.getPlayer(targetId);
        if (recipient != null && recipient.isOnline()) {
            if (config.notifyOnSend()) {
                recipient.sendMessage(config.format("send.notify", "player", sender.getName()));
            }
            if (config.playSounds()) {
                Sounds.notify(recipient);
            }
        }
    }

    // ================================================================ inbox

    public void openInbox(Player viewer, int page, boolean readOnly) {
        protectSession(viewer);
        int pages = config.inboxPages();
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        Inbox inbox = storage.loadInbox(viewer.getUniqueId());

        GiftyHolder holder = new GiftyHolder(GiftyHolder.Kind.INBOX, viewer.getUniqueId(),
                viewer.getUniqueId(), viewer.getName());
        holder.page = page;
        holder.readOnly = readOnly;
        Inventory inv = Bukkit.createInventory(holder, 54,
                Colors.parse(config.msg("gui.inbox-title", "page", page, "pages", pages)));
        holder.setInventory(inv);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, backdrop());
        }

        List<Delivery> deliveries = inbox.list();
        if (deliveries.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.PAPER, config.msg("gui.inbox-empty-name"),
                    split(config.msg("gui.inbox-empty-lore"))));
            inv.setItem(53, ItemBuilder.of(Material.BARRIER, config.msg("gui.close-name")));
        } else {
            int perPage = 45;
            int start = (page - 1) * perPage;
            long[] ids = new long[45];
            for (int i = 0; i < perPage; i++) {
                int idx = start + i;
                if (idx >= deliveries.size()) {
                    break;
                }
                Delivery d = deliveries.get(idx);
                ids[i] = d.id();
                inv.setItem(i, deliveryIcon(d));
            }
            holder.deliveryIds = ids;
            inv.setItem(45, ItemBuilder.of(Material.ARROW, config.msg("gui.page-prev-name"),
                    split(config.msg("gui.page-prev-lore"))));
            inv.setItem(47, ItemBuilder.of(Material.PAPER,
                    config.msg("gui.page-info-name", "page", page, "pages", pages),
                    split(config.msg("gui.page-info-lore"))));
            inv.setItem(49, ItemBuilder.of(Material.EMERALD, config.msg("gui.claim-all-money-name"),
                    split(config.msg("gui.claim-all-money-lore", "money", economy.format(inbox.totalMoney())))));
            inv.setItem(51, ItemBuilder.of(Material.ARROW, config.msg("gui.page-next-name"),
                    split(config.msg("gui.page-next-lore"))));
            inv.setItem(53, ItemBuilder.of(Material.BARRIER, config.msg("gui.close-name")));
        }

        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            session = new Session();
            sessions.put(viewer.getUniqueId(), session);
        }
        session.holder = holder;
        viewer.openInventory(inv);
        if (config.playSounds()) {
            Sounds.click(viewer);
        }
    }

    private ItemStack deliveryIcon(Delivery d) {
        Material icon = Material.PAPER;
        if (d.hasItems()) {
            try {
                icon = Material.valueOf(d.items().get(0).material());
            } catch (IllegalArgumentException ignored) {
                icon = Material.PAPER;
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add(config.msg("gui.delivery-from", "player", d.fromName()));
        lore.add(config.msg("gui.delivery-date", "date", Numbers.date(d.sentAt())));
        if (d.hasItems()) {
            lore.add(config.msg("gui.delivery-items", "count", Numbers.format(d.totalItems())));
            int shown = 0;
            for (SerialItem item : d.items()) {
                if (shown >= 3) {
                    lore.add(config.msg("gui.delivery-more", "count", d.items().size() - 3));
                    break;
                }
                lore.add(config.msg("gui.delivery-item-line", "type", friendly(item.material()), "amount", item.amount()));
                shown++;
            }
        }
        if (d.hasMoney()) {
            lore.add(config.msg("gui.delivery-money", "money", economy.format(d.money())));
        }
        if (d.message() != null) {
            lore.add(config.msg("gui.delivery-message-header"));
            for (String line : split(preview(d.message()))) {
                lore.add(config.msg("gui.delivery-message-line", "line", line));
            }
        }
        lore.add("");
        lore.add(config.msg("gui.delivery-hint-left"));
        lore.add(config.msg("gui.delivery-hint-right"));
        lore.add(config.msg("gui.delivery-hint-shift"));
        return ItemBuilder.of(icon, config.msg("gui.delivery-name", "id", d.id(), "player", d.fromName()), lore);
    }

    // ================================================================ claim

    public void claimItems(Player player, long deliveryId) {
        Inbox inbox = storage.loadInbox(player.getUniqueId());
        Delivery d = inbox.get(deliveryId);
        if (d == null) {
            return;
        }
        if (!d.hasItems()) {
            player.sendMessage(config.format("inbox.nothing"));
            if (config.playSounds()) {
                Sounds.error(player);
            }
            return;
        }
        for (SerialItem item : d.items()) {
            giveItem(player, item.toItemStack());
        }
        if (d.hasMoney()) {
            // keep the money part of the delivery
            inbox.remove(deliveryId);
            inbox.add(new Delivery(d.id(), d.fromUuid(), d.fromName(), d.sentAt(), d.message(), d.money(),
                    new ArrayList<SerialItem>()));
        } else {
            inbox.remove(deliveryId);
        }
        storage.markDirty(player.getUniqueId());
        storage.saveInbox(player.getUniqueId());
        player.sendMessage(config.format("inbox.claim-items", "id", deliveryId));
        if (config.playSounds()) {
            Sounds.success(player);
        }
        refreshInbox(player);
    }

    public void claimMoney(Player player, long deliveryId) {
        Inbox inbox = storage.loadInbox(player.getUniqueId());
        Delivery d = inbox.get(deliveryId);
        if (d == null) {
            return;
        }
        if (!d.hasMoney()) {
            player.sendMessage(config.format("inbox.nothing"));
            if (config.playSounds()) {
                Sounds.error(player);
            }
            return;
        }
        if (economy.isEnabled()) {
            economy.deposit(player, d.money());
        }
        if (d.hasItems()) {
            inbox.remove(deliveryId);
            inbox.add(new Delivery(d.id(), d.fromUuid(), d.fromName(), d.sentAt(), d.message(), 0d, d.items()));
        } else {
            inbox.remove(deliveryId);
        }
        storage.markDirty(player.getUniqueId());
        storage.saveInbox(player.getUniqueId());
        player.sendMessage(config.format("inbox.claim-money", "id", deliveryId));
        if (config.playSounds()) {
            Sounds.success(player);
        }
        refreshInbox(player);
    }

    public void claimAll(Player player, long deliveryId) {
        Inbox inbox = storage.loadInbox(player.getUniqueId());
        Delivery d = inbox.get(deliveryId);
        if (d == null) {
            return;
        }
        boolean any = false;
        if (d.hasItems()) {
            for (SerialItem item : d.items()) {
                giveItem(player, item.toItemStack());
            }
            any = true;
        }
        if (d.hasMoney() && economy.isEnabled()) {
            economy.deposit(player, d.money());
            any = true;
        }
        if (!any) {
            player.sendMessage(config.format("inbox.nothing"));
            return;
        }
        inbox.remove(deliveryId);
        storage.markDirty(player.getUniqueId());
        storage.saveInbox(player.getUniqueId());
        player.sendMessage(config.format("inbox.claim-all", "id", deliveryId));
        if (config.playSounds()) {
            Sounds.success(player);
        }
        refreshInbox(player);
    }

    public void claimAllMoney(Player player) {
        Inbox inbox = storage.loadInbox(player.getUniqueId());
        double total = 0;
        List<Long> toRemove = new ArrayList<>();
        List<Delivery> keep = new ArrayList<>();
        for (Delivery d : inbox.list()) {
            if (d.hasMoney()) {
                total += d.money();
                if (d.hasItems()) {
                    keep.add(new Delivery(d.id(), d.fromUuid(), d.fromName(), d.sentAt(), d.message(), 0d, d.items()));
                }
                toRemove.add(d.id());
            }
        }
        if (total <= 0) {
            player.sendMessage(config.format("inbox.nothing"));
            if (config.playSounds()) {
                Sounds.error(player);
            }
            return;
        }
        if (economy.isEnabled()) {
            economy.deposit(player, total);
        }
        for (long id : toRemove) {
            inbox.remove(id);
        }
        for (Delivery d : keep) {
            inbox.add(d);
        }
        storage.markDirty(player.getUniqueId());
        storage.saveInbox(player.getUniqueId());
        player.sendMessage(config.format("inbox.claim-all-money", "money", economy.format(total)));
        if (config.playSounds()) {
            Sounds.success(player);
        }
        refreshInbox(player);
    }

    private void refreshInbox(Player player) {
        Session session = sessions.get(player.getUniqueId());
        int page = 1;
        if (session != null && session.holder != null && session.holder.kind == GiftyHolder.Kind.INBOX) {
            page = session.holder.page;
            session.holder.done = true;
        }
        final int targetPage = page;
        player.closeInventory();
        PlatformScheduler.runEntitySync(player, () -> openInbox(player, targetPage, false));
    }

    // ================================================================ admin

    public void openInboxForAdmin(Player admin, UUID ownerId, int page) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        closeAny(admin);
        int pages = config.inboxPages();
        final int p = Math.max(1, Math.min(page, pages));
        PlatformScheduler.runEntitySync(admin, () -> openInboxAs(admin, owner, p));
    }

    private void openInboxAs(Player admin, OfflinePlayer owner, int page) {
        int pages = config.inboxPages();
        Inbox inbox = storage.loadInbox(owner.getUniqueId());
        GiftyHolder holder = new GiftyHolder(GiftyHolder.Kind.INBOX, admin.getUniqueId(),
                owner.getUniqueId(), owner.getName());
        holder.page = page;
        holder.readOnly = true;
        Inventory inv = Bukkit.createInventory(holder, 54,
                Colors.parse(config.msg("gui.inbox-title-other", "player", owner.getName(),
                        "page", page, "pages", pages)));
        holder.setInventory(inv);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, backdrop());
        }
        List<Delivery> deliveries = inbox.list();
        if (deliveries.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.PAPER, config.msg("gui.inbox-empty-name"),
                    split(config.msg("gui.inbox-empty-lore"))));
        } else {
            int start = (page - 1) * 45;
            long[] ids = new long[45];
            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= deliveries.size()) {
                    break;
                }
                ids[i] = deliveries.get(idx).id();
                inv.setItem(i, deliveryIcon(deliveries.get(idx)));
            }
            holder.deliveryIds = ids;
            inv.setItem(45, ItemBuilder.of(Material.ARROW, config.msg("gui.page-prev-name"),
                    split(config.msg("gui.page-prev-lore"))));
            inv.setItem(47, ItemBuilder.of(Material.PAPER,
                    config.msg("gui.page-info-name", "page", page, "pages", pages),
                    split(config.msg("gui.page-info-lore"))));
            inv.setItem(51, ItemBuilder.of(Material.ARROW, config.msg("gui.page-next-name"),
                    split(config.msg("gui.page-next-lore"))));
        }
        inv.setItem(53, ItemBuilder.of(Material.BARRIER, config.msg("gui.close-name")));
        Session session = new Session();
        session.holder = holder;
        sessions.put(admin.getUniqueId(), session);
        admin.openInventory(inv);
    }

    public int clearInbox(UUID ownerId) {
        Inbox inbox = storage.loadInbox(ownerId);
        int count = inbox.size();
        for (Delivery d : inbox.list()) {
            inbox.remove(d.id());
        }
        storage.markDirty(ownerId);
        storage.saveInbox(ownerId);
        return count;
    }

    /** Refunds every pending delivery of a player back to its sender. */
    public int refundInbox(UUID ownerId) {
        Inbox inbox = storage.loadInbox(ownerId);
        List<Delivery> snapshot = inbox.list();
        int count = 0;
        for (Delivery d : snapshot) {
            inbox.remove(d.id());
            if (d.fromUuid() == null) {
                continue;
            }
            Inbox senderInbox = storage.loadInbox(d.fromUuid());
            Delivery refund = new Delivery(storage.nextId(), null, "Gifty",
                    System.currentTimeMillis(),
                    config.msg("admin.refund-message", "id", d.id(), "player", inboxOwnerName(ownerId)),
                    d.money(), d.items());
            senderInbox.add(refund);
            storage.markDirty(d.fromUuid());
            storage.saveInbox(d.fromUuid());
            Player sender = Bukkit.getPlayer(d.fromUuid());
            if (sender != null && sender.isOnline()) {
                sender.sendMessage(config.format("admin.refunded-notify", "player", inboxOwnerName(ownerId)));
            }
            count++;
        }
        storage.markDirty(ownerId);
        storage.saveInbox(ownerId);
        return count;
    }

    private String inboxOwnerName(UUID ownerId) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(ownerId);
        String name = p.getName();
        return name == null ? ownerId.toString().substring(0, 8) : name;
    }

    // ================================================================ session lifecycle

    public GiftyHolder currentHolder(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s == null ? null : s.holder;
    }

    /** Closes whatever GUI the player has open without side effects. */
    public void closeAny(Player player) {
        protectSession(player);
    }

    /**
     * Closes the player's current GUI while protecting any in-progress
     * draft: compose items and unsent confirm items are handed back.
     */
    private void protectSession(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.holder == null || s.holder.done) {
            return;
        }
        GiftyHolder h = s.holder;
        h.done = true;
        if (h.kind == GiftyHolder.Kind.COMPOSE) {
            Inventory inv = h.getInventory();
            List<SerialItem> items = new ArrayList<>();
            for (int i = 0; i < config.sendSlots() && i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && stack.getType() != Material.AIR) {
                    items.add(SerialItem.fromItemStack(stack));
                }
            }
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            for (SerialItem item : items) {
                giveItem(player, item.toItemStack());
            }
            if (!items.isEmpty()) {
                player.sendMessage(config.format("inbox.returned"));
            }
        } else if (h.kind == GiftyHolder.Kind.CONFIRM) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            returnDraftItems(player, s);
        } else {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    public void onInboxClose(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.holder != null && s.holder.kind == GiftyHolder.Kind.INBOX && !s.holder.done) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void onQuit(Player player) {
        chatInput.clear(player.getUniqueId());
        Session s = sessions.remove(player.getUniqueId());
        if (s == null || s.holder == null || s.holder.done) {
            return;
        }
        GiftyHolder holder = s.holder;
        List<SerialItem> items = new ArrayList<>();
        if (holder.kind == GiftyHolder.Kind.COMPOSE) {
            Inventory inv = holder.getInventory();
            for (int i = 0; i < config.sendSlots() && i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && stack.getType() != Material.AIR) {
                    items.add(SerialItem.fromItemStack(stack));
                }
            }
        } else if (holder.kind == GiftyHolder.Kind.CONFIRM && s.draft != null) {
            items.addAll(s.draft.items);
        }
        if (!items.isEmpty()) {
            storage.queueReturn(player.getUniqueId(), items);
        }
    }

    public void onJoin(Player player) {
        List<SerialItem> returned = storage.takeReturns(player.getUniqueId());
        for (SerialItem item : returned) {
            giveItem(player, item.toItemStack());
        }
        if (!returned.isEmpty()) {
            player.sendMessage(config.format("inbox.returned"));
        }
        int pending = storage.pendingCount(player.getUniqueId());
        if (pending > 0) {
            player.sendMessage(config.format("inbox.notify-join", "count", pending));
        }
    }

    // ================================================================ expiry

    /** Data-only scan of loaded inboxes, safe from the global timer thread. */
    public void scanExpiry() {
        long hours = config.expireHours();
        if (hours <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - hours * 3600_000L;
        for (UUID owner : storage.cachedOwners()) {
            Inbox inbox = storage.loadInbox(owner);
            boolean changed = false;
            for (Delivery d : inbox.list()) {
                if (d.sentAt() < cutoff) {
                    inbox.remove(d.id());
                    refundSingle(owner, d);
                    changed = true;
                }
            }
            if (changed) {
                storage.markDirty(owner);
                storage.saveInbox(owner);
            }
        }
    }

    private void refundSingle(UUID originalOwner, Delivery d) {
        if (d.fromUuid() == null) {
            return;
        }
        Inbox senderInbox = storage.loadInbox(d.fromUuid());
        Delivery refund = new Delivery(storage.nextId(), null, "Gifty", System.currentTimeMillis(),
                config.msg("admin.expire-message", "id", d.id(), "player", inboxOwnerName(originalOwner)),
                d.money(), d.items());
        senderInbox.add(refund);
        storage.markDirty(d.fromUuid());
        storage.saveInbox(d.fromUuid());
        Player sender = Bukkit.getPlayer(d.fromUuid());
        if (sender != null && sender.isOnline()) {
            PlatformScheduler.runEntitySync(sender, () ->
                    sender.sendMessage(config.format("admin.expired-notify", "player", inboxOwnerName(originalOwner))));
        }
    }

    // ================================================================ helpers

    private void returnDraftItems(Player player, Session session) {
        if (session.draft != null) {
            for (SerialItem item : session.draft.items) {
                giveItem(player, item.toItemStack());
            }
            if (!session.draft.items.isEmpty()) {
                player.sendMessage(config.format("inbox.returned"));
            }
        }
    }

    private void giveItem(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack rest : leftover.values()) {
                try {
                    player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), rest);
                } catch (Throwable ignored) {
                    // no world — drop impossible
                }
            }
            player.sendMessage(config.format("inbox.overflow"));
        }
    }

    private ItemStack backdrop() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE, "§7");
    }

    private String preview(String message) {
        String s = message == null ? "" : message;
        return s.length() > 24 ? s.substring(0, 23) + "…" : s;
    }

    private String friendly(String material) {
        String s = material.toLowerCase().replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : s.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            out.add(line);
        }
        return out;
    }

    /** Seconds until the player may send again, 0 if ready. */
    public long cooldownLeft(UUID player) {
        long cooldown = config.cooldownSeconds();
        if (cooldown <= 0) {
            return 0;
        }
        Long last = lastSend.get(player);
        if (last == null) {
            return 0;
        }
        long remaining = cooldown - (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, remaining);
    }

    public int pendingCount(UUID player) {
        return storage.pendingCount(player);
    }

    /** Runs periodic maintenance (data/file access only — global-thread safe). */
    public void tick() {
        chatInput.cleanupTimeouts();
        scanExpiry();
    }
}
