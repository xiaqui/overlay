package com.xiaqui.overlay

import org.junit.Assert.*
import org.junit.Test

class RecentsPolicyTest {
    private fun target(pkg: String,cls: String?)=ForegroundTarget(pkg,cls,1L)

    @Test fun exactSystemUiRecentsOnly() {
        assertTrue(RecentsPolicy.shouldSuppress(true,target("com.android.systemui",".recents.RecentsActivity"),null,null))
        assertFalse(RecentsPolicy.shouldSuppress(true,target("com.android.systemui","com.android.systemui.statusbar.phone.StatusBar"),null,null))
        assertFalse(RecentsPolicy.shouldSuppress(true,target("com.android.systemui",null),null,null))
        assertFalse(RecentsPolicy.shouldSuppress(false,target("com.android.systemui",".recents.RecentsActivity"),null,null))
    }

    @Test fun configuredComponentMatchesAndLauncherFallbackIsNarrow() {
        val quickstep=RecentsComponent("com.android.launcher3","com.android.quickstep.RecentsActivity")
        assertTrue(RecentsPolicy.shouldSuppress(true,target("com.android.launcher3","com.android.quickstep.RecentsActivity"),"com.android.launcher3",quickstep))
        assertTrue(RecentsPolicy.shouldSuppress(true,target("com.android.launcher3","com.android.launcher3.Launcher"),"com.android.launcher3",quickstep))
        assertFalse(RecentsPolicy.shouldSuppress(true,target("example.app","example.app.Main"),"com.android.launcher3",quickstep))
        val system=RecentsComponent("com.vendor.systemui","com.vendor.systemui.RecentTasks")
        assertFalse(RecentsPolicy.shouldSuppress(true,target("com.vendor.launcher","com.vendor.launcher.Home"),"com.vendor.launcher",system))
        assertTrue(RecentsPolicy.shouldSuppress(true,target("com.vendor.systemui",".RecentTasks"),"com.vendor.launcher",system))
    }
}
