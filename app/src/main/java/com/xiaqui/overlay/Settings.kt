package com.xiaqui.overlay

import android.content.Context

class Settings(context: Context) {
    val prefs=context.getSharedPreferences("overlay",Context.MODE_PRIVATE)
    init {
        if(!prefs.contains("batterySchema")) {
            // Snapshot the old shared values once. Keep the original keys for downgrade.
            val edit=prefs.edit()
            if(!prefs.contains("battery_size"))edit.putInt("battery_size",number("size",14).coerceIn(10,28))
            if(!prefs.contains("battery_base_x"))edit.putInt("battery_base_x",offset("x",4))
            if(!prefs.contains("battery_base_y"))edit.putInt("battery_base_y",offset("y",0))
            if(!prefs.contains("battery_anchor_size"))edit.putInt("battery_anchor_size",number("size",14).coerceIn(10,28))
            edit.putInt("batterySchema",1).commit()
        }
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
    fun batteryDirection()=number("battery_direction",if(bool("battery_reversed"))1 else 0).coerceIn(0,2)
    fun batteryReversed()=batteryDirection()==1
    fun setBatteryDirection(value: Int) {
        val direction=value.coerceIn(0,2)
        prefs.edit().putInt("battery_direction",direction).putBoolean("battery_reversed",direction==1).apply()
    }
    fun batterySize()=number("battery_size",14).coerceIn(10,40)
    fun batteryAspect()=number("battery_aspect",100).coerceIn(60,160)
    fun independentBattery()=batteryDirection()==2 || batteryAspect()!=100 ||
        batterySize()!=number("size",14) || offset("battery_x",0)!=0 || offset("battery_y",0)!=0 ||
        number("battery_base_x",4)!=offset("x",4) || number("battery_base_y",0)!=offset("y",0)
    fun resetBatteryAdjustments() {
        prefs.edit().putInt("battery_size",number("battery_anchor_size",14).coerceIn(10,28))
            .putInt("battery_aspect",100).putInt("battery_x",0).putInt("battery_y",0).apply()
    }
    fun set(key: String,value: Boolean) { prefs.edit().putBoolean(key,value).apply() }
    fun set(key: String,value: Int) { prefs.edit().putInt(key,value).apply() }
    fun rule(pkg: String)=Policy.mode(number("corner2:$pkg",Policy.NORMAL))
    fun setRule(pkg: String,mode: Int) { prefs.edit().putInt("corner2:$pkg",Policy.mode(mode)).apply() }
    fun displayRule(pkg: String)=Policy.displayRule(number("display:$pkg",Policy.DISPLAY_AUTO))
    fun setDisplayRule(pkg: String,value: Int) {
        prefs.edit().apply {
            if(value==Policy.DISPLAY_AUTO)remove("display:$pkg")
            else putInt("display:$pkg",value.coerceIn(Policy.DISPLAY_BOTH,Policy.DISPLAY_BATTERY))
        }.apply()
    }
    fun navbarEnabled()=bool("navbar_enabled")
    fun configuredPackages()=prefs.all.keys.mapNotNull { key->
        listOf("corner2:","display:","navbar:","navbarMode:").firstOrNull { key.startsWith(it) }
            ?.let { key.removePrefix(it) }
    }.toSet()
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



