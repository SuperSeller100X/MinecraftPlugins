package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.market.AhService;
import dev.superseller.veltryx.market.OrderService;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.storage.SqliteDataStore;
import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Optional integration test: run with an actual SQLite JDBC driver on the classpath. */
public final class SqliteIntegrationTest {
    private static int passed, failed;
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path dir=Files.createTempDirectory("veltryx-sqlite-integration-");
        try { run(dir.resolve("old.db")); }
        finally {try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p -> {try {Files.deleteIfExists(p);}catch(Exception ignored){}});}}
        System.out.println("SQLite integration: "+passed+" passed, "+failed+" failed.");
        if(failed>0)System.exit(1);
    }
    private static void run(Path file) throws Exception {
        String url="jdbc:sqlite:"+file.toAbsolutePath();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("CREATE TABLE vel_accounts (uuid TEXT PRIMARY KEY, name TEXT, balance INTEGER)");
            st.execute("INSERT INTO vel_accounts VALUES ('"+a+"','Alice',123456789)");
            st.execute("CREATE TABLE vel_orders (id TEXT PRIMARY KEY,buyer TEXT,buyer_name TEXT,material TEXT,amount INTEGER,filled INTEGER,price_each INTEGER,created INTEGER,expires INTEGER,closed INTEGER)");
            st.execute("INSERT INTO vel_orders VALUES ('old','"+a+"','Alice','IRON_INGOT',5,3,100,1,9999999999999,0)");
            st.execute("CREATE TABLE vel_ah (id TEXT PRIMARY KEY,seller TEXT,seller_name TEXT,material TEXT,amount INTEGER,price INTEGER,listed INTEGER,expires INTEGER,closed INTEGER)");
            st.execute("INSERT INTO vel_ah VALUES ('old-ah','"+a+"','Alice','DIAMOND',3,12345,1,9999999999999,0)");
        }
        Logger log=Logger.getLogger("sqlite-integration"); log.setLevel(java.util.logging.Level.OFF);
        SqliteDataStore store=new SqliteDataStore(file.toFile(),log); store.load();
        eq("old integer balance migrated",store.getBalanceExact(a),BigInteger.valueOf(123456789));
        BuyOrder legacy=order(store,"old");
        eq("legacy order total migrated exactly",legacy.totalCents(),BigInteger.valueOf(500));
        eq("legacy auction price migrated exactly",listing(store,"old-ah").priceExactCents(),BigInteger.valueOf(12345));
        eq("legacy auction remains material-only",listing(store,"old-ah").itemData(),null);
        eq("legacy fills are not minted automatically",legacy.claimable(),0);
        BuyOrder collected=new BuyOrder(legacy.id(),legacy.buyer(),legacy.buyerName(),legacy.material(),legacy.amount(),
                legacy.filled(),legacy.priceEachCents(),legacy.created(),legacy.expires(),legacy.closed(),2);
        store.saveBuyOrder(collected);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("CREATE TRIGGER reject_order BEFORE UPDATE ON vel_orders BEGIN SELECT RAISE(ABORT, 'blocked'); END");
        }
        boolean rejectedOrder=false;
        try{store.saveBuyOrder(legacy);}catch(IllegalStateException expected){rejectedOrder=true;}
        check("sqlite order write failure reported",rejectedOrder);
        eq("sqlite failed order write leaves cache intact",order(store,"old").collected(),2);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {st.execute("DROP TRIGGER reject_order");}
        BigInteger marketTotal=new BigInteger("123456789012345678901234567890123456789");
        String itemData=java.util.Base64.getEncoder().encodeToString(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 3).serializeAsBytes());
        AhListing auction=new AhListing("big-ah",a,"Alice","DIAMOND",3,marketTotal,1,9_999_999_999_999L,false,itemData);
        BuyOrder largeOrder=new BuyOrder("big-order",a,"Alice","IRON_INGOT",3,1,marketTotal,1,9_999_999_999_999L,false,0);
        store.saveAhListing(auction);store.saveBuyOrder(largeOrder);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("CREATE TRIGGER reject_ah BEFORE UPDATE ON vel_ah BEGIN SELECT RAISE(ABORT, 'blocked'); END");
        }
        boolean rejectedAh=false;
        try {store.saveAhListing(auction.withClosed(true));} catch(IllegalStateException expected) {rejectedAh=true;}
        check("SQLite auction write failure reported",rejectedAh);
        check("SQLite auction write failure leaves cache intact",!listing(store,"big-ah").closed());
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {st.execute("DROP TRIGGER reject_ah");}
        EconomyService economy=new EconomyService(store,new VeltryxConfig(new YamlConfiguration()));
        BigInteger big=Money.parseExact("3l");
        economy.setBalanceExact(a,"Alice",big); economy.setBalanceExact(b,"Bob",big.multiply(BigInteger.TWO));
        eq("large sqlite account exact",store.getBalanceExact(a),big);
        eq("sqlite top uses full precision",store.top(2).get(0).uuid(),b);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("CREATE TRIGGER reject_b BEFORE UPDATE ON vel_accounts WHEN NEW.uuid = '"+b+"' BEGIN SELECT RAISE(ABORT, 'blocked'); END");
        }
        boolean failedTransfer=false;
        try {economy.transferExact(a,"Alice",b,"Bob",Money.parseExact("1l"));}
        catch(IllegalStateException expected){failedTransfer=true;}
        check("failed sqlite transaction reported",failedTransfer);
        eq("failed sqlite transfer retains source cache",store.getBalanceExact(a),big);
        eq("failed sqlite transfer retains destination cache",store.getBalanceExact(b),big.multiply(BigInteger.TWO));
        SqliteDataStore readBack=new SqliteDataStore(file.toFile(),log);readBack.load();
        eq("failed sqlite transaction rolled back source on disk",readBack.getBalanceExact(a),big);
        eq("failed sqlite transaction rolled back destination on disk",readBack.getBalanceExact(b),big.multiply(BigInteger.TWO));
        readBack.close();
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {st.execute("DROP TRIGGER reject_b");}
        check("successful sqlite transfer",economy.transferExact(a,"Alice",b,"Bob",Money.parseExact("1l")));
        UUID sellerId=UUID.randomUUID(), buyerId=UUID.randomUUID();
        TestInventory sellerInventory=new TestInventory(), buyerInventory=new TestInventory();
        var seller=testPlayer(sellerId,"SqlSeller",sellerInventory); var buyer=testPlayer(buyerId,"SqlBuyer",buyerInventory);
        BigInteger tradeTotal=Money.parseExact("123456789012345678901234567.89");
        economy.setBalanceExact(buyerId,"SqlBuyer",tradeTotal.multiply(BigInteger.TWO));
        var tradeItem=new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD,7);
        var meta=tradeItem.getItemMeta();meta.displayName(new net.kyori.adventure.text.StubComponent("SQLite item"));
        var key=org.bukkit.NamespacedKey.minecraft("sqlite_auction");
        meta.getPersistentDataContainer().set(key,org.bukkit.persistence.PersistentDataType.STRING,"persisted");
        meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING,1,true);tradeItem.setItemMeta(meta);
        sellerInventory.setItemInMainHand(tradeItem.clone());
        var config=new VeltryxConfig(new YamlConfiguration());
        var messages=new Messages(new org.bukkit.plugin.java.JavaPlugin() { });
        var market=new AhService(store,economy,messages,config);
        var created=market.listHeldItem(seller,tradeTotal);check("SQLite marketplace lists held item",created.ok());
        String tradeId=created.values().get("id");
        check("SQLite marketplace clears only held stack",sellerInventory.getItemInMainHand()==null);
        store.close();
        SqliteDataStore restarted=new SqliteDataStore(file.toFile(),log); restarted.load();
        eq("order collection survives SQLite restart",order(restarted,"old").collected(),2);
        eq("SQLite retains remaining claim",order(restarted,"old").claimable(),1);
        eq("large sqlite source survives restart",restarted.getBalanceExact(a),Money.parseExact("2l"));
        eq("large sqlite destination survives restart",restarted.getBalanceExact(b),Money.parseExact("7l"));
        eq("SQLite auction total survives restart",listing(restarted,"big-ah").priceExactCents(),marketTotal);
        eq("SQLite auction item data survives restart",listing(restarted,"big-ah").itemData(),itemData);
        check("failed SQLite listing update leaves disk intact",!listing(restarted,"big-ah").closed());
        eq("SQLite order total survives restart",order(restarted,"big-order").totalCents(),marketTotal);
        eq("SQLite partial order escrow survives restart",order(restarted,"big-order").refundCents(),largeOrder.refundCents());
        var restartedEconomy=new EconomyService(restarted,config);
        var restartedMarket=new AhService(restarted,restartedEconomy,messages,config);
        check("SQLite listing is still live after restart",!listing(restarted,tradeId).closed());
        check("SQLite marketplace buys listing after restart",restartedMarket.buy(buyer,tradeId).ok());
        var received=buyerInventory.getItem(0);
        check("SQLite delivers original custom stack",received!=null && received.getAmount()==7 && received.isSimilar(tradeItem));
        eq("SQLite delivers custom metadata",received.getItemMeta().getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.STRING),"persisted");
        eq("SQLite trade debits exact total",restartedEconomy.getBalanceExact(buyerId),tradeTotal);
        eq("SQLite trade credits exact total",restartedEconomy.getBalanceExact(sellerId),tradeTotal);
        var orderService=new OrderService(restarted,restartedEconomy,messages,config);
        var placed=orderService.create(buyer,"IRON_INGOT",3,BigInteger.valueOf(101));
        check("SQLite creates exact-total order",placed.ok());String orderId=placed.values().get("id");
        sellerInventory.setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_INGOT,1));
        check("SQLite partial order fill succeeds",orderService.fill(seller,orderId,1).ok());
        restarted.close();
        var verifyTrade=new SqliteDataStore(file.toFile(),log);verifyTrade.load();
        check("SQLite completed sale remains closed",listing(verifyTrade,tradeId).closed());
        eq("SQLite seller payouts survive restart",verifyTrade.getBalanceExact(sellerId),tradeTotal.add(BigInteger.valueOf(34)));
        var verifyEconomy=new EconomyService(verifyTrade,config);var resumed=new OrderService(verifyTrade,verifyEconomy,messages,config);
        check("SQLite partial order can be cancelled after restart",resumed.cancel(buyer,orderId).ok());
        eq("SQLite restart refunds exact remainder",verifyEconomy.getBalanceExact(buyerId),tradeTotal.subtract(BigInteger.valueOf(34)));
        check("SQLite partial claim survives restart and cancellation",resumed.claim(buyer,orderId).ok());
        eq("SQLite order buyer receives item",buyerInventory.getItem(1).getType(),org.bukkit.Material.IRON_INGOT);
        var factory = new dev.superseller.veltryx.spawner.SpawnerItems(new org.bukkit.plugin.java.JavaPlugin() {
            @Override public Logger getLogger() { return log; }
        });
        var identity = new dev.superseller.veltryx.spawner.SpawnerItems.Identity(UUID.randomUUID(), dev.superseller.veltryx.spawner.SpawnerType.COW);
        sellerInventory.setItemInMainHand(factory.create(identity, 3));
        var latestMarket = new AhService(verifyTrade, verifyEconomy, messages, config);
        var spawnerListing = latestMarket.listHeldItem(seller, BigInteger.valueOf(100));
        check("SQLite accepts an identity-bearing spawner", spawnerListing.ok());
        String spawnerId = spawnerListing.values().get("id"); verifyTrade.close();
        var verifySpawner = new SqliteDataStore(file.toFile(), log); verifySpawner.load();
        var spawnerMarket = new AhService(verifySpawner, new EconomyService(verifySpawner, config), messages, config);
        check("SQLite restart can reclaim identity-bearing spawner", spawnerMarket.reclaim(seller, spawnerId).ok());
        eq("SQLite restart preserves spawner identity", factory.identity(sellerInventory.getItem(0)), identity);
        verifySpawner.close();
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_orders SET collected=99 WHERE id='old'");
        }
        SqliteDataStore badOrder=new SqliteDataStore(file.toFile(),log); boolean badOrderRefused=false;
        try{badOrder.load();}catch(IllegalStateException expected){badOrderRefused=true;}finally{badOrder.close();}
        check("corrupt SQLite order fails closed",badOrderRefused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_orders SET collected=2 WHERE id='old'");
            st.execute("UPDATE vel_orders SET total_exact='broken' WHERE id='big-order'");
        }
        SqliteDataStore badTotal=new SqliteDataStore(file.toFile(),log); boolean badTotalRefused=false;
        try{badTotal.load();}catch(IllegalStateException expected){badTotalRefused=true;}finally{badTotal.close();}
        check("invalid exact order total fails closed",badTotalRefused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_orders SET total_exact='"+marketTotal+"' WHERE id='big-order'");
            st.execute("UPDATE vel_ah SET price_exact='broken' WHERE id='big-ah'");
        }
        SqliteDataStore badAh=new SqliteDataStore(file.toFile(),log); boolean badAhRefused=false;
        try{badAh.load();}catch(IllegalStateException expected){badAhRefused=true;}finally{badAh.close();}
        check("invalid exact auction total fails closed",badAhRefused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_ah SET price_exact='"+marketTotal+"' WHERE id='big-ah'");
            st.execute("UPDATE vel_accounts SET balance_exact='broken' WHERE uuid='"+a+"'");
        }
        SqliteDataStore invalid=new SqliteDataStore(file.toFile(),log); boolean refused=false;
        try{invalid.load();}catch(IllegalStateException expected){refused=true;} finally{invalid.close();}
        check("invalid sqlite exact balance fails closed",refused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement();var rs=st.executeQuery("SELECT balance_exact FROM vel_accounts WHERE uuid='"+a+"'")) {
            check("invalid sqlite value is not overwritten",rs.next() && "broken".equals(rs.getString(1)));
        }
    }
    private static org.bukkit.entity.Player testPlayer(UUID id,String name,TestInventory inventory) {
        return (org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()) {
                    case "getUniqueId" -> id; case "getName" -> name; case "getInventory" -> inventory; case "isOnline" -> true;
                    case "equals" -> proxy==args[0]; case "hashCode" -> id.hashCode(); case "toString" -> name;
                    default -> {
                        Class<?> type=method.getReturnType();
                        if(type==boolean.class) yield false;
                        if(type==int.class) yield 0;
                        if(type==long.class) yield 0L;
                        yield null;
                    }
                });
    }
    private static final class TestInventory implements org.bukkit.inventory.PlayerInventory {
        final org.bukkit.inventory.ItemStack[] items=new org.bukkit.inventory.ItemStack[36];
        public void setItem(int slot,org.bukkit.inventory.ItemStack item){items[slot]=item;}
        public org.bukkit.inventory.ItemStack getItem(int slot){return items[slot];}
        public int getSize(){return items.length;}
        public org.bukkit.inventory.ItemStack[] getContents(){return items;}
        public org.bukkit.inventory.InventoryHolder getHolder(){return null;}
        public org.bukkit.inventory.ItemStack getChestplate(){return null;}
        public org.bukkit.inventory.ItemStack getItemInMainHand(){return items[0];}
        public void setItemInMainHand(org.bukkit.inventory.ItemStack item){items[0]=item;}
        public java.util.HashMap<Integer,org.bukkit.inventory.ItemStack> addItem(org.bukkit.inventory.ItemStack... stacks){
            var left=new java.util.HashMap<Integer,org.bukkit.inventory.ItemStack>();
            for(int j=0;j<stacks.length;j++){
                int slot=firstEmpty(); if(slot<0)left.put(j,stacks[j]);else items[slot]=stacks[j].clone();
            }
            return left;
        }
    }
    private static BuyOrder order(SqliteDataStore store,String id) {return store.allBuyOrders().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow();}
    private static AhListing listing(SqliteDataStore store,String id) {return store.allAhListings().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();}
    private static void check(String name,boolean ok){if(ok)passed++;else{failed++;System.out.println("FAIL "+name);}}
    private static void eq(String name,Object actual,Object expected){check(name+" expected="+expected+" actual="+actual,java.util.Objects.equals(actual,expected));}
}
