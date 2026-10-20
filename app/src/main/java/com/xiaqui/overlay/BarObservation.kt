package com.xiaqui.overlay

import android.content.Context
import android.graphics.Rect
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Two transparent 1px strips observe insets without allocating a full-screen surface. */
@Suppress("DEPRECATION")
class BarObservation(private val context: Context, private val changed: () -> Unit) {
    private fun strip()=View(context).apply {
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(true)
        setOnApplyWindowInsetsListener { _, insets -> changed();insets }
        viewTreeObserver.addOnGlobalLayoutListener { changed() }
    }
    val vertical=strip()
    val horizontal=strip()
    val views get()=listOf(vertical,horizontal)
    fun read(): BarState {
        if(!vertical.isAttachedToWindow || !horizontal.isAttachedToWindow || vertical.height<=1 || horizontal.width<=1)return BarState()
        val v=vertical.rootWindowInsets ?: return BarState()
        val h=horizontal.rootWindowInsets ?: return BarState()
        val wm=vertical.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val real=Point();wm.defaultDisplay.getRealSize(real)
        val vp=IntArray(2);val hp=IntArray(2)
        vertical.getLocationOnScreen(vp);horizontal.getLocationOnScreen(hp)
        // Policy can clip the observer's frame to the nav bar before calculating insets.
        // Combine that frame gap with the remaining content inset; insets alone lose nav.
        fun dimension(name: String): Int {
            val id=vertical.resources.getIdentifier(name,"dimen","android")
            return if(id==0)0 else vertical.resources.getDimensionPixelSize(id)
        }
        val navHeight=maxOf(dimension("navigation_bar_height"),dimension("navigation_bar_height_landscape"),v.stableInsetBottom)
        val navWidth=maxOf(dimension("navigation_bar_width"),h.stableInsetLeft,h.stableInsetRight)
        val rawBottom=real.y-vp[1]-vertical.height+v.systemWindowInsetBottom
        val rawLeft=hp[0]+h.systemWindowInsetLeft
        val rawRight=real.x-hp[0]-horizontal.width+h.systemWindowInsetRight
        // Stable/resources cap IME/oversized values, but never establish visibility.
        val bottom=rawBottom.coerceIn(0,navHeight.coerceAtLeast(0))
        val left=rawLeft.coerceIn(0,navWidth.coerceAtLeast(0))
        val right=rawRight.coerceIn(0,navWidth.coerceAtLeast(0))
        val cutouts=if(Build.VERSION.SDK_INT>=28)listOfNotNull(v.displayCutout,h.displayCutout) else emptyList()
        val cutout=if(Build.VERSION.SDK_INT>=28)cutouts.any { it.safeInsetTop>0 || it.safeInsetBottom>0 || it.safeInsetLeft>0 || it.safeInsetRight>0 } else false
        val top=(vp[1]+v.systemWindowInsetTop).coerceAtLeast(0)
        val plausibleTop=top<=maxOf(dimension("status_bar_height"),v.stableInsetTop)
        val frame=Rect();vertical.getWindowVisibleDisplayFrame(frame)
        return BarState(known=!cutout && plausibleTop,top=if(!cutout && plausibleTop)top else 0,bottom=if(cutout)0 else bottom,left=if(cutout)0 else left,right=if(cutout)0 else right,
            detail="insets=${v.systemWindowInsetTop},${v.systemWindowInsetBottom},${h.systemWindowInsetLeft},${h.systemWindowInsetRight} frame=${vp[1]},${vertical.height},${hp[0]},${horizontal.width} real=$real visibleTop=${frame.top} cutout=$cutout")
    }

    companion object {
        fun params(vertical: Boolean,sdk: Int)=WindowManager.LayoutParams(
            if(vertical)1 else WindowManager.LayoutParams.MATCH_PARENT,
            if(vertical)WindowManager.LayoutParams.MATCH_PARENT else 1,
            if(sdk>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity=if(vertical)android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.LEFT or android.view.Gravity.CENTER_VERTICAL
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            title=if(vertical)"Overlay bar observer V" else "Overlay bar observer H"
            if(sdk>=28)layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
