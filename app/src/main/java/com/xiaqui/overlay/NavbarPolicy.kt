package com.xiaqui.overlay

data class NavbarColor(val color: Int,val source: String)

/** No Android dependencies; never infer a visible bar from its saved/stable size. */
object NavbarPolicy {
    fun eligible(enabled: Boolean,portrait: Boolean,threeButtons: Boolean,bars: BarState)=
        enabled && portrait && threeButtons && bars.known && bars.bottom>0 && bars.left==0 && bars.right==0

    fun select(fixed: Int?,automatic: Boolean,theme: ()->Int?,icon: ()->Int?): NavbarColor? {
        if(fixed!=null)return NavbarColor(fixed,"固定色")
        if(!automatic)return null
        theme()?.let { return NavbarColor(darken(it),"テーマ推定") }
        return icon()?.let { NavbarColor(darken(it),"アイコン推定") }
    }
    // SystemUI owns button contrast. Keep automatic candidates subdued; fixed colors are exact.
    fun darken(color: Int): Int {
        val r=color ushr 16 and 255;val g=color ushr 8 and 255;val b=color and 255
        val peak=maxOf(r,g,b)
        if(peak<=160)return color or (255 shl 24)
        return (255 shl 24) or ((r*160/peak) shl 16) or ((g*160/peak) shl 8) or (b*160/peak)
    }
    fun representative(pixels: IntArray): Int? {
        val bins=IntArray(4096)
        var saturated=false
        for(c in pixels)if(c ushr 24>=192) {
            val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
            if(maxOf(r,g,b)-minOf(r,g,b)>=32)saturated=true
        }
        for(c in pixels)if(c ushr 24>=192) {
            val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
            if(!saturated || maxOf(r,g,b)-minOf(r,g,b)>=32)bins[((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)]++
        }
        val best=bins.indices.maxByOrNull { bins[it] } ?: return null
        if(bins[best]==0)return null
        return (255 shl 24) or (((best shr 8)*16+8) shl 16) or ((((best shr 4) and 15)*16+8) shl 8) or ((best and 15)*16+8)
    }
}
