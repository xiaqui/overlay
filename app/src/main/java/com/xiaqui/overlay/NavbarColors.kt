package com.xiaqui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.TypedValue

/** Public package resources only. No screen capture, network, or reference-APK libraries. */
class NavbarColors(private val context: Context) {
    private data class Entry(val color: NavbarColor?)
    private val cache=object: LinkedHashMap<String,Entry>(32,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Entry>?)=size>32
    }
    var loads=0;private set
    fun clear() { cache.clear() }
    fun resolve(pkg: String,automatic: Boolean,fixed: Int?): NavbarColor? {
        if(fixed!=null || !automatic)return NavbarPolicy.select(fixed,automatic,{null},{null})
        cache[pkg]?.let { return it.color }
        loads++
        val value=NavbarPolicy.select(null,true,{themeColor(pkg)},{iconColor(pkg)})
        cache[pkg]=Entry(value)
        return value
    }
    internal fun themeColor(pkg: String): Int?=runCatching {
        val pm=context.packageManager
        val app=pm.getApplicationInfo(pkg,0)
        val res=pm.getResourcesForApplication(app)
        val component=pm.getLaunchIntentForPackage(pkg)?.component
        val style=component?.let { pm.getActivityInfo(it,0).theme }?.takeIf { it!=0 } ?: app.theme
        if(style==0)return@runCatching null
        val theme=res.newTheme().apply { applyStyle(style,true) }
        val custom=res.getIdentifier("colorPrimary","attr",pkg)
        for(attr in intArrayOf(custom,android.R.attr.colorPrimary))if(attr!=0) {
            val value=TypedValue()
            if(theme.resolveAttribute(attr,value,true) && value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT && value.data ushr 24==255)
                return@runCatching value.data
        }
        null
    }.getOrNull()
    private fun iconColor(pkg: String): Int?=runCatching { iconColor(context.packageManager.getApplicationIcon(pkg)) }.getOrNull()
    internal fun iconColor(drawable: Drawable): Int? {
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        return try {
            drawable.setBounds(0,0,32,32);drawable.draw(Canvas(bitmap))
            val pixels=IntArray(32*32);bitmap.getPixels(pixels,0,32,0,0,32,32)
            NavbarPolicy.representative(pixels)
        } finally { bitmap.recycle() }
    }
}
