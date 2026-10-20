package com.xiaqui.overlay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    private fun waitFor(message: String,condition: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+12_000
        while(android.os.SystemClock.elapsedRealtime()<end) {
            if(condition())return
            android.os.SystemClock.sleep(150)
        }
        fail("$message: ${OverlayService.runtime}")
    }
    @Before fun before() {
        main { ctx.stopService(Intent(ctx,OverlayService::class.java)) }
        waitFor("previous service stopped") { OverlayService.runtime==null }
        main { ctx.getSharedPreferences("overlay",0).edit().clear().commit() }
        settings=Settings(ctx)
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
        shell("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("appops set ${ctx.packageName} GET_USAGE_STATS allow")
    }
    @After fun after() {
        main {
            preview?.finish();settings.set("enabled",false);ctx.stopService(Intent(ctx,OverlayService::class.java))
        }
        waitFor("service cleanup") { OverlayService.runtime==null }
        main { settings.prefs.edit().clear().commit() }
    }
    private fun launch(mode: String) {
        val monitor=inst.addMonitor(PreviewActivity::class.java.name,null,false)
        ctx.startActivity(Intent(ctx,PreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bars",mode))
        preview=inst.waitForMonitorWithTimeout(monitor,10_000) as? PreviewActivity
        inst.removeMonitor(monitor);assertNotNull(preview);inst.waitForIdleSync()
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
        shell("input keyevent KEYCODE_BACK")
        waitFor("return to fullscreen app") { OverlayService.runtime?.let { it.bars.known && it.bars.top==0 && it.visible }==true }
        bars("normal",false)
        main { settings.set("fullscreen",false) }
        waitFor("always mode preserved") { OverlayService.runtime?.visible==true }
    }
    @Test fun fiveCornerModesActualPositionsRotationAndExceptions() {
        launch("normal");enable()
        waitFor("usage identifies app and bars") { OverlayService.runtime?.let { it.foreground==ctx.packageName && it.bars.known && it.bars.top>0 }==true }
        for(mode in Policy.NONE..Policy.BOTH_INSIDE) {
            main { settings.setRule(ctx.packageName,mode) }
            waitFor("mode $mode settled") {
                val state=OverlayService.runtime
                state!=null && state.foreground==ctx.packageName && state.offsets==(0..3).map { Policy.cornerOffset(it,mode,state.bars) }
            }
            android.os.SystemClock.sleep(500)
            val b=OverlayService.runtime!!.bars
            // Inspect real pixels at the moved top-left corner; policy state alone cannot pass this test.
            if(mode!=Policy.NONE && (mode==Policy.TOP_INSIDE || mode==Policy.BOTH_INSIDE)) {
                val shot=inst.uiAutomation.takeScreenshot()!!
                assertEquals("top corner painted below status bar",0,shot.getPixel(0,b.top+1) and 0xFFFFFF)
                shot.recycle()
            }
        }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor("landscape observed") { ctx.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE && OverlayService.runtime?.bars?.known==true }
        bars("immersive",true);bars("normal",false)
        main { settings.setDisplayRule(ctx.packageName,1) }
        waitFor("force display exception") { OverlayService.runtime?.let { it.foreground==ctx.packageName && it.visible }==true }
        main { settings.setDisplayRule(ctx.packageName,2) }
        waitFor("force hide exception") { OverlayService.runtime?.let { it.foreground==ctx.packageName && !it.visible }==true }
        main { settings.set("fullscreen",false) }
        waitFor("exception never disables always mode") { OverlayService.runtime?.visible==true }
        main { preview!!.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
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
        }
    }
}
