package com.xiaqui.overlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
        val s=Settings(ctx);s.prefs.edit().clear().commit();s.set("enabled",false)
        s.set("clock_left",true);s.set("clock",true);s.set("size",19);s.set("x",-13)
        s.setBatteryDirection(1);s.set("battery",true);s.set("battery_size",31);s.set("battery_aspect",123);s.set("battery_x",-17)
        s.set("radius",29);s.set("top",false);s.set("bottom",false);s.set("navbar_enabled",false);s.set("navbar_unified",true)
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun labels()=views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        fun contains(prefix: String)=labels().any { it.startsWith(prefix) }
        fun rebuild() { inst.runOnMainSync { activity.refreshForTest() };inst.waitForIdleSync() }
        try {
            val headings=listOf("一般設定","ステータスバー","ナビゲーションバー","ラウンドコーナー","アプリ別設定")
            assertEquals(headings,labels().filter { it in headings })
            assertFalse(contains("全体設定"));assertFalse(contains("上部情報"));assertFalse(contains("詳細／トラブル対応"))
            assertTrue(contains("重ねて表示："));assertTrue(contains("使用状況へのアクセス："))
            assertTrue(contains("確認テスト"));assertTrue(contains("詳細設定／マニュアル"))
            assertTrue(contains("時計の配置：左"));assertTrue(contains("電池の配置：横型・端子左"))
            assertTrue(contains("時計・電池の色"));assertTrue(contains("時計サイズ: 19"));assertTrue(contains("電池サイズ: 31"))
            assertTrue("Navbar children stay visible while master is OFF",contains("上部バーにも同じ色を適用"))
            assertTrue("radius stays visible while both masks are OFF",contains("角丸半径: 29"))
            assertTrue(contains("上下OFF中は半径を保持しますが適用しません。"))
            assertTrue(s.bool("navbar_unified"));assertEquals(-17,s.offset("battery_x",0));assertEquals(123,s.batteryAspect())
            s.set("clock",false);s.set("battery",false);rebuild()
            assertTrue(contains("時計の配置：なし"));assertTrue(contains("電池の配置：なし"))
            assertFalse(contains("時計サイズ"));assertFalse(contains("電池サイズ"))
            assertTrue(s.clockOnLeft());assertEquals(1,s.batteryDirection());assertEquals(31,s.batterySize())
            s.set("clock",true);s.set("battery",true);rebuild()
            assertTrue(contains("時計の配置：左"));assertTrue(contains("電池の配置：横型・端子左"))
            val scroll=views(activity.window.decorView).filterIsInstance<ScrollView>().first()
            inst.runOnMainSync { scroll.scrollTo(0,100) };inst.waitForIdleSync();rebuild()
            assertEquals(100,views(activity.window.decorView).filterIsInstance<ScrollView>().first().scrollY)
        } finally { inst.runOnMainSync { activity.finish() };s.prefs.edit().clear().commit() }
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

    @Test fun explicitNormalAndNoneCountAsConfiguredWithoutMutation() {
        val s=Settings(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            s.setRule("normal.app",Policy.NORMAL);s.setNavbarNone("hidden.app")
            val before=s.prefs.all.toMap()
            assertTrue(s.configuredPackages().containsAll(listOf("normal.app","hidden.app")))
            assertEquals(before,s.prefs.all)
        } finally { s.prefs.edit().clear().commit() }
    }
}
