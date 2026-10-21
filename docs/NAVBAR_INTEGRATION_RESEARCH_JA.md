# ナビゲーションバー背景色の軽量統合調査

> 後続技術検証の更新（2026-10-21）: 本調査時の「着色にも既存activeTypeを共有」という提案は、
> 全5OSの配置成功・実画素着色失敗を受けて異なる種別/flagsも比較したが、診断の結果は
> 初期不可視のままSurfaceがない実装不具合だった。種別による着色不可能の証拠とはしない。
> 後続試作では可視化と実描画後の所有移行を修正し、元のactiveType共有へ戻して再評価する。
> 実測結果と正式統合判断は [NAVBAR_PROTOTYPE_JA.md](NAVBAR_PROTOTYPE_JA.md) を参照。
> 以下は調査段階の記録であり、当時の未実装・提案という記述を保持している。

調査日: 2026-10-21（日本時間）
対象: xiaqui/overlay / Overlay 0.2.0 / Android 6〜10（API23〜29）
確認main: `badf1dfaff99dac3644997ac0b0a840f8b0308b9`。配布APKのsourceは `2f942bbaebadc7bee7666c0c9beb9ef1bc88280c`。

今回は調査だけを実施した。アプリ本体・Manifest・Gradle・Actions・設定データは変更していない。PR作成、ビルド、APK生成、端末へのインストール、参考APKの実行は行っていない。本書だけをコミットする。参考APKのコード・画像・レイアウト・ライブラリは転用しない。

## 1. 結論

**条件付きで軽量統合は可能と考える。推奨は、既存Service・UsageStats・バー位置観測を使った「アプリ別固定色＋着色と角丸の描画順統一」。画面からの連続採色や、すべてのアプリ・ナビ方式での強制着色は採用しない。**

Navbar Appsの解析から、背景色機能のためにアクセシビリティやrootが必須とは判断しない。着色は通常の重ねて表示窓、アプリ検出にはAccessibilityのほかUsageStats方式がある。自動色は画面の画素取得ではなく、主に対象アプリの静的テーマ色とアイコン色による推定だった。

重要な区別として、システムのナビゲーションバー窓より背面でも、着色が一律不可能になるわけではない。通常のアプリがDecorViewで描くバー背景より前、透明なSystemUIとそのボタンより後ろに着色帯を挟める場合がある。一方、SystemUIが不透明背景やスクリーンを重ねる状況を一般アプリ側から強制変更することはできない。全アプリ保証の機能ではなく、端末・対象アプリでの成立確認が必要。

現時点ではOverlay 0.2.0を維持する。次の開発を行うなら、まず別途許可された小さな技術検証でXperiaの着色・ボタン操作・角丸の成立を確認し、その後に固定色だけを任意機能として統合するのがよい。自動色推定まで含めたNavbar Appsの全面置換は勧めない。今回はその技術検証コードも作らない。

## 2. 入力と調査方法

| 入力 | 実測内容 |
|---|---|
| 添付 Overlay.apk | com.xiaqui.overlay / 0.2.0 / versionCode 2 / min23 / target25 / 645419 bytes |
| Overlay APK SHA256 | `a5fbf73e08e5e985a6b11c12fc81078d7f52f134cd3633b93c9a3892b472fe1b`（既存配布記録と一致） |
| 添付 Navbar Apps APK | pl.damianpiwowarski.navbarapps / 3.1 / versionCode 63 / min21 / target28 / 9226475 bytes |
| Navbar Apps APK SHA256 | `bdbbe8cdf1985013b8a43fa85e7d6fadd7573b1ec8a1f74780348ce0de9fd299` |
| 静的解析 | Androguard 4.1.4でManifest、バイナリXML、DEX命令と呼出しを確認。Navbar Appsの自パッケージ459クラス、コードを持つ2101メソッドを抽出し、関連経路を追跡 |
| OS資料 | Android 6/7のPhoneWindowManager、8/9/10のWindowManagerPolicy、6のPhoneWindow、10のDecorView・DisplayPolicy・SystemUI BarTransitions、およびAndroid公式API資料 |

