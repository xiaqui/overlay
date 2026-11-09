package com.xiaqui.overlay

import org.junit.Test
import org.junit.Assert.*

class BatteryGeometryTest {
    @Test fun transformsAndBoundsAtAllExtremes() {
        for(direction in 0..2)for(size in listOf(10f,14f,40f))for(aspect in listOf(60,100,160)) {
            val g=BatteryGeometry(size,1f,aspect,direction)
            assertTrue(g.longSide>2*g.inset);assertTrue(g.shortSide>2*g.inset)
            for(x in listOf(0f,g.longSide,g.longSide+g.cap))for(y in listOf(0f,g.shortSide)) {
                val p=g.point(x,y)
                assertTrue(p.first>=0 && p.first<=g.width)
                assertTrue(p.second>=0 && p.second<=g.height)
            }
            val low=g.point(g.inset,g.shortSide/2)
            val high=g.point(g.longSide-g.inset,g.shortSide/2)
            when(direction) {
                0->assertTrue(low.first<high.first)
                1->assertTrue(low.first>high.first)
                2->assertTrue(low.second>high.second)
            }
            assertEquals(0f,g.fillFraction(-1),0f);assertEquals(1f,g.fillFraction(999),0f)
        }
    }
}
