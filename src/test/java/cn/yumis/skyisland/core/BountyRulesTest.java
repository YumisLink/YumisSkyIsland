package cn.yumis.skyisland.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BountyRulesTest {
    @Test void exchangesRoundUpAndRejectTheUnclippedCap(){
        assertEquals(10,BountyRules.materialCount(1000,100,1536));
        assertEquals(2,BountyRules.materialCount(120,100,1536));
        assertEquals(1,BountyRules.materialCount(50,100,1536));
        assertEquals(1536,BountyRules.materialCount(153600,100,1536));
        assertEquals(0,BountyRules.materialCount(153601,100,1536));
        assertEquals(0,BountyRules.materialCount(0,100,1536));
        assertEquals(2,BountyRules.materialCount(Long.MAX_VALUE,Long.MAX_VALUE/2+1,1536));
    }
    @Test void paidSequenceIsIndependentOfLevelSlots(){
        assertEquals(100000,BountyRules.slotCost(0,100000,500000));
        assertEquals(500000,BountyRules.slotCost(1,100000,500000));
        assertEquals(1000000,BountyRules.slotCost(2,100000,500000));
        assertEquals(1500000,BountyRules.slotCost(3,100000,500000));
        assertThrows(ArithmeticException.class,()->BountyRules.slotCost(3,100000,Long.MAX_VALUE));
    }
}
