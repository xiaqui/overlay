package com.xiaqui.overlay

import org.junit.Test
import org.junit.Assert.*

class NavbarPolicyTest {
    @Test fun onlyKnownPortraitVisibleThreeButtonBarIsEligible() {
        val b=BarState(true,bottom=48)
        assertTrue(NavbarPolicy.eligible(true,true,true,b))
        assertFalse(NavbarPolicy.eligible(false,true,true,b))
        assertFalse(NavbarPolicy.eligible(true,false,true,b))
        assertFalse(NavbarPolicy.eligible(true,true,false,b))
        assertFalse(NavbarPolicy.eligible(true,true,true,BarState(false,bottom=48)))
        assertFalse(NavbarPolicy.eligible(true,true,true,BarState(true)))
        assertFalse(NavbarPolicy.eligible(true,true,true,BarState(true,bottom=48,right=48)))
    }
    @Test fun priorityAndAutoOffAvoidUnnecessaryResourceWork() {
        val fixed=0xff123456.toInt()
        val noLoad: ()->Int? = { throw AssertionError("lower priority loader must not run") }
        assertEquals(NavbarColor(fixed,"固定色"),NavbarPolicy.select(fixed,true,noLoad,noLoad))
        assertNull(NavbarPolicy.select(null,false,noLoad,noLoad))
        assertEquals("テーマ推定",NavbarPolicy.select(null,true,{fixed},noLoad)!!.source)
        assertEquals("アイコン推定",NavbarPolicy.select(null,true,{null},{fixed})!!.source)
        assertNull(NavbarPolicy.select(null,true,{null},{null}))
    }
    @Test fun automaticLightColorsAreSubduedButFixedColorsAreExact() {
        val white=0xffffffff.toInt()
        assertEquals(0xffa0a0a0.toInt(),NavbarPolicy.darken(white))
        assertEquals(white,NavbarPolicy.select(white,true,{null},{null})!!.color)
        assertEquals(0xff304050.toInt(),NavbarPolicy.darken(0xff304050.toInt()))
    }
    @Test fun iconHistogramIgnoresTransparentPixelsAndPrefersChromaticContent() {
        assertNull(NavbarPolicy.representative(intArrayOf(0,0x00123456)))
        val p=IntArray(100) { if(it<90)0xffffffff.toInt() else 0xff123456.toInt() }
        assertEquals(0xff183858.toInt(),NavbarPolicy.representative(p))
        assertEquals(0xff080808.toInt(),NavbarPolicy.representative(intArrayOf(0xff000000.toInt())))
    }
}
