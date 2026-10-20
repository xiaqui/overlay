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
        content.addView(text("Overlay",26f))
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
        toggle("全画面のときだけ（試験機能）","fullscreen")
        content.addView(text("全画面判定は機種・透明ステータスバーで誤判定する場合があります。その場合は上の設定をOFFにして常時表示してください。"))
        slider("文字サイズ","size",10,28,14)
        slider("右端からの距離","x",0,100,4)
        slider("上端からの距離","y",0,100,0)
        button("文字と電池の色") {
            AlertDialog.Builder(this).setTitle("表示色").setSingleChoiceItems(arrayOf("白（標準）","薄い灰色","濃い灰色"),settings.number("color",0)) { dialog,i -> settings.set("color",i);dialog.dismiss() }.show()
        }
        content.addView(text("角丸",20f));toggle("上の角丸","top");toggle("下の角丸","bottom")
        slider("角丸半径","radius",0,64,18)
        content.addView(text("アプリ別設定は使用状況へのアクセスを許可した場合だけ適用します。取得するのは前面アプリ名だけで、履歴は保存・送信しません。未許可時は上の共通設定を使います。"))
        content.addView(text(if(OverlayService.usageAllowed(this))"使用状況へのアクセス：許可済み" else "使用状況へのアクセス：未許可"))
        button("使用状況へのアクセスを設定") { startActivity(Intent(SystemSettings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("アプリ別の上・下を設定") { chooseApp() }
        toggle("互換表示方式（バーより背面）","modern")
        content.addView(text("標準方式はAndroid 6〜10向けの旧レイヤーです。表示できない機種は互換方式を試してください。互換方式ではシステムバーの上に角丸を描けません。Android 11以降は保証外です。"))
        content.addView(text("外観合わせ",20f))
        content.addView(text("初期値は参考画面の縦型電池＋右端時計を基準にしています。スクリーンショットの常時取得はせず、白・黒の全画面で位置と大きさを手動調整します。画面密度やOS設定による差は上のスライダーで合わせてください。"))
        button("白背景の全画面で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",false)) }
        button("黒背景の全画面で確認") { startActivity(Intent(this,PreviewActivity::class.java).putExtra("dark",true)) }
        button("電池の最適化設定") { startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        content.addView(text("省電力設定で停止する場合は端末側でOverlayを除外してください。再起動時は、停止前に表示が有効なら復元します。通信・広告・画面収集はありません。"))
    }
    private fun chooseApp() {
        val pm=packageManager
        val apps=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("アプリ別角丸").setItems(apps.map { it.loadLabel(pm).toString() }.toTypedArray()) { _,i ->
            val pkg=apps[i].activityInfo.packageName
            val labels=arrayOf("共通設定に従う","なし","上のみ","下のみ","上下")
            val selection=when(settings.rule(pkg)) {null->0;0->1;1->2;2->3;else->4}
            AlertDialog.Builder(this).setTitle(apps[i].loadLabel(pm)).setSingleChoiceItems(labels,selection) { dialog,n ->
                settings.setRule(pkg,if(n==0)null else n-1);dialog.dismiss()
            }.show()
        }.show()
    }
}

@Suppress("DEPRECATION")
class PreviewActivity: Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val dark=intent.getBooleanExtra("dark",false)
        setContentView(TextView(this).apply {
            setBackgroundColor(if(dark)android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            setTextColor(if(dark)android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            text="右上の時計・電池、四隅を確認\n\nこの画面をタップして戻る\n（タッチ透過の確認）";gravity=android.view.Gravity.CENTER;textSize=18f
            setOnClickListener { finish() }
        })
    }
}
