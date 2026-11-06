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
        for(c in 0..3)assertEquals(CornerOffset(),Policy.cornerOffset(c,Policy.BOTH_INSIDE,BarState(false,top=80,left=80,right=80,bottom=80)))
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
        assertTrue(Policy.statusVisible(true,3,BarState(true,top=24)))
        assertTrue(Policy.statusVisible(true,4,BarState(true,top=24)))
        for(e in 0..4)assertTrue(Policy.statusVisible(false,e,BarState(true,top=24)))
    }
    @Test fun fullscreenExceptionsSelectElementsWithoutOverridingGlobalOff() {
        assertEquals(Policy.DISPLAY_AUTO,Policy.displayRule(-1))
        assertEquals(Policy.DISPLAY_NONE,Policy.displayRule(999))
        for(rule in 0..4)assertEquals(rule,Policy.displayRule(rule))
        val normal=BarState(true,top=24)
        val hidden=BarState(true,top=0)
        assertEquals(0,Policy.statusElements(true,Policy.DISPLAY_AUTO,normal,3))
        assertEquals(3,Policy.statusElements(true,Policy.DISPLAY_AUTO,hidden,3))
        assertEquals(3,Policy.statusElements(true,Policy.DISPLAY_BOTH,normal,3))
        assertEquals(0,Policy.statusElements(true,Policy.DISPLAY_NONE,hidden,3))
        assertEquals(Policy.STATUS_CLOCK,Policy.statusElements(true,Policy.DISPLAY_CLOCK,normal,3))
        assertEquals(Policy.STATUS_BATTERY,Policy.statusElements(true,Policy.DISPLAY_BATTERY,normal,3))
        assertEquals(0,Policy.statusElements(true,Policy.DISPLAY_CLOCK,normal,Policy.STATUS_BATTERY))
        assertEquals(0,Policy.statusElements(true,Policy.DISPLAY_BATTERY,normal,Policy.STATUS_CLOCK))
        for(rule in 0..4)assertEquals(Policy.STATUS_CLOCK,Policy.statusElements(false,rule,normal,Policy.STATUS_CLOCK))
    }
    @Test fun supportedWindowTypes() {
        for(sdk in 23..25)assertEquals(listOf(2006),Policy.windowTypes(sdk,true))
        for(sdk in 26..29)assertEquals(listOf(2006,2038),Policy.windowTypes(sdk,false))
        for(sdk in 26..29)assertEquals(listOf(2038),Policy.windowTypes(sdk,false,true))
        assertEquals(listOf(2006),Policy.windowTypes(23,false,true))
        assertTrue(Policy.shouldRetryModernWindow(26,2006,2006,true,false))
        assertFalse("overlay permission loss must not masquerade as a legacy-type rejection",
            Policy.shouldRetryModernWindow(26,2006,2006,false,false))
        assertFalse(Policy.shouldRetryModernWindow(25,2006,2006,true,false))
        assertFalse(Policy.shouldRetryModernWindow(26,2038,2038,true,false))
        assertFalse(Policy.shouldRetryModernWindow(26,2006,2002,true,false))
        assertFalse(Policy.shouldRetryModernWindow(26,2006,2006,true,true))
    }
    @Test fun minuteSchedulingNeverBusyLoops() {
        assertEquals(60_000L,Policy.nextMinute(60_000));assertEquals(1L,Policy.nextMinute(119_999));assertEquals(1L,Policy.nextMinute(-1))
    }
}

