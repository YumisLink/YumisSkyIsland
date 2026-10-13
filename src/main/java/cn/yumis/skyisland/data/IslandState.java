package cn.yumis.skyisland.data;

import com.google.gson.JsonElement;
import java.util.*;

/** Plain data, serialized in the world's journal, never global to all saves. */
public final class IslandState {
    public int schemaVersion = 2;
    public long sequence, effectiveTicks, nextSlot;
    public long worldSeed;
    public boolean lobbyReady;
    public Map<UUID, Party> parties = new LinkedHashMap<>();
    public Map<UUID, Person> players = new LinkedHashMap<>();
    public Map<String, Box> boxes = new LinkedHashMap<>();
    public Map<UUID, InventoryRecovery> inventoryRecovery = new LinkedHashMap<>();
    public static final class Party {
        public UUID id, owner;
        public long slot, coins, marketEpoch = -1, overworldEpoch;
        public String name;
        public Set<UUID> members = new LinkedHashSet<>(), trusted = new LinkedHashSet<>();
        public Map<String, Long> blockCounts = new LinkedHashMap<>();
        public Map<String, String> quota = new LinkedHashMap<>();
        public Map<String, Integer> dailyStock = new LinkedHashMap<>();
        public boolean hidden, starterReady, resetInProgress;
        public long homeX, homeZ;
        public int homeY = 81;
        public Refresh refresh;
        public List<Long> resetChunks;
        public int resetCursor;
        public Map<String, Long> research = new LinkedHashMap<>();
        public int highestIslandLevel, paidBountySlots;
        public String cachedPoints;
        public long initialScanEpoch = -1;
        public double scanChunksPerSecond;
        public Set<String> soldProductIds = new LinkedHashSet<>(), acquiredStarterIds = new LinkedHashSet<>(),
            completedQuestIds = new LinkedHashSet<>(), unlockedFeatures = new LinkedHashSet<>();
        public List<Bounty> bounties = new ArrayList<>();
    }
    public static final class Person {
        public UUID activeParty, soloParty, inviteParty;
        public long invitationExpiresAt;
        public String name;
        public Map<String, Long> professionXp = new LinkedHashMap<>();
        public Set<String> milestones = new LinkedHashSet<>();
    }
    public static final class Box {
        public UUID party;
        public String dimension;
        public int x, y, z;
        public long islandEpoch, saleSequence, activationSequence;
        public boolean powered;
        public JsonElement contents;
    }
    public static final class InventoryRecovery {
        public long sequence;
        public JsonElement items;
    }
    public static final class Refresh {
        public boolean initial;
        public UUID jobId=UUID.randomUUID();
        public List<Long> chunks = new ArrayList<>();
        public int cursor;
        public Map<String, Long> counts = new LinkedHashMap<>();
        public Map<String, Long> delta = new LinkedHashMap<>();
        public Set<Long> scanned = new LinkedHashSet<>();
    }
    public static final class Bounty {
        public int slot;
        public long generation, createdAt, expiresAt;
        public String material, reward;
        public int quantity;
        public long materialPrice, rewardPrice;
        public String attemptContext;
        public JsonElement rewardStack;
    }
}