以下の「確認」はソース/DEX/Manifestを読んだ事実。「推定」「提案」は設計判断。新しい着色窓の実機・エミュレータ動作、CPU/電池/メモリ、実装後APKサイズは未測定。既存0.2.0のCI成功を新機能の動作証明には使わない。

## 3. 既存Overlayを再利用できる部分

### ウィンドウと描画順

`Policy.windowTypes()`、`OverlayService.params()/rebuild()/updateCorners()`、`OverlayViews.kt`を確認した。

- min23 / target25 / compile35。通常は`TYPE_SYSTEM_OVERLAY`（2006）、API26以降で拒否された場合や明示的な互換設定では`TYPE_APPLICATION_OVERLAY`（2038）。
- 時計・電池は小さなStatusView、角丸は4個のCornerView。透明形式、NOT_FOCUSABLE / NOT_TOUCHABLE / LAYOUT_IN_SCREEN / LAYOUT_NO_LIMITS。描画素材は独自Canvas。
- 現状の再構築順はStatusView→必要なら観測窓→角丸窓。角丸は設定/offset変更時に再作成する。別アプリと同じ種類の窓でも、UIDが同じというだけで前後が保証されるわけではない。
- `BarObservation`の縦横1px窓はAPI23〜25で2002、26以降で2038。これは描かない観測専用窓であり、着色面として流用するものではない。
- Insetsと実画面/窓寸法差からtop/bottom/left/rightを測定。非表示は0、cutout等で曖昧ならunknown。現在の位置補正と5択を保持する必要がある。

### 前面アプリ・常駐

- `UsageStatsManager.queryEvents()`の`MOVE_TO_FOREGROUND`でパッケージを把握。任意の使用状況アクセスを利用し、AccessibilityServiceはない。
- pollはバー観測が必要なら500ms、それ以外のアプリ別設定利用時は1000ms。画面OFFで停止。時計の分境界更新・電池イベント・既存foreground serviceを再利用できる。
- 今は`hasRules()`がcorner2/displayキー、`needsBars()`が全画面モード/内側角丸を前提にしている。将来の色設定だけを有効にした場合にも検出/観測を開始する条件の追加が必要。「検出コードがあるので無変更で使える」とは扱わない。
- アプリ切替と色変更は分ける。パッケージ/設定/構成が変わったときだけ色を解決し、500msごとにテーマやアイコンを解析しない。

## 4. Navbar Apps v3.1で確認した方式

難読化されたメソッド名は、このAPKの解析位置を再確認するための識別子であり、他バージョンへ一般化しない。以下は動作の要約で、逆コンパイルコードの転載ではない。

| 解析位置 | 確認した事実 |
|---|---|
| `utils.k.b(): int` | API26未満は2003（TYPE_SYSTEM_ALERT）、26以上は2038を返す |
| `service.ColoringNavigationBarService.onCreate()/b()/c()` | FrameLayout等をWindowManagerへaddView/updateViewLayout。横幅/ナビ高さを用い、下端や左右のナビ領域に配置する経路がある |
| 同ServiceのLayoutParams | 主着色窓はflags=536（0x218: NOT_FOCUSABLE、NOT_TOUCHABLE、LAYOUT_NO_LIMITS）、format=-3（TRANSLUCENT）。下端配置の経路ではナビ高さ分の負のyを指定。これは当該窓の親フレームに合わせた配置であり、その値をOverlayへコピーすべきではない |
| `ColoringNavigationBarService.b(int)` | 着色FrameLayoutにsetBackgroundColor、ほかに色遷移の分岐。SystemUIの設定を直接書くAPIによる主着色ではない |
| `b.a.a(String)` → `utils.d.c(String)` | 前面パッケージから色解決へ渡す経路。固定色選択・キャッシュ等の分岐を持つ |
| `utils.d.e(String)` | 他アプリResourcesを取得し、起動Activityのthemeに対して独自`colorPrimary`属性とframework属性16843827（android:colorPrimary）を解決する |
| `utils.d.d(String)` | アプリアイコンをBitmapへ描画し、support Paletteのvibrant/dark-vibrant候補を取得する |
| `utils.d.c(String)`等 | テーマ色を優先し、一部の色/条件ではアイコン色へfallback。キャッシュ・アプリ除外等がある。ライブ画面の背景色の正確な取得ではない |
| `service.AccessibilityDetectingService.onAccessibilityEvent()` | パッケージ/クラス名を使う。設定XMLはTYPE_WINDOW_STATE_CHANGED（0x20）、canRetrieveWindowContent=false |
| `b.c.f()` | UsageStatsManager.queryUsageStatsを使う代替検出経路が存在。Accessibilityだけが唯一の検出法ではない |
| `settings.HideNavbarActivity` | WRITE_SECURE_SETTINGSとpolicy_controlへの書込みは、ナビバー等を隠す別機能に存在。主着色のために同権限が必須とは読めない |

