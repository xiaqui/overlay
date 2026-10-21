# ステータスバー統一着色・Home/Recents非表示の技術調査

調査日: 2026-10-22（日本時間）  
対象: `xiaqui/overlay` / Android 6〜10（API 23〜29）  
確認main: `710c297d43b7630c7eb6107f31aeaa92816dbc66`

本書は技術調査だけを記録する。アプリ本体、設定、Manifest、Gradle、Actionsは変更していない。
PR作成、ビルド、APK生成、端末操作も行っていない。

## 1. 結論

### 統一着色

**条件付きで試作可能。色解決は既存Navbar機構をそのまま拡張できるが、他アプリのステータスバーを公開APIで直接変更することはできない。**

推奨する試作は、既存の1個の色候補を上下で共有し、ステータスバー領域には通知アイコンより背面となる小さな非タッチoverlayを置く方式である。候補の優先順位は次のとおり。

1. アプリ別固定色
2. 起動Activity/applicationテーマの `colorPrimaryDark`
3. 同テーマの `colorPrimary`
4. アイコン代表色
5. 無着色

上下で別々に色を解決せず、同じキャッシュ済み `NavbarColor` を渡す。既存の自動色の暗色化を維持する場合も、暗色化後の同一値を上下へ使う。

ただし可視性と通知アイコンのコントラストは端末・前面アプリ・SystemUIに依存する。試作は初期OFFかつNavbar試験機能の追加オプションとし、まずXperia XZ1 Compact / Android 8で確認する。全機種保証や正式統合を先に判断してはいけない。

### Home/Recentsでの非表示

**Homeは高い確度で、Recentsは実装方式によって条件付きで検出できる。完全検出はAccessibilityなしでは保証できない。**

- `ACTION_MAIN + CATEGORY_HOME` の既定ActivityをPackageManagerで解決し、そのパッケージが前面なら上下着色だけを止める候補にする。
- RecentsがLauncher/Quickstep側なら同じパッケージ判定で隠せる。HomeとRecentsを区別する必要はない。
- RecentsがSystemUI側なら、UsageEventsが返すpackageとclassが、端末で解決できたRecents componentまたは既知の完全修飾クラス名と一致したときだけ隠す。
- `com.android.systemui` というパッケージ名だけでは隠さない。通知シェード、ロック画面、権限画面等まで誤って対象にするためである。
- 判定不能、class欠落、Usage event欠落では隠さない。Recentsで装飾が残るfalse negativeを、通常アプリで装飾を消すfalse positiveより優先する。

時計・電池の `StatusView`、全画面表示判定、角丸には抑止を適用せず、上下の色帯だけを一時的に無効化する。通常アプリの次のforeground eventを受けたら既存poll内で復元する。本節は後続の確定要件（角丸は非表示にしない）で旧案を訂正した。

## 2. 確認した現行実装

指定文書 `NAVBAR_INTEGRATION_RESEARCH_JA.md`、`NAVBAR_PROTOTYPE_JA.md` とmainの次を確認した。

| 箇所 | 現状 | 今回の含意 |
|---|---|---|
| `OverlayService.updateForeground()` | UsageStats `queryEvents()` の `MOVE_TO_FOREGROUND`からpackageだけを保持。着色ON時は直近5秒を重複照会 | Home/Recentsには既存pollを再利用できるが、classも保持する小変更が必要 |
| `OverlayService.updateCorners()` | foreground packageから角丸5状態とNavbar色を決定 | 抑止フラグはこの入口で色だけ透明化する。角丸maskと時計へ波及させない |
| `updateStatusVisibility()` | 全画面設定、アプリ別例外、BarStateから時計・電池を制御 | Home/Recents抑止から変更しない |
| `BarObservation` | 透明1px窓2個でtop/bottom/left/right insetを観測。unknown/cutoutは保守的停止 | 上色帯は `known && top>0` のときだけ出し、top=0/unknownでは消す |
| `NavbarColors` | 固定色→colorPrimary→アイコン色。最大32件LRU、切替時のみ解決 | colorPrimaryDarkをcolorPrimaryの前へ追加し、同じ結果を上下で共有可能 |
| `NavbarLayer/NavbarBandView` | 下帯を先、黒い下隅を後に同一Canvas描画。初回onDraw後だけ旧下隅から所有移行 | 下側は変更不要。上側も失敗時に既存表示へ戻すtransactional設計が必要 |
| `StatusView` | 時計・電池を独立した小窓に白/灰色＋黒影で描画 | 上色帯と統合して描き直すより、既存窓を維持する方が回帰リスクが低い |
| Window設定 | 通常 `TYPE_SYSTEM_OVERLAY` 2006、既存条件で2038。非focus・非touch・透明 | 色帯も非focus・非touchを維持。全画面の大きな窓は不要 |

