package dev.superseller.swifttpa;

import dev.superseller.swifttpa.request.RequestStore;
import dev.superseller.swifttpa.request.TeleportRequest;
import dev.superseller.swifttpa.request.RequestType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestStoreTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    private static TeleportRequest request(UUID sender, UUID target, long created, long expires) {
        return new TeleportRequest(sender, target, RequestType.TPA, created, expires);
    }

    @Test
    void latestIncomingIsWhatBareAcceptAnswers() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 61_000L), true, 5);
        store.add(request(BOB, CAROL, 2_000L, 62_000L), true, 5);
        assertEquals(BOB, store.latestIncoming(CAROL).orElseThrow().sender());
        store.remove(BOB, CAROL);
        assertEquals(ALICE, store.latestIncoming(CAROL).orElseThrow().sender());
    }

    @Test
    void oneOutgoingReplacesThePreviousRequest() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, BOB, 1_000L, 61_000L), true, 5);
        RequestStore.AddResult result = store.add(request(ALICE, CAROL, 2_000L, 62_000L), true, 5);
        assertNotNull(result.replacedOutgoing());
        assertEquals(BOB, result.replacedOutgoing().target());
        assertTrue(store.incoming(BOB).isEmpty());
        assertEquals(1, store.incoming(CAROL).size());
        assertEquals(1, store.size());
    }

    @Test
    void multiOutgoingKeepsBothWhenAllowed() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, BOB, 1_000L, 61_000L), false, 5);
        RequestStore.AddResult result = store.add(request(ALICE, CAROL, 2_000L, 62_000L), false, 5);
        assertNull(result.replacedOutgoing());
        assertEquals(2, store.size());
    }

    @Test
    void fullQueueEvictsTheOldestRequest() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 61_000L), false, 2);
        store.add(request(BOB, CAROL, 2_000L, 62_000L), false, 2);
        UUID dave = UUID.randomUUID();
        RequestStore.AddResult result = store.add(request(dave, CAROL, 3_000L, 63_000L), false, 2);
        assertNotNull(result.evictedOldest());
        assertEquals(ALICE, result.evictedOldest().sender());
        assertEquals(2, store.incoming(CAROL).size());
        assertTrue(store.findIncoming(CAROL, ALICE).isEmpty());
        assertTrue(store.findIncoming(CAROL, dave).isPresent());
    }

    @Test
    void expireSweepOnlyDropsStaleRequests() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 10_000L), true, 5);
        store.add(request(BOB, CAROL, 2_000L, 60_000L), true, 5);
        List<TeleportRequest> expired = store.expire(30_000L);
        assertEquals(1, expired.size());
        assertEquals(ALICE, expired.get(0).sender());
        assertEquals(1, store.size());
        assertFalse(store.expire(30_000L).size() > 1);
    }

    @Test
    void zeroExpiryNeverExpires() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 0L), true, 5);
        assertTrue(store.expire(Long.MAX_VALUE).isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void findIncomingMatchesTheSender() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 60_000L), true, 5);
        assertTrue(store.findIncoming(CAROL, ALICE).isPresent());
        assertTrue(store.findIncoming(CAROL, BOB).isEmpty());
    }

    @Test
    void removeOutgoingClearsBothIndexes() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 60_000L), true, 5);
        assertTrue(store.removeOutgoing(ALICE).isPresent());
        assertTrue(store.incoming(CAROL).isEmpty());
        assertTrue(store.outgoing(ALICE).isEmpty());
        assertEquals(0, store.size());
    }

    @Test
    void removeAllInvolvingCoversBothDirections() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 60_000L), true, 5);
        store.add(request(BOB, CAROL, 2_000L, 60_000L), true, 5);
        List<TeleportRequest> removed = store.removeAllInvolving(CAROL);
        assertEquals(2, removed.size());
        assertEquals(0, store.size());
    }

    @Test
    void clearReturnsTheRemovedCount() {
        RequestStore store = new RequestStore();
        store.add(request(ALICE, CAROL, 1_000L, 60_000L), true, 5);
        store.add(request(BOB, CAROL, 2_000L, 60_000L), true, 5);
        assertEquals(2, store.clear());
        assertEquals(0, store.size());
        assertEquals(0, store.clear());
    }

    @Test
    void requestKnowsWhoMoves() {
        TeleportRequest tpa = new TeleportRequest(ALICE, BOB, RequestType.TPA, 0L, 0L);
        assertEquals(ALICE, tpa.mover());
        assertEquals(BOB, tpa.anchor());
        TeleportRequest here = new TeleportRequest(ALICE, BOB, RequestType.TPA_HERE, 0L, 0L);
        assertEquals(BOB, here.mover());
        assertEquals(ALICE, here.anchor());
    }
}