ManifestはSYSTEM_ALERT_WINDOW / PACKAGE_USAGE_STATSに加えAccessibility、WRITE_SECURE_SETTINGS、音声、通信、課金、ストレージ等を含む。広告/分析等のコンポーネントもあり、9.2MB全部が着色機能の必要量ではない。これらの権限・SDK・装飾をOverlayへ持ち込む理由はない。

自パッケージの解析した色解決経路にMediaProjectionやスクリーンショット取得は見つからなかった。難読化/外部ライブラリを含むAPK全体の「あらゆる画面取得が絶対にない」という証明ではないが、追跡した自動色の主要経路はテーマ/アイコン推定で説明できる。

## 5. 他アプリのナビバーを着色できる条件

### 自分のWindowのAPIと、他アプリ表示中の着色は別

`Window.setNavigationBarColor()`は自分のWindowに関するAPI。Overlayの設定Activityで呼んでも、その後に表示する他アプリのWindowを直接変更できない。Serviceの一般overlay窓を非focusのまま追加しただけで、他アプリのバー設定やボタン配色を自由に変更する公開APIもない。[S1][S2]

AOSPの通常経路では、アプリのDecorViewがナビバー背景のColorViewを描く。Android6のPhoneWindow内DecorView、Android10のDecorViewで当該処理を確認した。SystemUIのナビバーはボタン等と、状態に応じた透明/半透明/不透明の背景を別に描く。[S3][S4][S5]

したがって、次の順序が成立する状況では着色帯が見える（成立条件の推定であり、今回端末で試してはいない）。

| 手前→奥 | 内容 |
|---|---|
| SystemUI | 実際の戻る/ホーム/履歴ボタン。背景が透明ならその下の色が見える |
| Overlayの同一描画面 | 黒い角丸を最後に描く |
| 同じ面の下地 | 独自のナビ着色帯 |
| 前面アプリのWindow | DecorViewが描く元のバー背景とアプリ内容 |

**SystemUI窓自体より上に着色窓を置く必要はない。** ただしSystemUIの背景が不透明、半透明scrim、IME/ロック/分割画面等の別経路なら、帯が隠れる/混色する可能性がある。バーを透明にする設定を他アプリへ強制する権限は持たない。既存Insets観測だけで「色が実際に見えた」ことまでは判定できない。

また実ボタン/ジェスチャーハンドルの明暗はOS/前面アプリ側の状態に従う。着色帯を置いても自動的にボタン色が追従するとは限らない。初期範囲では暗い背景色を中心にし、白/黒両方のアイコンとの視認性を実機確認する。明るい任意色を無条件に許す設計は避ける。

### 角丸が隠れる理由と統合時の注意

AOSPの一般アプリ向けwindow layerをソースで確認した（数値が大きいほど前、OS版間で数値の大小そのものを比較しない）。[S6〜S10]

