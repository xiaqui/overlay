package com.xiaqui.overlay

import android.content.res.Configuration
import android.graphics.*
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryFinishTest {
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun raster(settings: Settings,direction: Int,level: Int,charging: Boolean,background: Int,
        scale: Float=1f,mask: Int=Policy.STATUS_BATTERY): Bitmap {
        settings.setBatteryDirection(direction)
        val context=ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { fontScale=scale })
        val view=StatusView(context,settings,mask).apply { percent=level;this.charging=charging;clockTextOverride="17:03" }
        view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
        return Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {
            it.eraseColor(background);view.draw(Canvas(it))
        }
    }
    @Test fun legacyMigrationResetAndIndependentClockPixels() {
        val prefs=ctx.getSharedPreferences("overlay",0)
        try {
            prefs.edit().clear().putInt("size",22).putInt("x",-10).putInt("y",-6)
                .putBoolean("battery_reversed",true).commit()
            val s=Settings(ctx)
            assertTrue("12-hour width transition updates the shared placement calculation",
                StatusView.batteryAnchor("10:00",28f,2f)>StatusView.batteryAnchor("9:59",28f,2f))
            assertEquals(1,s.batteryDirection());assertEquals(22,s.batterySize())
            assertEquals(-10,s.number("battery_base_x",0));assertEquals(-6,s.number("battery_base_y",0))
            assertFalse(s.independentBattery())
            val clock=raster(s,1,50,false,Color.WHITE,mask=Policy.STATUS_CLOCK)
            s.setBatteryDirection(2);s.set("battery_size",40);s.set("battery_aspect",160)
            s.set("battery_x",-64);s.set("battery_y",100)
            assertEquals(2,Settings(ctx).batteryDirection());assertTrue(s.independentBattery())
            val after=raster(s,2,50,true,Color.WHITE,mask=Policy.STATUS_CLOCK)
            assertEquals(clock.width,after.width);assertEquals(clock.height,after.height)
            val a=IntArray(clock.width*clock.height);val b=IntArray(a.size)
            clock.getPixels(a,0,clock.width,0,0,clock.width,clock.height)
            after.getPixels(b,0,after.width,0,0,after.width,after.height)
            assertArrayEquals("battery adjustments must not change clock pixels",a,b)
            clock.recycle();after.recycle()
            s.resetBatteryAdjustments()
            assertEquals(22,s.batterySize());assertEquals(100,s.batteryAspect())
            assertEquals(0,s.offset("battery_x",99));assertEquals(0,s.offset("battery_y",99))
            assertEquals(2,s.batteryDirection());assertEquals(22,s.number("size",0))
            s.setBatteryDirection(1);assertTrue(s.bool("battery_reversed"))
            s.set("battery_reversed",false);assertEquals("new direction takes precedence",1,s.batteryDirection())
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun directionsCapacityChargeShadowAndExtremeGeometry() {
        val prefs=ctx.getSharedPreferences("overlay",0)
        try {
            prefs.edit().clear().commit();val s=Settings(ctx)
            for(direction in 0..2)for(size in listOf(10,14,40))for(aspect in listOf(60,100,160))
                for(scale in listOf(1f,1.5f))for(background in listOf(Color.WHITE,Color.BLACK)) {
                s.set("battery_size",size);s.set("battery_aspect",aspect)
                var previous=-1
                for(level in listOf(0,20,50,80,100)) {
                    val image=raster(s,direction,level,false,background,scale)
                    val d=ctx.resources.displayMetrics.density
                    val g=BatteryGeometry(size*ctx.resources.displayMetrics.density*scale,d,aspect,direction)
                    val top=(image.height-g.height)/2f
                    var bright=0
                    for(step in 0 until 100) {
                        val p=g.point(g.inset+(g.longSide-2*g.inset)*(step+0.5f)/100f,g.shortSide/2)
                        if(Color.red(image.getPixel((3*d+p.first).toInt(),(top+p.second).toInt()))>200)bright++
                    }
                    assertTrue("monotonic fill $direction/$size/$aspect/$scale/$level",bright>=previous)
                    if(level==100)assertTrue("full capacity is bright",bright>90)
                    if(level==0)assertTrue("empty capacity is distinct on either background",bright<10)
                    previous=bright
                    val terminal=g.point(g.longSide+g.cap/2,g.shortSide/2)
                    assertTrue("terminal survives",Color.red(image.getPixel((3*d+terminal.first).toInt(),(top+terminal.second).toInt()))>180)
                    image.recycle()
                }
                val plain=raster(s,direction,-1,false,background,scale)
                val charged=raster(s,direction,-1,true,background,scale)
                var changed=0
                for(y in 0 until plain.height)for(x in 0 until plain.width)
                    if(plain.getPixel(x,y)!=charged.getPixel(x,y))changed++
                assertTrue("unknown-capacity charging bolt visible",changed>2)
                plain.recycle();charged.recycle()
            }
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun exteriorShadowHasSoftIntermediatePixels() {
        val prefs=ctx.getSharedPreferences("overlay",0)
        try {
            prefs.edit().clear().commit();val s=Settings(ctx)
            s.set("battery_size",14)
            for(direction in 0..2) {
                val image=raster(s,direction,0,false,Color.WHITE)
                val d=ctx.resources.displayMetrics.density
                val g=BatteryGeometry(14*d,d,100,direction)
                val top=(image.height-g.height)/2f
                // Exterior under a long body edge, away from foreground stroke/cap/empty fill.
                var intermediate=0
                for(x in 0 until image.width)for(y in 0 until image.height) {
                    val outside=if(direction==2)
                        x>3*d+g.width+g.stroke/2+0.1f*d && y>top+g.cap+3*d && y<top+g.height-3*d
                    else y>top+g.height+g.stroke/2+0.1f*d && x>3*d+3*d && x<3*d+g.longSide-3*d
                    val value=Color.red(image.getPixel(x,y))
                    if(outside && value in 100..253)intermediate++
                }
                assertTrue("soft exterior shadow survives clipping direction=$direction",intermediate>0)
                image.recycle()
            }
        } finally { prefs.edit().clear().commit() }
    }
}
