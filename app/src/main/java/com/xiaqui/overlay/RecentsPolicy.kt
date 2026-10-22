package com.xiaqui.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class ForegroundTarget(val packageName: String,val className: String?,val eventTime: Long)
data class RecentsComponent(val packageName: String,val className: String)

/** Exact component matching first; package-only SystemUI matching is deliberately forbidden. */
object RecentsPolicy {
    private const val AOSP_SYSTEMUI_PACKAGE="com.android.systemui"
    private const val AOSP_SYSTEMUI_CLASS="com.android.systemui.recents.RecentsActivity"

    fun normalizeClass(pkg: String,cls: String?): String?=when {
        cls.isNullOrBlank()->null
        cls.startsWith(".")->pkg+cls
        '.' !in cls->"$pkg.$cls"
        else->cls
    }

    fun shouldSuppress(enabled: Boolean,target: ForegroundTarget?,homePackage: String?,recents: RecentsComponent?): Boolean {
        if(!enabled || target==null)return false
        val cls=normalizeClass(target.packageName,target.className) ?: return false
        if(target.packageName==AOSP_SYSTEMUI_PACKAGE && cls==AOSP_SYSTEMUI_CLASS)return true
        if(recents!=null) {
            val expected=normalizeClass(recents.packageName,recents.className)
            if(target.packageName==recents.packageName && cls==expected)return true
            // Some Quickstep builds keep Launcher resumed and expose Overview only as state.
            // Hiding Home as well is the conservative fallback explicitly allowed by the spec.
            if(recents.packageName==homePackage && target.packageName==homePackage)return true
        }
        return false
    }
}

class RecentsDetector(context: Context) {
    private val resources=context.resources
    private val pm=context.packageManager
    val homePackage: String?=runCatching {
        val result=pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY)
        result?.activityInfo?.packageName?.takeUnless { it=="android" }
    }.getOrNull()
    val recentsComponent: RecentsComponent?=runCatching {
        val id=resources.getIdentifier("config_recentsComponentName","string","android")
        if(id==0)null else ComponentName.unflattenFromString(resources.getString(id))?.let {
            component->RecentsComponent(component.packageName,component.className)
        }
    }.getOrNull()
    fun shouldSuppress(enabled: Boolean,target: ForegroundTarget?)=
        RecentsPolicy.shouldSuppress(enabled,target,homePackage,recentsComponent)
}
