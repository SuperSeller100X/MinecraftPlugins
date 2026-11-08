package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BuyOrder;
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
        }
        Logger log=Logger.getLogger("sqlite-integration"); log.setLevel(java.util.logging.Level.OFF);
        SqliteDataStore store=new SqliteDataStore(file.toFile(),log); store.load();
        eq("old integer balance migrated",store.getBalanceExact(a),BigInteger.valueOf(123456789));
        BuyOrder legacy=store.allBuyOrders().get(0);
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
        eq("sqlite failed order write leaves cache intact",store.allBuyOrders().get(0).collected(),2);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {st.execute("DROP TRIGGER reject_order");}
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
        store.close();
        SqliteDataStore restarted=new SqliteDataStore(file.toFile(),log); restarted.load();
        eq("order collection survives SQLite restart",restarted.allBuyOrders().get(0).collected(),2);
        eq("SQLite retains remaining claim",restarted.allBuyOrders().get(0).claimable(),1);
        eq("large sqlite source survives restart",restarted.getBalanceExact(a),Money.parseExact("2l"));
        eq("large sqlite destination survives restart",restarted.getBalanceExact(b),Money.parseExact("7l"));
        restarted.close();
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_orders SET collected=99 WHERE id='old'");
        }
        SqliteDataStore badOrder=new SqliteDataStore(file.toFile(),log); boolean badOrderRefused=false;
        try{badOrder.load();}catch(IllegalStateException expected){badOrderRefused=true;}finally{badOrder.close();}
        check("corrupt SQLite order fails closed",badOrderRefused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement()) {
            st.execute("UPDATE vel_orders SET collected=2 WHERE id='old'");
            st.execute("UPDATE vel_accounts SET balance_exact='broken' WHERE uuid='"+a+"'");
        }
        SqliteDataStore invalid=new SqliteDataStore(file.toFile(),log); boolean refused=false;
        try{invalid.load();}catch(IllegalStateException expected){refused=true;} finally{invalid.close();}
        check("invalid sqlite exact balance fails closed",refused);
        try(Connection db=DriverManager.getConnection(url);Statement st=db.createStatement();var rs=st.executeQuery("SELECT balance_exact FROM vel_accounts WHERE uuid='"+a+"'")) {
            check("invalid sqlite value is not overwritten",rs.next() && "broken".equals(rs.getString(1)));
        }
    }
    private static void check(String name,boolean ok){if(ok)passed++;else{failed++;System.out.println("FAIL "+name);}}
    private static void eq(String name,Object actual,Object expected){check(name+" expected="+expected+" actual="+actual,java.util.Objects.equals(actual,expected));}
}
