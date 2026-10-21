package com.xiaqui.overlay

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings as SystemSettings
import android.view.*

@Suppress("DEPRECATION")
class OverlayService : Service() {
    private lateinit var settings: Settings
    private lateinit var wm: WindowManager
    private val handler=Handler(Looper.getMainLooper())
    private val windows=mutableListOf<View>()
    private var status: StatusView?=null
    private var observation: BarObservation?=null
    private var bars=BarState()
    private var lastCornerLayout: List<CornerOffset>?=null
    private var updatingBars=false
    private var screenOn=true
    private var battery=-1
    private var charging=false
    private var foreground: String?=null
    private var lastQuery=0L
    private var lastMask=-1
    private var receiverRegistered=false
    private var activeType=2006
    private val cornerWindows=mutableListOf<View>()
    private var navbarLayer: NavbarLayer?=null
    private var navbarColors: NavbarColors?=null
    private var navbarKey: String?=null
    private var navbarCandidate: NavbarColor?=null
    private var navbarReason="未適用"
    private var lastBandOwned=false
    private var packagesRegistered=false
    private val packageReceiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            navbarColors?.clear();navbarKey=null
            handler.removeCallbacks(onBarsChanged);if(screenOn)handler.post(onBarsChanged)
        }
    }
    private val minute=object: Runnable {
        override fun run() {
            if (!screenOn) return
            if (!SystemSettings.canDrawOverlays(this@OverlayService)) { fail("重ねて表示の権限が取り消されました");return }
            status?.apply { requestLayout();invalidate() }
            handler.postDelayed(this,Policy.nextMinute(System.currentTimeMillis()))
        }
    }
    private val poll=object: Runnable {
        override fun run() {
            if (!screenOn) return
            if (!SystemSettings.canDrawOverlays(this@OverlayService)) { fail("重ねて表示の権限が取り消されました");return }
            try { updateForeground();refreshBars();updateStatusVisibility();updateCorners() }
            catch (_: SecurityException) { fail("表示権限または使用状況の許可を確認してください");return }
            catch (_: WindowManager.BadTokenException) { fail("表示ウィンドウが拒否されました");return }
            handler.postDelayed(this,if(settings.needsBars())500 else 1000)
        }
    }
    private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "diagnostic") { lastQuery=0;rebuild() }
    }
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn=false;handler.removeCallbacks(minute);handler.removeCallbacks(poll)
                    windows.forEach { it.visibility=View.GONE }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn=true;lastQuery=0;rebuild()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    battery=Policy.batteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),intent.getIntExtra(BatteryManager.EXTRA_SCALE,0))
                    charging=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0
                    status?.apply { percent=battery;this.charging=this@OverlayService.charging;if(screenOn) invalidate() }
                }
                else -> { status?.apply { requestLayout();if(screenOn) invalidate() };handler.removeCallbacks(minute);if(screenOn)handler.post(minute) }
            }
        }
    }
    override fun onCreate() {
        super.onCreate();settings=Settings(this);wm=getSystemService(WINDOW_SERVICE) as WindowManager
        screenOn=(getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        if(Build.VERSION.SDK_INT>=26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel("overlay","Overlayの常駐",NotificationManager.IMPORTANCE_LOW))
        }
        val piFlags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),piFlags)
        val stop=PendingIntent.getService(this,1,Intent(this,OverlayService::class.java).setAction("stop"),piFlags)
        val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"overlay") else Notification.Builder(this)
        startForeground(1,builder.setSmallIcon(com.xiaqui.overlay.R.drawable.ic_overlay).setContentTitle("Overlay")
            .setContentText("時計・電池・角丸を表示中").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"停止",stop).build()).build())
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        registerReceiver(receiver,IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(Intent.ACTION_TIME_TICK)
        });receiverRegistered=true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="stop") { settings.set("enabled",false);stopSelf();return START_NOT_STICKY }
        if(!settings.bool("enabled")) { stopSelf();return START_NOT_STICKY }
        rebuild();return START_STICKY
    }
    private fun fail(message: String) {
        settings.prefs.edit().putString("diagnostic",message).apply()
        settings.set("enabled",false);clearWindows();stopSelf()
    }
    private fun clearWindows() {
        handler.removeCallbacks(minute);handler.removeCallbacks(poll);handler.removeCallbacks(onBarsChanged)
        navbarLayer?.clear();navbarLayer=null;lastBandOwned=false;navbarKey=null;navbarCandidate=null
        observation=null;bars=BarState();lastCornerLayout=null
        windows.toList().forEach { runCatching { wm.removeViewImmediate(it) } }
        windows.clear();cornerWindows.clear();status=null;lastMask=-1;runtime=null
    }
    private fun params(width: Int,height: Int,gravityValue: Int,typeValue: Int=activeType)=WindowManager.LayoutParams(
        width,height,typeValue,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply {
        gravity=gravityValue
        if(Build.VERSION.SDK_INT>=28) layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        title="Overlay"
    }
    private fun add(view: View,p: WindowManager.LayoutParams) { wm.addView(view,p);windows.add(view) }
    private fun rebuild() {
        if(!::settings.isInitialized) return
        syncNavbarPackages()
        clearWindows()
        if(!settings.bool("enabled")) { stopSelf();return }
        if(!SystemSettings.canDrawOverlays(this)) { fail("重ねて表示を許可してください");return }
        if(!screenOn)return
        val types=Policy.windowTypes(Build.VERSION.SDK_INT,settings.bool("modern"))
        for(type in types) {
            activeType=type
            try {
                status=StatusView(this,settings).apply { percent=battery;charging=this@OverlayService.charging }
                val density=resources.displayMetrics.density
                val p=params(-2,-2,Gravity.TOP or Gravity.RIGHT).apply {
                    x=(settings.number("x",4)*density).toInt();y=(settings.number("y",0)*density).toInt()
                }
                add(status!!,p)
                if(settings.needsBars())attachBarObservation()
                updateStatusVisibility()
                updateForeground();updateCorners()
                settings.prefs.edit().putString("diagnostic",if(type==2006)"旧方式 2006（バーとの重なりは機種依存）" else "互換方式 2038（システムバーより背面）").apply()
                handler.post(minute)
                if(settings.needsBars() || (settings.hasRules() && usageAllowed(this)))handler.post(poll)
                return
            } catch (_: SecurityException) { clearWindows() }
              catch (_: WindowManager.BadTokenException) { clearWindows() }
        }
        fail("表示ウィンドウを作れません。権限・端末の制限を確認してください")
    }
    private fun attachBarObservation() {
        val observer=BarObservation(this) {
            // Post after onApplyWindowInsets stores the latest root insets.
            handler.removeCallbacks(onBarsChanged);handler.post(onBarsChanged)
        }
        observation=observer
        try {
            add(observer.vertical,BarObservation.params(true,Build.VERSION.SDK_INT))
            add(observer.horizontal,BarObservation.params(false,Build.VERSION.SDK_INT))
        } catch (_: SecurityException) { detachObservation(observer) }
          catch (_: WindowManager.BadTokenException) { detachObservation(observer) }
    }
    private fun detachObservation(observer: BarObservation) {
        observation=null
        observer.views.forEach { if(windows.remove(it))runCatching { wm.removeViewImmediate(it) } }
        bars=BarState()
    }
    private val onBarsChanged=Runnable {
        if(screenOn && observation!=null)try { refreshBars();updateStatusVisibility();updateCorners() }
        catch (_: SecurityException) { fail("表示権限を確認してください") }
        catch (_: WindowManager.BadTokenException) { fail("表示ウィンドウが拒否されました") }
    }
    private fun refreshBars() {
        if(updatingBars)return
        updatingBars=true
        try { bars=observation?.read() ?: BarState() } finally { updatingBars=false }
    }
    private fun updateStatusVisibility() {
        val exception=foreground?.let { settings.displayRule(it) } ?: 0
        val visible=Policy.statusVisible(settings.bool("fullscreen"),exception,bars)
        status?.visibility=if(screenOn && visible && (settings.bool("clock",true)||settings.bool("battery",true)))View.VISIBLE else View.INVISIBLE
        publishRuntime()
    }
    private fun publishRuntime() {
        runtime=RuntimeState(status?.visibility==View.VISIBLE,bars,foreground,lastCornerLayout ?: emptyList(),cornerWindows.map {
            val p=IntArray(2);it.getLocationOnScreen(p);CornerOffset(p[0],p[1])
        },if(settings.navbarEnabled())NavbarRuntime(screenOn && navbarLayer?.ready==true,navbarCandidate?.color,navbarCandidate?.source,
            navbarLayer?.view?.height ?: 0,activeType,navbarLayer?.reason ?: navbarReason,navbarColors?.loads ?: 0,navbarLayer?.view?.draws ?: 0) else null,windows.size,activeType)
    }
    private fun syncNavbarPackages() {
        if(settings.navbarEnabled() && !packagesRegistered) {
            registerReceiver(packageReceiver,IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REPLACED);addAction(Intent.ACTION_PACKAGE_REMOVED);addDataScheme("package")
            });packagesRegistered=true
        } else if(!settings.navbarEnabled()) {
            if(packagesRegistered) { unregisterReceiver(packageReceiver);packagesRegistered=false }
            navbarColors=null
        }
    }
    private fun navbarSpec(mode: Int,mask: Int,r: Int,display: android.graphics.Point): NavbarSpec? {
        val portrait=resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT && display.y>display.x
        val navMode=resources.getIdentifier("config_navBarInteractionMode","integer","android")
        val threeButtons=navMode==0 || runCatching { resources.getInteger(navMode)==0 }.getOrDefault(false)
        if(!NavbarPolicy.eligible(settings.navbarEnabled(),portrait,threeButtons,bars) || bars.bottom>display.y/3) {
            navbarReason="横画面・ナビ非表示・方式対象外・判定不能のため従来表示";return null
        }
        val pkg=foreground
        if(pkg==null || !usageAllowed(this)) { navbarReason="使用状況アクセス/前面アプリを確認してください";return null }
        val fixed=settings.navbarColor(pkg);val automatic=settings.bool("navbar_auto")
        val key="$pkg:$fixed:$automatic"
        if(key!=navbarKey) {
            navbarCandidate=if(fixed==null && !automatic)null else {
                val colors=navbarColors ?: NavbarColors(this).also { navbarColors=it }
                colors.resolve(pkg,automatic,fixed)
            }
            navbarKey=key
        }
        val color=navbarCandidate ?: run { navbarReason="固定色・推定色なし（従来表示）";return null }
        val bottom=mask and 2!=0 && r>0
        val spec=NavbarSpec(display.x,display.y,bars.bottom,r,bottom,mode==Policy.BOTTOM_INSIDE || mode==Policy.BOTH_INSIDE,color)
        val upperExtent=maxOf(bars.top+(if(mask and 1!=0)r else 0),
            (status?.height ?: 0)+(settings.number("y",0)*resources.displayMetrics.density).toInt())
        if(spec.top<=upperExtent) { navbarReason="上側表示領域との重なりを避けて従来表示";return null }
        return spec
    }
    private fun updateForeground() {
        if(!settings.hasRules() || !usageAllowed(this)) {
            foreground=null
            // Only the opt-in prototype resets the cursor on revoke: allow again may emit no
            // new foreground event. Reuse the existing first-query recovery once permitted.
            if(settings.navbarEnabled())lastQuery=0
            return
        }
        val now=System.currentTimeMillis()
        val start=if(lastQuery==0L || lastQuery>now)now-86_400_000
            else if(settings.navbarEnabled())lastQuery-5000 else lastQuery
        val events=(getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start,now)
        val event=UsageEvents.Event()
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType==UsageEvents.Event.MOVE_TO_FOREGROUND)foreground=event.packageName
        }
        lastQuery=now-1
    }
    private fun updateCorners() {
        val mode=foreground?.let { settings.rule(it) } ?: Policy.NORMAL
        val mask=Policy.cornerMask(settings.defaultMask(),mode)
        val offsets=(0..3).map { Policy.cornerOffset(it,mode,bars) }
        if(!settings.navbarEnabled() && !lastBandOwned && mask==lastMask && offsets==lastCornerLayout) { publishRuntime();return }
        val r=(settings.number("radius",18)*resources.displayMetrics.density).toInt()
        val display=android.graphics.Point();wm.defaultDisplay.getRealSize(display)
        var bandOwned=false
        if(settings.navbarEnabled()) {
            val spec=navbarSpec(mode,mask,r,display)
            if(spec!=null && navbarLayer==null)navbarLayer=NavbarLayer(this,wm,
                { w,h->params(w,h,Gravity.TOP or Gravity.LEFT) },{ v,p->add(v,p) },
                { v->windows.remove(v);wm.removeViewImmediate(v) },
                { handler.removeCallbacks(onBarsChanged);handler.post(onBarsChanged) })
            bandOwned=navbarLayer?.apply(spec)==true
        }
        if(mask==lastMask && offsets==lastCornerLayout && bandOwned==lastBandOwned) { publishRuntime();return }
        cornerWindows.forEach { runCatching { wm.removeViewImmediate(it) };windows.remove(it) };cornerWindows.clear()
        if(r>0)for(c in 0..3) {
            if((mask and (if(c<2)1 else 2))==0)continue
            if(c>=2 && bandOwned)continue
            // Absolute screen coordinates avoid BOTTOM/RIGHT gravity using a nav-clipped frame.
            val x=if(c%2==0)offsets[c].x else display.x-r-offsets[c].x
            val y=if(c<2)offsets[c].y else display.y-r-offsets[c].y
            val v=CornerView(this,c)
            // Some policies also shift the parent origin (e.g. left-side nav in reverse landscape).
            // Align the actual screen location after layout, not just the requested parent-relative x/y.
            var previousLocation: Pair<Int,Int>?=null
            v.viewTreeObserver.addOnGlobalLayoutListener {
                if(v.isAttachedToWindow && windows.contains(v)) {
                    val location=IntArray(2);v.getLocationOnScreen(location)
                    val actual=location[0] to location[1]
                    if(actual==(x to y))previousLocation=null
                    else if(actual!=previousLocation) {
                        previousLocation=actual // Do not loop if an OEM clamps a requested position.
                        val p=v.layoutParams as WindowManager.LayoutParams
                        p.x+=x-location[0];p.y+=y-location[1]
                        try { wm.updateViewLayout(v,p) }
                        catch (_: SecurityException) { fail("表示権限を確認してください") }
                        catch (_: WindowManager.BadTokenException) { fail("表示ウィンドウが拒否されました") }
                    }
                }
            }
            add(v,params(r,r,Gravity.TOP or Gravity.LEFT).apply { this.x=x;this.y=y });cornerWindows.add(v)
        }
        lastMask=mask;lastCornerLayout=offsets;lastBandOwned=bandOwned;publishRuntime()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig);navbarColors?.clear();lastQuery=0;rebuild() }
    override fun onDestroy() {
        if(::settings.isInitialized)settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        if(receiverRegistered)unregisterReceiver(receiver)
        if(packagesRegistered)unregisterReceiver(packageReceiver)
        clearWindows();stopForeground(true);super.onDestroy()
    }
    override fun onBind(intent: Intent?) : IBinder?=null
    data class NavbarRuntime(val active: Boolean,val color: Int?,val source: String?,val height: Int,val windowType: Int,val reason: String,val colorLoads: Int,val draws: Int=0)
    data class RuntimeState(val visible: Boolean,val bars: BarState,val foreground: String?,val offsets: List<CornerOffset>,val locations: List<CornerOffset>,val navbar: NavbarRuntime?=null,val windowCount: Int=0,val primaryWindowType: Int=2006)
    companion object {
        @Volatile internal var runtime: RuntimeState?=null
        fun usageAllowed(context: Context): Boolean {
            val ops=context.getSystemService(APP_OPS_SERVICE) as AppOpsManager
            return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
        }
        fun start(context: Context) {
            val intent=Intent(context,OverlayService::class.java)
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent) else context.startService(intent)
        }
    }
}

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED && Settings(context).bool("enabled") && SystemSettings.canDrawOverlays(context)) {
            runCatching { OverlayService.start(context) }
        }
    }
}

