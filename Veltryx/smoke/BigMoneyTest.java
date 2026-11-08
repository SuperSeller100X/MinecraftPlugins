package dev.superseller.veltryx.smoke;

import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.storage.YamlDataStore;
import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Exact-currency, migration and transaction regressions using the offline YAML double. */
public final class BigMoneyTest {
    private static int passed, failed;
    public static void main(String[] args) throws Exception {
        String[] suffix = {"k", "m", "b", "t", "q", "d", "g", "l"};
        for (int i=0; i<suffix.length; i++) {
            int exponent=(i+1)*3;
            BigInteger cents=BigInteger.TEN.pow(exponent+2);
            eq("exact suffix "+suffix[i], Money.parseExact("1"+suffix[i]), cents);
            eq("uppercase suffix "+suffix[i], Money.parseExact("1"+suffix[i].toUpperCase()), cents);
            eq("decimal suffix "+suffix[i], Money.parseExact("1.5"+suffix[i]), cents.multiply(BigInteger.valueOf(3)).divide(BigInteger.TWO));
        }
        eq("exact cents at long boundary", Money.parseExact("92233720368547758.07"),BigInteger.valueOf(Long.MAX_VALUE));
        eq("legacy boundary", Money.parse("92233720368547758.07"),Long.MAX_VALUE);
        eq("legacy refuses overflow", Money.parse("92233720368547758.08"),Money.INVALID);
        eq("no floating point drift",Money.parseExact("999999999999999999999999.99"), new BigInteger("99999999999999999999999999"));
        eq("round half cent",Money.parseExact("0.005"),BigInteger.ONE);
        eq("round below half cent",Money.parseExact("0.004"),BigInteger.ZERO);
        check("reject negative", Money.parseExact("-1l")==null);
        check("reject absurdly long command", Money.parseExact("9".repeat(161))==null);
        check("reject NaN and exponent bomb", Money.parseExact("NaN")==null && Money.parseExact("1e99999999")==null);
        eq("q compact", Money.formatCompact(Money.parseExact("1.25q")), "1.3q");
        eq("d compact", Money.formatCompact(Money.parseExact("2d")), "2d");
        eq("g compact", Money.formatCompact(Money.parseExact("3.5g")), "3.5g");
        eq("l compact", Money.formatCompact(Money.parseExact("7l")), "7l");
        eq("exact full format", Money.format(Money.parseExact("1l")), "$1,000,000,000,000,000,000,000,000.00");

        Path dir=Files.createTempDirectory("veltryx-big-money-");
        try { exerciseStore(dir); }
        finally { try (var paths=Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch(Exception ignored) { } }); } }
        System.out.println("Big money: "+passed+" passed, "+failed+" failed.");
        if(failed>0) System.exit(1);
    }
    private static void exerciseStore(Path dir) throws Exception {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        // Previous releases wrote numeric long cents under this same key.
        Files.writeString(dir.resolve("economy.yml"), a+":\n  name: Alice\n  balance: 123456789\n");
        YamlDataStore store=new YamlDataStore(dir.toFile(),Logger.getLogger("big-money-test"));
        store.load();
        eq("load legacy numeric cents",store.getBalanceExact(a),BigInteger.valueOf(123456789));
        EconomyService economy=new EconomyService(store,new VeltryxConfig(new YamlConfiguration()));
        BigInteger oneL=Money.parseExact("1l");
        economy.setBalanceExact(a,"Alice",oneL);
        economy.setBalanceExact(b,"Bob",oneL.multiply(BigInteger.TWO));
        eq("legacy view clamps",economy.getBalance(a),Long.MAX_VALUE);
        economy.deposit(a,"Alice",123L);
        eq("legacy deposit preserves entire high balance",economy.getBalanceExact(a),oneL.add(BigInteger.valueOf(123)));
        check("legacy withdrawal works at high balance",economy.withdraw(a,"Alice",23L));
        eq("withdrawal is exact",economy.getBalanceExact(a),oneL.add(BigInteger.valueOf(100)));
        BigInteger payment=Money.parseExact("2.5g");
        check("large transfer succeeds",economy.transferExact(b,"Bob",a,"Alice",payment));
        eq("large payment credited exactly",economy.getBalanceExact(a),oneL.add(BigInteger.valueOf(100)).add(payment));
        eq("large payment debited exactly",economy.getBalanceExact(b),oneL.multiply(BigInteger.TWO).subtract(payment));
        check("unaffordable transfer fails",!economy.transferExact(a,"Alice",b,"Bob",oneL.multiply(BigInteger.TEN)));
        eq("top sorted by full precision",store.top(2).get(0).uuid(),b);
        check("YAML stores balance as a quoted decimal string",Files.readString(dir.resolve("economy.yml")).contains("'"+economy.getBalanceExact(a)+"'"));
        BuyOrder order=new BuyOrder("test-order",a,"Alice","IRON_INGOT",5,3,100,1,9_999_999_999_999L,false,1);
        store.saveBuyOrder(order);
        YamlDataStore reopened=new YamlDataStore(dir.toFile(),Logger.getLogger("big-money-test")); reopened.load();
        eq("YAML preserves collected count",reopened.allBuyOrders().get(0).collected(),1);
        eq("YAML preserves remaining claim",reopened.allBuyOrders().get(0).claimable(),2);
        Files.createDirectory(dir.resolve("market.yml.tmp"));
        boolean orderWriteFailed=false;
        try {store.saveBuyOrder(new BuyOrder(order.id(),a,"Alice","IRON_INGOT",5,3,100,1,9_999_999_999_999L,false,2));}
        catch(IllegalStateException expected) {orderWriteFailed=true;}
        check("YAML order save failure reported",orderWriteFailed);
        eq("failed order save restores cache",store.allBuyOrders().get(0).collected(),1);
        YamlDataStore afterOrderFailure=new YamlDataStore(dir.toFile(),Logger.getLogger("big-money-test"));afterOrderFailure.load();
        eq("failed order save preserves disk state",afterOrderFailure.allBuyOrders().get(0).collected(),1);
        Files.delete(dir.resolve("market.yml.tmp"));
        eq("high balance survives restart",reopened.getBalanceExact(a),economy.getBalanceExact(a));
        eq("other balance survives restart",reopened.getBalanceExact(b),economy.getBalanceExact(b));
        eq("ordering survives restart",reopened.top(2).get(0).uuid(),b);
        BigInteger beforeA=economy.getBalanceExact(a), beforeB=economy.getBalanceExact(b);
        Files.createDirectory(dir.resolve("economy.yml.tmp"));
        boolean failedWrite=false;
        try { economy.transferExact(a,"Alice",b,"Bob",BigInteger.ONE); }
        catch(IllegalStateException expected) { failedWrite=true; }
        check("failed atomic transfer reports error",failedWrite);
        eq("failed transfer restores source in memory",economy.getBalanceExact(a),beforeA);
        eq("failed transfer restores destination in memory",economy.getBalanceExact(b),beforeB);
        reopened=new YamlDataStore(dir.toFile(),Logger.getLogger("big-money-test")); reopened.load();
        eq("failed transfer leaves durable source intact",reopened.getBalanceExact(a),beforeA);
        eq("failed transfer leaves durable destination intact",reopened.getBalanceExact(b),beforeB);
        Files.delete(dir.resolve("economy.yml.tmp"));
        ExecutorService threads=Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> jobs=new java.util.ArrayList<>();
            for(int n=0;n<4;n++) jobs.add(threads.submit(() -> {for(int i=0;i<20;i++) economy.deposit(a,"Alice",1L);}));
            for(Future<?> job:jobs) job.get();
        } finally {threads.shutdownNow();}
        eq("concurrent deposits don't lose cents",economy.getBalanceExact(a),beforeA.add(BigInteger.valueOf(80)));
        Path legacyMarket=dir.resolve("legacy-market"); Files.createDirectories(legacyMarket);
        Files.writeString(legacyMarket.resolve("market.yml"), "orders:\n  historical:\n    buyer: '"+a+"'\n    buyer-name: 'Alice'\n    material: 'IRON_INGOT'\n    amount: 5\n    filled: 3\n    price-each: 100\n    created: 1\n    expires: 9999999999999\n    closed: false\n");
        Logger quiet=Logger.getLogger("legacy-market-test");quiet.setLevel(java.util.logging.Level.OFF);
        YamlDataStore legacyStore=new YamlDataStore(legacyMarket.toFile(),quiet);legacyStore.load();
        eq("old YAML fills are not minted without reconciliation",legacyStore.allBuyOrders().get(0).claimable(),0);
        legacyStore.save();
        YamlDataStore legacyRestart=new YamlDataStore(legacyMarket.toFile(),quiet);legacyRestart.load();
        eq("legacy reconciliation marker survives restart",legacyRestart.allBuyOrders().get(0).collected(),3);
        Path badMarket=dir.resolve("bad-market");Files.createDirectories(badMarket);
        String invalidMarket="orders:\n  broken:\n    buyer: '"+a+"'\n    material: 'IRON_INGOT'\n    amount: 5\n    filled: 3\n    collected: 9\n    price-each: 100\n";
        Files.writeString(badMarket.resolve("market.yml"),invalidMarket);
        YamlDataStore brokenMarket=new YamlDataStore(badMarket.toFile(),quiet);boolean refusedMarket=false;
        try {brokenMarket.load();} catch(IllegalArgumentException expected) {refusedMarket=true;}
        finally {brokenMarket.close();}
        check("corrupt YAML order fails closed",refusedMarket);
        eq("failed market startup cannot overwrite data",Files.readString(badMarket.resolve("market.yml")),invalidMarket);
        Path corrupt=dir.resolve("corrupt"); Files.createDirectories(corrupt);
        String invalid=a+":\n  name: Alice\n  balance: broken\n";
        Files.writeString(corrupt.resolve("economy.yml"),invalid);
        YamlDataStore broken=new YamlDataStore(corrupt.toFile(),Logger.getLogger("big-money-test"));
        boolean refused=false; try {broken.load();} catch(IllegalStateException expected) {refused=true;}
        broken.save(); broken.close();
        check("invalid account aborts loading",refused);
        eq("failed startup and disable never overwrite bad source",Files.readString(corrupt.resolve("economy.yml")),invalid);
    }
    private static void check(String name, boolean ok){if(ok)passed++;else{failed++;System.out.println("FAIL "+name);}}
    private static void eq(String name,Object actual,Object expected){check(name+" expected="+expected+" actual="+actual,java.util.Objects.equals(actual,expected));}
}