| OS | Navbar Apps | Overlay標準 | SystemUIナビ窓 | 含意 |
|---|---|---|---|---|
| Android6 / 7 | 2003: layer11 | 2006: layer20 | layer21 | この組合せだけでは着色窓がOverlayより上になるとは説明できない。OEM/実際の角丸アプリの窓種類等が必要 |
| Android8 / 9 / 10 | 2038: layer12 | 非systemの2006: layer11 | layer23 | Navbar Appsの帯がOverlay標準の角丸を覆う経路がある |
| Android8〜10でOverlay互換2038 | 2038 | 2038 | layer23 | 同種窓の追加/更新順等も関係し、他アプリとの安定した優先順は保証しない |

ユーザー報告は「Navbar AppsとRound Cornersの併用で下側が隠れる」という事実。今回それを実機再現したわけではなく、第三者Round Corners側の実際の実行時窓/表示状態も今回は追っていない。上表はOverlay 0.2.0と添付Navbar Appsの組合せのソース上の説明であり、報告事象すべての原因を確定したものではない。

**同じアプリに入れただけでは解消しない。** 新着色窓だけ2038、既存角丸は2006のままなら8〜10で同じ競合を作る。第一候補は既存`activeType`を共有し、重なる着色と角丸を同じCanvas/親View内で背景→角丸の順に描くこと。別窓の場合は同種窓で着色を先、時計/角丸を後に追加し、再作成でも順序を崩さない管理が必要。初期実装でアプリ全体を2038へ強制移行する案は採らない。

SystemUIより背面という制限は統合後も残る。不透明なSystemUI背景を黒い角丸で必ず上書きできる機能とは約束しない。

## 6. Android各バージョンの懸念

| バージョン | 懸念と扱い |
|---|---|
| 6（API23） | 重ねて表示の明示許可と任意Usage Accessが必要。2006を維持。3ボタン/メーカー独自バー、白いボタンと明るい帯の低コントラスト、下/右ナビ位置を確認する |
| 7 / 7.1（24 / 25） | 分割画面では単一の前面パッケージだけで色を一意に選べない。初期は分割画面を保証対象にしない。端末のナビ固定/回転挙動を仮定しない |
| 8 / 8.1（26 / 27） | target26以上の非systemアプリでは旧alert窓が制限され、Navbar Appsは2038へ分岐。Overlayのtarget25と2006互換経路を維持し、拒否時の2038 fallbackも壊さない。API26からLIGHT_NAVIGATION_BARがあるが他アプリの実ボタン色をOverlayから確実には指定できない |
| 9（28） | 3ボタン/2ボタンと切り欠き。既存cutout unknownでは着色を停止する保守的fallback。公開APIだけで全OEMのナビ方式/色可視性を判定できるとはしない |
| 10（29） | ジェスチャー領域、透明バー/contrast scrim、IME管理のnav色、左右ナビ、マルチresumeに注意。初期はジェスチャー方式を対象外/機能OFFと明示する。0のinsetを保存寸法で補って帯を強制表示しない |

全バージョン共通で、没入型/ナビ非表示では帯を出さない。バー状態unknown、権限取消、停止、消灯でも新機能だけを安全に止め、0.2.0の時計・電池・角丸を継続/復元する。transientバーやIMEの観測には既知の限界があるため、見えないバーへ色だけを残さないことを将来の検証項目にする。

## 7. 自動色取得と固定色の比較

