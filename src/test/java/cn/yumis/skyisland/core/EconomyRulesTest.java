package cn.yumis.skyisland.core;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EconomyRulesTest {
    @Test void durabilityIsContinuousAndSupportsOddMaximums() {
        assertEquals(Rational.ONE, Durability.multiplier(100, 100));
        assertEquals(Rational.of(7, 8), Durability.multiplier(95, 100));
        assertEquals(Rational.of(1, 2), Durability.multiplier(80, 100));
        assertEquals(Rational.of(1, 3), Durability.multiplier(70, 100));
        assertEquals(Rational.ZERO, Durability.multiplier(50, 100));
        assertEquals(Rational.ZERO, Durability.multiplier(49, 100));
        assertEquals(Rational.of(5, 606), Durability.multiplier(51, 101));
    }
    @Test void blockAndNineIngotsSpanTheSameQuotaBoundary() {
        assertEquals(Rational.of(63), QuotaCurve.DEFAULT.quote(Rational.of(995), 1, Rational.of(9), Rational.of(81)));
        assertEquals(Rational.of(63), QuotaCurve.DEFAULT.quote(Rational.of(995), 9, Rational.ONE, Rational.of(9)));
        assertEquals(Rational.of(7), QuotaCurve.DEFAULT.quote(Rational.of(9999), 3, Rational.ONE, Rational.of(10)));
        assertEquals(Rational.of(1, 90), QuotaCurve.DEFAULT.integral(Rational.of(90001, 9)).subtract(QuotaCurve.DEFAULT.integral(Rational.of(10000))));
    }
    @Test void batchFractionsRoundOnceAndZeroTransactionsDoNotConsumeQuota() {
        SalePlanner planner = new SalePlanner(QuotaCurve.DEFAULT);
        Map<String, Rational> counts = Map.of("iron", Rational.of(10000));
        SalePlanner.Input one = new SalePlanner.Input(0, "a", 1, "iron", Rational.ONE, Rational.ONE);
        SalePlanner.Plan zero = planner.prepare(42, counts, List.of(one));
        assertFalse(zero.canCommit()); assertEquals(42, zero.resultingWallet()); assertEquals(counts, zero.counters()); assertTrue(zero.removals().isEmpty());
        SalePlanner.Plan ten = planner.prepare(42, counts, List.of(new SalePlanner.Input(0, "a", 10, "iron", Rational.ONE, Rational.ONE)));
        assertEquals(1, ten.coins()); assertEquals(43, ten.resultingWallet()); assertEquals(Rational.of(10010), ten.counters().get("iron"));
    }
    @Test void walletOverflowRejectsBeforeAnyCommit() {
        SalePlanner planner = new SalePlanner(QuotaCurve.DEFAULT);
        assertThrows(ArithmeticException.class, () -> planner.prepare(Long.MAX_VALUE, Map.of(), List.of(new SalePlanner.Input(0, "a", 1, "iron", Rational.ONE, Rational.ONE))));
        assertEquals("1亿4523万5522金币", Money.format(145235522));
        assertEquals("20亿金币", Money.format(2000000000));
        assertEquals("0金币", Money.format(0));
    }
    @Test void halfOpenClaimsAndLobbyNeverOverlap() {
        assertEquals(0, ClaimGrid.slotAt(9999, 9999).orElseThrow());
        assertEquals(1, ClaimGrid.slotAt(10000, 9999).orElseThrow());
        assertFalse(ClaimGrid.region(0).contains(10000, 0));
        assertTrue(ClaimGrid.slotAt(-1, 0).isEmpty());
        assertTrue(ClaimGrid.slotAt(ClaimGrid.LOBBY_X, ClaimGrid.LOBBY_Z).isEmpty());
    }
    @Test void currentStockAndRemovalHaveInverseScoreEffects() {
        BigInteger sixtyFour = BlockScorer.scaledPoints(64, 100, BlockScorer.DEFAULT);
        assertEquals(BigInteger.valueOf(64_000_000), sixtyFour);
        assertEquals(Rational.of(25), BlockScorer.marginal(64, 1, 100, BlockScorer.DEFAULT));
        assertEquals(Rational.of(-25), BlockScorer.marginal(65, -1, 100, BlockScorer.DEFAULT));
    }
    @Test void dailySelectionIsStableUniqueAndLevelFiltered() {
        List<DailyShopSelector.Candidate> pool = List.of(new DailyShopSelector.Candidate("a", 1, 0, 1, 64), new DailyShopSelector.Candidate("b", 2, 50, 1, 16));
        UUID id = UUID.fromString("7ba5daed-61f3-4c17-b540-cc9f54c66e0a");
        var first = DailyShopSelector.roll(1, id, 8, 0, pool, 500, 12, 16, new int[]{0,70,25,5}, new int[]{0,15,50,35});
        var repeated = DailyShopSelector.roll(1, id, 8, 0, pool, 500, 12, 16, new int[]{0,70,25,5}, new int[]{0,15,50,35});
        assertEquals(first, repeated); assertEquals(1, first.offers().size()); assertEquals("a", first.offers().getFirst().productId());
    }
}