現在の再構築順はStatusView→観測窓→角丸であり、Navbar帯は状態確定後に追加される。上色帯を同じwindow typeで最後に追加すると既存時計を覆う可能性があるため、そのまま追加してはいけない。

## 3. 統一着色の実現方法

### 3.1 他アプリのWindow色は変更できない

`Window.setStatusBarColor()` と `setNavigationBarColor()` は呼出元自身のWindowに対するAPIであり、前面にいる他アプリのWindowへ適用できない。またバー背景を描くには対象Window自身が `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` 等を持つ必要がある。[S1][S2]

AOSPのDecorViewはstatus/nav背景用ColorViewをアプリWindow内に作る。SystemUIは通知・状態アイコンやナビボタンを別のsystem windowで描く。[S3] Overlayから可能なのは、アプリWindowより前・SystemUI表示より後ろになるoverlay面へ色を置く方法に限られる。

Android 8以降の `TYPE_APPLICATION_OVERLAY` は通常アプリより前、status barやIME等の重要system windowより背面と公式に定義される。[S4] これは通知アイコンを覆わない方向には有利だが、OEMのstatus bar windowが不透明背景を持つ場合は色帯自体が見えない。

### 3.2 推奨する上側レイヤー

推奨は全画面窓ではなく、幅=実画面、縦=`bars.top` の `StatusBarBandView` 1枚である。

描画・Z順は次を目標にする。

| 手前→奥 | 内容 |
|---|---|
| SystemUI | 通知・通信・時刻等の標準status icons |
| 既存Overlay | 時計・電池、黒い上側角丸 |
| 新しい上色帯 | 共通色の矩形。非focus・非touch |
| 前面アプリ | DecorViewと元のstatus bar背景 |

重要なのは、新色帯を既存 `StatusView` の後から同じtypeで追加しないこと。候補は次の順で比較する。

1. **API 23〜25:** `TYPE_PHONE` 2002の小さい下地窓を優先検証する。AOSPではstatus barより背面に置かれ、既存OverlayもBarObservationでこのtypeを利用している。既存の重ねて表示許可内で追加特別権限は不要。
2. **API 26〜29:** まず既存 `activeType` と同じtypeの上色帯を再構築の最初に登録し、その後でStatusViewと上隅を登録する。非systemのapplication overlayはstatus barより背面となるが、同一UID・同一type内の前後は追加順を崩さない実機検証が必要。
3. `TYPE_PHONE` をAPI 26以降へ常用する案は非推奨。API 26でdeprecatedとなり、OEM拒否の可能性がある。[S4]
4. 色帯だけ2038へ変え、既存時計/隅を2006のままにする案も非推奨。OS 8〜10では2038が非system 2006より手前になる構成があり、自分の時計や上隅を覆う競合を作り得る。

上色帯は透明な待機Viewを先にaddし、foreground・bars確定後に同じViewを更新する設計が考えられる。ただしNavbar試作で判明した「初期INVISIBLEのままSurfaceが作られない」問題を繰り返さないよう、実装時は実際のonDraw、画面座標、寸法を確認してからactive扱いにする。待機中は透明描画またはwindow alpha更新を比較し、時計・通知アイコンの実画素を自動テストする。

### 3.3 色解決

`NavbarColors.themeColor()` の探索順を次へ変えるだけで追加のService、権限、ライブラリ、pollは不要である。

1. 対象パッケージ固有attr `colorPrimaryDark`
2. `android.R.attr.colorPrimaryDark`
3. 対象パッケージ固有attr `colorPrimary`
4. `android.R.attr.colorPrimary`

起動Activityテーマ、なければapplicationテーマという現行方針を維持する。解決値が不透明なcolor intでない場合は次候補へ進む。失敗時だけ現行32×32アイコン代表色を使う。固定色は常に最優先とする。

これは静的な「アプリらしい色」の推定であり、現在Activity、画面内Toolbar、Webコンテンツ、動画、動的テーマ、夜間モードの独自切替とは一致しない場合がある。`android:statusBarColor` をcolorPrimaryDarkと別に明示するアプリもあるが、今回確定された優先順位に含まれないため読まない。

### 3.4 既存表示への影響

