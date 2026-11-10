package com.xiaqui.overlay

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings as SystemSettings
import android.view.View
import android.view.WindowManager
import android.widget.*

class MainActivity: Activity() {
    private lateinit var settings: Settings
    private lateinit var content: LinearLayout
    private var scroll: ScrollView?=null
    private var savedScroll=0
    override fun onCreate(state: Bundle?) { super.onCreate(state);settings=Settings(this);savedScroll=state?.getInt("settingsScroll",0) ?: 0 }
    override fun onSaveInstanceState(state: Bundle) { state.putInt("settingsScroll",scroll?.scrollY ?: savedScroll);super.onSaveInstanceState(state) }
    override fun onResume() { super.onResume();if(settings.bool("enabled")) OverlayService.start(this);showSettings() }
    private fun text(value: String,size: Float=14f) = TextView(this).apply { text=value;textSize=size;setPadding(0,12,0,8) }
    private fun button(label: String,action: ()->Unit) { content.addView(Button(this).apply { text=label;setOnClickListener { action() } }) }
    private fun toggle(label: String,key: String,default: Boolean=false) {
        content.addView(Switch(this).apply {
            text=label;isChecked=settings.bool(key,default);setPadding(0,14,0,14)
            setOnCheckedChangeListener { _, value -> settings.set(key,value);showSettings() }
        })
    }
    private fun slider(label: String,key: String,min: Int,max: Int,default: Int,unit: String=if(key=="size")"sp" else "dp") {
        val current=settings.number(key,default).coerceIn(min,max)
        val title=text("$label: $current $unit");content.addView(title)
        content.addView(SeekBar(this).apply {
            this.max=max-min;progress=current-min
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar,value: Int,fromUser: Boolean) { title.text="$label: ${value+min} $unit" }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) { settings.set(key,bar.progress+min) }
            })
        })
    }
    private fun choice(title: String,labels: Array<String>,selected: Int,save: (Int)->Unit) {
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected) { dialog,n->
            save(n);dialog.dismiss();showSettings()
        }.setNegativeButton("戻る",null).show()
    }
    private fun showSettings() {
        val y=scroll?.scrollY ?: savedScroll
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,18,24,24) }
        val next=ScrollView(this).apply { addView(content) };scroll=next;setContentView(next)
        fun section(label: String) { content.addView(text(label,22f)) }
        section("全体設定")
        button(if(settings.bool("enabled"))"表示を停止" else "表示を開始") {
            if(!SystemSettings.canDrawOverlays(this))
                startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            else {
                val enabled=!settings.bool("enabled");settings.set("enabled",enabled)
                if(enabled)OverlayService.start(this) else stopService(Intent(this,OverlayService::class.java))
            }
            showSettings()
        }
        content.addView(text(if(SystemSettings.canDrawOverlays(this))"重ねて表示：許可済み" else "重ねて表示：未許可"))
        button("重ねて表示の許可") { startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        button("電池の最適化設定") { startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("白背景で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",false)) }
        button("黒背景で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",true)) }
        button("詳細／トラブル対応") { showDetails() }
        section("上部情報")
        toggle("時計","clock",true);toggle("電池","battery",true)
        button("文字・電池の色") {
            choice("文字・電池の色",arrayOf("白（標準）","薄い灰色","濃い灰色"),settings.number("color",0)) { settings.set("color",it) }
        }
        toggle("全画面のときだけ","fullscreen")
        if(settings.bool("clock",true)) {
            button("時計の左右配置：" + if(settings.clockOnLeft())"左" else "右") {
                choice("時計の左右配置",arrayOf("右","左"),if(settings.clockOnLeft())1 else 0) { settings.set("clock_left",it==1) }
            }
            slider("時計サイズ","size",10,28,14)
            slider("時計の横位置","x",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,4)
            slider("時計の縦位置","y",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
        }
        if(settings.bool("battery",true)) {
            val directions=arrayOf("横型・端子右","横型・端子左","縦型・端子上")
            button("電池の向き："+directions[settings.batteryDirection()]) {
                choice("電池の向き",directions,settings.batteryDirection()) { settings.setBatteryDirection(it) }
            }
            slider("電池サイズ","battery_size",10,40,settings.batterySize(),"sp")
            slider("電池の縦横比","battery_aspect",60,160,100,"%（長辺補正）")
            slider("電池の横位置","battery_x",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
            slider("電池の縦位置","battery_y",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
        }
        section("角丸")
        toggle("上の角丸","top");toggle("下の角丸","bottom")
        if(settings.defaultMask()!=0)slider("角丸半径","radius",0,64,18)
        section("ナビバー")
        toggle("ナビバー着色","navbar_enabled")
        if(settings.navbarEnabled()) {
            toggle("上部バーにも同じ色を適用","navbar_unified")
            toggle("Recentsでは着色しない","navbar_recents_off")
            content.addView(text("固定色優先、それ以外は自動推定。明るい色ではボタンの視認性に注意。"))
        }
        section("アプリ別設定")
        content.addView(text(if(OverlayService.usageAllowed(this))"使用状況へのアクセス：許可済み" else "使用状況へのアクセス：未許可（保存・編集可／適用不可）"))
        button("使用状況へのアクセスを設定") { startActivity(Intent(SystemSettings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("アプリ別設定") { chooseApp() }
        next.post { if(scroll===next)next.scrollTo(0,y) }
    }
    private fun showDetails() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12) }
        box.addView(text(settings.prefs.getString("diagnostic","まだ開始していません") ?: ""))
        box.addView(Switch(this).apply {
            text="互換表示方式（バーより背面）";isChecked=settings.bool("modern")
            setOnCheckedChangeListener { _,value->settings.set("modern",value) }
        })
        box.addView(text("Android 6/7では方式切替は無効。Android 8以降は標準方式の拒否時に自動フォールバックします。互換方式はシステムバーより背面です。\n位置は負数も指定できます。OS文字倍率を反映します。\n全画面例外は全画面モードと使用状況アクセスが必要で、全体OFFを上書きしません。\n自動色は画面の採色ではなく推定。上下同色は同一候補色を適用する機能で、OS本来の色を取得しません。バー非表示・方向・端末制限・候補不在では同色を保証しません。\n通信・広告・画面収集なし。Android 11以降は保証外。"))
        AlertDialog.Builder(this).setTitle("詳細／トラブル対応")
            .setView(ScrollView(this).apply { addView(box) }).setPositiveButton("閉じる",null).show()
    }
    internal fun configuredPackages()=settings.configuredPackages()
    private fun chooseApp() {
        val pm=packageManager
        val packages=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map { it.activityInfo.packageName }.toMutableSet()
        packages.addAll(configuredPackages().filter { runCatching { pm.getApplicationInfo(it,0) }.isSuccess })
        val labels=packages.associateWith { pkg->runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg) }
        val configured=configuredPackages()
        val sorted=packages.sortedWith(compareBy<String> { if(it in configured)0 else 1 }.thenBy { labels[it] }.thenBy { it })
        AlertDialog.Builder(this).setTitle("アプリ別設定").setItems(
            sorted.map { (if(it in configured)"設定済み： " else "")+labels[it] }.toTypedArray()
        ) { _,i->showAppSettings(sorted[i],labels[sorted[i]] ?: sorted[i]) }.setNegativeButton("戻る",null).show()
    }
    private val displayLabels=arrayOf("自動判定","時計・電池を表示","時計・電池を非表示","時計のみ表示","電池のみ表示")
    private fun showAppSettings(pkg: String,label: String) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12) }
        fun row(labelText: String,action: ()->Unit) {
            box.addView(Button(this).apply { text=labelText;setOnClickListener { action() } })
        }
        val allowed=OverlayService.usageAllowed(this)
        if(!allowed)box.addView(text("使用状況アクセス未許可：保存・編集できますが適用されません。"))
        if(!allowed)row("使用状況へのアクセスを設定") { startActivity(Intent(SystemSettings.ACTION_USAGE_ACCESS_SETTINGS)) }
        val dialog=AlertDialog.Builder(this).setTitle(label).setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("戻る") { _,_->chooseApp() }.create()
        fun edit(title: String,labels: Array<String>,selected: Int,save: (Int)->Unit) {
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected) { child,n->
                save(n);child.dismiss();dialog.dismiss();showAppSettings(pkg,label)
            }.setNegativeButton("戻る",null).show()
        }
        row("角丸："+Policy.cornerLabels[settings.rule(pkg)]+if(settings.defaultMask()==0)"（上下OFFで未適用）" else "") {
            edit("角丸",Policy.cornerLabels.toTypedArray(),settings.rule(pkg)) { settings.setRule(pkg,it) }
        }
        row("時計・電池の表示："+displayLabels[settings.displayRule(pkg)]+
            if(!settings.bool("fullscreen"))"（全画面モードOFFで未適用）" else "（全体OFFは優先）") {
            edit("時計・電池の表示",displayLabels,settings.displayRule(pkg)) { settings.setDisplayRule(pkg,it) }
        }
        val colorLabels=arrayOf("自動","固定色","なし")
        row("ナビバー着色："+colorLabels[settings.navbarMode(pkg)]+
            (settings.navbarColor(pkg)?.let { " #%06X".format(it and 0xffffff) } ?: "")+
            if(!settings.navbarEnabled())"（着色OFFで未適用）" else "") {
            AlertDialog.Builder(this).setTitle("ナビバー着色").setSingleChoiceItems(colorLabels,settings.navbarMode(pkg)) { child,n->
                child.dismiss()
                if(n==Settings.NAVBAR_FIXED)showNavbarColor(label,pkg) {
                    dialog.dismiss();showAppSettings(pkg,label)
                } else {
                    if(n==Settings.NAVBAR_NONE)settings.setNavbarNone(pkg) else settings.setNavbarColor(pkg,null)
                    dialog.dismiss();showAppSettings(pkg,label)
                }
            }.setNegativeButton("戻る",null).show()
        }
        dialog.show()
    }
    private fun showNavbarColor(title: CharSequence,pkg: String,onSaved: ()->Unit) {
        val entry=EditText(this).apply {
            setSingleLine();hint="#RRGGBB（例 #304050）"
            setText(settings.navbarColor(pkg)?.let { "#%06X".format(it and 0xffffff) } ?: "")
        }
        val dialog=AlertDialog.Builder(this).setTitle("$title：固定色").setView(entry)
            .setPositiveButton("保存",null).setNegativeButton("戻る",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=entry.text.toString().trim()
                if(!Regex("#[0-9a-fA-F]{6}").matches(value))entry.error="#RRGGBB形式で入力してください"
                else { settings.setNavbarColor(pkg,android.graphics.Color.parseColor(value));dialog.dismiss();onSaved() }
            }
        }
        dialog.show()
    }
}

@Suppress("DEPRECATION")
class PreviewActivity: Activity() {
    fun applyBars(mode: String) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor=android.graphics.Color.rgb(20,150,80)
        window.navigationBarColor=android.graphics.Color.rgb(30,90,180)
        window.decorView.systemUiVisibility=when(mode) {
            "normal"->0
            "transparent"->{ window.statusBarColor=android.graphics.Color.TRANSPARENT;View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE }
            "status-hidden"->{window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);View.SYSTEM_UI_FLAG_FULLSCREEN}
            else->{window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY}
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        applyBars(intent.getStringExtra("bars") ?: "immersive")
        val dark=intent.getBooleanExtra("dark",false)
        setContentView(TextView(this).apply {
            setBackgroundColor(if(dark)android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            setTextColor(if(dark)android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            text="右上の時計・電池、四隅を確認\n\nこの画面をタップして戻る\n（タッチ透過の確認）";gravity=android.view.Gravity.CENTER;textSize=18f
            setOnClickListener { finish() }
        })
    }
}



