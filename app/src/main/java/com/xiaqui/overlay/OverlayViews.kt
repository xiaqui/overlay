package com.xiaqui.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import android.text.format.DateFormat
import java.util.Date

/** Original geometry; no assets or code from reference APKs. */
class StatusView(context: Context, private val settings: Settings) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var percent = -1
    var charging = false
    private val d get() = resources.displayMetrics.density
    private val size get() = settings.number("size", 14) * d
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun clockText(): String = DateFormat.getTimeFormat(context).format(Date())
    private fun textWidth(): Float {
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        return if (settings.bool("clock", true)) paint.measureText(clockText()) else 0f
    }
    override fun onMeasure(w: Int, h: Int) {
        val batteryWidth = if (settings.bool("battery", true)) size * 0.9f else 0f
        val gap = if (batteryWidth > 0 && settings.bool("clock", true)) 5 * d else 0f
        setMeasuredDimension(kotlin.math.ceil(textWidth() + batteryWidth + gap + 6*d).toInt().coerceAtLeast(1), (size*1.55f+6*d).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = when (settings.number("color", 0)) { 1 -> Color.LTGRAY; 2 -> Color.DKGRAY; else -> Color.WHITE }
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(1.1f*d, 0f, 0.6f*d, 0xB0000000.toInt())
        val y = (height-size)/2
        var x = 3*d
        if (settings.bool("battery", true)) {
            val bw = size*0.58f
            val bh = size*1.02f
            // Xperia-style upright battery: terminal, outline, proportional interior.
            canvas.drawRect(x+bw*0.3f, y, x+bw*0.7f, y+1.5f*d, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.8f*d
            canvas.drawRect(x, y+2*d, x+bw, y+bh, paint)
            paint.style = Paint.Style.FILL
            if (percent >= 0) {
                val bottom = y+bh-1.3f*d
                val top = y+3.3f*d
                canvas.drawRect(x+1.3f*d, bottom-(bottom-top)*percent/100f, x+bw-1.3f*d, bottom, paint)
            }
            if (charging) {
                paint.clearShadowLayer(); paint.color = Color.DKGRAY
                val bolt = Path().apply {
                    moveTo(x+bw*0.65f,y+bh*0.25f); lineTo(x+bw*0.25f,y+bh*0.6f)
                    lineTo(x+bw*0.5f,y+bh*0.6f); lineTo(x+bw*0.35f,y+bh*0.95f)
                    lineTo(x+bw*0.8f,y+bh*0.45f); lineTo(x+bw*0.55f,y+bh*0.45f); close()
                }
                canvas.drawPath(bolt,paint)
            }
            x += size*0.9f + if (settings.bool("clock", true)) 5*d else 0f
        }
        if (settings.bool("clock", true)) {
            paint.color = when(settings.number("color",0)) {1->Color.LTGRAY;2->Color.DKGRAY;else->Color.WHITE}
            paint.setShadowLayer(1.1f*d,0f,0.6f*d,0xB0000000.toInt())
            paint.textSize=size; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            canvas.drawText(clockText(), x, height/2f-(paint.ascent()+paint.descent())/2f, paint)
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
