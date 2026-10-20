package com.xiaqui.overlay

import android.content.Context

class Settings(context: Context) {
    val prefs = context.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    fun bool(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun number(key: String, default: Int) = prefs.getInt(key, default)
    fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun set(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun rule(pkg: String): Int? = if (prefs.contains("app:$pkg")) number("app:$pkg", 0) else null
    fun setRule(pkg: String, mask: Int?) {
        prefs.edit().apply { if (mask == null) remove("app:$pkg") else putInt("app:$pkg", mask) }.apply()
    }
    fun hasRules() = prefs.all.keys.any { it.startsWith("app:") }
    fun defaultMask() = (if (bool("top")) 1 else 0) or (if (bool("bottom")) 2 else 0)
}
