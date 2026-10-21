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
            setOnCheckedChangeListener { _, value -> settings.set(key,value) }
        })
    }
    private fun slider(label: String,key: String,min: Int,max: Int,default: Int) {
        val title=text("$label: ${settings.number(key,default)} dp");content.addView(title)
        content.addView(SeekBar(this).apply {
            this.max=max-min;progress=settings.number(key,default)-min
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar,value: Int,fromUser: Boolean) { title.text="$label: ${value+min} dp" }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) { settings.set(key,bar.progress+min) }
            })
        })
    }
    private fun showSettings() {
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,18,24,24) }
        setContentView(ScrollView(this).apply { addView(content) })
        content.addView(text("Overlay — Navbar技術検証版",26f))
        content.addView(text("Android 6〜10・Xperia向け。時計と電池は右上、背景透明・タッチ透過。"))
        val allowed=SystemSettings.canDrawOverlays(this)
        content.addView(text(if(allowed)"重ねて表示：許可済み" else "最初に「他のアプリの上に重ねて表示」を許可してください。"))
        button("重ねて表示の許可") { startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        button(if(settings.bool("enabled"))"表示を停止" else "表示を開始") {
            if(!SystemSettings.canDrawOverlays(this)) {
                startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            } else {
                val enabled=!settings.bool("enabled");settings.set("enabled",enabled)
                if(enabled)OverlayService.start(this) else stopService(Intent(this,OverlayService::class.java))
            }
            showSettings()
        }
        content.addView(text(settings.prefs.getString("diagnostic","まだ開始していません") ?: ""))
        toggle("時計","clock",true);toggle("電池","battery",true)
        toggle("全画面のときだけ","fullscreen")
        content.addView(text("ステータスバーが隠れたときに時計・電池を表示します。判定不能時は表示を維持します。機種差や一時的なバー表示に合わないアプリは、下の例外設定で調整できます。"))
        slider("文字サイズ","size",10,28,14)
        slider("右端からの距離","x",0,100,4)
        slider("上端からの距離","y",0,100,0)
        button("文字と電池の色") {
            AlertDialog.Builder(this).setTitle("表示色").setSingleChoiceItems(arrayOf("白（標準）","薄い灰色","濃い灰色"),settings.number("color",0)) { dialog,i -> settings.set("color",i);dialog.dismiss() }.show()
        }
        content.addView(text("角丸",20f));toggle("上の角丸","top");toggle("下の角丸","bottom")
        slider("角丸半径","radius",0,64,18)
        content.addView(text("アプリ別設定は使用状況へのアクセスを許可した場合だけ適用します。取得するのは前面アプリ名だけで、履歴は保存・送信しません。未許可時は「通常」の配置になります。上・下の全体ON/OFFは維持します。"))
        content.addView(text(if(OverlayService.usageAllowed(this))"使用状況へのアクセス：許可済み" else "使用状況へのアクセス：未許可"))
        button("使用状況へのアクセスを設定") { startActivity(Intent(SystemSettings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("アプリ別の角丸配置") { chooseApp() }
        button("全画面判定のアプリ別例外") { chooseApp(true) }
        content.addView(text("例外は「全画面のときだけ」がONのときに適用し、使用状況へのアクセスが必要です。通常の自動判定・常時表示はそのまま使えます。"))
        content.addView(text("ナビバー着色（技術検証）",20f))
        toggle("ナビバー着色を試す（初期OFF）","navbar_enabled")
        toggle("テーマ・アイコンから自動色を推定","navbar_auto")
        button("アプリ別の固定色") { chooseNavbarApp() }
        content.addView(text("固定色 → 自動テーマ色 → アイコン代表色の順です。自動色OFFで未設定のアプリは着色しません。使用状況へのアクセスが必要です。縦画面・3ボタンでお試しください。横画面・バー非表示・判定不能時は従来表示へ戻します。ジェスチャー・分割画面・IME・OEM固有バーは保証外です。Navbar Apps等の着色アプリは停止してください。"))
        content.addView(text("自動色は画面の採色ではなく推定で、ボタンの視認性のため明るさを抑えます。固定色は指定通りですが、OSのボタン色は変更できません。明るい色でボタンが見づらい場合は暗い色に変更するか着色をOFFにしてください。"))
        if(settings.navbarEnabled())content.addView(text(OverlayService.runtime?.navbar?.let { "${it.reason} / ${it.source ?: "候補なし"}" } ?: "表示開始後に状態を確認できます"))
        toggle("互換表示方式（バーより背面）","modern")
        content.addView(text("標準方式はAndroid 6〜10向けの旧レイヤーです。表示できない機種は互換方式を試してください。互換方式ではシステムバーの上に角丸を描けません。Android 11以降は保証外です。"))
        content.addView(text("外観合わせ",20f))
        content.addView(text("初期値は参考画面の縦型電池＋右端時計を基準にしています。スクリーンショットの常時取得はせず、白・黒の全画面で位置と大きさを手動調整します。画面密度やOS設定による差は上のスライダーで合わせてください。"))
        button("白背景の全画面で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",false)) }
        button("黒背景の全画面で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",true)) }
        button("電池の最適化設定") { startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        content.addView(text("省電力設定で停止する場合は端末側でOverlayを除外してください。再起動時は、停止前に表示が有効なら復元します。通信・広告・画面収集はありません。"))
    }
    private fun chooseApp(display: Boolean=false) {
        val pm=packageManager
        val apps=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle(if(display)"時計・電池の表示例外" else "アプリ別角丸").setItems(apps.map { it.loadLabel(pm).toString() }.toTypedArray()) { _,i ->
            val pkg=apps[i].activityInfo.packageName
            val labels=if(display)arrayOf("自動判定","時計・電池を表示","時計・電池を非表示") else Policy.cornerLabels.toTypedArray()
            val selection=if(display)settings.displayRule(pkg) else settings.rule(pkg)
            AlertDialog.Builder(this).setTitle(apps[i].loadLabel(pm)).setSingleChoiceItems(labels,selection) { dialog,n ->
                if(display)settings.setDisplayRule(pkg,n) else settings.setRule(pkg,n);dialog.dismiss()
            }.show()
        }.show()
    }
    private fun chooseNavbarApp() {
        val pm=packageManager
        val apps=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("ナビバーの固定色").setItems(apps.map { it.loadLabel(pm).toString() }.toTypedArray()) { _,i ->
            val pkg=apps[i].activityInfo.packageName
            val entry=EditText(this).apply {
                setSingleLine();hint="#RRGGBB（例 #304050）"
                setText(settings.navbarColor(pkg)?.let { "#%06X".format(it and 0xffffff) } ?: "")
            }
            val dialog=AlertDialog.Builder(this).setTitle(apps[i].loadLabel(pm)).setView(entry)
                .setPositiveButton("保存",null).setNeutralButton("固定色を解除") { _,_->settings.setNavbarColor(pkg,null) }.setNegativeButton("戻る",null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value=entry.text.toString().trim()
                    if(!Regex("#[0-9a-fA-F]{6}").matches(value))entry.error="#RRGGBB形式で入力してください"
                    else { settings.setNavbarColor(pkg,android.graphics.Color.parseColor(value));dialog.dismiss() }
                }
            }
            dialog.show()
        }.show()
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

