package com.xiaqui.overlay

/** Canonical body coordinates; rotation also transforms terminal, fill and bolt. */
internal data class BatteryGeometry(val size: Float,val density: Float,val aspect: Int,val direction: Int) {
    val longSide=size*0.94f*aspect.coerceIn(60,160)/100f
    val shortSide=size*0.56f
    val cap=maxOf(1.2f*density,size*0.08f)
    val stroke=minOf(maxOf(0.8f*density,size*0.045f),shortSide*0.12f,longSide*0.12f)
    val inset=minOf(maxOf(1.2f*density,size*0.07f),shortSide*0.24f,longSide*0.24f)
    val width get()=if(direction==2)shortSide else longSide+cap
    val height get()=if(direction==2)longSide+cap else shortSide
    fun point(x: Float,y: Float): Pair<Float,Float> = when(direction) {
        1->longSide+cap-x to y
        2->y to longSide+cap-x
        else->x to y
    }
    fun fillFraction(percent: Int)=percent.coerceIn(0,100)/100f
}
