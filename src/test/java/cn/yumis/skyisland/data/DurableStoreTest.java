package cn.yumis.skyisland.data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class DurableStoreTest {
    @TempDir Path directory;
    @Test void publishesAfterForceAndRecoversPartyInventoryIntent() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>(); UUID id = UUID.randomUUID();
        try (DurableStore store = new DurableStore(directory,callbacks::add)) {
            store.commit(s -> { var p = new IslandState.Party(); p.id=id;p.coins=145235522L;s.parties.put(id,p);var recovery=new IslandState.InventoryRecovery();recovery.sequence=s.sequence+1;recovery.items=new com.google.gson.JsonArray();s.inventoryRecovery.put(id,recovery); },() -> {},e -> fail(e));
            assertFalse(store.available());assertTrue(store.state().parties.isEmpty());
            Runnable callback=callbacks.poll(10,TimeUnit.SECONDS); assertNotNull(callback); callback.run();
            assertEquals(145235522L,store.state().parties.get(id).coins);
        }
        try (DurableStore restored = new DurableStore(directory,Runnable::run)) {
            assertEquals(145235522L,restored.state().parties.get(id).coins);
            assertEquals(1L,restored.state().inventoryRecovery.get(id).sequence);
        }
    }
    @Test void tornTailDoesNotErasePreviousCheckpoint() throws Exception {
        try(DurableStore store=new DurableStore(directory,Runnable::run)){store.state().effectiveTicks=98765;}
        Files.write(directory.resolve("state.journal"),new byte[]{0,0,2},StandardOpenOption.APPEND);
        try(DurableStore restored=new DurableStore(directory,Runnable::run)){assertEquals(98765,restored.state().effectiveTicks);}
    }
    @Test void compactionKeepsLatestStateAndBoundsJournal() throws Exception {
        BlockingQueue<Runnable> callbacks=new LinkedBlockingQueue<>();
        try(DurableStore store=new DurableStore(directory,callbacks::add)){
            for(int n=0;n<132;n++){int number=n;store.commit(s->s.nextSlot=number,()->{},e->fail(e));var callback=callbacks.poll(10,TimeUnit.SECONDS);assertNotNull(callback);callback.run();}
            assertTrue(Files.exists(directory.resolve("state.checkpoint")));assertEquals(131,store.state().nextSlot);
            assertTrue(Files.size(directory.resolve("state.journal"))<8192);
        }
        try(DurableStore restored=new DurableStore(directory,Runnable::run)){assertEquals(131,restored.state().nextSlot);}
    }
    @Test void corruptedCheckpointFailsClosed() throws Exception {
        try(DurableStore store=new DurableStore(directory,Runnable::run)){store.state().nextSlot=12;}
        byte[] bytes=Files.readAllBytes(directory.resolve("state.checkpoint"));bytes[bytes.length-2]^=1;Files.write(directory.resolve("state.checkpoint"),bytes);
        assertThrows(java.io.IOException.class,()->new DurableStore(directory,Runnable::run));
    }
    @Test void teamProgressAndFrozenOffersSurviveCheckpointAndReset() throws Exception {
        UUID id=UUID.randomUUID();BlockingQueue<Runnable> callbacks=new LinkedBlockingQueue<>();
        try(DurableStore store=new DurableStore(directory,callbacks::add)){
            store.commit(s->{var p=new IslandState.Party();p.id=id;p.highestIslandLevel=30;p.cachedPoints="12340000";p.paidBountySlots=2;p.initialScanEpoch=0;p.soldProductIds.add("minecraft:cobblestone");p.acquiredStarterIds.add("minecraft:wheat_seeds");p.completedQuestIds.add("first_cobblestone");var b=new IslandState.Bounty();b.slot=0;b.generation=7;b.createdAt=100;b.expiresAt=168100;b.material="minecraft:cobblestone";b.quantity=120;b.reward="minecraft:birch_sapling";b.rewardPrice=12000;p.bounties.add(b);s.parties.put(id,p);},()->{},e->fail(e));
            var publish=callbacks.poll(10,TimeUnit.SECONDS);assertNotNull(publish);publish.run();
        }
        try(DurableStore restored=new DurableStore(directory,Runnable::run)){
            var p=restored.state().parties.get(id);assertEquals(30,p.highestIslandLevel);assertEquals(2,p.paidBountySlots);assertEquals(7,p.bounties.getFirst().generation);assertEquals(168100,p.bounties.getFirst().expiresAt);assertTrue(p.acquiredStarterIds.contains("minecraft:wheat_seeds"));assertTrue(p.completedQuestIds.contains("first_cobblestone"));
            cn.yumis.skyisland.server.ProgressionService.reset(p);assertEquals(0,p.highestIslandLevel);assertEquals(0,p.paidBountySlots);assertTrue(p.bounties.isEmpty());assertTrue(p.soldProductIds.isEmpty());assertTrue(p.acquiredStarterIds.isEmpty());assertTrue(p.completedQuestIds.isEmpty());
        }
    }
}
