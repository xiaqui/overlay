package com.xiaqui.overlay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @Test fun sectionsDependentsAndSavedBatteryValues() {
        val inst=InstrumentationRegistry.getInstrumentation();val ctx=inst.targetContext
        val s=Settings(ctx);s.set("enabled",false)
        s.set("battery_size",31);s.set("battery_aspect",123);s.set("battery_x",-17)
        s.set("radius",29);s.set("top",true);s.set("bottom",false);s.set("navbar_unified",true)
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
        fun labels()=views(activity.window.decorView).filterIsInstance<TextView>()
        fun contains(prefix: String)=labels().any { it.text.toString().startsWith(prefix) }
        fun click(label: String) { inst.runOnMainSync { labels().first { it.text.toString()==label }.performClick() };inst.waitForIdleSync() }
        try {
            for(label in listOf("全体設定","上部情報","角丸","ナビバー","アプリ別設定"))assertTrue(contains(label))
            assertFalse(contains("電池の調整を初期値"));assertFalse(contains("互換表示方式"))
            click("電池");assertFalse(contains("電池サイズ"));assertEquals(31,s.batterySize());assertEquals(123,s.batteryAspect())
            click("電池");assertTrue(contains("電池サイズ: 31"));assertEquals(-17,s.offset("battery_x",0))
            click("時計");assertFalse(contains("時計サイズ"));click("時計");assertTrue(contains("時計サイズ"))
            click("上の角丸");assertFalse(contains("角丸半径"));assertEquals(29,s.number("radius",0))
            click("上の角丸");assertTrue(contains("角丸半径: 29"))
        } finally { inst.runOnMainSync { activity.finish() };s.prefs.edit().clear().commit() }
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
