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
    override fun onCreate(state: Bundle?) { super.onCreate(state);settings=Settings(this) }
    override fun onResume() { super.onResume();if(settings.bool("enabled")) OverlayService.start(this);showSettings() }
    private fun text(value: String,size: Float=14f) = TextView(this).apply { text=value;textSize=size;setPadding(0,12,0,8) }
    private fun button(label: String,action: ()->Unit) { content.addView(Button(this).apply { text=label;setOnClickListener { action() } }) }
    private fun toggle(label: String,key: String,default: Boolean=false) {
        content.addView(Switch(this).apply {
            text=label;isChecked=settings.bool(key,default);setPadding(0,14,0,14)
            setOnCheckedChangeListener { _, value -> if(settings.bool(key,default)!=value)settings.set(key,value) }
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
    private fun choice(title: String,labels: Array<String>,selected: Int,refresh: Boolean=true,save: (Int)->Unit) {
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected) { dialog,n->
            if(n!=selected)save(n)
            dialog.dismiss()
            if(n!=selected && refresh)showSettings()
        }.setNegativeButton("戻る",null).show()
    }
    private fun showSettings() {
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,18,24,24) }
        setContentView(ScrollView(this).apply { addView(content) })
        fun section(label: String) { content.addView(text(label,22f)) }
        section("一般設定")
        button(if(settings.bool("enabled"))"表示を停止" else "表示を開始") {
            if(!SystemSettings.canDrawOverlays(this))
                startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            else {
                val enabled=!settings.bool("enabled");settings.set("enabled",enabled)
                if(enabled)OverlayService.start(this) else stopService(Intent(this,OverlayService::class.java))
            }
            showSettings()
        }
        button(if(SystemSettings.canDrawOverlays(this))"重ねて表示：許可済み" else "重ねて表示：未許可") {
            startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        }
        button("電池の最適化設定") { startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("確認テスト") { startActivity(Intent(this,PreviewActivity::class.java)) }
        button("詳細設定／マニュアル") { showDetails() }
        section("ステータスバー")
        val clockPosition=if(!settings.bool("clock",true))2 else if(settings.clockOnLeft())1 else 0
        button("時計の配置："+arrayOf("右","左","なし")[clockPosition]) {
            choice("時計の配置",arrayOf("右","左","なし"),clockPosition) {
                if(it==2)settings.set("clock",false)
                else { settings.set("clock_left",it==1);settings.set("clock",true) }
            }
        }
        val directions=arrayOf("横型・端子右","横型・端子左","縦型・端子上","なし")
        val batteryPosition=if(settings.bool("battery",true))settings.batteryDirection() else 3
        button("電池の配置："+directions[batteryPosition]) {
            choice("電池の配置",directions,batteryPosition) {
                if(it==3)settings.set("battery",false)
                else { settings.setBatteryDirection(it);settings.set("battery",true) }
            }
        }
        button("時計・電池の色") {
            choice("時計・電池の色",arrayOf("白（標準）","薄い灰色","濃い灰色"),settings.number("color",0),refresh=false) { settings.set("color",it) }
        }
        toggle("全画面のときだけ","fullscreen")
        content.addView(text("ON時はステータスバー非表示で表示。判定不能時は表示を維持します。"))
        if(settings.bool("clock",true)) {
            slider("時計サイズ","size",10,28,14)
            slider("時計の横位置","x",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,4)
            slider("時計の縦位置","y",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
        }
        if(settings.bool("battery",true)) {
            slider("電池サイズ","battery_size",10,40,settings.batterySize(),"sp")
            slider("電池の縦横比","battery_aspect",60,160,100,"%（長辺補正）")
            slider("電池の横位置","battery_x",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
            slider("電池の縦位置","battery_y",Settings.MIN_OFFSET_DP,Settings.MAX_OFFSET_DP,0)
        }
        section("ナビゲーションバー")
        toggle("ナビバー着色","navbar_enabled")
        toggle("上部バーにも同じ色を適用","navbar_unified")
        toggle("Recentsでは着色しない","navbar_recents_off")
        content.addView(text("着色OFF中も下の2設定を保持しますが適用しません。"))
        content.addView(text("Recents停止をONにすると一部端末ではHomeも無着色。判定不能時は着色を維持します。"))
        content.addView(text("固定色優先、それ以外は自動推定。明るい色ではボタンの視認性に注意。"))
        section("ラウンドコーナー")
        toggle("上の角丸","top");toggle("下の角丸","bottom")
        slider("角丸半径","radius",0,64,18)
        content.addView(text("上下OFF中は半径を保持しますが適用しません。"))
        section("アプリ別設定")
        button(if(OverlayService.usageAllowed(this))"使用状況へのアクセス：許可済み" else "使用状況へのアクセス：未許可") {
            startActivity(Intent(SystemSettings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if(!OverlayService.usageAllowed(this))content.addView(text("未許可でも設定は編集できますが、前面アプリへ適用されません。"))
        button("アプリ別設定") { chooseApp() }
    }
    internal fun refreshForTest()=showSettings()
    private fun showDetails() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12) }
        box.addView(text("使い方",18f))
        box.addView(text("「表示を開始」には重ねて表示の許可が必要です。時計と電池は配置で「なし」を選ぶと非表示になり、サイズや位置は保存されます。全画面のときだけを使う場合、アプリ別例外には使用状況へのアクセスが必要です。"))
        box.addView(text("ナビゲーションバーとラウンドコーナー",18f))
        box.addView(text("着色はアプリ別固定色を優先し、未指定ならテーマやアイコンから推定します。「なし」は着色しません。Recents停止は端末によりHomeも対象になります。明るい固定色ではOSのナビボタンが読みにくい場合があります。角丸は全体の上下設定を優先します。"))
        box.addView(text("上下同色と互換方式",18f))
        box.addView(text("上下同色はOverlayが決めた同じ候補色を別々の窓へ渡します。OS本来のバー色を変更する機能ではなく、SystemUIの前後関係や端末制限で上側が見えない場合があります。診断のready／描画回数は画面上の可視性を証明しません。Android 6/7では互換方式の手動切替は無効です。Android 8以降は標準方式が拒否された場合に自動フォールバックします。"))
        box.addView(text("困ったとき",18f))
        box.addView(text("重ねて表示と使用状況アクセス、表示開始状態、対象アプリの固定色／なし、全画面・Recents、画面の向きを確認してください。確認テストは白→黒の順で時計・電池・四隅とタッチ透過を確認します。通信・広告・画面収集はありません。Android 11以降は保証外です。"))
        box.addView(text("現在の診断",18f))
        box.addView(text(diagnosticText()))
        box.addView(Switch(this).apply {
            text="互換表示方式（バーより背面）";isChecked=settings.bool("modern")
            setOnCheckedChangeListener { _,value->settings.set("modern",value) }
        })
        AlertDialog.Builder(this).setTitle("詳細設定／マニュアル")
            .setView(ScrollView(this).apply { addView(box) }).setPositiveButton("閉じる",null).show()
    }
    private fun diagnosticText(): String {
        fun color(value: Int?)=value?.let { "#%08X".format(it) } ?: "不明"
        fun geometry(label: String,g: LayerGeometry?,fallback: String?): String {
            if(g==null)return label+"：窓なし 理由="+(fallback ?: "不明")
            return label+"：色="+color(g.expectedColor)+" type="+(g.windowType ?: "不明")+
                " ready="+g.ready+" draws="+g.draws+"\n  要求=("+g.requestedX+","+g.requestedY+
                ") "+g.requestedWidth+"x"+g.requestedHeight+"\n  実座標=("+g.actualX+","+g.actualY+
                ") "+g.actualWidth+"x"+g.actualHeight+"\n  理由="+g.reason
        }
        val current=OverlayService.runtime
        val r=OverlayService.diagnosticRuntime ?: current ?: return (settings.prefs.getString("diagnostic","まだ開始していません") ?: "まだ開始していません")+
            "\n実行状態がないため、表示を開始して対象アプリへ切り替えてください。"
        val captured=if(OverlayService.diagnosticRuntime!=null && OverlayService.diagnosticCapturedAt>0)
            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(OverlayService.diagnosticCapturedAt)) else "現在"
        return "採取対象="+(r.foreground ?: "不明")+" 採取時刻="+captured+
            "\n現在前面="+(current?.foreground ?: "不明")+"\n重ねて表示="+r.overlayAllowed+" 使用状況="+r.usageAllowed+
            " Recents停止="+r.recentsTintSuppressed+"\nバー: known="+r.bars.known+" top="+r.bars.top+
            " bottom="+r.bars.bottom+" left="+r.bars.left+" right="+r.bars.right+
            "\n色ソース="+(r.navbar?.source ?: "なし")+" 上期待="+color(r.statusbar?.color)+
            " 下期待="+color(r.navbar?.color)+"\n"+geometry("上",r.statusbarGeometry,r.statusbar?.reason)+"\n"+
            geometry("下",r.navbarGeometry,r.navbar?.reason)+
            "\nready／drawsは実画面上の可視性を証明しません。SystemUI遮蔽はZ3TC実機で確認してください。"
    }
    internal fun diagnosticTextForTest()=diagnosticText()
    internal fun configuredPackages()=settings.configuredPackages()
    internal fun appEntries(): List<Pair<String,String>> {
        val pm=packageManager
        val packages=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map { it.activityInfo.packageName }.toMutableSet()
        // Home replacements (including Xperia Home) may not expose a LAUNCHER activity.
        packages.addAll(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)
            .map { it.activityInfo.packageName })
        // Some Xperia builds do not return the stock home through the above query.
        for(pkg in listOf("com.sonyericsson.home","com.sonymobile.home")) {
            if(runCatching { pm.getApplicationInfo(pkg,0) }.isSuccess)packages.add(pkg)
        }
        packages.addAll(configuredPackages().filter { runCatching { pm.getApplicationInfo(it,0) }.isSuccess })
        val labels=packages.associateWith { pkg->runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg) }
        val configured=configuredPackages()
        val sorted=packages.sortedWith(compareBy<String> { if(it in configured)0 else 1 }.thenBy { labels[it] }.thenBy { it })
        return sorted.map { it to (labels[it] ?: it) }
    }
    private fun chooseApp() {
        val entries=appEntries();val configured=configuredPackages()
        AlertDialog.Builder(this).setTitle("アプリ別設定").setItems(
            entries.map { (if(it.first in configured)"設定済み： " else "")+it.second }.toTypedArray()
        ) { _,i->showAppSettings(entries[i].first,entries[i].second) }.setNegativeButton("戻る",null).show()
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
            if(!settings.bool("fullscreen"))"（全画面モードOFFで未適用）" else "") {
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
    private var stage=0
    private lateinit var preview: TextView
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
        stage=state?.getInt("previewStage",0) ?: 0
        applyBars(intent.getStringExtra("bars") ?: "immersive")
        preview=TextView(this).apply {
            gravity=android.view.Gravity.CENTER;textSize=18f
            setOnClickListener { if(stage==0) { stage=1;renderPreview() } else finish() }
        }
        setContentView(preview);renderPreview()
    }
    override fun onSaveInstanceState(state: Bundle) { state.putInt("previewStage",stage);super.onSaveInstanceState(state) }
    private fun renderPreview() {
        val dark=stage==1
        preview.setBackgroundColor(if(dark)android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        preview.setTextColor(if(dark)android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        preview.text=if(dark)"黒背景を確認\n\nタップして設定へ戻る" else "白背景を確認\n\nタップして黒背景へ\n（時計・電池・四隅・タッチ透過を確認）"
    }
}



