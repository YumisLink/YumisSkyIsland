package cn.yumis.skyisland.data;

import com.google.gson.JsonElement;
import java.math.BigInteger;
import java.util.*;

/** Plain data, serialized in the world's journal, never global to all saves. */
public final class IslandState {
    public int schemaVersion = 3;
    public long sequence, effectiveTicks, nextSlot, marketRound;
    public long lastMarketDay = -1;
    public long worldSeed;
    /** Server-only seed, initialized durably before the first experience draw. */
    public Long lotterySeed;
    public boolean lobbyReady;
    public long lastBloodMoonNight = -1;
    public int failedBloodMoonNights;
    public boolean bloodMoonActive;
    public Map<UUID, Party> parties = new LinkedHashMap<>();
    public Map<UUID, Person> players = new LinkedHashMap<>();
    public Map<String, Box> boxes = new LinkedHashMap<>();
    public Map<UUID, InventoryRecovery> inventoryRecovery = new LinkedHashMap<>();
    /** Start receipts only; templates and per-block work use the separate prefab journal. */
    public Map<String, JsonElement> prefabJobs = new LinkedHashMap<>();
    public static final class Party {
        public UUID id, owner;
        public long slot, coins, marketEpoch = -1, overworldEpoch;
        public String name;
        public Set<UUID> members = new LinkedHashSet<>(), trusted = new LinkedHashSet<>();
        public Map<String, Long> blockCounts = new LinkedHashMap<>();
        public Map<String, String> quota = new LinkedHashMap<>();
        public Map<UUID, Long> islandDeaths = new LinkedHashMap<>();
        public Map<String, Integer> dailyStock = new LinkedHashMap<>();
        public boolean hidden, starterReady, resetInProgress;
        public long homeX, homeZ;
        public int homeY = 81;
        public Refresh refresh;
        public List<Long> resetChunks;
        public int resetCursor;
        public Map<String, Long> research = new LinkedHashMap<>();
        /** Dimension and chunk keyed biome overrides survive main-island resets. */
        public Map<String, String> biomeOverrides = new LinkedHashMap<>();
        public int highestIslandLevel, paidBountySlots;
        public long lotteryUpEpoch = -1;
        public String lotteryUpCatalog = "";
        /** Last eligible UP, retained while the island is below the draw gate. */
        public String lotteryUpId = "";
        public List<String> lotteryUpOrder = new ArrayList<>();
        public String cachedPoints;
        public long initialScanEpoch = -1;
        /** Historical non-air mutations in this overworldEpoch, including zero-score blocks. */
        public Set<Long> placedChunks = new LinkedHashSet<>();
        public Set<String> readyQuestIds = new LinkedHashSet<>();
        public Map<String, Long> questResetVersions = new LinkedHashMap<>();
        public boolean featureDefaultsInitialized;
        public double scanChunksPerSecond;
        public Set<String> soldProductIds = new LinkedHashSet<>(), acquiredStarterIds = new LinkedHashSet<>(),
            completedQuestIds = new LinkedHashSet<>(), unlockedFeatures = new LinkedHashSet<>();
        public List<Bounty> bounties = new ArrayList<>();
        public Settlement settlement = new Settlement();
    }
    public static final class Person {
        public UUID activeParty, soloParty, inviteParty;
        public long invitationExpiresAt;
        public String name;
        public Map<String, BigInteger> professionXp = new LinkedHashMap<>();
        /** Personal currency earned at the same rate as real profession experience. */
        public Map<String, BigInteger> professionCoins = new LinkedHashMap<>();
        public Map<String, Integer> residentXpRemainders = new LinkedHashMap<>();
        /** Number of permanently consumed profession fruits, capped independently at 100. */
        public Map<String, Integer> consumedFruits = new LinkedHashMap<>();
        public Set<String> unlockedAccessories = new LinkedHashSet<>();
        public Set<String> activeAccessories = new LinkedHashSet<>();
        public int pendingAccessoryDraws;
        public long drawSequence;
        public long lotteryOperationSequence;
        public BigInteger tokens = BigInteger.ZERO;
        public int drawsWithoutLegendary, drawsWithoutEpic;
        public boolean epicOwed, featuredGuaranteed;
        public Set<Integer> rewardedTokenLevels = new LinkedHashSet<>();
        public int accessorySlots = 3;
        /** Hundredths of an experience point still owed by the study pendant. */
        public int studyXpRemainder;
        public NetherReturnPortal netherReturnPortal;
        public Set<Integer> rewardedAccessoryLevels = new LinkedHashSet<>();
        /** Real-world expiry, so logging out does not pause a flight ticket. */
        public long flightExpiryEpochMs;
        public Set<String> completedQuestIds = new LinkedHashSet<>(), readyQuestIds = new LinkedHashSet<>();
        public Map<String, Long> questResetVersions = new LinkedHashMap<>();
        public Map<String, Map<String, Integer>> questProgress = new LinkedHashMap<>();
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
    public static final class Settlement {
        public boolean starterClaimed;
        public long reputation, effectRevision, journalSequence, journalBaseSequence, chefDays;
        public long processedRound = -1, commissionsRound = -1,onlineRound = -1;
        public Map<UUID, House> houses = new LinkedHashMap<>();
        public Map<UUID, Resident> residents = new LinkedHashMap<>();
        public Map<UUID, ResidentCommission> commissions = new LinkedHashMap<>();
    }
    public static final class House {
        public UUID id;
        public long islandEpoch, eligibleRound;
        public String target="carpenter", status="valid", message="";
        public long seed, bed;
        public int air;
        public Set<Long> markers=new LinkedHashSet<>(), interior=new LinkedHashSet<>(), boundary=new LinkedHashSet<>(),chunks=new LinkedHashSet<>();
        public Map<Long,String> facilities=new LinkedHashMap<>();
        public List<Long> supply=new ArrayList<>(), output=new ArrayList<>();
    }
    public static final class Resident {
        public UUID id, house, entity, profileId;
        public long islandEpoch, suppliedRound=-1, processedRound=-1, producedBatches, absentDays, missedDays;
        public String job="ordinary", account="", status="waiting", message="等待下一次供养";
        public JsonElement profile;
        public int x,y,z;
    }
    public static final class ResidentCommission {
        public UUID id,resident;
        public long round;
        public String item;
        public int count;
        public boolean completed;
    }
    public static final class InventoryRecovery {
        public long sequence;
        public JsonElement items;
        public int experiencePoints;
        public ExperienceSnapshot beforeExperience;
        public int experienceLevelCost;
        public LotteryDrawResult lotteryResult;
    }
    public static final class ExperienceSnapshot {
        public int level, total;
        public float progress;
    }
    public static final class LotteryPrize {
        public String productId, rarity;
        public boolean pity;
        public JsonElement rewardStack;
    }
    public static final class LotteryDrawResult {
        public long sequence;
        public long operationSequence;
        public String action;
        public int islandLevel;
        public String productId, rarity;
        public JsonElement rewardStack;
        public List<LotteryPrize> prizes;
    }
    public static final class NetherReturnPortal {
        public UUID partyId, sourcePartyId;
        public long overworldEpoch, sourceOverworldEpoch;
        public int x, y, z;
        public String axis;
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
