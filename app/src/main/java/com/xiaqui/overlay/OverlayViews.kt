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
    override fun onMeasure(w: Int, h: Int) {
        val batteryWidth = if (batteryEnabled()) size * 1.18f else 0f
        val gap = if (batteryWidth > 0 && clockEnabled()) 4 * d else 0f
        setMeasuredDimension(kotlin.math.ceil(textWidth() + batteryWidth + gap + 6*d).toInt().coerceAtLeast(1), (size*1.35f+6*d).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = when (settings.number("color", 0)) { 1 -> Color.LTGRAY; 2 -> Color.DKGRAY; else -> Color.WHITE }
        paint.style = Paint.Style.FILL
        applyStatusShadow()
        var x = 3*d
        if (batteryEnabled()) {
            val bw = size*0.94f
            val bh = size*0.56f
            val by = (height-bh)/2f
            val cap = maxOf(1.2f*d,size*0.08f)
            val reversed=settings.batteryReversed()
            val bodyX=if(reversed)x+cap else x
            // Keep the original battery frame, terminal and capacity geometry intact.
            // A single solid, slightly displaced silhouette is the shadow behind ALL of
            // those shapes, including the terminal. Do not paint a second inset rectangle:
            // its mismatched edges made the tiny terminal appear like a dark square.
            val faceColor=paint.color
            val terminalLeft=if(reversed)bodyX-cap else bodyX+bw
            val terminalRight=if(reversed)bodyX else bodyX+bw+cap
            paint.clearShadowLayer()
            paint.style=Paint.Style.FILL
            paint.color=BATTERY_SILHOUETTE_SHADOW_COLOR
            canvas.save()
            canvas.translate(SHADOW_DX_DP*d,SHADOW_DY_DP*d)
            canvas.drawRect(bodyX,by,bodyX+bw,by+bh,paint)
            canvas.drawRect(terminalLeft,by+bh*0.31f,terminalRight,by+bh*0.69f,paint)
            canvas.restore()

            // Foreground: the same thin white outline, tiny terminal and filled capacity
            // as before. The dark shadow visible through empty capacity supplies contrast.
            paint.color=faceColor
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=maxOf(0.8f*d,size*0.045f)
            canvas.drawRect(bodyX,by,bodyX+bw,by+bh,paint)
            paint.style=Paint.Style.FILL
            canvas.drawRect(terminalLeft,by+bh*0.31f,terminalRight,by+bh*0.69f,paint)
            if (percent >= 0) {
                val inset=maxOf(1.2f*d,size*0.07f)
                val innerLeft=bodyX+inset
                val innerRight=bodyX+bw-inset
                val filled=(innerRight-innerLeft)*percent.coerceIn(0,100)/100f
                if(reversed)canvas.drawRect(innerRight-filled,by+inset,innerRight,by+bh-inset,paint)
                else canvas.drawRect(innerLeft,by+inset,innerLeft+filled,by+bh-inset,paint)
            }
            if (charging) {
                paint.clearShadowLayer(); paint.color = Color.DKGRAY
                val points=batteryBoltPoints(reversed)
                val bolt = Path().apply {
                    moveTo(bodyX+bw*points[0],by+bh*points[1])
                    var i=2
                    while(i<points.size) {
                        lineTo(bodyX+bw*points[i],by+bh*points[i+1]);i+=2
                    }
                    close()
                }
                // The dark bolt alone vanishes against the dark empty-capacity
                // silhouette. Give it a narrow light keyline, then retain its
                // original dark fill so it also remains visible over white charge.
                paint.style=Paint.Style.STROKE
                paint.strokeJoin=Paint.Join.ROUND
                paint.strokeWidth=maxOf(0.65f*d,size*0.04f)
                paint.color=faceColor
                canvas.drawPath(bolt,paint)
                paint.style=Paint.Style.FILL
                paint.color=Color.DKGRAY
                canvas.drawPath(bolt,paint)
            }
            x += size*1.18f + if (clockEnabled()) 4*d else 0f
        }
        if (clockEnabled()) {
            paint.color = when(settings.number("color",0)) {1->Color.LTGRAY;2->Color.DKGRAY;else->Color.WHITE}
            applyStatusShadow()
            paint.textSize=size; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            canvas.drawText(clockText(), x, height/2f-(paint.ascent()+paint.descent())/2f, paint)
        }
    }
    companion object {
        internal const val ELEMENT_CLOCK=Policy.STATUS_CLOCK
        internal const val ELEMENT_BATTERY=Policy.STATUS_BATTERY
        internal const val SHADOW_RADIUS_DP=0.55f
        internal const val SHADOW_DX_DP=0.55f
        internal const val SHADOW_DY_DP=0.65f
        internal const val SHADOW_COLOR=0xB8000000.toInt()
        internal const val BATTERY_SILHOUETTE_SHADOW_COLOR=0xB8000000.toInt()
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


