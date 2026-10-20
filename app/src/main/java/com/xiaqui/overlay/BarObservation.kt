package com.xiaqui.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Two transparent 1px strips observe insets without allocating a full-screen surface. */
@Suppress("DEPRECATION")
class BarObservation(context: Context, private val changed: () -> Unit) {
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
        // Stable insets only cap IME/oversized values, never establish visibility.
        val bottom=v.systemWindowInsetBottom.coerceIn(0,v.stableInsetBottom.coerceAtLeast(0))
        val left=h.systemWindowInsetLeft.coerceIn(0,h.stableInsetLeft.coerceAtLeast(0))
        val right=h.systemWindowInsetRight.coerceIn(0,h.stableInsetRight.coerceAtLeast(0))
        val cutout=if(Build.VERSION.SDK_INT>=28)v.displayCutout?.safeInsetTop ?: 0 else 0
        val top=v.systemWindowInsetTop.coerceAtLeast(0)
        val frame=Rect();vertical.getWindowVisibleDisplayFrame(frame)
        return BarState(known=cutout==0,top=if(cutout==0)top else 0,bottom=bottom,left=left,right=right,
            detail="insets=$top,$bottom,$left,$right frameTop=${frame.top} cutout=$cutout")
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
