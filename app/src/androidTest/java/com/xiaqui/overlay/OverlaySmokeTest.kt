package com.xiaqui.overlay

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real WindowManager and foreground-service APIs on API23/26/29. */
@RunWith(AndroidJUnit4::class)
class OverlaySmokeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun shell(command: String): String {
        val fd=instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
    private fun waitFor(predicate: ()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+10_000
        while(android.os.SystemClock.elapsedRealtime()<end) {
            if(predicate())return
            android.os.SystemClock.sleep(100)
        }
        assertTrue("Timed out waiting for service state",predicate())
    }
    @Test fun overlayLifecycleAndFullscreenWhiteBlack() {
        val settings=Settings(context)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        try {
            instrumentation.runOnMainSync {
                settings.set("enabled",true);settings.set("top",true);settings.set("bottom",true)
                OverlayService.start(context)
            }
            waitFor { settings.prefs.getString("diagnostic","")?.startsWith("旧方式 2006")==true }
            for(dark in listOf(false,true)) {
                val preview=instrumentation.startActivitySync(Intent(context,PreviewActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("dark",dark))
                assertNotNull("Preview reached foreground",preview)
                instrumentation.waitForIdleSync()
                val background=if(dark)0 else 0xFFFFFF
                // After real navigation-button tests, SystemUI may retain a transient bar for
                // longer than 1s. Require actual hidden-bar pixels, not an arbitrary sleep.
                waitFor {
                    val frame=instrumentation.uiAutomation.takeScreenshot()
                    if(frame==null)false else try {
                        (frame.getPixel(frame.width/2,frame.height/4) and 0xFFFFFF)==background &&
                            (frame.getPixel(frame.width/2,2) and 0xFFFFFF)==background &&
                            (frame.getPixel(frame.width/2,frame.height-3) and 0xFFFFFF)==background
                    } finally { frame.recycle() }
                }
                val shot=instrumentation.uiAutomation.takeScreenshot()
                assertNotNull("Fullscreen display is capturable",shot)
                val bitmap=shot!!
                assertEquals("Preview background",background,bitmap.getPixel(bitmap.width/2,bitmap.height/4) and 0xFFFFFF)
                assertEquals("Status bar is hidden",background,bitmap.getPixel(bitmap.width/2,2) and 0xFFFFFF)
                assertEquals("Navigation bar is hidden",background,bitmap.getPixel(bitmap.width/2,bitmap.height-3) and 0xFFFFFF)
                // The black mask must be present at all four physical corners.
                for((x,y) in listOf(0 to 0,(bitmap.width-1) to 0,0 to (bitmap.height-1),(bitmap.width-1) to (bitmap.height-1))) {
                    assertEquals("Black corner at $x,$y",0,bitmap.getPixel(x,y) and 0xFFFFFF)
                }
                // White clock/battery pixels survive on a black fullscreen activity.
                if(dark) {
                    var bright=0
                    val density=context.resources.displayMetrics.density
                    val xmin=(bitmap.width-160*density).toInt().coerceAtLeast(0)
                    val ymax=(50*density).toInt().coerceAtMost(bitmap.height)
                    for(y in 0 until ymax)for(x in xmin until bitmap.width) {
                        val c=bitmap.getPixel(x,y)
                        if(android.graphics.Color.red(c)>180 && android.graphics.Color.green(c)>180 && android.graphics.Color.blue(c)>180)bright++
                    }
                    assertTrue("Clock/battery visible above fullscreen",bright>20)
                }
                val x=(bitmap.width-40*context.resources.displayMetrics.density).toInt()
                val y=(12*context.resources.displayMetrics.density).toInt()
                bitmap.recycle()
                shell("input tap $x $y")
                waitFor { preview!!.isFinishing || preview.isDestroyed }
            }
            instrumentation.runOnMainSync { settings.set("enabled",false);context.stopService(Intent(context,OverlayService::class.java)) }
            waitFor { !shell("dumpsys activity services ${context.packageName}").contains("ServiceRecord{") }
            // Explicitly test the newer fallback type on Oreo+.
            if(android.os.Build.VERSION.SDK_INT>=26) {
                instrumentation.runOnMainSync { settings.set("modern",true);settings.set("enabled",true);OverlayService.start(context) }
                waitFor { settings.prefs.getString("diagnostic","")?.startsWith("互換方式 2038")==true }
            }
        } finally {
            instrumentation.runOnMainSync { settings.set("enabled",false);context.stopService(Intent(context,OverlayService::class.java));settings.prefs.edit().clear().commit() }
        }
    }
}

