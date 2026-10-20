package com.xiaqui.overlay
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun batteryHandlesInvalidAndOverflow() {
        assertEquals(-1,Policy.batteryPercent(-1,100));assertEquals(-1,Policy.batteryPercent(1,0))
        assertEquals(50,Policy.batteryPercent(100,200));assertEquals(100,Policy.batteryPercent(Int.MAX_VALUE,1))
    }
    @Test fun exactFiveLabelsAndInvalidDefault() {
        assertEquals(listOf("なし","通常","上だけ内側","下だけ内側","上下とも内側"),Policy.cornerLabels)
        assertEquals(Policy.NORMAL,Policy.mode(-1));assertEquals(Policy.NORMAL,Policy.mode(999))
    }
    @Test fun legacyVisibilityNeverBecomesPositionOffset() {
        assertEquals(Policy.NONE,Policy.migrateLegacy(0))
        for(mask in 1..3)assertEquals(Policy.NORMAL,Policy.migrateLegacy(mask))
    }
    @Test fun globalSwitchesAndPerAppNone() {
        for(mode in 0..4)assertEquals(0,Policy.cornerMask(0,mode))
        assertEquals(0,Policy.cornerMask(3,Policy.NONE))
        for(mode in 1..4)assertEquals(3,Policy.cornerMask(3,mode))
        assertEquals(1,Policy.cornerMask(1,Policy.BOTH_INSIDE))
    }
    @Test fun fiveModesProduceCorrectPortraitOffsets() {
        val b=BarState(true,top=24,bottom=48)
        for(c in 0..3) {
            assertEquals(CornerOffset(),Policy.cornerOffset(c,Policy.NORMAL,b))
            assertEquals(CornerOffset(y=if(c<2)24 else 0),Policy.cornerOffset(c,Policy.TOP_INSIDE,b))
            assertEquals(CornerOffset(y=if(c>=2)48 else 0),Policy.cornerOffset(c,Policy.BOTTOM_INSIDE,b))
            assertEquals(CornerOffset(y=if(c<2)24 else 48),Policy.cornerOffset(c,Policy.BOTH_INSIDE,b))
        }
    }
    @Test fun hiddenBarsAreZeroAndUnknownTopIsNotAssumedVisible() {
        for(c in 0..3)assertEquals(CornerOffset(),Policy.cornerOffset(c,Policy.BOTH_INSIDE,BarState(true)))
        assertEquals(CornerOffset(),Policy.cornerOffset(0,Policy.TOP_INSIDE,BarState(false,top=80)))
    }
    @Test fun landscapeNavMovesOnlyCorrespondingLowerCorner() {
        val right=BarState(true,right=42)
        assertEquals(CornerOffset(),Policy.cornerOffset(0,Policy.BOTH_INSIDE,right))
        assertEquals(CornerOffset(),Policy.cornerOffset(2,Policy.BOTTOM_INSIDE,right))
        assertEquals(CornerOffset(x=42),Policy.cornerOffset(3,Policy.BOTTOM_INSIDE,right))
        assertEquals(CornerOffset(x=42),Policy.cornerOffset(2,Policy.BOTTOM_INSIDE,BarState(true,left=42)))
    }
    @Test fun normalTransparentAndFullscreenVisibility() {
        assertFalse(Policy.statusVisible(true,0,BarState(true,top=24)))
        assertTrue(Policy.statusVisible(true,0,BarState(true,top=0,bottom=48)))
        assertTrue(Policy.statusVisible(true,0,BarState()))
    }
    @Test fun appExceptionsDoNotChangeAlwaysMode() {
        assertTrue(Policy.statusVisible(true,1,BarState(true,top=24)))
        assertFalse(Policy.statusVisible(true,2,BarState(true)))
        for(e in 0..2)assertTrue(Policy.statusVisible(false,e,BarState(true,top=24)))
    }
    @Test fun supportedWindowTypes() {
        for(sdk in 23..25)assertEquals(listOf(2006),Policy.windowTypes(sdk,true))
        for(sdk in 26..29)assertEquals(listOf(2006,2038),Policy.windowTypes(sdk,false))
    }
    @Test fun minuteSchedulingNeverBusyLoops() {
        assertEquals(60_000L,Policy.nextMinute(60_000));assertEquals(1L,Policy.nextMinute(119_999));assertEquals(1L,Policy.nextMinute(-1))
    }
}