| 方式 | 精度・制限 | 権限/負荷 | 判断 |
|---|---|---|---|
| パッケージ→ユーザー指定固定色 | 指定色は確定。ただし前面検出の遅れ/誤認、SystemUIの遮蔽や混色は別問題 | 既存Usage Accessとoverlay許可。Map/SharedPreferences参照中心 | 最小実装の第一候補 |
| 起動Activityの静的theme/colorPrimary推定 | Navbar Appsの主要方式。アプリらしい色は得られ得るが、現在Activity/画面/タブ/動的テーマ/夜間モード/アプリ独自描画とは一致しないことがある | PackageManager/Resources。一般に追加特別権限なし。パッケージ/構成単位にcacheすれば軽い | 第二段階の任意「推定色」。固定色を優先 |
| アプリアイコンの代表色 | 画面色と一致する根拠は弱い。多色/単色/透明/adaptive iconで不安定 | 小Bitmapの抽出/集計が必要。毎pollは不可 | 初期は不要。必要なら独自小規模処理、Palette追加を当然視しない |
| ActivityManagerのTaskDescription等 | 他アプリの現在task情報を自由に取得できない。getRunningTasks/getRecentTasksはAPI21以降制限 | 一般アプリの公開APIでは安定しない | 採用しない [S11] |
| Accessibilityでイベント/画面木を読む | パッケージ/クラスは得られるが、画面木から描画の背景色が正確に分かるわけではない | 新しい強い権限依存、端末差/保守負担 | 方針に反するため不採用 |
| MediaProjectionで画素を採色 | 表示画素に近いが、動画/画像/スクロールで変動し、何を代表色にするかも曖昧。自分の帯が採色へ混入する循環やFLAG_SECURE画面の制限もある | ユーザー同意と画面取得、Surface/画像処理、常駐負荷が増える | 軽量統合には不採用 [S12] |

UsageStatsが分かるのは色ではなく前面パッケージ。自動「アプリ切替に追従」と「現在画面の色を正確に自動取得」は別要件である。正答率の数値は測定しておらず提示しない。テーマの例外/取得失敗は、固定色または無着色へ戻す。前アプリの色を無期限に引き継がない。

## 8. 推奨する最小実装案（将来の設計案）

1. 初期OFF。ユーザーがアプリ別固定色を登録した場合だけ適用。未設定/不明パッケージは既存表示のまま。現行5択/上下ON/OFF/半径とcorner2設定を変更しない。
2. 同じOverlayServiceと既存pollを再利用。追加Service/通知/別ポーリング/画面キャプチャ/通信を作らない。機能OFF時は追加窓と観測・検出負荷をゼロにする。色ルールをhasRules/needsBars相当の判定へ含める。
3. 描画には既存activeType（2006優先、既存条件で2038）を使う。Navbar Appsの2038分岐をそのままコピーしない。描く色帯は現在のnav領域だけとし、黒い下隅を最後に描く。
4. まず縦画面・通常3ボタン・ナビ可視・knownの範囲に絞る。帯と下側2隅の領域を小さな一つのViewにまとめれば、通常位置と内側位置のどちらでも描画順を内部で決められる。内側の角丸はバーより上へ移るため、必要な窓高はnav高だけでなく角丸半径分も含める。
5. 下側2窓を帯側が担当するときだけ既存の下側窓を外し、追加失敗/OFF/対象外では旧窓を復元する。帯を別窓で重ねてから古い角丸が見えることを期待する実装は避ける。時計・電池・上側窓の描画コードと更新方式は維持する。
6. 回転中/横画面、2ボタン/ジェスチャー、cutout、unknown、ナビ非表示、IME/マルチwindowでは初期機能を適用しない/停止して元の表示へ戻す方針とする。ただし既存BarObservationはIME専用の検出機構ではなく、ADJUST_NOTHINGとnav寸法の上限処理もあるため、IME表示の完全な検出・自動停止を保証できない。UsageStatsも通常はキーボードの表示イベントを前面アプリ切替として提供しない。これが必須なら追加検証が成立するまで正式採用しない。ナビ方式を厳密に識別できない端末は、3ボタン使用中であることを利用者が確認して有効化し、適用/除外設定を持つ必要がある。横画面対応は別段階で検証する。