| 対象 | 影響と対策 |
|---|---|
| Overlay時計・電池 | 既存StatusViewを維持。上色帯より手前であることを実画素確認。白/灰＋黒影は暗色候補に適するが、明るい固定色でも確認する |
| 黒い上側角丸 | 色帯より後に描く。同じCanvasにまとめられない場合も、上隅が手前であることを保証してから色帯を有効化 |
| 黒い下側角丸 | 現行NavbarBandViewの「色→黒隅」を維持 |
| 標準通知アイコン | 絶対に色帯より手前である必要がある。2006がstatus barより手前になるAPI 23〜25/OEMで全幅帯を使わない |
| iconの白/黒 | API 23から前面アプリはlight status bar（暗いicon）を要求できる。Overlayは他アプリのicon appearanceを確実に変更・取得できない。暗い色帯＋暗いiconの低コントラストが最大リスク |
| 通知シェード/Heads-up/IME/ロック画面 | SystemUIの別windowや不透明scrimが色帯を隠す、または機種依存の順序になる。初期試作の保証外 |
| 全画面 | `bars.top==0` またはunknownなら上色帯だけ除去。時計・電池は既存の常時/全画面設定を継続 |
| タッチ | NOT_TOUCHABLEを維持。上帯・下帯とも戻る/ホーム/履歴、通知シェード操作を妨げないことを実機確認 |

特にstatus iconの明暗状態は、一般overlayから他アプリについて信頼できる公開APIで得られない。画面キャプチャを使わない方針では自動コントラスト保証は不可能である。正式統合時も「暗い候補を基本、読みにくいアプリは固定色/OFF」という逃げ道が必要。

## 4. Home/Recents検出

### 4.1 Home

起動時・構成変更・関連パッケージ変更時に次のimplicit intentをPackageManagerで解決する。

```text
action = android.intent.action.MAIN
category = android.intent.category.HOME
```

`resolveActivity(..., MATCH_DEFAULT_ONLY)` が実Activityを一意に返す場合だけ、そのpackageをdefault homeとして保持する。[S5][S6] resolver/chooser、null、無効componentなら判定不能とし、隠さない。

UsageEventsの最新foreground packageがdefault homeと一致したら着色抑止とする。Launcher内のApp drawerや設定画面も同packageなら着色が止まるが、通常の第三者アプリを誤停止するより限定的であり、HomeとRecentsを厳密に分けない要件にも合う。

### 4.2 Launcher/QuickstepのRecents

Android 9のAOSP設定は既定Recents componentを `com.android.launcher3/com.android.quickstep.RecentsActivity` とし、SystemUI版を代替として明記している。[S7] LauncherがRecentsを担当する端末では、default home package一致だけでHomeとRecentsの両方を抑止できる。

Android 10やOEMではRecents animation/stateが必ず独立Activityのforeground eventになるとは限らない。Launcher packageが通知されれば抑止できるが、直前アプリのforegroundが残れば検出できない。この場合は装飾を残す。

### 4.3 SystemUIのRecents

AOSP 6〜9系には `com.android.systemui.recents.RecentsActivity` が存在する構成があり、Android 9のSystemUI Manifestにもalternate Recentsとして明示される。[S8] Frameworkの内部resource `config_recentsComponentName` はOEM overlayで差し替え可能である。[S7]

実装候補は次の保守的な完全一致判定である。

- `UsageEvents.Event` のpackageとclassの両方を保存する。`getClassName()` はAPI 21からあるが、イベントによってnullになり得る。[S9]
- `Resources.getIdentifier("config_recentsComponentName", "string", "android")` で端末のcomponentが得られ、`ComponentName.unflattenFromString()` で妥当なら、そのpackage/class完全一致を採用する。
- resourceが取得不能でも、実イベントが `com.android.systemui/com.android.systemui.recents.RecentsActivity` と完全一致する場合だけAOSP fallback候補にできる。
- packageだけが `com.android.systemui`、classがnull、`Recents`という部分文字列だけ、直前にRecentsボタンを押した時刻だけ、という条件では隠さない。

内部resource名は公開SDK契約ではなく、OEMで削除・名称変更され得る。UsageStatsへSystemUI Activityのresume eventが公開されない端末もある。この経路はbest effortであり、検出不能時にAccessibilityやrootへ自動的に拡張しない。

### 4.4 抑止と復元

foregroundを次のような値として保持する設計がよい。

```text
ForegroundTarget(packageName, className, eventTime)
```

分類結果 `suppressDecorations` は描画直前にだけ使う。

