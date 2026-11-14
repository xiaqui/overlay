package com.xiaqui.overlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    private fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())

    @Test fun reorderedSectionsAndSavedValuesRemainVisible() {
        val inst=InstrumentationRegistry.getInstrumentation();val ctx=inst.targetContext
        ctx.getSharedPreferences("overlay",0).edit().clear().commit()
        val s=Settings(ctx);s.set("enabled",false)
        s.set("clock_left",true);s.set("clock",true);s.set("size",19);s.set("x",-13)
        s.setBatteryDirection(1);s.set("battery",true);s.set("battery_size",31);s.set("battery_aspect",123);s.set("battery_x",-17)
        s.set("radius",29);s.set("top",true);s.set("bottom",false);s.set("navbar_enabled",false);s.set("navbar_unified",true)
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun labels()=views(activity.window.decorView).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
        fun contains(prefix: String)=labels().any { it.startsWith(prefix) }
        fun rebuild() { inst.runOnMainSync { activity.refreshForTest() };inst.waitForIdleSync() }
        try {
            val headings=listOf("オーバーレイ","ステータスバー補助","ナビゲーションバー着色","ラウンドコーナー","詳細設定")
            assertEquals(headings,labels().filter { it in headings }.distinct())
            assertFalse(contains("全体設定"));assertFalse(contains("上部情報"));assertFalse(contains("詳細／トラブル対応"))
            assertTrue(contains("重ねて表示："));assertTrue(contains("使用状況へのアクセス："))
            assertTrue(contains("確認テスト"));assertTrue(contains("マニュアルと診断"))
            assertTrue(contains("時計の配置：左"));assertTrue(contains("電池の配置：横型・端子左"))
            val statusLabels=labels()
            val colorIndex=statusLabels.indexOfFirst { it=="時計・電池の色" }
            val fullscreenIndex=statusLabels.indexOfFirst { it=="全画面のときだけ" }
            val clockIndex=statusLabels.indexOfFirst { it.startsWith("時計の配置：") }
            val clockSliderIndex=statusLabels.indexOfFirst { it.startsWith("時計サイズ:") }
            val batteryIndex=statusLabels.indexOfFirst { it.startsWith("電池の配置：") }
            val batterySliderIndex=statusLabels.indexOfFirst { it.startsWith("電池サイズ:") }
            assertTrue("status menu order",fullscreenIndex in 0 until colorIndex && colorIndex<clockIndex && clockIndex<clockSliderIndex && clockSliderIndex<batteryIndex && batteryIndex<batterySliderIndex)
            assertTrue(contains("時計・電池の色"));assertTrue(contains("時計サイズ: 19"));assertTrue(contains("電池サイズ: 31"))
            assertTrue("Navbar children stay visible while master is OFF",contains("上部バーにも同じ色を適用"))
            assertTrue(contains("角丸半径: 29"))
            assertTrue(s.fullscreenOnly());assertFalse(s.prefs.contains("fullscreen"))
            assertTrue(contains("以前の設定：上の角丸のみ有効"));assertEquals(1,s.defaultMask())
            assertTrue(labels().indexOfFirst { it.startsWith("使用状況へのアクセス：") } in
                (labels().indexOf("ナビゲーションバー着色")+1) until labels().indexOf("ラウンドコーナー"))
            assertEquals(listOf("アプリ別設定","マニュアルと診断"),labels().drop(labels().indexOf("詳細設定")+1))
            fun switch(label: String)=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text==label }
            val unified=switch("上部バーにも同じ色を適用")
            val recents=switch("Recentsでは着色しない")
            val fullscreen=switch("全画面のときだけ")
            val corner=switch("ラウンドコーナー")
            val radius=views(activity.window.decorView).filterIsInstance<SeekBar>().last()
            assertTrue(fullscreen.isChecked && fullscreen.isEnabled);assertFalse(switch("オーバーレイ").isChecked)
            assertFalse(unified.isEnabled);assertFalse(recents.isEnabled)
            assertFalse(unified.createAccessibilityNodeInfo().isEnabled)
            assertTrue(corner.isChecked && radius.isEnabled)
            val rootBeforeToggle=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val navbarSwitch=switch("ナビゲーションバー着色")
            val scrollBefore=rootBeforeToggle as ScrollView
            inst.runOnMainSync { scrollBefore.scrollTo(0,100) }
            val y=scrollBefore.scrollY
            assertTrue(y>0)
            inst.runOnMainSync { navbarSwitch.isChecked=true };inst.waitForIdleSync()
            assertSame("switch changes do not replace ScrollView",rootBeforeToggle,activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
            assertTrue(s.bool("navbar_enabled"))
            assertTrue(unified.isEnabled && unified.isChecked && recents.isEnabled)
            assertEquals(y,scrollBefore.scrollY)
            inst.runOnMainSync { navbarSwitch.isChecked=false };inst.waitForIdleSync()
            assertFalse(s.bool("navbar_enabled"))
            assertFalse(unified.isEnabled);assertFalse(recents.isEnabled)
            inst.runOnMainSync { corner.isChecked=false;fullscreen.isChecked=false }
            assertEquals(0,s.defaultMask());assertFalse(radius.isEnabled);assertEquals(29,s.number("radius",18))
            assertTrue("radius stays visible while both masks are OFF",contains("角丸半径: 29"))
            inst.runOnMainSync { corner.isChecked=true }
            assertEquals(3,s.defaultMask());assertTrue(radius.isEnabled)
            assertFalse(Settings(ctx).fullscreenOnly())
            assertTrue(s.bool("navbar_unified"));assertEquals(-17,s.offset("battery_x",0));assertEquals(123,s.batteryAspect())
            s.set("clock",false);s.set("battery",false);rebuild()
            assertTrue(contains("時計の配置：なし"));assertTrue(contains("電池の配置：なし"))
            assertFalse(contains("時計サイズ"));assertFalse(contains("電池サイズ"))
            assertTrue(s.clockOnLeft());assertEquals(1,s.batteryDirection());assertEquals(31,s.batterySize())
            s.set("clock",true);s.set("battery",true);rebuild()
            assertTrue(contains("時計の配置：左"));assertTrue(contains("電池の配置：横型・端子左"))
            val scroll=views(activity.window.decorView).filterIsInstance<ScrollView>().first()
            inst.runOnMainSync { scroll.scrollTo(0,100) };inst.waitForIdleSync();rebuild()
            assertEquals("PR26 behavior: full refresh starts at the top",0,views(activity.window.decorView).filterIsInstance<ScrollView>().first().scrollY)
        } finally { inst.runOnMainSync { activity.finish() };s.prefs.edit().clear().commit() }
    }

    @Test fun headingStartsStopsAndResumesWithoutResettingChildSettings() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val ctx=inst.targetContext
        fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        fun waitFor(message: String,predicate: ()->Boolean) {
            val end=SystemClock.elapsedRealtime()+8000L
            while(!predicate() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(100)
            assertTrue(message,predicate())
        }
        val prefs=ctx.getSharedPreferences("overlay",0)
        prefs.edit().clear().commit()
        shell("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            val all=views(activity.window.decorView).filterIsInstance<Switch>()
            val master=all.single { it.text=="オーバーレイ" }
            val corner=all.single { it.text=="ラウンドコーナー" }
            inst.runOnMainSync { corner.isChecked=true;master.isChecked=true }
            waitFor("start applies settings edited while stopped") { OverlayService.runtime?.locations?.size==4 }
            inst.runOnMainSync { master.isChecked=false }
            waitFor("stop removes service windows") { OverlayService.runtime==null }
            assertTrue(corner.isEnabled && corner.isChecked)
            assertEquals(3,Settings(ctx).defaultMask())
            inst.runOnMainSync { master.isChecked=true }
            waitFor("restart uses same settings") { OverlayService.runtime?.locations?.size==4 }
        } finally {
            inst.runOnMainSync {
                prefs.edit().putBoolean("enabled",false).apply()
                ctx.stopService(Intent(ctx,OverlayService::class.java))
                activity.finish()
            }
            inst.waitForIdleSync()
            prefs.edit().clear().commit()
        }
    }

    @Test fun confirmationTestRunsWhiteThenBlackAndReturns() {
        val inst=InstrumentationRegistry.getInstrumentation();val ctx=inst.targetContext
        val activity=inst.startActivitySync(Intent(ctx,PreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PreviewActivity
        try {
            val view=activity.findViewById<View>(android.R.id.content).let { (it as ViewGroup).getChildAt(0) }
            assertEquals(Color.WHITE,(view.background as ColorDrawable).color)
            inst.runOnMainSync { view.performClick() };inst.waitForIdleSync()
            assertEquals(Color.BLACK,(view.background as ColorDrawable).color)
            inst.runOnMainSync { view.performClick() };inst.waitForIdleSync()
            assertTrue(activity.isFinishing)
        } finally { if(!activity.isFinishing)inst.runOnMainSync { activity.finish() } }
    }

    @Test fun returningAllAppRulesToDefaultsRemovesConfiguredMarker() {
        val s=Settings(InstrumentationRegistry.getInstrumentation().targetContext)
        val pkg="test.default.reset"
        try {
            s.setRule(pkg,Policy.TOP_INSIDE)
            s.setDisplayRule(pkg,Policy.DISPLAY_ALWAYS)
            s.setNavbarNone(pkg)
            assertTrue(s.configuredPackages().contains(pkg))
            s.setRule(pkg,Policy.NORMAL)
            assertTrue("other overrides still count",s.configuredPackages().contains(pkg))
            s.setDisplayRule(pkg,Policy.DISPLAY_AUTO)
            s.setNavbarColor(pkg,null)
            assertFalse("all defaults must remove configured status",s.configuredPackages().contains(pkg))
            assertFalse(s.prefs.contains("corner2:$pkg"))
            assertFalse(s.prefs.contains("display:$pkg"))
            assertFalse(s.prefs.contains("navbar:$pkg"))
            assertFalse(s.prefs.contains("navbarMode:$pkg"))
            s.setNavbarNone(pkg)
            assertTrue("explicit navbar NONE remains configured",s.configuredPackages().contains(pkg))
        } finally { s.prefs.edit().clear().commit() }
    }

    @Test fun migrationPreservesSupportedRulesAndSavedFullscreenValues() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("overlay",0)
        try {
            for(saved in listOf(null,false,true)) {
                val editor=prefs.edit().clear()
                if(saved!=null)editor.putBoolean("fullscreen",saved)
                for(i in 0..4)editor.putInt("display:migration.$i",i)
                editor.putInt("corner2:migration.3",Policy.TOP_INSIDE)
                    .putInt("navbarMode:migration.4",Settings.NAVBAR_NONE)
                    .putBoolean("bottom",true).commit()
                repeat(2) {
                    val s=Settings(ctx)
                    assertEquals(saved ?: true,s.fullscreenOnly())
                    assertEquals(saved!=null,prefs.contains("fullscreen"))
                    assertEquals(2,s.defaultMask())
                    assertEquals(listOf(0,1,2,0,0),(0..4).map { s.displayRule("migration.$it") })
                    for(i in listOf(0,3,4))assertFalse(prefs.contains("display:migration.$i"))
                    assertEquals(Policy.TOP_INSIDE,s.rule("migration.3"))
                    assertEquals(Settings.NAVBAR_NONE,s.navbarMode("migration.4"))
                    assertEquals(setOf("migration.1","migration.2","migration.3","migration.4"),s.configuredPackages())
                }
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun homeCategoryAppsAppearInAppPicker() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val ctx=inst.targetContext
        val pm=ctx.packageManager
        val homePackages=pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0
        ).map { it.activityInfo.packageName }.toSet()
        val s=Settings(ctx)
        s.set("enabled",false)
        val activity=inst.startActivitySync(
            Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        try {
            val packages=activity.appEntries().map { it.first }.toSet()
            assertTrue("All HOME handlers must appear in app picker",packages.containsAll(homePackages))
            for(pkg in listOf("com.sonyericsson.home","com.sonymobile.home")) {
                if(runCatching { pm.getApplicationInfo(pkg,0) }.isSuccess)
                    assertTrue("Installed Xperia Home must appear: $pkg",packages.contains(pkg))
            }
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
