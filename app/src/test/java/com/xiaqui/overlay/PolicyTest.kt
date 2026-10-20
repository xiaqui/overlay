package com.xiaqui.overlay
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun batteryHandlesMissingAndInvalidBroadcasts() {
        assertEquals(-1,Policy.batteryPercent(-1,100));assertEquals(-1,Policy.batteryPercent(1,0))
        assertEquals(50,Policy.batteryPercent(100,200));assertEquals(100,Policy.batteryPercent(Int.MAX_VALUE,1))
        assertEquals(0,Policy.batteryPercent(0,100))
    }
    @Test fun explicitNoCornersOverridesDefaults() {
        assertEquals(0,Policy.corners(3,0));assertEquals(2,Policy.corners(3,2));assertEquals(3,Policy.corners(3,null))
    }
    @Test fun fallbackNeverUsesNewWindowTypeBeforeOreo() {
        for(sdk in 23..25) assertEquals(listOf(2006),Policy.windowTypes(sdk,true))
        for(sdk in 26..29) {
            assertEquals(listOf(2006,2038),Policy.windowTypes(sdk,false))
            assertEquals(listOf(2038),Policy.windowTypes(sdk,true))
        }
    }
    @Test fun minuteSchedulingNeverBusyLoopsAtBoundary() {
        assertEquals(60000L,Policy.nextMinute(60000));assertEquals(1L,Policy.nextMinute(119999))
        assertEquals(1L,Policy.nextMinute(-1))
    }
}
