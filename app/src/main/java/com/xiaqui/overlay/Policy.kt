package com.xiaqui.overlay

data class BarState(val known: Boolean=false,val top: Int=0,val bottom: Int=0,val left: Int=0,val right: Int=0,val detail: String="unavailable")
data class CornerOffset(val x: Int=0,val y: Int=0)

/** Android-independent decisions, also exercised on the JVM. */
object Policy {
    const val STATUS_CLOCK=1
    const val STATUS_BATTERY=2
    const val DISPLAY_AUTO=0
    const val DISPLAY_ALWAYS=1
    const val DISPLAY_NONE=2
    fun displayRule(value: Int)=when {
        value<DISPLAY_AUTO || value==3 || value==4->DISPLAY_AUTO
        value>DISPLAY_NONE->DISPLAY_NONE // Preserve old behavior for unrecognized high values.
        else->value
    }
    const val NONE=0
    const val NORMAL=1
    const val TOP_INSIDE=2
    const val BOTTOM_INSIDE=3
    const val BOTH_INSIDE=4
    val cornerLabels=listOf("なし","通常","上だけ内側","下だけ内側","上下とも内側")
    fun mode(value: Int)=if(value in NONE..BOTH_INSIDE)value else NORMAL
    fun migrateLegacy(value: Int)=if(value==0)NONE else NORMAL
    fun cornerMask(globalMask: Int,mode: Int)=if(mode==NONE)0 else globalMask and 3
    fun cornerOffset(corner: Int,mode: Int,bars: BarState): CornerOffset {
        if(!bars.known)return CornerOffset()
        val topInside=mode==TOP_INSIDE || mode==BOTH_INSIDE
        val bottomInside=mode==BOTTOM_INSIDE || mode==BOTH_INSIDE
        if(corner<2)return CornerOffset(y=if(topInside && bars.known)bars.top.coerceAtLeast(0) else 0)
        return if(bottomInside)CornerOffset(x=(if(corner%2==0)bars.left else bars.right).coerceAtLeast(0),y=bars.bottom.coerceAtLeast(0)) else CornerOffset()
    }
    fun statusVisible(fullscreenOnly: Boolean,exception: Int,bars: BarState)=
        when(displayRule(exception)) {
            DISPLAY_ALWAYS->true
            DISPLAY_NONE->false
            else->!fullscreenOnly || !bars.known || bars.top==0
        }
    fun statusElements(fullscreenOnly: Boolean,exception: Int,bars: BarState,global: Int): Int {
        val allowed=global and (STATUS_CLOCK or STATUS_BATTERY)
        return if(statusVisible(fullscreenOnly,exception,bars))allowed else 0
    }
    fun batteryPercent(level: Int,scale: Int): Int =
        if(level<0 || scale<=0)-1 else (100L*level/scale).coerceIn(0L,100L).toInt()
    fun windowTypes(sdk: Int,modern: Boolean,legacyRejected: Boolean=false): List<Int> = when {
        sdk<26->listOf(2006)
        modern || legacyRejected->listOf(2038)
        else->listOf(2006,2038)
    }
    fun shouldRetryModernWindow(sdk: Int,activeType: Int,attemptedType: Int,
        overlayAllowed: Boolean,legacyRejected: Boolean): Boolean =
        sdk>=26 && activeType==2006 && attemptedType==2006 && overlayAllowed && !legacyRejected
    fun nextMinute(now: Long): Long=60_000L-((now%60_000L+60_000L)%60_000L)
}