- `updateStatusVisibility()` は変更しない。時計・電池は従来どおり。
- 角丸maskと5状態は一切変更しない。
- statusの色specをnull、navの色描画を透明化して帯が所有する下隅を維持する。
- 色キャッシュやforeground自体は捨てない。
- 通常アプリの次のforeground eventで分類がfalseになれば、既存poll内で角丸と色を復元する。
- Usage Access取消、event/class欠落、Home component不明ではfalse（表示を維持）。

pollは既存の500ms/1000msを再利用し、新しいtimerや入力監視を作らない。実際の反映はpoll周期にUsageEventsの公開遅延が加わる。現行の5秒重複照会は取りこぼし低減には役立つが、イベント欠落をなくす保証ではない。

前面状態がlauncherのまま古く残り、次アプリのeventが欠落すると、通常アプリで抑止が残る可能性がある。ActivityManagerのrecent/running task APIで他アプリの正確な現在taskを補強することも、API 21以降の一般アプリには安定した方法ではない。[S10] この制約は実機の長時間アプリ切替試験で評価し、発生する端末では本機能をOFFにする。SystemUI package全体判定など、誤検出を増やすfallbackは追加しない。

## 5. Android 6〜10の見込み

| OS | ステータスバー着色 | Home/Recents抑止 | 主なリスク |
|---|---|---|---|
| 6 / API23 | 2002の小さい下地窓を優先。light status iconあり | Homeは既定launcher。AOSP RecentsはSystemUI Activity完全一致を試す | Xperia独自layer、SystemUI event欠落、暗iconとの低コントラスト |
| 7 / API24-25 | API23と同じ | SystemUI Recents候補。分割画面では単一foregroundが曖昧 | 分割画面、Tabletのsystem bar位置、OEM class差 |
| 8 / API26-27 | application overlay制約下。既存activeType/追加順を実機比較 | AOSP/XperiaはSystemUI実装の可能性が高い。class完全一致のみ | XZ1最優先。OEM不透明status背景、2006/2038の自己競合 |
| 9 / API28 | 3ボタン縦画面から。cutout unknownは着色停止 | AOSPはQuickstep launcherでpackage一致しやすい。SystemUI代替もあり | 2ボタン、cutout、keyguard event、OEM launcher差 |
| 10 / API29 | 3ボタン縦画面から。gestureは初期保証外 | Quickstep/launcher一致を優先。独立Activity eventがない状態遷移あり | gesture/recents animation、multi-resume、contrast scrim、IME |

横画面、左右status/nav、分割画面、ジェスチャー中のOverview、外部displayは初期試作の保証外とする。判定不能時は上下着色を止め、角丸は従来表示へ戻す。ただしHome/Recents分類だけが不能な場合は、誤消去回避のため従来の角丸と色を表示する。

## 6. 推奨する最小試作範囲

1. Navbar試験機能の色resolverへ `colorPrimaryDark` を追加し、1個の候補を上下で共有する。
2. 「上下バーを同色にする」をNavbar試験機能配下の独立した初期OFF設定にする。既存Navbar OFFなら上側も起動しない。
3. 縦画面・3ボタン・`bars.known`・top/bottom可視だけを対象にする。上または下が0なら該当帯だけ消す。
4. API23〜25は2002下地窓、API26〜29は既存typeと登録順を比較する小さな実験を先に行う。通知アイコン、Overlay時計、上隅の実画素が全て残る方式だけ採る。
5. foregroundへclass/timeを追加し、default home packageと正確なRecents componentだけで `suppressDecorations` を決める。
6. 抑止対象は上下色帯のみ。4隅、時計・電池、全画面判定、foreground service、設定移行は変更しない。
7. 新規Service、poll、権限、ライブラリ、通信、Accessibility、root、ADB常用、画面キャプチャは追加しない。
8. OFF時はHome/Recents分類、上色帯、追加cache処理を動かさず、0.2.0相当の窓数・poll条件へ戻す。

設定を一度に正式化せず、まずテスト専用branch/APKで次を判定する。

- 上下が実際に同じRGBで見える。
- 標準通知icon、Overlay時計・電池、上下の黒隅が隠れない。
- 明/暗status iconの両アプリで最低限の視認性がある。
- HomeとLauncher実装Recentsで上下色だけが消え、角丸・時計・電池が残る。
- SystemUI実装Recentsは検出できた端末だけ消える。通知シェード/lockscreenでは誤消去しない。
- 通常アプリ復帰が複数回の高速切替、長時間Home滞在、画面OFF/ON後も成立する。
- 全画面、status/nav一時表示、Usage許可取消、回転で色帯が残留しない。