横画面の全面対応は自明ではない。側面navの帯は上隅や右上時計にも重なり得る。帯と両下隅を一つの矩形窓で包むと、L字の内容でも画面全体サイズのSurfaceになり得る。将来対応するなら辺ごとの細い窓で管理し、重なる上隅/時計の順序も保持する。単にbottom insetをleft/rightへ読み替えるだけでは不十分。

## 9. 権限・負荷・サイズ・工数

以下は静的設計からの概算。新実装の測定値ではない。

| 項目 | 最小案の見込み |
|---|---|
| 権限 | 既存SYSTEM_ALERT_WINDOW、PACKAGE_USAGE_STATS（設定画面でのUsage Access許可）、FOREGROUND_SERVICE、BOOTのみ。INTERNET/ストレージ/音声/Accessibility/WRITE_SECURE_SETTINGS/root/常時ADBは不要 |
| 常駐 | 既存1プロセス・1Serviceを共有。既存500〜1000ms poll以上の頻度にしない。色が同じなら再描画/再追加しない。消灯中停止を維持 |
| CPU | 固定色はMap参照と状態変化時のinvalidate程度を想定。ただしGPU合成する帯の面積は増えるため「負荷ゼロ」「電池差なし」とは言わない。現在アプリ別設定を使っていない利用者にはUsageStats poll自体が追加負荷になる |
| メモリ | 例: 1080×144pxのRGBA帯は1 buffer約0.59MiB、3 bufferなら約1.78MiBの画素領域。内側角丸分/Surface管理/driver分は別。実際のPSS増加ではない。全画面1080×1920なら1枚約7.91MiBとなるため全画面Canvas窓を避ける |
| 設定データ | パッケージ→色の少数エントリ。使用履歴や画像を保存しない |
| APK増分 | 新規外部依存・画像なしの固定色UI/描画なら概ね10〜40KiB程度を仮置き。依存/署名/ビルド条件で変わり、保証値ではない。9.2MBの参考APKをそのまま統合する必要はない |
| 工数 | 1人日を通常の開発日として、技術検証0.5〜1.5日、固定色/UI/停止復元/描画順1.5〜3日、5OS自動＋実機回帰と修正2〜3日。合計4〜8人日程度、端末確保待ち別。自動テーマ推定はさらに1〜2日、横/側面・複数ナビ方式はさらに2〜4日程度の幅を見込む |

既存機能を守る上での主な変更点はServiceの窓所有/順序と有効条件である。独立した色クラスだけ追加して済むとは見積もらない。新窓の失敗を既存Service全停止へ波及させない設計も必要。

## 10. 実装を判断するための確認条件

今回は未実施。次の開発を別途行う場合の合否条件とする。

- まずXperiaのAndroid8でNavbar Appsを停止し、通常アプリ/古い不透明バーのアプリ/白黒バーについて、着色帯が見える範囲と見えない範囲を区別する。角丸の物理端/内側、時計・電池を画素で確認する。
- Android6/7/8/9/10のエミュレータで同じ帯の成立条件を確認。自分のActivityでのsetNavigationBarColorだけを試し、他アプリ表示中の成功と扱わない。
- 戻る/ホーム/履歴のタップ・長押し・スワイプを通す。アイコンの明暗と色のコントラスト、システムUIへの影響を確認する。
- 全画面→通常、回転、左/右nav、IME、権限取消、画面OFF→ON、停止/再起動、使用状況アクセス拒否で元の0.2.0の表示へ戻ること。対象外の横画面等で色だけ残さないこと。
- 機能OFFの窓数/タイマー/CPU/PSSを現行と比較し、有効時の増分も測る。低メモリ/長時間の電池差は別に測る。
- 着色が主要Xperiaアプリで見えない、実ボタンが見づらい、タッチを妨げる、時計/角丸を不安定にする場合は統合を中止する。上位の特権窓やAccessibilityへ拡大して無理に成立させない。

## 11. 統合か併用か

**限定した固定色機能の統合には進める価値があるが、正式採用は上記の小規模技術検証の後。現時点で0.2.0への本実装を開始すべきとはしない。**

