package com.xiaqui.overlay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Issue4Test {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=inst.targetContext
    private lateinit var settings: Settings
    private var preview: PreviewActivity?=null
    private fun shell(command: String): String=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun main(action: ()->Unit)=inst.runOnMainSync(action)
    private fun waitFor(message: String,timeoutMs: Long=12_000,condition: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+timeoutMs
        while(android.os.SystemClock.elapsedRealtime()<end) {
            if(condition())return
            android.os.SystemClock.sleep(150)
        }
        fail("$message: ${OverlayService.runtime}")
    }
    private fun blackPixel(message: String,x: Int,y: Int) {
        waitFor(message) {
            val image=inst.uiAutomation.takeScreenshot()!!
            val black=image.getPixel(x,y) and 0xffffff==0
            image.recycle();black
        }
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
        main {
            val activities=linkedSetOf<PreviewActivity>()
            preview?.let { activities.add(it) }
            val monitor=ActivityLifecycleMonitorRegistry.getInstance()
            for(stage in listOf(Stage.CREATED,Stage.STARTED,Stage.RESUMED,Stage.PAUSED,Stage.STOPPED))
                activities.addAll(monitor.getActivitiesInStage(stage).filterIsInstance<PreviewActivity>())
            activities.forEach { it.finish() };preview=null
            settings.set("enabled",false);ctx.stopService(Intent(ctx,OverlayService::class.java))
        }
        waitFor("service cleanup") { !shell("dumpsys activity services ${ctx.packageName}").contains("ServiceRecord{") }
        main { settings.prefs.edit().clear().commit() }
    }
    private fun launch(mode: String) {
        preview=inst.startActivitySync(Intent(ctx,PreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("bars",mode)) as PreviewActivity
        inst.waitForIdleSync()
    }
    private fun resumedPreview(): PreviewActivity? {
        var result: PreviewActivity?=null
        main {
            result=ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<PreviewActivity>().firstOrNull()
        }
        return result
    }
    private fun enable()=main {
        settings.set("top",true);settings.set("bottom",true);settings.set("fullscreen",true)
        settings.setRule(ctx.packageName,Policy.BOTH_INSIDE);settings.set("enabled",true);OverlayService.start(ctx)
    }
    private fun bars(mode: String,visible: Boolean) {
        main { preview!!.applyBars(mode) }
        waitFor("$mode, overlay visible=$visible") {
            val state=OverlayService.runtime
            state!=null && state.bars.known && state.visible==visible &&
                (if(visible)state.bars.top==0 else state.bars.top>0)
        }
        android.util.Log.i("OverlayBarsTest","$mode ${OverlayService.runtime}")
    }
    @Test fun normalTransparentFullscreenAndAppSwitch() {
        launch("normal");enable()
        waitFor("normal status bar hides overlay") { OverlayService.runtime?.let { it.bars.known && it.bars.top>0 && !it.visible }==true }
        bars("transparent",false)
        bars("immersive",true)
        waitFor("hidden bars produce zero offsets") { OverlayService.runtime?.let { it.bars.bottom==0 && it.bars.left==0 && it.bars.right==0 && it.offsets.all { p->p==CornerOffset() } }==true }
        bars("status-hidden",true)
        bars("normal",false)
        // An independent package controls the system bars here; no Activity flags are read by the service.
        bars("immersive",true)
        ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("external settings app shows status bar") { OverlayService.runtime?.let { it.bars.known && it.bars.top>0 && !it.visible }==true }
        // Start a fresh fullscreen Activity: Settings task/back-stack behavior varies by OS.
        main { preview!!.finish() }
        launch("immersive")
        waitFor("return to fullscreen app") { OverlayService.runtime?.let { it.bars.known && it.bars.top==0 && it.visible }==true }
        bars("normal",false)
        main { settings.set("fullscreen",false) }
        waitFor("always mode preserved") { OverlayService.runtime?.visible==true }
    }
    @Test fun fiveCornerModesActualPositionsRotationAndExceptions() {
        launch("normal");enable()
        waitFor("usage identifies app and bars") { OverlayService.runtime?.let { it.foreground==ctx.packageName && it.bars.known && it.bars.top>0 }==true }
        assertTrue("visible nav bar must be measured",OverlayService.runtime!!.bars.let { it.bottom+it.left+it.right>0 })
        for(mode in Policy.NONE..Policy.BOTH_INSIDE) {
            main { settings.setRule(ctx.packageName,mode) }
            waitFor("mode $mode settled") {
                val state=OverlayService.runtime
                state!=null && state.bars.known && state.bars.top>0 &&
                    // NORMAL now removes the last per-app rule. With no rules left,
                    // foreground polling correctly stops and foreground becomes null.
                    // The next non-default mode must restart tracking.
                    (if(mode==Policy.NORMAL)state.foreground==null
                     else state.foreground==ctx.packageName) &&
                    state.offsets==(0..3).map { Policy.cornerOffset(it,mode,state.bars) }
            }
            android.os.SystemClock.sleep(500)
            val b=OverlayService.runtime!!.bars
            if(mode==Policy.BOTTOM_INSIDE || mode==Policy.BOTH_INSIDE) {
                val shot=inst.uiAutomation.takeScreenshot()!!
                val x=shot.width-1-b.right
                val y=shot.height-1-b.bottom
                assertTrue("navigation bar offset is nonzero",b.bottom+b.left+b.right>0)
                shot.recycle()
                blackPixel("lower corner painted inside nav, bars=$b",x,y)
            }
            // Inspect real pixels at the moved top-left corner; policy state alone cannot pass this test.
            if(mode!=Policy.NONE && (mode==Policy.TOP_INSIDE || mode==Policy.BOTH_INSIDE)) {
                blackPixel("top corner painted below status bar",0,b.top+1)
            }
        }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor("landscape observed") { ctx.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE && OverlayService.runtime?.bars?.known==true }
        bars("immersive",true);bars("normal",false)
        assertTrue("landscape nav measured",OverlayService.runtime!!.bars.let { it.bottom+it.left+it.right>0 })
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE }
        waitFor("reverse landscape bars and per-app placement settled") {
            OverlayService.runtime?.let {
                val side=if(android.os.Build.VERSION.SDK_INT>=26)it.bars.left>0 else it.bars.left+it.bars.right>0
                it.foreground==ctx.packageName && it.bars.known && it.bars.top>0 && side &&
                    it.offsets==(0..3).map { c->Policy.cornerOffset(c,Policy.BOTH_INSIDE,it.bars) }
            }==true
        }
        android.os.SystemClock.sleep(700)
        val reverse=OverlayService.runtime!!.bars
        val inside=inst.uiAutomation.takeScreenshot()!!
        val navCornerX=if(reverse.left>0)reverse.left else inside.width-1-reverse.right
        assertEquals("lower corner inside side nav: ${OverlayService.runtime}",0,inside.getPixel(navCornerX,inside.height-1-reverse.bottom) and 0xFFFFFF)
        // The left nav bar may cover an upper corner. Inspect the real View position there.
        assertEquals("upper left stays at physical edge: ${OverlayService.runtime}",CornerOffset(0,reverse.top),OverlayService.runtime!!.locations[0])
        inside.recycle()
        main { settings.setRule(ctx.packageName,Policy.NORMAL) }
        waitFor("normal mode in reverse landscape") { OverlayService.runtime?.offsets?.all { it==CornerOffset() }==true }
        android.os.SystemClock.sleep(700)
        val normal=inst.uiAutomation.takeScreenshot()!!
        // System bars can be above legacy 2006 windows: require physical coordinates, not bar coverage.
        val radius=(settings.number("radius",18)*ctx.resources.displayMetrics.density).toInt()
        assertEquals("normal physical corner positions in reverse landscape",
            listOf(CornerOffset(0,0),CornerOffset(normal.width-radius,0),CornerOffset(0,normal.height-radius),CornerOffset(normal.width-radius,normal.height-radius)),
            OverlayService.runtime!!.locations)
        normal.recycle()
        main { settings.setDisplayRule(ctx.packageName,1) }
        waitFor("force display exception") { OverlayService.runtime?.let { it.foreground==ctx.packageName && it.visible }==true }
        main { settings.setDisplayRule(ctx.packageName,2) }
        waitFor("force hide exception") { OverlayService.runtime?.let { it.foreground==ctx.packageName && !it.visible }==true }
        main { settings.set("clock",false);settings.set("battery",false);settings.setDisplayRule(ctx.packageName,Policy.DISPLAY_ALWAYS) }
        waitFor("global OFF wins over always display") {
            OverlayService.runtime?.let { !it.visible && it.statusElements==0 && it.statusLocations.isEmpty() }==true
        }
        main { settings.set("clock",true);settings.set("fullscreen",false) }
        waitFor("only globally enabled clock displays") {
            OverlayService.runtime?.let { it.visible && it.statusElements==Policy.STATUS_CLOCK && it.statusLocations.size==1 }==true
        }
        main { settings.setDisplayRule("com.android.settings",Policy.DISPLAY_NONE) }
        ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("app switch applies the other package's hide rule without a preference rebuild") {
            OverlayService.runtime?.let {
                it.foreground=="com.android.settings" && !it.visible &&
                    it.statusElements==0 && it.statusLocations.isEmpty()
            }==true
        }
        shell("am start -n ${ctx.packageName}/.PreviewActivity --es bars normal")
        waitFor("returning app restores its always-display rule") {
            OverlayService.runtime?.let {
                it.foreground==ctx.packageName && it.visible && it.statusElements==Policy.STATUS_CLOCK
            }==true
        }
        waitFor("foreground preview is tracked for transition checks") {
            resumedPreview()?.let { preview=it;true }==true
        }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor("globally enabled clock survives rotation") {
            ctx.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                OverlayService.runtime?.let { it.visible && it.statusElements==Policy.STATUS_CLOCK }==true
        }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        waitFor("globally enabled clock restores after portrait rotation") {
            ctx.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                OverlayService.runtime?.let { it.visible && it.statusElements==Policy.STATUS_CLOCK }==true
        }
        // The API 23 emulator does not reliably wake again after KEYCODE_SLEEP and can leave
        // the instrumentation process blocked. API 24-29 exercise the real broadcast path;
        // Android 6 screen-off/on remains an explicit Xperia device acceptance item.
        if(android.os.Build.VERSION.SDK_INT>=24) {
            shell("input keyevent KEYCODE_SLEEP")
            waitFor("screen off makes runtime status non-visible",25_000) { OverlayService.runtime?.visible==false }
            shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
            waitFor("screen on rebuild restores globally enabled clock") {
                OverlayService.runtime?.let { it.visible && it.statusElements==Policy.STATUS_CLOCK }==true
            }
        }
        main { settings.set("fullscreen",true) }
        shell("appops set ${ctx.packageName} GET_USAGE_STATS ignore")
        waitFor("usage access revoke safely falls back to auto mode") {
            OverlayService.runtime?.let { it.foreground==null && !it.visible && it.statusElements==0 }==true
        }
        shell("appops set ${ctx.packageName} GET_USAGE_STATS allow")
        waitFor("usage access restore recovers foreground and always-display rule without an app event") {
            OverlayService.runtime?.let {
                it.foreground==ctx.packageName && it.visible && it.statusElements==Policy.STATUS_CLOCK
            }==true
        }
        main { settings.set("fullscreen",false);settings.setDisplayRule(ctx.packageName,Policy.DISPLAY_NONE) }
        waitFor("hide exception still applies with fullscreen-only off") {
            OverlayService.runtime?.let { !it.visible && it.statusElements==0 }==true
        }
        main { settings.setDisplayRule(ctx.packageName,Policy.DISPLAY_AUTO) }
        waitFor("default restores globally enabled clock in always mode") {
            OverlayService.runtime?.statusElements==Policy.STATUS_CLOCK
        }
    }
    @Test fun legacyPrefsMigrateOnceWithoutDestroyingOriginals() {
        main {
            settings.prefs.edit().clear().putInt("app:none",0).putInt("app:top",1).putInt("app:bottom",2).putInt("app:both",3).putBoolean("top",true).commit()
            val upgraded=Settings(ctx)
            assertEquals(Policy.NONE,upgraded.rule("none"))
            for(pkg in listOf("top","bottom","both","unset"))assertEquals(Policy.NORMAL,upgraded.rule(pkg))
            assertEquals(1,upgraded.number("app:top",-1));assertTrue(upgraded.bool("top"))
            upgraded.setRule("top",Policy.BOTH_INSIDE)
            assertEquals(Policy.BOTH_INSIDE,Settings(ctx).rule("top"))
            assertFalse("unified is initially off",upgraded.unifiedBars())
            assertFalse("Recents tint stop is initially off",upgraded.suppressRecentsTint())
            upgraded.set("navbar_unified",true);upgraded.set("navbar_recents_off",true)
            assertFalse("Navbar master switch controls both additions",upgraded.unifiedBars())
            assertFalse("Navbar master switch controls both additions",upgraded.suppressRecentsTint())
            upgraded.set("navbar_enabled",true)
            assertTrue(upgraded.unifiedBars());assertTrue(upgraded.suppressRecentsTint())
        }
    }
}


