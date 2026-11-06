package com.xiaqui.overlay

import android.content.Context

class Settings(context: Context) {
    val prefs=context.getSharedPreferences("overlay",Context.MODE_PRIVATE)
    init {
        if(prefs.getInt("cornerSchema",0)<2) {
            val editor=prefs.edit()
            for((key,value) in prefs.all)if(key.startsWith("app:") && value is Int) {
                val migrated="corner2:"+key.removePrefix("app:")
                if(!prefs.contains(migrated))editor.putInt(migrated,Policy.migrateLegacy(value))
            }
            // Keep original app:* values for downgrade; one atomic, idempotent migration.
            editor.putInt("cornerSchema",2).commit()
        }
    }
    fun bool(key: String,default: Boolean=false)=prefs.getBoolean(key,default)
    fun number(key: String,default: Int)=prefs.getInt(key,default)
    fun offset(key: String,default: Int)=number(key,default).coerceIn(MIN_OFFSET_DP,MAX_OFFSET_DP)
    fun clockOnLeft()=bool("clock_left")
    fun batteryReversed()=bool("battery_reversed")
    fun set(key: String,value: Boolean) { prefs.edit().putBoolean(key,value).apply() }
    fun set(key: String,value: Int) { prefs.edit().putInt(key,value).apply() }
    fun rule(pkg: String)=Policy.mode(number("corner2:$pkg",Policy.NORMAL))
    fun setRule(pkg: String,mode: Int) { prefs.edit().putInt("corner2:$pkg",Policy.mode(mode)).apply() }
    fun displayRule(pkg: String)=number("display:$pkg",0).coerceIn(0,2)
    fun setDisplayRule(pkg: String,value: Int) {
        prefs.edit().apply { if(value==0)remove("display:$pkg") else putInt("display:$pkg",value.coerceIn(1,2)) }.apply()
    }
    fun navbarEnabled()=bool("navbar_enabled")
    fun unifiedBars()=navbarEnabled() && bool("navbar_unified")
    fun suppressRecentsTint()=navbarEnabled() && bool("navbar_recents_off")
    fun navbarMode(pkg: String): Int {
        if(number("navbarMode:$pkg",NAVBAR_AUTO)==NAVBAR_NONE)return NAVBAR_NONE
        return if(prefs.contains("navbar:$pkg"))NAVBAR_FIXED else NAVBAR_AUTO
    }
    fun navbarColor(pkg: String): Int?=if(prefs.contains("navbar:$pkg"))number("navbar:$pkg",0) else null
    fun setNavbarColor(pkg: String,color: Int?) {
        prefs.edit().apply {
            if(color==null) { remove("navbar:$pkg");remove("navbarMode:$pkg") }
            else { putInt("navbar:$pkg",color or (255 shl 24));putInt("navbarMode:$pkg",NAVBAR_FIXED) }
        }.apply()
    }
    fun setNavbarNone(pkg: String) {
        prefs.edit().remove("navbar:$pkg").putInt("navbarMode:$pkg",NAVBAR_NONE).apply()
    }
    fun hasRules()=navbarEnabled() || prefs.all.keys.any { it.startsWith("corner2:") || it.startsWith("display:") }
    fun needsBars()=navbarEnabled() || bool("fullscreen") || prefs.all.any { (k,v)->k.startsWith("corner2:") && v is Int && v in Policy.TOP_INSIDE..Policy.BOTH_INSIDE }
    fun defaultMask()=(if(bool("top"))1 else 0) or (if(bool("bottom"))2 else 0)

    companion object {
        const val MIN_OFFSET_DP=-64
        const val MAX_OFFSET_DP=100
        const val NAVBAR_AUTO=0
        const val NAVBAR_FIXED=1
        const val NAVBAR_NONE=2
    }
}


