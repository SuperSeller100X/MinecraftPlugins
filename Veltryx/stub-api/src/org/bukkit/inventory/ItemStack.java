package org.bukkit.inventory;

import java.io.*;
import java.util.Map;
import net.kyori.adventure.text.StubComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.StubItemMeta;
import org.bukkit.persistence.StubPersistentDataContainer;

/** Small stateful compile stub, used by offline tests only. */
public class ItemStack implements Cloneable {
    /** Test hook to simulate a server refusing to apply item metadata. */
    public static boolean testRejectItemMeta;
    private Material type;
    private int amount;
    private ItemMeta meta;
    private final java.util.Map<io.papermc.paper.datacomponent.DataComponentType, Object> data = new java.util.HashMap<>();
    public ItemStack(Material type) { this(type, 1); }
    public ItemStack(Material type, int amount) {
        this.type = type; this.amount = amount;
        String name = type.name();
        if (name.equals("ENCHANTED_BOOK")) meta = new org.bukkit.inventory.meta.StubEnchantmentStorageMeta();
        else if (name.equals("POTION") || name.equals("SPLASH_POTION") || name.equals("LINGERING_POTION") || name.equals("TIPPED_ARROW")) meta = new org.bukkit.inventory.meta.StubPotionMeta();
        else if (name.equals("FILLED_MAP") || name.endsWith("_MAP")) meta = new org.bukkit.inventory.meta.StubMapMeta();
        else if (name.equals("WRITTEN_BOOK")) meta = new org.bukkit.inventory.meta.StubBookMeta();
        else meta = new StubItemMeta();
        if (type == Material.LINGERING_POTION) data.put(io.papermc.paper.datacomponent.DataComponentTypes.POTION_DURATION_SCALE, 0.25f);
        if (type == Material.TIPPED_ARROW) data.put(io.papermc.paper.datacomponent.DataComponentTypes.POTION_DURATION_SCALE, 0.125f);
        if (type == Material.TRIDENT) {
            data.put(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, 250);
            data.put(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, 0);
        }
    }
    public Material getType() { return type; }
    public int getAmount() { return amount; }
    public int getMaxStackSize() { return type.getMaxStackSize(); }
    public void setAmount(int amount) { this.amount = amount; }
    public void setType(Material type) { this.type = type; }
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) { return meta == null ? 0 : meta.getEnchants().getOrDefault(enchantment,0); }
    public Map<org.bukkit.enchantments.Enchantment, Integer> getEnchantments() { return meta == null ? Map.of() : meta.getEnchants(); }
    public <T> void setData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> type, T value) {
        data.put(type, value);
        if (meta instanceof org.bukkit.inventory.meta.Damageable damage) {
            if (type == io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE) damage.setDamage((Integer)value);
            if (type == io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE) damage.setMaxDamage((Integer)value);
        }
    }
    public void setData(io.papermc.paper.datacomponent.DataComponentType.NonValued type) { data.put(type, Boolean.TRUE); }
    public void unsetData(io.papermc.paper.datacomponent.DataComponentType type) { data.remove(type); }
    public boolean hasData(io.papermc.paper.datacomponent.DataComponentType type) { return data.containsKey(type); }
    @SuppressWarnings("unchecked") public <T> T getData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) { return (T)data.get(type); }
    public ItemMeta getItemMeta() { return meta; }
    public boolean setItemMeta(ItemMeta meta) {
        if (testRejectItemMeta) return false;
        this.meta = meta;
        if (meta instanceof org.bukkit.inventory.meta.Damageable damage) {
            if (data.containsKey(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE))
                data.put(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, damage.getDamage());
            if (damage.hasMaxDamage()) data.put(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, damage.getMaxDamage());
        }
        return true;
    }
    @Override public ItemStack clone() {
        ItemStack result = new ItemStack(type,amount);
        if (meta instanceof StubItemMeta m) result.meta=m.copy(); else result.meta=meta;
        result.data.putAll(data); return result;
    }
    public boolean isSimilar(ItemStack other) {
        if (other == null || type != other.type || !data.equals(other.data)) return false;
        if (meta instanceof StubItemMeta a && other.meta instanceof StubItemMeta b) return a.same(b);
        return meta == other.meta;
    }
    /** Test encoding only; production uses Paper's NBT implementation. */
    public byte[] serializeAsBytes() {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
            out.writeUTF(type.name()); out.writeInt(amount);
            StubItemMeta m=(StubItemMeta)meta;
            out.writeBoolean(m.name instanceof StubComponent);
            if(m.name instanceof StubComponent c)out.writeUTF(c.content);
            var values=((StubPersistentDataContainer)m.getPersistentDataContainer()).values;
            out.writeInt(values.size());
            for(var e:values.entrySet()) {
                out.writeUTF(e.getKey().toString()); Object v=e.getValue();
                if(v instanceof String s){out.writeByte(1);out.writeUTF(s);}
                else if(v instanceof Integer n){out.writeByte(2);out.writeInt(n);}
                else if(v instanceof Long n){out.writeByte(3);out.writeLong(n);}
                else if(v instanceof Byte n){out.writeByte(4);out.writeByte(n);}
                else throw new IllegalArgumentException("unsupported test metadata");
            }
            out.writeBoolean(m.hasItemModel());
            if (m.hasItemModel()) out.writeUTF(m.getItemModel().toString());
            out.writeInt(m.getDamage());
            writeEnchants(out, m.getEnchants());
            if (m instanceof org.bukkit.inventory.meta.StubEnchantmentStorageMeta book) writeEnchants(out, book.stored);
            else out.writeInt(0);
            var lore=m.lore(); out.writeInt(lore == null ? 0 : lore.size());
            if (lore != null) for (var line : lore) {
                StubComponent c=(StubComponent)line; out.writeUTF(c.content); out.writeBoolean(c.italicDisabled);
            }
            out.writeBoolean(m.testCustomModelData()!=null);
            if (m.testCustomModelData()!=null) out.writeInt(m.testCustomModelData());
            out.writeBoolean(hasData(io.papermc.paper.datacomponent.DataComponentTypes.UNBREAKABLE));
            if (m instanceof org.bukkit.inventory.meta.StubPotionMeta potion && potion.getBasePotionType()!=null) {
                out.writeBoolean(true);out.writeUTF(potion.getBasePotionType().name());
            } else out.writeBoolean(false);
            out.writeBoolean(m.hasMaxDamage());
            if (m.hasMaxDamage()) out.writeInt(m.getMaxDamage());
            out.flush(); return bytes.toByteArray();
        } catch(IOException e){throw new IllegalStateException(e);}
    }
    private static void writeEnchants(DataOutputStream out, Map<org.bukkit.enchantments.Enchantment,Integer> values) throws IOException {
        out.writeInt(values.size());
        for (var entry : values.entrySet()) {
            String found=null;
            for (var field : org.bukkit.enchantments.Enchantment.class.getFields()) try {
                if (field.get(null)==entry.getKey()) { found=field.getName(); break; }
            } catch (ReflectiveOperationException ignored) { }
            if(found==null)throw new IOException("unknown test enchantment");
            out.writeUTF(found);out.writeInt(entry.getValue());
        }
    }
    private static org.bukkit.enchantments.Enchantment enchant(String name) throws IOException {
        try { return (org.bukkit.enchantments.Enchantment)org.bukkit.enchantments.Enchantment.class.getField(name).get(null); }
        catch (ReflectiveOperationException e) { throw new IOException(e); }
    }
    public static ItemStack deserializeBytes(byte[] bytes) {
        try {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
            ItemStack stack=new ItemStack(Material.valueOf(in.readUTF()),in.readInt()); StubItemMeta m=(StubItemMeta)stack.meta;
            if(in.readBoolean())m.name=new StubComponent(in.readUTF());
            var values=((StubPersistentDataContainer)m.getPersistentDataContainer()).values;
            int count=in.readInt();
            for(int i=0;i<count;i++) {
                NamespacedKey key=NamespacedKey.fromString(in.readUTF()); int tag=in.readByte();
                Object value=switch(tag){case 1 -> in.readUTF();case 2 -> in.readInt();case 3 -> in.readLong();case 4 -> in.readByte();default -> throw new IOException("bad test tag");};
                values.put(key,value);
            }
            if (in.available() > 0 && in.readBoolean()) m.setItemModel(NamespacedKey.fromString(in.readUTF()));
            if (in.available() > 0) {
                m.setDamage(in.readInt());
                if (stack.data.containsKey(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE))
                    stack.data.put(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, m.getDamage());
                int enchants=in.readInt(); for(int i=0;i<enchants;i++) m.addEnchant(enchant(in.readUTF()),in.readInt(),true);
                int stored=in.readInt(); for(int i=0;i<stored;i++) {
                    var e=enchant(in.readUTF()); int level=in.readInt();
                    if (m instanceof org.bukkit.inventory.meta.StubEnchantmentStorageMeta book) book.addStoredEnchant(e,level,true);
                }
                int lines=in.readInt(); java.util.List<net.kyori.adventure.text.Component> lore=new java.util.ArrayList<>();
                for(int i=0;i<lines;i++){StubComponent c=new StubComponent(in.readUTF());c.italicDisabled=in.readBoolean();lore.add(c);} m.lore(lore);
                if(in.readBoolean())m.setCustomModelData(in.readInt());
                if(in.readBoolean())stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.UNBREAKABLE);
                if(in.available()>0 && in.readBoolean()) {
                    org.bukkit.potion.PotionType type=org.bukkit.potion.PotionType.valueOf(in.readUTF());
                    if(m instanceof org.bukkit.inventory.meta.StubPotionMeta potion)potion.setBasePotionType(type);
                }
                if(in.available()>0 && in.readBoolean())
                    stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE, in.readInt());
            }
            return stack;
        } catch(IOException e){throw new IllegalArgumentException(e);}
    }
}
