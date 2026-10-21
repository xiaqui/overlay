package com.xiaqui.overlay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import android.view.Gravity
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
    @Test fun resourcesCacheCanvasOrderAndFailedWindowRollback() {
        val colors=NavbarColors(ctx)
        assertEquals("foreign APK application theme",0xff426a88.toInt(),colors.themeColor(inst.context.packageName))
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
            val layer=NavbarLayer(ctx,ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager,
                { w,h->WindowManager.LayoutParams(w,h,2006,24,-3).apply { gravity=Gravity.TOP or Gravity.LEFT } },
                { _,_->throw WindowManager.BadTokenException("test rejection") },{}, {})
            assertFalse(layer.apply(NavbarSpec(160,300,30,18,true,false,NavbarColor(red,"test"))))
            assertFalse(layer.ready);assertNull(layer.view)
        }
    }
    @Test fun actualBandAppSwitchFiveModesOffFullscreenRotationAndButtons() {
        launch()
        main {
            settings.set("top",true);settings.set("bottom",true);settings.set("radius",18)
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
        assertEquals("bounded tint layer",NavbarPolicy.windowType(android.os.Build.VERSION.SDK_INT),OverlayService.runtime!!.navbar!!.windowType)
        assertEquals("clock and upper corners keep existing type",2006,OverlayService.runtime!!.primaryWindowType)
        assertEquals("two probes, one band replaces two bottom corners",6,OverlayService.runtime!!.windowCount)
        // Wait for actual composition, not only WM layout/ready. Dump geometry if still occluded.
        var painted=false;var observed=0
        repeat(20) {
            if(!painted) {
                val frame=inst.uiAutomation.takeScreenshot()!!
                observed=frame.getPixel(frame.width/2,frame.height-OverlayService.runtime!!.bars.bottom+5)
                painted=observed==red;frame.recycle()
                if(!painted)android.os.SystemClock.sleep(250)
            }
        }
        if(!painted) {
            val dump=shell("dumpsys window windows")
            val section=dump.substringAfter("Overlay Navbar Test",dump).take(6500)
            android.util.Log.i("NavbarPrototypeTest","band diagnostic: ${OverlayService.runtime}\n$section")
        }
        assertEquals("band visible after composition wait",red,observed)
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
        ctx.startActivity(settingsIntent)
        waitFor("external package fixed color follows usage events") { OverlayService.runtime?.let { it.foreground==pkg && it.navbar?.active==true && it.navbar?.color==blue }==true }
        main { settings.setNavbarColor(pkg,null) }
        waitFor("unset external app clears tint") { OverlayService.runtime?.let { it.foreground==pkg && it.navbar?.active==false }==true }
        main { settings.set("navbar_auto",true) }
        waitFor("external APK automatic estimate applied") { OverlayService.runtime?.let { it.foreground==pkg && it.navbar?.active==true && it.navbar?.source in listOf("テーマ推定","アイコン推定") }==true }
        val cachedLoads=OverlayService.runtime!!.navbar!!.colorLoads
        android.os.SystemClock.sleep(1600)
        assertEquals("poll reuses cached automatic estimate",cachedLoads,OverlayService.runtime!!.navbar!!.colorLoads)
        main { settings.set("navbar_auto",false) }
        waitFor("automatic OFF removes unset app tint") { OverlayService.runtime?.navbar?.active==false }
        launch();active()
        shell("appops set ${ctx.packageName} GET_USAGE_STATS ignore")
        waitFor("usage permission revoke clears stale color") { OverlayService.runtime?.let { it.foreground==null && it.navbar?.active==false }==true }
        shell("appops set ${ctx.packageName} GET_USAGE_STATS allow")
        active()
        main { preview!!.applyBars("immersive") }
        waitFor("hidden nav removes tint and restores four masks") { OverlayService.runtime?.let { it.bars.known && it.bars.bottom==0 && it.navbar?.active==false && it.locations.size==4 }==true }
        main { preview!!.applyBars("normal") };active()
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
        shell("input tap ${size.x*5/6} ${size.y-b/2}")
        waitFor("Recents touch passes through") { !preview!!.hasWindowFocus() }
        launch();active()
        // Existing poll count does not grow. Record observational process cost, not a battery claim.
        val loads=OverlayService.runtime!!.navbar!!.colorLoads
        val cpu=android.os.Process.getElapsedCpuTime();val start=android.os.SystemClock.elapsedRealtime()
        android.os.SystemClock.sleep(2500)
        assertEquals("fixed color never loads APK resources",loads,OverlayService.runtime!!.navbar!!.colorLoads)
        android.util.Log.i("NavbarPrototypeTest","mode=ON,api=${android.os.Build.VERSION.SDK_INT},windows=${OverlayService.runtime!!.windowCount},pssKiB=${android.os.Debug.getPss()},cpuMs=${android.os.Process.getElapsedCpuTime()-cpu},elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
    }
}
