package com.xiaqui.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.WindowManager

data class NavbarSpec(val width: Int,val screenHeight: Int,val navHeight: Int,val radius: Int,val bottom: Boolean,val inside: Boolean,val color: NavbarColor) {
    val height get()=navHeight+if(bottom)radius else 0
    val top get()=screenHeight-height
}

/** A small portrait band owns BOTH fill and the lower masks, in that exact drawing order. */
class NavbarBandView(context: Context,var spec: NavbarSpec): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var draws=0;private set
    var painted: (() -> Unit)?=null
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        draws++
        paint.color=spec.color.color
        canvas.drawRect(0f,(height-spec.navHeight).toFloat(),width.toFloat(),height.toFloat(),paint)
        if(spec.bottom && spec.radius>0) {
            paint.color=Color.BLACK
            val r=spec.radius.toFloat()
            val y=height-r-(if(spec.inside)spec.navHeight else 0)
            for(right in listOf(false,true)) {
                canvas.save();canvas.translate(if(right)width.toFloat() else 0f,y+r)
                canvas.scale(if(right)-1f else 1f,-1f)
                val path=Path().apply { moveTo(0f,0f);lineTo(r,0f);arcTo(RectF(0f,0f,2*r,2*r),270f,-90f);close() }
                canvas.drawPath(path,paint);canvas.restore()
            }
        }
        painted?.let { callback->painted=null;post { callback() } }
    }
}

/** Transactional: the legacy lower windows remain until a correctly positioned band is visible. */
class NavbarLayer(private val context: Context,private val wm: WindowManager,
    private val params: (Int,Int)->WindowManager.LayoutParams,
    private val add: (View,WindowManager.LayoutParams)->Unit,private val remove: (View)->Unit,
    private val changed: ()->Unit) {
    var view: NavbarBandView?=null;private set
    var ready=false;private set
    var reason="未適用";private set
    private var failed: NavbarSpec?=null
    private var started=0L
    fun clear() {
        val old=view;view=null;ready=false
        if(old!=null)runCatching { remove(old) }
    }
    private fun reject(spec: NavbarSpec) { clear();failed=spec;reason="着色窓を配置できないため従来表示";changed() }
    fun apply(spec: NavbarSpec?): Boolean {
        if(spec==null) { clear();failed=null;reason="対象外・無着色";return false }
        if(failed==spec)return false
        val old=view
        if(old!=null && old.spec.width==spec.width && old.spec.height==spec.height && old.spec.top==spec.top) {
            if(!ready && android.os.SystemClock.elapsedRealtime()-started>5000) { reject(spec);return false }
            if(old.spec!=spec) { old.spec=spec;old.invalidate() }
            return ready
        }
        clear()
        val v=NavbarBandView(context,spec).apply { visibility=View.INVISIBLE }
        view=v;reason="配置待ち"
        started=android.os.SystemClock.elapsedRealtime()
        var activating=false
        v.painted={
            if(view===v && v.isAttachedToWindow && v.visibility==View.VISIBLE) {
                val p=IntArray(2);v.getLocationOnScreen(p)
                if(p[0]==0 && p[1]==spec.top && v.width==spec.width && v.height==spec.height) {
                    ready=true;reason="描画済み（実機の可視性は要検収）";changed()
                } else reject(spec)
            }
        }
        var lastPosition: Pair<Int,Int>?=null
        v.viewTreeObserver.addOnGlobalLayoutListener {
            if(view===v && v.isAttachedToWindow && v.width>0 && v.height>0) {
                val p=IntArray(2);v.getLocationOnScreen(p)
                if(p[0]==0 && p[1]==spec.top && v.width==spec.width && v.height==spec.height) {
                    lastPosition=null
                    if(!ready && !activating) {
                        activating=true;reason="初回描画待ち"
                        // A root visibility change during traversal can be lost by relayout.
                        // Force the WM visibility update on the NEXT traversal, then wait onDraw.
                        v.post {
                            if(view===v && v.isAttachedToWindow) {
                                v.visibility=View.VISIBLE;v.requestLayout();v.invalidate()
                                try { wm.updateViewLayout(v,v.layoutParams as WindowManager.LayoutParams) }
                                catch (_: RuntimeException) { reject(spec) }
                            }
                        }
                    }
                } else if(lastPosition==(p[0] to p[1]))reject(spec)
                else {
                    lastPosition=p[0] to p[1]
                    val lp=v.layoutParams as WindowManager.LayoutParams
                    lp.x-=p[0];lp.y+=spec.top-p[1]
                    try { wm.updateViewLayout(v,lp) } catch (_: RuntimeException) { reject(spec) }
                }
            }
        }
        try { add(v,params(spec.width,spec.height).apply { x=0;y=spec.top;title="Overlay Navbar Test" }) }
        catch (_: RuntimeException) { reject(spec) }
        return ready
    }
}