併用を続けてもAndroid8〜10の2038/2006の層差は自然には解消しない。同種窓の場合の起動順変更も恒久解決ではない。いまの運用で角丸を優先するならNavbar Appsを停止するか、Overlayの「下だけ内側 / 上下とも内側」で帯と重なる位置を避ける方法を試す価値はある。ただし後者で衝突が必ず解消することは今回確認していない。

将来の最小案は「固定色をアプリに応じて切替、同じ描画面で黒角丸を最後に描く」。自動テーマ色は後付けの推定機能、ライブ画面採色は対象外。この範囲なら別常駐アプリを減らす利点と、Overlayの軽さを両立できる可能性がある。

## 12. 参照資料

### 既存ソース・配布記録

- [OverlayService.kt（確認main固定）](https://github.com/xiaqui/overlay/blob/badf1dfaff99dac3644997ac0b0a840f8b0308b9/app/src/main/java/com/xiaqui/overlay/OverlayService.kt)
- [BarObservation.kt](https://github.com/xiaqui/overlay/blob/badf1dfaff99dac3644997ac0b0a840f8b0308b9/app/src/main/java/com/xiaqui/overlay/BarObservation.kt)
- [Policy.kt](https://github.com/xiaqui/overlay/blob/badf1dfaff99dac3644997ac0b0a840f8b0308b9/app/src/main/java/com/xiaqui/overlay/Policy.kt)、Settings.kt、OverlayViews.kt、Manifest、build.gradle.kts
- [WORK_PROGRESS.md](https://github.com/xiaqui/overlay/blob/badf1dfaff99dac3644997ac0b0a840f8b0308b9/docs/WORK_PROGRESS.md)、ISSUE4_JA.md、TASK.md
- 参考APK: 本書2節のSHA256に固定した添付ファイル。逆コンパイルコードや素材はリポジトリへ追加しない。

### Android一次資料

- [S1: Window.setNavigationBarColor / contrast](https://developer.android.com/reference/android/view/Window#setNavigationBarColor(int))。現行ドキュメントにあるAPI35以降の変更は今回の6〜10へ適用しない。
- [S2: WindowManager.LayoutParams / TYPE_APPLICATION_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [S3: AOSP Android6 PhoneWindow（DecorViewのnav ColorView）](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-6.0.1_r1/core/java/com/android/internal/policy/PhoneWindow.java)
- [S4: AOSP Android10 DecorView](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/core/java/com/android/internal/policy/DecorView.java)
- [S5: AOSP Android10 DisplayPolicy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/services/core/java/com/android/server/wm/DisplayPolicy.java)、[SystemUI BarTransitions](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/packages/SystemUI/src/com/android/systemui/statusbar/phone/BarTransitions.java)
- [S6: AOSP Android6 PhoneWindowManager.windowTypeToLayerLw](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-6.0.1_r1/services/core/java/com/android/server/policy/PhoneWindowManager.java)
- [S7: AOSP Android7 PhoneWindowManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-7.0.0_r1/services/core/java/com/android/server/policy/PhoneWindowManager.java)
- [S8: AOSP Android8 WindowManagerPolicy.getWindowLayerFromTypeLw](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/view/WindowManagerPolicy.java)
- [S9: AOSP Android9 WindowManagerPolicy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/policy/WindowManagerPolicy.java)
- [S10: AOSP Android10 WindowManagerPolicy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/services/core/java/com/android/server/policy/WindowManagerPolicy.java)
- [S11: ActivityManager（task取得制限）](https://developer.android.com/reference/android/app/ActivityManager#getRunningTasks(int))、[UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)、[PackageManager.getResourcesForApplication](https://developer.android.com/reference/android/content/pm/PackageManager#getResourcesForApplication(java.lang.String))
- [S12: MediaProjection](https://developer.android.com/media/grow/media-projection)。API21からの画面取得方式を参照。新OS限定の同意/FGS規則を6〜10へ遡及して要求するものではない。

