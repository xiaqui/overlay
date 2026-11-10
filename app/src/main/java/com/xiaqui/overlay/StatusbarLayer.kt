package com.xiaqui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.WindowManager

data class StatusbarSpec(val width: Int,val height: Int,val color: NavbarColor,val radius: Int=0,val topCorners: Boolean=false)

/** A non-touchable background band. It is registered before status content and upper corners. */
class StatusbarBandView(context: Context): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var spec: StatusbarSpec?=null
    var draws=0;private set
    var painted: (() -> Unit)?=null
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        draws++
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
        spec?.let {
            paint.color=it.color.color;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
            if(it.topCorners && it.radius>0) {
                paint.color=Color.BLACK
                val r=it.radius.toFloat()
                for(right in listOf(false,true)) {
                    canvas.save()
                    if(right) { canvas.translate(width.toFloat(),0f);canvas.scale(-1f,1f) }
                    val path=Path().apply { moveTo(0f,0f);lineTo(r,0f);arcTo(RectF(0f,0f,2*r,2*r),270f,-90f);close() }
                    canvas.drawPath(path,paint);canvas.restore()
                }
            }
        }
        painted?.let { callback->painted=null;post { callback() } }
    }
}

/** Failure is local to the optional top band; callers keep every existing overlay alive. */
class StatusbarLayer(private val context: Context,private val wm: WindowManager,
    private val params: (Int,Int)->WindowManager.LayoutParams,
    private val add: (View,WindowManager.LayoutParams)->Unit,
    private val remove: (View)->Unit,private val changed: ()->Unit) {
    var view: StatusbarBandView?=null;private set
    var ready=false;private set
    var reason="未適用";private set
    private var failed: StatusbarSpec?=null
    private var started=0L

    fun geometry(): LayerGeometry {
        val v=view
        val spec=v?.spec
        val lp=v?.layoutParams as? WindowManager.LayoutParams
        val actual=IntArray(2)
        if(v?.isAttachedToWindow==true)v.getLocationOnScreen(actual)
        return LayerGeometry(lp?.type,lp?.x,lp?.y,lp?.width,lp?.height,
            if(v?.isAttachedToWindow==true)actual[0] else null,
            if(v?.isAttachedToWindow==true)actual[1] else null,
            v?.width,v?.height,spec?.color?.color,ready,v?.draws ?: 0,reason)
    }

    fun attach(): Boolean {
        if(view!=null)return true
        return try {
            val v=StatusbarBandView(context)
            view=v
            add(v,params(1,1).apply { x=0;y=0;title="Overlay Statusbar Test" })
            reason="待機中";true
        } catch (_: RuntimeException) {
            view=null;ready=false;reason="上側着色窓を作れないため停止";false
        }
    }
    fun clear() {
        val old=view;view=null;ready=false
        if(old!=null)runCatching { remove(old) }
    }
    private fun hide(message: String="対象外・無着色") {
        val v=view ?: return
        ready=false;reason=message;v.spec=null;v.painted=null
        try {
            val lp=v.layoutParams as WindowManager.LayoutParams
            if(lp.width!=1 || lp.height!=1 || lp.x!=0 || lp.y!=0) {
                lp.width=1;lp.height=1;lp.x=0;lp.y=0;wm.updateViewLayout(v,lp)
            }
            v.invalidate()
        } catch (_: RuntimeException) { clear();reason="上側着色窓の更新を停止" }
    }
    private fun reject(spec: StatusbarSpec) {
        failed=spec;hide("上側着色を配置できないため停止");changed()
    }
    fun apply(spec: StatusbarSpec?,absentReason: String="対象外・無着色"): Boolean {
        val v=view ?: return false
        if(spec==null) { failed=null;hide(absentReason);return false }
        if(failed==spec)return false
        if(v.spec==spec) {
            if(ready)return true
            if(SystemClock.elapsedRealtime()-started>5000)reject(spec)
            return false
        }
        ready=false;reason="初回描画待ち";started=SystemClock.elapsedRealtime();v.spec=spec
        v.painted={
            if(view===v && v.isAttachedToWindow) {
                val p=IntArray(2);v.getLocationOnScreen(p)
                if(p[0]==0 && p[1]==0 && v.width==spec.width && v.height==spec.height) {
                    ready=true;reason="描画済み（実機の可視性は要検収）";changed()
                } else reject(spec)
            }
        }
        return try {
            val lp=v.layoutParams as WindowManager.LayoutParams
            lp.width=spec.width;lp.height=spec.height;lp.x=0;lp.y=0
            wm.updateViewLayout(v,lp);v.invalidate();false
        } catch (_: RuntimeException) { reject(spec);false }
    }
}

