package com.xiaqui.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import android.text.format.DateFormat
import java.util.Date

/** Original geometry; no assets or code from reference APKs. */
class StatusView(
    context: Context,
    private val settings: Settings,
    private val elements: Int = ELEMENT_CLOCK or ELEMENT_BATTERY
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var percent = -1
    var charging = false
    internal var clockTextOverride: String? = null
    private val d get() = resources.displayMetrics.density
    // The saved value is a base sp size. scaledDensity applies the OS fontScale once.
    private val size get() = settings.number("size", 14) * resources.displayMetrics.scaledDensity
    private fun clockEnabled()=elements and ELEMENT_CLOCK!=0 && settings.bool("clock",true)
    private fun batteryEnabled()=elements and ELEMENT_BATTERY!=0 && settings.bool("battery",true)
    private fun applyStatusShadow() {
        // Keep the Xperia-style edge readable without surrounding every stroke with a halo.
        // A small right/down displacement matches the reference FloatClock more closely.
        paint.setShadowLayer(SHADOW_RADIUS_DP*d,SHADOW_DX_DP*d,SHADOW_DY_DP*d,SHADOW_COLOR)
    }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun clockText(): String = clockTextOverride ?: DateFormat.getTimeFormat(context).format(Date())
    private fun textWidth(): Float {
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        return if (clockEnabled()) paint.measureText(clockText()) else 0f
    }
    private fun geometry()=BatteryGeometry(settings.batterySize()*resources.displayMetrics.scaledDensity,
        d,settings.batteryAspect(),settings.batteryDirection())
    internal fun batterySlotWidth(): Float {
        val g=geometry()
        if(settings.batteryDirection()==2)return g.width+g.stroke
        return maxOf(g.width+g.stroke,settings.batterySize()*resources.displayMetrics.scaledDensity*1.18f)
    }
    override fun onMeasure(w: Int, h: Int) {
        val batteryWidth = if (batteryEnabled()) batterySlotWidth() else 0f
        val gap = if (batteryWidth > 0 && clockEnabled()) 4 * d else 0f
        val baseSize=if(clockEnabled())size else settings.batterySize()*resources.displayMetrics.scaledDensity
        setMeasuredDimension(kotlin.math.ceil(textWidth() + batteryWidth + gap + 6*d).toInt().coerceAtLeast(1),
            maxOf((baseSize*1.35f+6*d).toInt(),if(batteryEnabled())kotlin.math.ceil(geometry().height+geometry().stroke+6*d).toInt() else 1))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = when (settings.number("color", 0)) { 1 -> Color.LTGRAY; 2 -> Color.DKGRAY; else -> Color.WHITE }
        paint.style = Paint.Style.FILL
        applyStatusShadow()
        var x = 3*d
        if (batteryEnabled()) {
            drawBattery(canvas,x)
            x += batterySlotWidth() + if (clockEnabled()) 4*d else 0f
        }
        if (clockEnabled()) {
            paint.color = when(settings.number("color",0)) {1->Color.LTGRAY;2->Color.DKGRAY;else->Color.WHITE}
            applyStatusShadow()
            paint.textSize=size; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            canvas.drawText(clockText(), x, height/2f-(paint.ascent()+paint.descent())/2f, paint)
        }
    }
    private fun drawBattery(canvas: Canvas,x: Float) {
        val g=geometry()
        val top=(height-g.height)/2f
        fun rectangle(path: Path,l: Float,t: Float,r: Float,b: Float) {
            val a=g.point(l,t);val q=g.point(r,t);val z=g.point(r,b);val v=g.point(l,b)
            path.moveTo(x+a.first,top+a.second);path.lineTo(x+q.first,top+q.second)
            path.lineTo(x+z.first,top+z.second);path.lineTo(x+v.first,top+v.second);path.close()
        }
        val body=Path();rectangle(body,0f,0f,g.longSide,g.shortSide)
        val terminal=Path()
        rectangle(terminal,g.longSide,g.shortSide*0.31f,g.longSide+g.cap,g.shortSide*0.69f)
        val silhouette=Path(body).apply { addPath(terminal) }
        val faceColor=paint.color
        paint.style=Paint.Style.FILL
        applyStatusShadow()
        // Only the soft outside shadow; empty capacity has independent contrast.
        canvas.save()
        @Suppress("DEPRECATION")
        canvas.clipPath(silhouette,Region.Op.DIFFERENCE)
        canvas.drawPath(silhouette,paint)
        canvas.restore()
        paint.clearShadowLayer()
        val inner=Path()
        rectangle(inner,g.inset,g.inset,g.longSide-g.inset,g.shortSide-g.inset)
        paint.color=BATTERY_EMPTY_COLOR
        canvas.drawPath(inner,paint)
        paint.color=faceColor
        paint.style=Paint.Style.STROKE;paint.strokeWidth=g.stroke
        canvas.drawPath(body,paint)
        paint.style=Paint.Style.FILL
        canvas.drawPath(terminal,paint)
        if(percent>=0) {
            val fill=Path()
            rectangle(fill,g.inset,g.inset,
                g.inset+(g.longSide-2*g.inset)*g.fillFraction(percent),g.shortSide-g.inset)
            canvas.drawPath(fill,paint)
        }
        if(charging) {
            val points=batteryBoltPoints(false)
            val bolt=Path()
            for(i in points.indices step 2) {
                val p=g.point(g.longSide*points[i],g.shortSide*points[i+1])
                if(i==0)bolt.moveTo(x+p.first,top+p.second)
                else bolt.lineTo(x+p.first,top+p.second)
            }
            bolt.close()
            paint.style=Paint.Style.STROKE;paint.strokeJoin=Paint.Join.ROUND
            paint.strokeWidth=minOf(maxOf(0.65f*d,settings.batterySize()*resources.displayMetrics.scaledDensity*0.04f),
                g.shortSide*0.12f,g.longSide*0.12f)
            canvas.drawPath(bolt,paint)
            paint.style=Paint.Style.FILL;paint.color=Color.DKGRAY
            canvas.drawPath(bolt,paint)
        }
    }
    companion object {
        internal fun batteryAnchor(text: String,size: Float,density: Float): Float {
            val paint=Paint().apply {
                textSize=size;typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            }
            return paint.measureText(text)+4*density
        }
        internal const val ELEMENT_CLOCK=Policy.STATUS_CLOCK
        internal const val ELEMENT_BATTERY=Policy.STATUS_BATTERY
        internal const val SHADOW_RADIUS_DP=0.55f
        internal const val SHADOW_DX_DP=0.55f
        internal const val SHADOW_DY_DP=0.65f
        internal const val SHADOW_COLOR=0xB8000000.toInt()
        internal const val BATTERY_EMPTY_COLOR=0x70000000
        private val BOLT_POINTS=floatArrayOf(
            0.58f,0.08f, 0.30f,0.56f, 0.49f,0.56f,
            0.40f,0.94f, 0.72f,0.43f, 0.53f,0.43f
        )
        internal fun batteryBoltPoints(reversed: Boolean)=FloatArray(BOLT_POINTS.size) { i->
            if(reversed && i%2==0)1f-BOLT_POINTS[i] else BOLT_POINTS[i]
        }
    }
}

class CornerView(context: Context, private val corner: Int) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val r=width.toFloat()
        canvas.save()
        if (corner==1 || corner==3) { canvas.translate(r,0f);canvas.scale(-1f,1f) }
        if (corner>=2) { canvas.translate(0f,r);canvas.scale(1f,-1f) }
        val p=Path().apply {
            moveTo(0f,0f);lineTo(r,0f);arcTo(RectF(0f,0f,2*r,2*r),270f,-90f);close()
        }
        canvas.drawPath(p,paint);canvas.restore()
    }
}



