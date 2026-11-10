package com.xiaqui.overlay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Capture and shell are TEST tools only. The shipped application uses neither. */
@RunWith(AndroidJUnit4::class)
class NavbarTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=inst.targetContext
    private lateinit var settings: Settings
    private var preview: PreviewActivity?=null
    private val red=0xff804030.toInt()
    private fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun main(action: ()->Unit)=inst.runOnMainSync(action)
    private fun waitFor(message: String,condition: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+15_000
        while(android.os.SystemClock.elapsedRealtime()<end) {
            if(condition())return
            android.os.SystemClock.sleep(150)
        }
        android.util.Log.i("NavbarPrototypeTest",shell("dumpsys activity activities").lines().filter { it.contains("mResumedActivity") || it.contains("mFocusedActivity") || it.contains("topResumedActivity") }.joinToString("\n"))
        fail("$message: ${OverlayService.runtime}")
    }
    @Before fun before() {
        main { ctx.stopService(Intent(ctx,OverlayService::class.java)) }
        waitFor("previous service stopped") { !shell("dumpsys activity services ${ctx.packageName}").contains("ServiceRecord{") }
        main { ctx.getSharedPreferences("overlay",0).edit().clear().commit() }
        settings=Settings(ctx)
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
        shell("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("appops set ${ctx.packageName} GET_USAGE_STATS allow")
    }
    @After fun after() {
        main { preview?.finish();settings.set("enabled",false);ctx.stopService(Intent(ctx,OverlayService::class.java)) }
        waitFor("service cleanup") { !shell("dumpsys activity services ${ctx.packageName}").contains("ServiceRecord{") }
        main { settings.prefs.edit().clear().commit() }
    }
    private fun launch() {
        preview=inst.startActivitySync(Intent(ctx,PreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("bars","normal")) as PreviewActivity
        inst.waitForIdleSync()
    }
    private fun active(color: Int=red) {
        waitFor("band ready $color") { OverlayService.runtime?.let { it.foreground==ctx.packageName && it.navbar?.active==true && it.navbar?.color==color }==true }
        android.os.SystemClock.sleep(400)
    }
    private fun assertBandPixel(expected: Int) {
        var painted=false;var observed=0
        repeat(20) {
            if(!painted) {
                val frame=inst.uiAutomation.takeScreenshot()!!
                observed=frame.getPixel(frame.width/2,frame.height-OverlayService.runtime!!.bars.bottom+5)
                painted=observed==expected;frame.recycle()
                if(!painted)android.os.SystemClock.sleep(250)
            }
        }
        if(!painted) {
            val dump=shell("dumpsys window windows")
            val section=dump.substringAfter("Overlay Navbar Test",dump).take(6500)
            android.util.Log.i("NavbarPrototypeTest","band diagnostic: ${OverlayService.runtime}\n$section")
        }
        assertEquals("band visible after composition wait",expected,observed)
    }
    private fun assertUnifiedPixels(expected: Int) {
        var top=0;var bottom=0
        repeat(20) {
            val state=OverlayService.runtime!!
            val frame=inst.uiAutomation.takeScreenshot()!!
            top=frame.getPixel(frame.width/2,maxOf(1,state.bars.top/2))
            bottom=frame.getPixel(frame.width/2,frame.height-state.bars.bottom+5)
            frame.recycle()
            if(top==expected && bottom==expected)return
            android.os.SystemClock.sleep(250)
        }
        assertEquals("status and navbar use one composed RGB",expected,top)
        assertEquals("status and navbar use one composed RGB",expected,bottom)
    }
    /** Display-finish pixels are emulator evidence; Xperia acceptance remains a device check. */
    @Test fun displayFinishSettingsFontScaleHorizontalBatteryAndShadow() {
        assertTrue("shadow is displaced right",StatusView.SHADOW_DX_DP>0f)
        assertTrue("shadow is displaced down",StatusView.SHADOW_DY_DP>0f)
        assertTrue("shadow blur stays finer than one dp",StatusView.SHADOW_RADIUS_DP in 0.25f..0.75f)
        assertTrue("shadow stays translucent",Color.alpha(StatusView.SHADOW_COLOR) in 128..208)
        fun pixels(view: StatusView,background: Int): List<Int> {
            view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            view.layout(0,0,view.measuredWidth,view.measuredHeight)
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            image.eraseColor(background);view.draw(Canvas(image))
            val result=ArrayList<Int>(image.width*image.height)
            for(py in 0 until image.height)for(px in 0 until image.width)result.add(image.getPixel(px,py))
            image.recycle();return result
        }
        settings.set("size",14);settings.set("clock",true);settings.set("battery",false);settings.set("color",0)
        val clock=StatusView(ctx,settings).apply { clockTextOverride="17:03" }
        assertTrue("actual clock shadow contrasts on white",pixels(clock,Color.WHITE).count {
            Color.red(it)<245 && Color.green(it)<245 && Color.blue(it)<245
        }>10)
        assertTrue("actual clock face survives on black",pixels(clock,Color.BLACK).count {
            Color.red(it)>180 && Color.green(it)>180 && Color.blue(it)>180
        }>10)
        settings.set("x",-32);settings.set("y",-20)
        assertEquals(-32,Settings(ctx).offset("x",4));assertEquals(-20,Settings(ctx).offset("y",0))
        settings.set("x",-999);settings.set("y",999)
        assertEquals(Settings.MIN_OFFSET_DP,settings.offset("x",4));assertEquals(Settings.MAX_OFFSET_DP,settings.offset("y",0))

        val pkg="legacy.fixed.app";val fixed=0xff345678.toInt()
        settings.prefs.edit().putInt("navbar:$pkg",fixed).remove("navbarMode:$pkg").commit()
        assertEquals("old fixed-color data remains fixed",Settings.NAVBAR_FIXED,settings.navbarMode(pkg))
        settings.setNavbarNone(pkg)
        assertEquals(Settings.NAVBAR_NONE,settings.navbarMode(pkg));assertNull(settings.navbarColor(pkg))
        settings.setNavbarColor(pkg,fixed)
        assertEquals(Settings.NAVBAR_FIXED,settings.navbarMode(pkg));assertEquals(fixed,settings.navbarColor(pkg))
        settings.setNavbarColor(pkg,null);assertEquals(Settings.NAVBAR_AUTO,settings.navbarMode(pkg))

        settings.set("size",14);settings.set("clock",true);settings.set("battery",true)
        fun measuredHeight(fontScale: Float): Int {
            val config=Configuration(ctx.resources.configuration).apply { this.fontScale=fontScale }
            val view=StatusView(ctx.createConfigurationContext(config),settings)
            view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            return view.measuredHeight
        }
        val normal=measuredHeight(1f);val large=measuredHeight(1.5f)
        assertTrue("OS fontScale enlarges status once: $normal -> $large",large>normal*1.35f)

        settings.set("clock",false);settings.set("battery",true);settings.set("color",0)
        val battery=StatusView(ctx,settings).apply { percent=50 }
        battery.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
        battery.layout(0,0,battery.measuredWidth,battery.measuredHeight)
        val bitmap=Bitmap.createBitmap(battery.width,battery.height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE);battery.draw(Canvas(bitmap))
        val contrasted=mutableListOf<Pair<Int,Int>>()
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width) {
            val c=bitmap.getPixel(x,y)
            // The outside of a fine blurred black shadow is intentionally mid/light gray.
            if(Color.red(c)<245 && Color.green(c)<245 && Color.blue(c)<245)contrasted.add(x to y)
        }
        assertTrue("shadow creates contrast on white",contrasted.size>10)
        val minX=contrasted.minOf { it.first };val maxX=contrasted.maxOf { it.first }
        val minY=contrasted.minOf { it.second };val maxY=contrasted.maxOf { it.second }
        assertTrue("battery silhouette is horizontal",maxX-minX>maxY-minY)
        val centerY=(minY+maxY)/2
        assertTrue("right-side terminal reaches the silhouette edge",contrasted.any { it.first>=maxX-1 && kotlin.math.abs(it.second-centerY)<=3 })
        bitmap.recycle()
        assertTrue("actual battery face survives on black",pixels(battery,Color.BLACK).count {
            Color.red(it)>180 && Color.green(it)>180 && Color.blue(it)>180
        }>10)
        settings.set("clock",true);settings.set("battery",true)
        val clockOnly=StatusView(ctx,settings,Policy.STATUS_CLOCK).apply { clockTextOverride="17:03" }
        val batteryOnly=StatusView(ctx,settings,Policy.STATUS_BATTERY).apply { percent=50 }
        assertTrue("clock-only mask paints actual clock pixels",pixels(clockOnly,Color.WHITE).count {
            Color.red(it)<245 && Color.green(it)<245 && Color.blue(it)<245
        }>10)
        assertTrue("battery-only mask paints actual battery pixels",pixels(batteryOnly,Color.WHITE).count {
            Color.red(it)<245 && Color.green(it)<245 && Color.blue(it)<245
        }>10)
        clockOnly.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
        batteryOnly.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
        assertTrue("single-element masks produce distinct intrinsic widths",clockOnly.measuredWidth!=batteryOnly.measuredWidth)
        battery.charging=true
        assertTrue("charging battery keeps a visible white-background silhouette",pixels(battery,Color.WHITE).count {
            Color.red(it)<245 && Color.green(it)<245 && Color.blue(it)<245
        }>10)
        settings.set("clock",true);settings.set("battery",true)
        val combined=StatusView(ctx,settings).apply { percent=50;charging=true;clockTextOverride="17:03" }
        combined.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
        combined.layout(0,0,combined.measuredWidth,combined.measuredHeight)
        val combinedImage=Bitmap.createBitmap(combined.width,combined.height,Bitmap.Config.ARGB_8888)
        combinedImage.eraseColor(Color.WHITE);combined.draw(Canvas(combinedImage))
        var clockContrast=0
        for(py in 0 until combinedImage.height)for(px in combinedImage.width/2 until combinedImage.width) {
            val c=combinedImage.getPixel(px,py)
            if(Color.red(c)<245 && Color.green(c)<245 && Color.blue(c)<245)clockContrast++
        }
        combinedImage.recycle()
        assertTrue("clock restores its shadow after the charging bolt clears paint shadow",clockContrast>10)
    }
    @Test fun batteryCapacityHasVisibleBoundaryOnWhite() {
        settings.set("size",14)
        settings.set("clock",false)
        settings.set("battery",true)
        settings.set("color",0)
        val d=ctx.resources.displayMetrics.density
        val bw=14*ctx.resources.displayMetrics.scaledDensity*0.94f
        val inset=maxOf(1.2f*d,14*ctx.resources.displayMetrics.scaledDensity*0.07f)
        val cap=maxOf(1.2f*d,14*ctx.resources.displayMetrics.scaledDensity*0.08f)
        for(reversed in listOf(false,true))for(percent in listOf(20,50,80)) {
            settings.set("battery_reversed",reversed)
            val view=StatusView(ctx,settings,Policy.STATUS_BATTERY).apply { this.percent=percent }
            view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            view.layout(0,0,view.measuredWidth,view.measuredHeight)
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.WHITE)
                view.draw(Canvas(image))
                val left=3*d+(if(reversed)cap else 0f)+inset
                val right=3*d+(if(reversed)cap else 0f)+bw-inset
                val y=view.height/2
                fun lightAt(fraction: Float)=Color.red(image.getPixel((left+(right-left)*fraction).toInt(),y))
                val filledSample=if(reversed)lightAt(0.9f) else lightAt(0.1f)
                val emptySample=if(reversed)lightAt(0.1f) else lightAt(0.9f)
                assertTrue("filled portion stays bright percent=$percent reverse=$reversed: $filledSample",filledSample>220)
                assertTrue("empty portion contrasts on white percent=$percent reverse=$reversed: $emptySample",emptySample<205)
                assertTrue("capacity boundary is visible percent=$percent reverse=$reversed",
                    filledSample-emptySample>30)
            } finally { image.recycle() }
        }
    }

    @Test fun batterySilhouetteShadowPreservesSmallTerminalOnWhite() {
        settings.set("size",14)
        settings.set("clock",false)
        settings.set("battery",true)
        settings.set("color",0)
        // Scale the raster to make the original tiny terminal testable without
        // changing its runtime dp geometry or adding a second backing rectangle.
        val cfg=Configuration(ctx.resources.configuration).apply { fontScale=2f }
        val scaled=ctx.createConfigurationContext(cfg)
        val d=scaled.resources.displayMetrics.density
        val effectiveSize=14*scaled.resources.displayMetrics.scaledDensity
        val bw=effectiveSize*0.94f
        val cap=maxOf(1.2f*d,effectiveSize*0.08f)
        for(reversed in listOf(false,true))for(percent in listOf(-1,20,50,80)) {
            settings.set("battery_reversed",reversed)
            val view=StatusView(scaled,settings,Policy.STATUS_BATTERY).apply { this.percent=percent }
            view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            view.layout(0,0,view.measuredWidth,view.measuredHeight)
            val frame=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {
                frame.eraseColor(Color.WHITE)
                view.draw(Canvas(frame))
                val bodyX=3*d+(if(reversed)cap else 0f)
                val terminalX=if(reversed)bodyX-cap/2f else bodyX+bw+cap/2f
                val ty=view.height/2
                val terminal=Color.red(frame.getPixel(terminalX.toInt(),ty))
                assertTrue("terminal remains bright and shaped by foreground reverse=$reversed percent=$percent: $terminal",terminal>190)
                // Even with unknown charge percentage the shadow must cover the
                // body silhouette rather than an inset percentage-specific bar.
                if(percent<0) {
                    val center=Color.red(frame.getPixel((bodyX+bw/2f).toInt(),ty))
                    assertTrue("solid silhouette covers empty body with unknown percent: $center",center<170)
                }
            } finally { frame.recycle() }
        }
    }

    @Test fun chargingBoltContrastsWithEmptySilhouetteOnWhite() {
        settings.set("size",28);settings.set("battery_size",28)
        settings.set("clock",false)
        settings.set("battery",true)
        settings.set("color",0)
        for(reversed in listOf(false,true)) {
            settings.set("battery_reversed",reversed)
            fun frame(charging: Boolean): Bitmap {
                val view=StatusView(ctx,settings,Policy.STATUS_BATTERY).apply {
                    percent=0
                    this.charging=charging
                }
                view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
                view.layout(0,0,view.measuredWidth,view.measuredHeight)
                return Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    view.draw(Canvas(it))
                }
            }
            val off=frame(false)
            val on=frame(true)
            try {
                var differing=0
                var lightKeyline=0
                for(y in 0 until on.height)for(x in 0 until on.width) {
                    val a=Color.red(off.getPixel(x,y))
                    val b=Color.red(on.getPixel(x,y))
                    if(kotlin.math.abs(a-b)>30) {
                        differing++
                        if(b>a+40)lightKeyline++
                    }
                }
                assertTrue("bolt alters empty battery pixels reversed=$reversed: $differing",differing>5)
                assertTrue("light bolt keyline contrasts with dark empty shadow reversed=$reversed: $lightKeyline",lightKeyline>2)
            } finally { off.recycle();on.recycle() }
        }
    }

    @Test fun batteryDirectionMirrorsTerminalFillAndChargingAppearance() {
        assertFalse("existing installs default to the legacy right-terminal direction",settings.batteryReversed())
        settings.set("size",14);settings.set("clock",false);settings.set("battery",true);settings.set("color",0)
        val density=ctx.resources.displayMetrics.density
        val size=14*ctx.resources.displayMetrics.scaledDensity
        val cap=maxOf(1.2f*density,size*0.08f)
        val bw=size*0.94f
        val inset=maxOf(1.2f*density,size*0.07f)
        data class Shot(val image: Bitmap,val bodyX: Float,val centerY: Int)
        fun shot(percent: Int,reversed: Boolean,charging: Boolean=false,background: Int=Color.BLACK): Shot {
            settings.set("battery_reversed",reversed)
            val view=StatusView(ctx,settings,StatusView.ELEMENT_BATTERY).apply {
                this.percent=percent;this.charging=charging
            }
            view.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED)
            view.layout(0,0,view.measuredWidth,view.measuredHeight)
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            image.eraseColor(background);view.draw(Canvas(image))
            return Shot(image,3*density+(if(reversed)cap else 0f),view.height/2)
        }
        fun filledCount(percent: Int,reversed: Boolean): Int {
            val s=shot(percent,reversed)
            var count=0
            for(px in kotlin.math.ceil(s.bodyX+inset).toInt() until kotlin.math.floor(s.bodyX+bw-inset).toInt()) {
                val c=s.image.getPixel(px,s.centerY)
                if(Color.red(c)>180 && Color.green(c)>180 && Color.blue(c)>180)count++
            }
            s.image.recycle();return count
        }
        for(reversed in listOf(false,true)) {
            val counts=listOf(0,20,50,80,100).map { filledCount(it,reversed) }
            assertTrue("0/20/50/80/100 fill grows at every step for reversed=$reversed: $counts",counts.zipWithNext().all { it.first<it.second })
            assertTrue("full battery fills more than empty for reversed=$reversed: $counts",counts.last()>counts.first()+5)
        }
        val normal=shot(20,false)
        fun halfFill(shot: Shot): Pair<Int,Int> {
            val left=kotlin.math.ceil(shot.bodyX+inset).toInt()
            val right=kotlin.math.floor(shot.bodyX+bw-inset).toInt()
            val middle=(left+right)/2
            var leftCount=0;var rightCount=0
            for(px in left until right) {
                if(Color.red(shot.image.getPixel(px,shot.centerY))>180) {
                    if(px<middle)leftCount++ else rightCount++
                }
            }
            return leftCount to rightCount
        }
        val normalHalves=halfFill(normal)
        assertTrue("normal 20% fills from left: $normalHalves",normalHalves.first>normalHalves.second)
        assertTrue("normal terminal is on right",Color.red(normal.image.getPixel((normal.bodyX+bw+cap/2).toInt(),normal.centerY))>180)
        normal.image.recycle()
        val reverse=shot(20,true)
        val reverseHalves=halfFill(reverse)
        assertTrue("reversed 20% fills from right: $reverseHalves",reverseHalves.second>reverseHalves.first)
        assertTrue("reversed terminal is on left",Color.red(reverse.image.getPixel((reverse.bodyX-cap/2).toInt(),reverse.centerY))>180)
        reverse.image.recycle()
        val normalBolt=StatusView.batteryBoltPoints(false)
        val reverseBolt=StatusView.batteryBoltPoints(true)
        for(i in normalBolt.indices step 2) {
            assertEquals("charging bolt x point $i mirrors",1f-normalBolt[i],reverseBolt[i],0.0001f)
            assertEquals("charging bolt y point " + (i+1) + " stays aligned",normalBolt[i+1],reverseBolt[i+1],0.0001f)
        }
        for(reversed in listOf(false,true))for(background in listOf(Color.WHITE,Color.BLACK)) {
            val charging=shot(50,reversed,true,background)
            var contrast=0
            for(py in 0 until charging.image.height)for(px in 0 until charging.image.width) {
                val c=charging.image.getPixel(px,py)
                if(background==Color.WHITE && Color.red(c)<180 || background==Color.BLACK && Color.red(c)>180)contrast++
            }
            charging.image.recycle()
            assertTrue("charging silhouette remains visible reversed=$reversed background=$background",contrast>10)
        }
    }
    @Test fun resourcesCacheCanvasOrderAndFailedWindowRollback() {
        val colors=NavbarColors(ctx)
        assertEquals("foreign APK application theme",0xff426a88.toInt(),colors.themeColor(inst.context.packageName))
        assertEquals("unified mode prefers colorPrimaryDark",0xff234567.toInt(),colors.themeColor(inst.context.packageName,true))
        assertEquals(0xff183858.toInt(),colors.iconColor(ColorDrawable(0xff123456.toInt())))
        assertEquals("テーマ推定",colors.resolve(inst.context.packageName,true,null)?.source)
        repeat(5) { colors.resolve(inst.context.packageName,true,null) }
        assertEquals(1,colors.loads)
        assertNull(colors.resolve("invalid.no.such.package",true,null))
        assertNull(colors.resolve("invalid.no.such.package",true,null));assertEquals(2,colors.loads)
        assertEquals(red,colors.resolve(inst.context.packageName,false,red)?.color)
        assertNull(colors.resolve(inst.context.packageName,false,null));assertEquals(2,colors.loads)
        colors.clear();colors.resolve(inst.context.packageName,true,null);assertEquals(3,colors.loads)
        main {
            for(mode in Policy.NONE..Policy.BOTH_INSIDE) {
                val inside=mode==Policy.BOTTOM_INSIDE || mode==Policy.BOTH_INSIDE
                val spec=NavbarSpec(160,300,30,18,mode!=Policy.NONE,inside,NavbarColor(red,"test"))
                val view=NavbarBandView(ctx,spec)
                view.layout(0,0,spec.width,spec.height)
                val bitmap=Bitmap.createBitmap(spec.width,spec.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                assertEquals("fill first",red,bitmap.getPixel(80,spec.height-3))
                val y=if(inside)spec.height-spec.navHeight-1 else spec.height-1
                assertEquals("black lower corners last, mode=$mode",if(mode==Policy.NONE)red else Color.BLACK,bitmap.getPixel(0,y))
                bitmap.recycle()
            }
            val recentsSpec=NavbarSpec(160,300,30,18,true,false,NavbarColor(red,"test"),false)
            val recentsView=NavbarBandView(ctx,recentsSpec)
            recentsView.layout(0,0,recentsSpec.width,recentsSpec.height)
            val recentsBitmap=Bitmap.createBitmap(recentsSpec.width,recentsSpec.height,Bitmap.Config.ARGB_8888)
            recentsView.draw(Canvas(recentsBitmap))
            assertEquals("Recents stops fill",Color.TRANSPARENT,recentsBitmap.getPixel(80,recentsSpec.height-3))
            assertEquals("Recents preserves lower corner",Color.BLACK,recentsBitmap.getPixel(0,recentsSpec.height-1))
            recentsBitmap.recycle()
            val statusView=StatusbarBandView(ctx).apply { spec=StatusbarSpec(160,24,NavbarColor(red,"test"),18,true);layout(0,0,160,24) }
            val statusBitmap=Bitmap.createBitmap(160,24,Bitmap.Config.ARGB_8888)
            statusView.draw(Canvas(statusBitmap));assertEquals("status fill",red,statusBitmap.getPixel(80,12))
            assertTrue("status paints physical upper masks after fill",(0 until 18).any { y->(0 until 18).any { x->statusBitmap.getPixel(x,y)==Color.BLACK } })
            statusBitmap.recycle()
            val layer=NavbarLayer(ctx,ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager,
                { w,h->WindowManager.LayoutParams(w,h,2006,24,-3).apply { gravity=Gravity.TOP or Gravity.LEFT } },
                { _,_->throw WindowManager.BadTokenException("test rejection") },{}, {})
            assertFalse(layer.apply(NavbarSpec(160,300,30,18,true,false,NavbarColor(red,"test"))))
            assertFalse(layer.ready);assertNull(layer.view)
            val statusLayer=StatusbarLayer(ctx,ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager,
                { w,h->WindowManager.LayoutParams(w,h,2002,24,-3).apply { gravity=Gravity.TOP or Gravity.LEFT } },
                { _,_->throw WindowManager.BadTokenException("test rejection") },{}, {})
            assertFalse("top failure remains local",statusLayer.attach());assertNull(statusLayer.view)
            assertTrue(statusLayer.geometry().reason.contains("作れない"))
        }
    }
    @Test fun clockSideDefaultsRightAndLeftModeKeepsBatteryRightAcrossRotationAndToggles() {
        assertFalse("existing installs default to the legacy right layout",settings.clockOnLeft())
        launch()
        main {
            settings.set("top",true);settings.set("bottom",true)
            settings.set("x",-10);settings.set("y",-6)
            settings.set("clock_left",false);settings.set("clock",true);settings.set("battery",true)
            settings.set("enabled",true);OverlayService.start(ctx)
        }
        waitFor("independent positions use two minimal status windows") {
            OverlayService.runtime?.let { it.visible && it.statusLocations.size==2 }==true
        }
        // Observational CI telemetry only. Emulator PSS/CPU are not device battery estimates.
        fun recordCost(mode: String) {
            val startCpu=android.os.Process.getElapsedCpuTime()
            val start=android.os.SystemClock.elapsedRealtime()
            android.os.SystemClock.sleep(1200)
            android.util.Log.i("OverlayPerf","api=${android.os.Build.VERSION.SDK_INT},layout=$mode,pssKiB=${android.os.Debug.getPss()},cpuMs=${android.os.Process.getElapsedCpuTime()-startCpu},elapsedMs=${android.os.SystemClock.elapsedRealtime()-start},windows=${OverlayService.runtime?.windowCount},statusWindows=${OverlayService.runtime?.statusLocations?.size}")
        }
        recordCost("right")
        val originalClock=OverlayService.runtime!!.statusLocations[0]
        main {
            settings.setBatteryDirection(2);settings.set("battery_size",40)
            settings.set("battery_aspect",160);settings.set("battery_x",-64);settings.set("battery_y",100)
        }
        waitFor("battery extremes cannot move the right clock window") {
            OverlayService.runtime?.let {
                it.statusLocations.size==2 && it.statusLocations[0]==originalClock &&
                    it.statusLocations[1].y>originalClock.y+50
            }==true
        }
        recordCost("independent-battery")
        main { settings.resetBatteryAdjustments();settings.setBatteryDirection(0) }
        waitFor("battery reset preserves right clock location") {
            OverlayService.runtime?.let { it.statusLocations.size==2 && it.statusLocations[0]==originalClock }==true
        }
        main { settings.set("clock_left",true) }
        waitFor("left clock and right battery attach as two status windows") {
            OverlayService.runtime?.let {
                it.visible && it.statusLocations.size==2 && it.windowCount==6 &&
                    it.statusLocations.minOf { p->p.x }<0 &&
                    it.statusLocations.maxOf { p->p.x }>ctx.resources.displayMetrics.widthPixels/2
            }==true
        }
        recordCost("left")
        var screen=inst.uiAutomation.takeScreenshot()!!
        assertTrue("negative left offset is preserved",OverlayService.runtime!!.statusLocations.minOf { it.x }<0)
        assertTrue("battery remains at the right edge",OverlayService.runtime!!.statusLocations.maxOf { it.x }>screen.width/2)
        screen.recycle()

        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor("left/right split survives rotation") {
            ctx.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE &&
                OverlayService.runtime?.statusLocations?.let { it.size==2 && it.minOf { p->p.x }<0 && it.maxOf { p->p.x }>ctx.resources.displayMetrics.widthPixels/2 }==true
        }
        main { settings.set("clock",false) }
        waitFor("clock OFF leaves only the right battery window") {
            OverlayService.runtime?.let { it.statusLocations.size==1 && it.statusLocations[0].x>ctx.resources.displayMetrics.widthPixels/2 && it.windowCount==5 }==true
        }
        main { settings.set("clock",true);settings.set("battery",false) }
        waitFor("battery OFF leaves only the left clock window") {
            OverlayService.runtime?.let { it.statusLocations.size==1 && it.statusLocations[0].x<0 && it.windowCount==5 }==true
        }
        main { settings.set("clock",false) }
        waitFor("both OFF leave no invisible status window") {
            OverlayService.runtime?.let { !it.visible && it.statusLocations.isEmpty() && it.windowCount==4 }==true
        }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun actualBandAppSwitchFiveModesOffFullscreenRotationAndButtons() {
        launch()
        main {
            settings.set("top",true);settings.set("bottom",true);settings.set("radius",18)
            settings.set("color",2) // Overlay固有の濃灰で、標準status iconと分けて実画素を確認する。
            settings.setNavbarColor(ctx.packageName,red);settings.set("navbar_auto",true)
            settings.set("enabled",true);OverlayService.start(ctx)
        }
        waitFor("OFF retains original five windows without probes") { OverlayService.runtime?.let { it.navbar==null && it.windowCount==5 }==true }
        assertFalse(settings.needsBars());assertFalse(settings.hasRules())
        val offCpu=android.os.Process.getElapsedCpuTime();val offStart=android.os.SystemClock.elapsedRealtime()
        android.os.SystemClock.sleep(2500)
        android.util.Log.i("NavbarPrototypeTest","mode=OFF,api=${android.os.Build.VERSION.SDK_INT},windows=${OverlayService.runtime!!.windowCount},pssKiB=${android.os.Debug.getPss()},cpuMs=${android.os.Process.getElapsedCpuTime()-offCpu},elapsedMs=${android.os.SystemClock.elapsedRealtime()-offStart}")
        main { settings.set("navbar_auto",false);settings.set("navbar_enabled",true) }
        active()
        assertEquals("band retains existing window type",OverlayService.runtime!!.primaryWindowType,OverlayService.runtime!!.navbar!!.windowType)
        assertEquals("clock and upper corners keep existing type",2006,OverlayService.runtime!!.primaryWindowType)
        assertTrue("ready requires an actual onDraw",OverlayService.runtime!!.navbar!!.draws>0)
        assertEquals("two probes, one band replaces two bottom corners",6,OverlayService.runtime!!.windowCount)
        // Wait for actual composition, not only WM layout/ready. Dump geometry if still occluded.
        assertBandPixel(red)
        main { settings.set("navbar_unified",true) }
        waitFor("top and bottom bands are drawn") { OverlayService.runtime?.let {
            it.foreground==ctx.packageName && it.navbar?.active==true && it.statusbar?.active==true && it.navbar?.color==red && it.statusbar?.color==red
        }==true }
        assertUnifiedPixels(red)
        main { settings.setNavbarColor(ctx.packageName,Color.WHITE) }
        waitFor("fixed white reaches both specs without darkening") { OverlayService.runtime?.let {
            val navbar=it.navbar;val statusbar=it.statusbar
            navbar?.active==true && statusbar?.active==true &&
                navbar.color==Color.WHITE && statusbar.color==Color.WHITE &&
                navbar.source=="固定色" && it.navbarGeometry?.expectedColor==Color.WHITE &&
                it.statusbarGeometry?.expectedColor==Color.WHITE
        }==true }
        val whiteDiagnostic=OverlayService.runtime!!
        val lowerGeometry=whiteDiagnostic.navbarGeometry!!;val upperGeometry=whiteDiagnostic.statusbarGeometry!!
        assertEquals("lower band keeps active overlay type",whiteDiagnostic.primaryWindowType,lowerGeometry.windowType)
        assertEquals("Android 6/7 upper diagnostic reports TYPE_PHONE",if(android.os.Build.VERSION.SDK_INT<26)2002 else whiteDiagnostic.primaryWindowType,upperGeometry.windowType)
        assertEquals(0,upperGeometry.actualX);assertEquals(0,upperGeometry.actualY)
        assertTrue(upperGeometry.draws>0);assertTrue(lowerGeometry.draws>0)
        main { settings.setNavbarColor(ctx.packageName,red) };active()
        main { settings.setNavbarNone(ctx.packageName) }
        waitFor("per-app none stops both tints") { OverlayService.runtime?.let {
            it.foreground==ctx.packageName && it.navbar?.active==false && it.navbar?.color==null && it.statusbar?.active!=true &&
                !it.recentsTintSuppressed && it.locations.size==4
        }==true }
        assertTrue("diagnostic separates candidate absence",OverlayService.runtime!!.statusbar!!.reason.contains("候補色なし"))
        main { settings.setNavbarColor(ctx.packageName,red) };active();waitFor("fixed tint restores after none") { OverlayService.runtime?.statusbar?.active==true }
        var unifiedShot=inst.uiAutomation.takeScreenshot()!!
        val cornerRadius=(settings.number("radius",18)*ctx.resources.displayMetrics.density).toInt()
        val hasBlackUpperCorner=(0 until cornerRadius).any { y->(0 until cornerRadius).any { x->unifiedShot.getPixel(x,y)==Color.BLACK } }
        assertTrue("upper corner remains black over status tint",hasBlackUpperCorner)
        val hasLightStatusContent=(0 until unifiedShot.width).step(2).any { x->
            val c=unifiedShot.getPixel(x,maxOf(1,OverlayService.runtime!!.bars.top/2))
            Color.red(c)>190 && Color.green(c)>190 && Color.blue(c)>190
        }
        val overlayStatusPixels=(unifiedShot.width/2 until unifiedShot.width).sumOf { x->
            (0 until OverlayService.runtime!!.bars.top).count { y->unifiedShot.getPixel(x,y)==Color.DKGRAY }
        }
        unifiedShot.recycle()
        assertTrue("native/overlay status symbols remain above tint",hasLightStatusContent)
        assertTrue("Overlay clock/battery remain above tint as distinct dark-gray pixels",overlayStatusPixels>4)
        // These are emulator composition pixels, NOT proof of Xperia SystemUI visibility.
        val shot=inst.uiAutomation.takeScreenshot()!!
        val nav=OverlayService.runtime!!.bars.bottom
        assertEquals("fill visible behind emulator SystemUI",red,shot.getPixel(shot.width/2,shot.height-nav+5))
        assertEquals("normal lower black corner over fill",Color.BLACK,shot.getPixel(0,shot.height-1))
        shot.recycle()
        for(mode in Policy.NONE..Policy.BOTH_INSIDE) {
            main { settings.setRule(ctx.packageName,mode) };active()
            val state=OverlayService.runtime!!
            assertEquals((0..3).map { Policy.cornerOffset(it,mode,state.bars) },state.offsets)
            val image=inst.uiAutomation.takeScreenshot()!!
            val inside=mode==Policy.BOTTOM_INSIDE || mode==Policy.BOTH_INSIDE
            val y=if(inside)image.height-state.bars.bottom-1 else image.height-1
            assertEquals("five-state band masks, mode=$mode",if(mode==Policy.NONE)red else Color.BLACK,image.getPixel(0,y))
            image.recycle()
        }
        main { settings.setRule(ctx.packageName,Policy.NORMAL);settings.set("bottom",false) };active()
        var image=inst.uiAutomation.takeScreenshot()!!
        assertEquals("global bottom disable",red,image.getPixel(0,image.height-1));image.recycle()
        main { settings.set("bottom",true);settings.set("radius",0) };active()
        image=inst.uiAutomation.takeScreenshot()!!
        assertEquals("zero radius",red,image.getPixel(0,image.height-1));image.recycle()
        main { settings.set("radius",18) };active()
        val settingsIntent=Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pkg=ctx.packageManager.resolveActivity(settingsIntent,0)!!.activityInfo.packageName
        val blue=0xff304080.toInt()
        main { settings.setNavbarColor(pkg,blue) }
        val launched=shell("am start -W -a android.settings.SETTINGS")
        assertTrue("external Activity actually starts: $launched",launched.contains("Status: ok"))
        waitFor("external package fixed color follows usage events") { OverlayService.runtime?.let { it.foreground==pkg && it.navbar?.active==true && it.navbar?.color==blue }==true }
        assertBandPixel(blue)
        assertEquals("external target snapshot is captured before opening settings",pkg,OverlayService.diagnosticRuntime?.foreground)
        val controls=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor("opening controls must not overwrite target snapshot") {
            OverlayService.runtime?.foreground==ctx.packageName && OverlayService.diagnosticRuntime?.foreground==pkg
        }
        val stableCapturedAt=OverlayService.diagnosticCapturedAt
        android.os.SystemClock.sleep(700)
        assertEquals("snapshot timestamp stops changing after Overlay is foreground",stableCapturedAt,OverlayService.diagnosticCapturedAt)
        assertEquals(pkg,OverlayService.diagnosticRuntime?.foreground)
        assertTrue("manual reports the captured target",controls.diagnosticTextForTest().contains("採取対象="+pkg))
        main { controls.finish() }
        val relaunched=shell("am start -W -a android.settings.SETTINGS")
        assertTrue("external Settings task returns: $relaunched",relaunched.contains("Status: ok"))
        waitFor("external package returns after diagnostics") { OverlayService.runtime?.foreground==pkg }
        main { settings.setNavbarColor(pkg,null) }
        waitFor("removing fixed color directly restores automatic estimate despite legacy OFF") { OverlayService.runtime?.let { it.foreground==pkg && it.navbar?.active==true && it.navbar?.source in listOf("テーマ推定","アイコン推定") }==true }
        assertBandPixel(OverlayService.runtime!!.navbar!!.color!!)
        val cachedLoads=OverlayService.runtime!!.navbar!!.colorLoads
        android.os.SystemClock.sleep(1600)
        assertEquals("poll reuses cached automatic estimate",cachedLoads,OverlayService.runtime!!.navbar!!.colorLoads)
        main { settings.set("navbar_auto",false) }
        waitFor("legacy automatic OFF no longer disables standard estimation") { OverlayService.runtime?.navbar?.active==true }
        launch();active()
        shell("appops set ${ctx.packageName} GET_USAGE_STATS ignore")
        waitFor("usage permission revoke clears stale color") { OverlayService.runtime?.let { it.foreground==null && it.navbar?.active==false }==true }
        shell("appops set ${ctx.packageName} GET_USAGE_STATS allow")
        active()
        main { preview!!.applyBars("immersive") }
        waitFor("hidden bars remove both tints and restore four masks") { OverlayService.runtime?.let { it.bars.known && it.bars.bottom==0 && it.bars.top==0 && it.navbar?.active==false && it.statusbar?.active==false && it.locations.size==4 }==true }
        main { preview!!.applyBars("normal") };active()
        waitFor("status tint restores after fullscreen") { OverlayService.runtime?.statusbar?.active==true }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor("landscape safe fallback") { ctx.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE && OverlayService.runtime?.navbar?.active==false }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT };active()
        main { settings.set("navbar_enabled",false) }
        waitFor("OFF removes band, restores lower windows") { OverlayService.runtime?.let { it.navbar==null && it.locations.size==4 }==true }
        assertEquals(18,settings.number("radius",-1));assertEquals(Policy.NORMAL,settings.rule(ctx.packageName))
        main { settings.set("navbar_enabled",true) };active()
        val b=OverlayService.runtime!!.bars.bottom
        val size=android.graphics.Point();(ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(size)
        shell("input tap ${size.x/2} ${size.y-b/2}")
        waitFor("Home touch passes through") { OverlayService.runtime?.foreground?.let { it!=ctx.packageName }==true }
        launch();active()
        shell("input tap ${size.x/6} ${size.y-b/2}")
        waitFor("Back touch passes through") { preview!!.isFinishing || preview!!.isDestroyed }
        launch();active()
        main { settings.set("navbar_recents_off",true) }
        active();waitFor("status remains ready before Recents") { OverlayService.runtime?.statusbar?.active==true }
        shell("input tap ${size.x*5/6} ${size.y-b/2}")
        waitFor("Recents touch passes through") { !preview!!.hasWindowFocus() }
        val recentsEnd=android.os.SystemClock.elapsedRealtime()+5_000
        while(android.os.SystemClock.elapsedRealtime()<recentsEnd && OverlayService.runtime?.recentsTintSuppressed!=true)
            android.os.SystemClock.sleep(150)
        if(OverlayService.runtime?.recentsTintSuppressed==true) {
            val state=OverlayService.runtime!!
            assertFalse("top tint stops in detected Recents",state.statusbar?.active==true)
            assertTrue("transparent lower owner keeps both lower corners",state.navbar?.active==true)
            val image=inst.uiAutomation.takeScreenshot()!!
            assertEquals("lower corner remains black",Color.BLACK,image.getPixel(0,image.height-1));image.recycle()
            launch();active();waitFor("tints restore after Recents") { OverlayService.runtime?.statusbar?.active==true && OverlayService.runtime?.recentsTintSuppressed==false }
        } else android.util.Log.i("NavbarPrototypeTest","Recents component was not observable on API ${android.os.Build.VERSION.SDK_INT}; exact policy is covered by JVM tests: ${OverlayService.runtime}")
        launch();active()
        // Existing poll count does not grow. Record observational process cost, not a battery claim.
        val loads=OverlayService.runtime!!.navbar!!.colorLoads
        val cpu=android.os.Process.getElapsedCpuTime();val start=android.os.SystemClock.elapsedRealtime()
        android.os.SystemClock.sleep(2500)
        assertEquals("fixed color never loads APK resources",loads,OverlayService.runtime!!.navbar!!.colorLoads)
        android.util.Log.i("NavbarPrototypeTest","mode=ON,api=${android.os.Build.VERSION.SDK_INT},windows=${OverlayService.runtime!!.windowCount},pssKiB=${android.os.Debug.getPss()},cpuMs=${android.os.Process.getElapsedCpuTime()-cpu},elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
    }
}


