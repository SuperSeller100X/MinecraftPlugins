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
    }
    public Material getType() { return type; }
    public int getAmount() { return amount; }
    public int getMaxStackSize() { return type.getMaxStackSize(); }
    public void setAmount(int amount) { this.amount = amount; }
    public void setType(Material type) { this.type = type; }
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) { return meta == null ? 0 : meta.getEnchants().getOrDefault(enchantment,0); }
    public Map<org.bukkit.enchantments.Enchantment, Integer> getEnchantments() { return meta == null ? Map.of() : meta.getEnchants(); }
    public <T> void setData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> type, T value) { data.put(type, value); }
    @SuppressWarnings("unchecked") public <T> T getData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) { return (T)data.get(type); }
    public ItemMeta getItemMeta() { return meta; }
    public boolean setItemMeta(ItemMeta meta) { this.meta = meta; return true; }
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
            out.flush(); return bytes.toByteArray();
        } catch(IOException e){throw new IllegalStateException(e);}
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
            return stack;
        } catch(IOException e){throw new IllegalArgumentException(e);}
    }
}