## 7. 工数・負荷・リスク概算

| 項目 | 概算 | 注記 |
|---|---:|---|
| 色resolver拡張・共有 | 0.5日 | JVM testを含む |
| 上色帯のlayer試作 | 1.5〜3日 | OS/OEM差が最大の不確定要素 |
| Home/Recents分類 | 1〜2日 | component解決、class保持、失敗時表示、test fixture |
| 自動テスト | 2〜3日 | API23/24/26/28/29、実画素、切替、権限、全画面 |
| Xperiaほか実機検収 | 1〜2日以上 | 5端末、3ボタン縦を先行。人手必須 |
| 合計 | 5〜8.5開発日＋実機調整 | SystemUI差で延長し得る |

追加依存は不要で、APK増分はコード・文言・テストを除けば数〜十数KiB程度と予想するが、ビルドしていないため実測値ではない。常駐処理は既存poll内の文字列比較が中心。上色帯ONで小Surfaceが1枚増え、Home/Recents時は色帯と角丸Surfaceが外れる。CPU/PSS/電池差は実機でON/OFF同条件測定が必要で、「負荷増なし」とは断定しない。

### 主なP1/P2候補

- P1: API23〜25で全幅2006色帯がSystemUI通知iconより前に出て覆う。
- P1: 上色帯が既存StatusView/上隅より前に出る、またはタッチを遮る。
- P1: 全画面・権限取消・Service停止後に色Surfaceが残る。
- P2: status iconの明暗と共通色が衝突して読めない。
- P2: stale launcher eventで通常アプリの角丸/色が消え続ける。
- P2: SystemUI packageの広すぎる判定でlockscreen/notification shadeまで抑止する。
- P2: SystemUI Recentsのclass/eventが端末で得られず、Recentsだけ装飾が残る。この項目は制限として許容可能。

## 8. 正式統合判断

**推奨は「小規模試作を行う価値あり。ただし統一着色とRecents完全検出のどちらも、現時点では正式統合を確約しない」。**

次を全て満たす場合に限り正式統合候補とする。

- XZ1 Compact / Android 8で、上下同色、通知icon、Overlay時計・電池、黒隅、タッチ透過が同時に成立する。
- Android 6/7で通知iconを覆わない下地typeが成立する。
- Home/Launcher Recentsは安定して抑止・復元し、通常アプリの誤抑止がない。
- SystemUI Recentsは「対応端末だけbest effort」と明記しても実用上許容できる。
- 全画面・回転・権限取消・画面OFF/ONで残留がない。
- 既存Navbar OFF時の窓数・poll・見た目に回帰がない。

上色帯がSystemUI/OEM背景に隠れる、通知iconを覆う、iconコントラストを許容できない場合は統一着色を採用しない。SystemUI Recentsを検出できないだけなら、Home/Launcher Recentsだけを安全に抑止し、未対応条件を明示する縮小統合が現実的である。検出率を上げるためにAccessibility、root、ADB常用、画面キャプチャへ拡張することは推奨しない。

## 9. 参照資料

- [S1: Android Window.setStatusBarColor / setNavigationBarColor](https://android.googlesource.com/platform/frameworks/base/+/7bee686ef7193eb0dcef28c6af46bc6f86c0aaa5/core/java/android/view/Window.java#2146)
- [S2: Android Developers Window API](https://developer.android.com/reference/android/view/Window)
- [S3: AOSP DecorViewのstatus/navigation ColorView](https://android.googlesource.com/platform/frameworks/base/+/880eff6/core/java/com/android/internal/policy/DecorView.java#159)
- [S4: Android Developers WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [S5: Android Developers Intent CATEGORY_HOME](https://developer.android.com/reference/android/content/Intent#CATEGORY_HOME)
- [S6: Android Developers PackageManager.resolveActivity/queryIntentActivities](https://developer.android.com/reference/android/content/pm/PackageManager#resolveActivity(android.content.Intent,%20int))
- [S7: AOSP Android 9 config_recentsComponentName](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r16/core/res/res/values/config.xml#2562)
- [S8: AOSP Android 9 SystemUI RecentsActivity](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r18/packages/SystemUI/AndroidManifest.xml#315)
- [S9: Android Developers UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [S10: Android Developers ActivityManager（task取得APIの制限）](https://developer.android.com/reference/android/app/ActivityManager)
- [現行Navbar調査](NAVBAR_INTEGRATION_RESEARCH_JA.md)
- [現行Navbar試作記録](NAVBAR_PROTOTYPE_JA.md)

