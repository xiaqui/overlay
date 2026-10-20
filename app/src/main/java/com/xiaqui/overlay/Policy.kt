package com.xiaqui.overlay

data class BarState(val known: Boolean=false,val top: Int=0,val bottom: Int=0,val left: Int=0,val right: Int=0,val detail: String="unavailable")
data class CornerOffset(val x: Int=0,val y: Int=0)

/** Android-independent decisions, also exercised on the JVM. */
object Policy {
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
        val topInside=mode==TOP_INSIDE || mode==BOTH_INSIDE
        val bottomInside=mode==BOTTOM_INSIDE || mode==BOTH_INSIDE
        if(corner<2)return CornerOffset(y=if(topInside && bars.known)bars.top.coerceAtLeast(0) else 0)
        return if(bottomInside)CornerOffset(x=(if(corner%2==0)bars.left else bars.right).coerceAtLeast(0),y=bars.bottom.coerceAtLeast(0)) else CornerOffset()
    }
    // exception: 0=automatic, 1=always show, 2=always hide (only in fullscreen-only mode).
    fun statusVisible(fullscreenOnly: Boolean,exception: Int,bars: BarState)=
        !fullscreenOnly || when(exception) {1->true;2->false;else->!bars.known || bars.top==0}
    fun batteryPercent(level: Int,scale: Int): Int =
        if(level<0 || scale<=0)-1 else (100L*level/scale).coerceIn(0L,100L).toInt()
    fun windowTypes(sdk: Int,modern: Boolean): List<Int> = when {
        sdk<26->listOf(2006)
        modern->listOf(2038)
        else->listOf(2006,2038)
    }
    fun nextMinute(now: Long): Long=60_000L-((now%60_000L+60_000L)%60_000L)
}
