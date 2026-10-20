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
    private var probe: View?=null
    private var screenOn=true
    private var battery=-1
    private var charging=false
    private var foreground: String?=null
    private var lastQuery=0L
    private var lastMask=-1
    private var receiverRegistered=false
    private var activeType=2006
    private val cornerWindows=mutableListOf<View>()
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
            try { updateForeground();updateCorners() }
            catch (_: SecurityException) { fail("表示権限または使用状況の許可を確認してください");return }
            catch (_: WindowManager.BadTokenException) { fail("表示ウィンドウが拒否されました");return }
            handler.postDelayed(this,1000)
        }
    }
    private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "diagnostic") rebuild()
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
        handler.removeCallbacks(minute);handler.removeCallbacks(poll)
        windows.toList().forEach { runCatching { wm.removeViewImmediate(it) } }
        windows.clear();cornerWindows.clear();status=null;probe=null;lastMask=-1
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
                if(settings.bool("fullscreen")) attachProbe()
                updateStatusVisibility()
                updateForeground();updateCorners()
                settings.prefs.edit().putString("diagnostic",if(type==2006)"旧方式 2006（バーとの重なりは機種依存）" else "互換方式 2038（システムバーより背面）").apply()
                handler.post(minute)
                if(settings.hasRules() && usageAllowed(this))handler.post(poll)
                return
            } catch (_: SecurityException) { clearWindows() }
              catch (_: WindowManager.BadTokenException) { clearWindows() }
        }
        fail("表示ウィンドウを作れません。権限・端末の制限を確認してください")
    }
    private fun attachProbe() {
        val v=View(this)
        // A normally inset, invisible 1px window tracks whether the status bar reserves space.
        val p=params(1,1,Gravity.TOP or Gravity.LEFT,if(Build.VERSION.SDK_INT>=26)2038 else 2002)
        p.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        add(v,p);probe=v
        v.viewTreeObserver.addOnGlobalLayoutListener { updateStatusVisibility() }
    }
    private fun updateStatusVisibility() {
        val visible=if(!settings.bool("fullscreen")) true else probe?.let {
            val xy=IntArray(2);it.getLocationOnScreen(xy);xy[1]==0
        } ?: true
        status?.visibility=if(visible && (settings.bool("clock",true)||settings.bool("battery",true)))View.VISIBLE else View.INVISIBLE
    }
    private fun updateForeground() {
        if(!settings.hasRules() || !usageAllowed(this)) { foreground=null;return }
        val now=System.currentTimeMillis()
        val start=if(lastQuery==0L || lastQuery>now)now-86_400_000 else lastQuery
        val events=(getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start,now)
        val event=UsageEvents.Event()
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType==UsageEvents.Event.MOVE_TO_FOREGROUND)foreground=event.packageName
        }
        lastQuery=now-1
    }
    private fun updateCorners() {
        val mask=Policy.corners(settings.defaultMask(),foreground?.let { settings.rule(it) })
        if(mask==lastMask)return
        cornerWindows.forEach { runCatching { wm.removeViewImmediate(it) };windows.remove(it) };cornerWindows.clear()
        val r=(settings.number("radius",18)*resources.displayMetrics.density).toInt()
        if(r>0)for(c in 0..3) {
            if((mask and (if(c<2)1 else 2))==0)continue
            val gravity=(if(c<2)Gravity.TOP else Gravity.BOTTOM) or (if(c%2==0)Gravity.LEFT else Gravity.RIGHT)
            val v=CornerView(this,c);add(v,params(r,r,gravity));cornerWindows.add(v)
        }
        lastMask=mask
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig);lastQuery=0;rebuild() }
    override fun onDestroy() {
        if(::settings.isInitialized)settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        if(receiverRegistered)unregisterReceiver(receiver)
        clearWindows();stopForeground(true);super.onDestroy()
    }
    override fun onBind(intent: Intent?) : IBinder?=null
    companion object {
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
