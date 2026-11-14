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
    private class LegacyWindowRejected(cause: Throwable): RuntimeException(cause)
    private lateinit var settings: Settings
    private lateinit var wm: WindowManager
    private val handler=Handler(Looper.getMainLooper())
    private val windows=mutableListOf<View>()
    private val statusViews=mutableListOf<StatusView>()
    private var lastStatusElements=-1
    private var batteryPositionUpdater: (() -> Unit)?=null
    private var observation: BarObservation?=null
    private var bars=BarState()
    private var lastCornerLayout: List<CornerOffset>?=null
    private var updatingBars=false
    private var screenOn=true
    private var battery=-1
    private var charging=false
    private var foreground: String?=null
    private var foregroundTarget: ForegroundTarget?=null
    private var lastQuery=0L
    private var lastMask=-1
    private var receiverRegistered=false
    private var activeType=2006
    private var legacyTypeRejected=false
    private val cornerWindows=mutableListOf<View>()
    private var navbarLayer: NavbarLayer?=null
    private var statusbarLayer: StatusbarLayer?=null
    private var navbarColors: NavbarColors?=null
    private var recentsDetector: RecentsDetector?=null
    private var navbarKey: String?=null
    private var navbarCandidate: NavbarColor?=null
    private var navbarReason="未適用"
    private var statusbarReason="未適用"
    private var recentsTintSuppressed=false
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
            statusViews.forEach { it.requestLayout();it.invalidate() }
            batteryPositionUpdater?.invoke()
            handler.postDelayed(this,Policy.nextMinute(System.currentTimeMillis()))
        }
    }
    private val poll=object: Runnable {
        override fun run() {
            if (!screenOn) return
            if (!SystemSettings.canDrawOverlays(this@OverlayService)) { fail("重ねて表示の権限が取り消されました");return }
            try { updateForeground();refreshBars();updateStatusVisibility();updateCorners() }
            catch (_: LegacyWindowRejected) { retryModernAfterLegacyRejection();return }
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
                    publishRuntime()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn=true;lastQuery=0;rebuild()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    battery=Policy.batteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),intent.getIntExtra(BatteryManager.EXTRA_SCALE,0))
                    charging=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0
                    statusViews.forEach { it.percent=battery;it.charging=this@OverlayService.charging;if(screenOn)it.invalidate() }
                }
                else -> { statusViews.forEach { it.requestLayout();if(screenOn)it.invalidate() };handler.removeCallbacks(minute);if(screenOn)handler.post(minute) }
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
        statusbarLayer?.clear();statusbarLayer=null;recentsDetector=null;recentsTintSuppressed=false
        observation=null;bars=BarState();lastCornerLayout=null
        windows.toList().forEach { runCatching { wm.removeViewImmediate(it) } }
        windows.clear();cornerWindows.clear();statusViews.clear();batteryPositionUpdater=null;lastStatusElements=-1;lastMask=-1;runtime=null
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
    private fun add(view: View,p: WindowManager.LayoutParams) {
        try { wm.addView(view,p) }
        catch (error: SecurityException) {
            if(Policy.shouldRetryModernWindow(Build.VERSION.SDK_INT,activeType,p.type,
                    SystemSettings.canDrawOverlays(this),legacyTypeRejected))
                throw LegacyWindowRejected(error)
            throw error
        }
        catch (error: WindowManager.BadTokenException) {
            if(Policy.shouldRetryModernWindow(Build.VERSION.SDK_INT,activeType,p.type,
                    SystemSettings.canDrawOverlays(this),legacyTypeRejected))
                throw LegacyWindowRejected(error)
            throw error
        }
        windows.add(view)
    }
    private fun globalStatusElements()=
        (if(settings.bool("clock",true))Policy.STATUS_CLOCK else 0) or
            (if(settings.bool("battery",true))Policy.STATUS_BATTERY else 0)
    private fun syncStatusWindows(elements: Int) {
        if(elements==lastStatusElements)return
        batteryPositionUpdater=null
        statusViews.toList().forEach { view->
            statusViews.remove(view)
            if(windows.remove(view))wm.removeViewImmediate(view)
        }
        if(elements==0 || !screenOn) { lastStatusElements=elements;return }
        val density=resources.displayMetrics.density
        fun attachStatus(mask: Int,gravity: Int,batteryOnly: Boolean=false) {
            val view=StatusView(this,settings,mask).apply {
                percent=battery;charging=this@OverlayService.charging
            }
            val p=params(-2,-2,Gravity.TOP or gravity).apply {
                x=(settings.offset("x",4)*density).toInt();y=(settings.offset("y",0)*density).toInt()
            }
            if(batteryOnly) {
                fun batteryX(): Int {
                    var anchor=0f
                    if(!settings.clockOnLeft() && elements and Policy.STATUS_CLOCK!=0) {
                        // Re-evaluate on the existing minute/time callbacks, including 9:59→10:00.
                        val metrics=resources.displayMetrics
                        anchor=StatusView.batteryAnchor(
                            android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date()),
                            settings.number("battery_anchor_size",14)*metrics.scaledDensity,density)
                    }
                    return ((settings.number("battery_base_x",4)+settings.offset("battery_x",0))*density+anchor).toInt()
                }
                p.x=batteryX()
                p.y=((settings.number("battery_base_y",0)+settings.offset("battery_y",0))*density).toInt()
                batteryPositionUpdater={
                    val target=batteryX()
                    if(windows.contains(view) && target!=p.x) {
                        p.x=target
                        try { wm.updateViewLayout(view,p) }
                        catch (_: SecurityException) { fail("表示権限を確認してください") }
                        catch (_: WindowManager.BadTokenException) { fail("表示ウィンドウが拒否されました") }
                    }
                }
            }
            add(view,p);statusViews.add(view)
            view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_->
                if(statusViews.contains(view))publishRuntime()
            }
        }
        if(settings.clockOnLeft() || settings.independentBattery()) {
            if(elements and Policy.STATUS_CLOCK!=0)attachStatus(Policy.STATUS_CLOCK,if(settings.clockOnLeft())Gravity.LEFT else Gravity.RIGHT)
            if(elements and Policy.STATUS_BATTERY!=0)attachStatus(Policy.STATUS_BATTERY,Gravity.RIGHT,true)
        } else {
            attachStatus(elements,Gravity.RIGHT)
        }
        lastStatusElements=elements
    }
    private fun rebuild() {
        if(!::settings.isInitialized) return
        syncNavbarPackages()
        clearWindows()
        if(!settings.bool("enabled")) { stopSelf();return }
        if(!SystemSettings.canDrawOverlays(this)) { fail("重ねて表示を許可してください");return }
        if(!screenOn)return
        val types=Policy.windowTypes(Build.VERSION.SDK_INT,settings.bool("modern"),legacyTypeRejected)
        for(type in types) {
            activeType=type
            try {
                if(settings.unifiedBars()) {
                    val statusType=if(Build.VERSION.SDK_INT<26)2002 else activeType
                    StatusbarLayer(this,wm,{ w,h->params(w,h,Gravity.TOP or Gravity.LEFT,statusType) },
                        { v,p->add(v,p) },{ v->windows.remove(v);wm.removeViewImmediate(v) },
                        { handler.removeCallbacks(onBarsChanged);handler.post(onBarsChanged) }).also {
                        statusbarLayer=it;it.attach()
                    }
                }
                if(settings.needsBars())attachBarObservation()
                updateForeground();updateStatusVisibility();updateCorners()
                settings.prefs.edit().putString("diagnostic",if(type==2006)"旧方式 2006（バーとの重なりは機種依存）" else "互換方式 2038（システムバーより背面）").apply()
                handler.post(minute)
                if(settings.needsBars() || (settings.hasRules() && usageAllowed(this)))handler.post(poll)
                return
            } catch (_: LegacyWindowRejected) {
                legacyTypeRejected=true
                clearWindows()
            }
              catch (_: SecurityException) {
                if(Build.VERSION.SDK_INT>=26 && type==2006)legacyTypeRejected=true
                clearWindows()
            }
              catch (_: WindowManager.BadTokenException) {
                  if(Build.VERSION.SDK_INT>=26 && type==2006)legacyTypeRejected=true
                  clearWindows()
              }
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
        catch (_: LegacyWindowRejected) { retryModernAfterLegacyRejection() }
        catch (_: SecurityException) {
            fail("表示権限を確認してください")
        }
        catch (_: WindowManager.BadTokenException) {
            fail("表示ウィンドウが拒否されました")
        }
    }
    private fun retryModernAfterLegacyRejection(): Boolean {
        if(!Policy.shouldRetryModernWindow(Build.VERSION.SDK_INT,activeType,2006,
                true,legacyTypeRejected))return false
        legacyTypeRejected=true
        handler.removeCallbacks(minute);handler.removeCallbacks(poll);handler.removeCallbacks(onBarsChanged)
        handler.post { if(screenOn && settings.bool("enabled"))rebuild() }
        return true
    }
    private fun refreshBars() {
        if(updatingBars)return
        updatingBars=true
        try { bars=observation?.read() ?: BarState() } finally { updatingBars=false }
    }
    private fun updateStatusVisibility() {
        val exception=foreground?.let { settings.displayRule(it) } ?: Policy.DISPLAY_AUTO
        val elements=Policy.statusElements(settings.fullscreenOnly(),exception,bars,globalStatusElements())
        syncStatusWindows(elements)
        statusViews.forEach { it.visibility=View.VISIBLE }
        publishRuntime()
    }
    private fun publishRuntime() {
        val state=RuntimeState(screenOn && statusViews.any { it.visibility==View.VISIBLE },bars,foreground,foregroundTarget?.className,lastCornerLayout ?: emptyList(),cornerWindows.map {
            val p=IntArray(2);it.getLocationOnScreen(p);CornerOffset(p[0],p[1])
        },if(settings.navbarEnabled())NavbarRuntime(screenOn && navbarLayer?.ready==true,navbarCandidate?.color,navbarCandidate?.source,
            navbarLayer?.view?.height ?: 0,activeType,navbarLayer?.reason ?: navbarReason,navbarColors?.loads ?: 0,navbarLayer?.view?.draws ?: 0) else null,
            if(settings.navbarEnabled())StatusbarRuntime(screenOn && statusbarLayer?.ready==true,
                if(settings.unifiedBars())navbarCandidate?.color else null,statusbarLayer?.view?.height ?: 0,
                statusbarLayer?.reason ?: statusbarReason,statusbarLayer?.view?.draws ?: 0) else null,
            recentsTintSuppressed,windows.size,activeType,statusViews.map {
                    val p=IntArray(2);it.getLocationOnScreen(p);CornerOffset(p[0],p[1])
                },lastStatusElements.coerceAtLeast(0),navbarLayer?.geometry(),statusbarLayer?.geometry(),
                SystemSettings.canDrawOverlays(this),usageAllowed(this))
        runtime=state
        if(state.foreground!=null && state.foreground!=packageName) {
            diagnosticRuntime=state;diagnosticCapturedAt=System.currentTimeMillis()
        }
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
    private data class TintResolution(val color: NavbarColor?,val suppressed: Boolean)
    private fun tintResolution(): TintResolution {
        recentsTintSuppressed=false
        val pkg=foreground
        if(pkg==null || !usageAllowed(this)) {
            navbarReason="使用状況アクセス/前面アプリを確認してください";navbarCandidate=null;navbarKey=null
            return TintResolution(null,false)
        }
        // Explicit per-app NONE wins before the temporary Recents policy. It must remove the
        // Navbar band instead of replacing it with the transparent Recents corner owner.
        val appMode=settings.navbarMode(pkg)
        if(appMode==Settings.NAVBAR_NONE) {
            navbarReason="アプリ別設定「なし」（OS・アプリ本来のバー表示）";navbarCandidate=null;navbarKey="none:$pkg"
            return TintResolution(null,false)
        }
        if(settings.suppressRecentsTint()) {
            val detector=recentsDetector ?: RecentsDetector(this).also { recentsDetector=it }
            if(detector.shouldSuppress(true,foregroundTarget)) {
                recentsTintSuppressed=true;navbarReason="Recents判定により上下着色を停止";navbarCandidate=null;navbarKey=null
                return TintResolution(null,true)
            }
        }
        val fixed=settings.navbarColor(pkg);val automatic=true
        val key="$pkg:$appMode:$fixed:$automatic"
        if(key!=navbarKey) {
            navbarCandidate=if(fixed==null && !automatic)null else {
                val colors=navbarColors ?: NavbarColors(this).also { navbarColors=it }
                colors.resolve(pkg,automatic,fixed) // Preserve main's Navbar candidate for both bars.
            }
            navbarKey=key
        }
        if(navbarCandidate==null)navbarReason="固定色・推定色なし（従来表示）"
        return TintResolution(navbarCandidate,false)
    }
    private fun navbarSpec(mode: Int,mask: Int,r: Int,display: android.graphics.Point,tint: TintResolution): NavbarSpec? {
        val portrait=resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT && display.y>display.x
        val navMode=resources.getIdentifier("config_navBarInteractionMode","integer","android")
        val threeButtons=navMode==0 || runCatching { resources.getInteger(navMode)==0 }.getOrDefault(false)
        if(!NavbarPolicy.eligible(settings.navbarEnabled(),portrait,threeButtons,bars) || bars.bottom>display.y/3) {
            navbarReason="横画面・ナビ非表示・方式対象外・判定不能のため従来表示";return null
        }
        val color=tint.color ?: if(tint.suppressed)NavbarColor(android.graphics.Color.TRANSPARENT,"Recents停止") else return null
        val bottom=mask and 2!=0 && r>0
        val spec=NavbarSpec(display.x,display.y,bars.bottom,r,bottom,mode==Policy.BOTTOM_INSIDE || mode==Policy.BOTH_INSIDE,color,!tint.suppressed)
        val upperExtent=maxOf(bars.top+(if(mask and 1!=0)r else 0),
            (statusViews.maxOfOrNull { it.height } ?: 0)+(settings.offset("y",0)*resources.displayMetrics.density).toInt())
        if(spec.top<=upperExtent) { navbarReason="上側表示領域との重なりを避けて従来表示";return null }
        return spec
    }
    private fun statusbarSpec(display: android.graphics.Point,tint: TintResolution,mode: Int,mask: Int,r: Int): StatusbarSpec? {
        if(!settings.navbarEnabled()) { statusbarReason="ナビバー着色OFF";return null }
        if(!settings.unifiedBars()) { statusbarReason="上下同色OFF";return null }
        if(tint.suppressed) { statusbarReason="Recents判定で停止";return null }
        val color=tint.color ?: run { statusbarReason="候補色なし："+navbarReason;return null }
        val portrait=resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT && display.y>display.x
        if(!portrait) { statusbarReason="縦画面以外";return null }
        if(!bars.known) { statusbarReason="システムバー状態不明";return null }
        if(bars.top<=0) { statusbarReason="ステータスバー非表示";return null }
        if(bars.top>display.y/3) { statusbarReason="上部バー高が異常";return null }
        if(bars.left>0 || bars.right>0) { statusbarReason="側面システムバーのため対象外";return null }
        val topAtPhysicalEdge=mode!=Policy.TOP_INSIDE && mode!=Policy.BOTH_INSIDE
        statusbarReason="spec成立・配置待ち"
        return StatusbarSpec(display.x,bars.top,color,r,mask and 1!=0 && topAtPhysicalEdge)
    }
    private fun updateForeground() {
        if(!settings.hasRules() || !usageAllowed(this)) {
            foreground=null;foregroundTarget=null
            // Permission may be restored without a new foreground event. Force a bounded
            // first-query recovery so per-app display rules resume as well as Navbar rules.
            lastQuery=0
            return
        }
        val now=System.currentTimeMillis()
        val start=if(lastQuery==0L || lastQuery>now)now-86_400_000
            else if(settings.navbarEnabled())lastQuery-5000 else lastQuery
        val events=(getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start,now)
        val event=UsageEvents.Event()
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType==UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foreground=event.packageName
                foregroundTarget=ForegroundTarget(event.packageName,event.className,event.timeStamp)
            }
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
        val tint=if(settings.navbarEnabled())tintResolution() else TintResolution(null,false)
        val upperSpec=statusbarSpec(display,tint,mode,mask,r)
        statusbarLayer?.apply(upperSpec,statusbarReason)
        var bandOwned=false
        if(settings.navbarEnabled()) {
            val spec=navbarSpec(mode,mask,r,display,tint)
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
    data class StatusbarRuntime(val active: Boolean,val color: Int?,val height: Int,val reason: String,val draws: Int=0)
    data class RuntimeState(val visible: Boolean,val bars: BarState,val foreground: String?,val foregroundClass: String?,val offsets: List<CornerOffset>,val locations: List<CornerOffset>,val navbar: NavbarRuntime?=null,val statusbar: StatusbarRuntime?=null,val recentsTintSuppressed: Boolean=false,val windowCount: Int=0,val primaryWindowType: Int=2006,val statusLocations: List<CornerOffset> = emptyList(),val statusElements: Int=0,val navbarGeometry: LayerGeometry?=null,val statusbarGeometry: LayerGeometry?=null,val overlayAllowed: Boolean=false,val usageAllowed: Boolean=false)
    companion object {
        @Volatile internal var runtime: RuntimeState?=null
        @Volatile internal var diagnosticRuntime: RuntimeState?=null
        @Volatile internal var diagnosticCapturedAt: Long=0
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



