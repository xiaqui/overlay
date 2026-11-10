# Z3TC上下バー同色診断・設定メニュー再整理（2026-11-11 JST）

| 項目 | 結果 |
|---|---|
| 起点main | `1b3a1ed5af31bb20ed925ac3da4cf925a73cb961` |
| 実装PR / 最終head | [#32](https://github.com/xiaqui/overlay/pull/32) / `6c461c0385c7583a04938a0b1679ca59a727bdea` |
| merge / APK source | `4a0c231dc1021ad63761f54a6954b58c520bb986` |
| PR CI | [run 42883270082](https://github.com/xiaqui/overlay/actions/runs/42883270082) attempt 1 success |
| main署名build | [run 42885050031](https://github.com/xiaqui/overlay/actions/runs/42885050031) success / Artifact `13795343871` (`Overlay-signed`) |
| app | `com.xiaqui.overlay` / versionCode 9 / `0.9.0-diagnostics` / min23 / target25 |
| APK | 687172 bytes / SHA256 `a4a677a9de0066d7f5dda1ee4d7e0beb3bea8820d24855708e79c61862281dfd` |
| 署名 | SHA256 `dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2`（既存証明書と一致） |
| Drive | 既存Overlay固定ファイルIDを維持して上書き。再取得APKはcmp、容量、SHA256、署名がActions出力と一致。固定ID/URLはrepoに記録しない |

設定画面は「一般設定／ステータスバー／ナビゲーションバー／ラウンドコーナー／アプリ別設定」へ整理した。権限状態をボタンへ統合し、白→黒の確認テスト、時計/電池の配置と「なし」の統合、Navbar従属項目と角丸半径の常時表示、説明文の局所整理を実装した。既存の時計・電池調整値、角丸、Navbar、アプリ別設定と適用優先順位は変更していない。

上下バー診断は色候補を一度だけ解決して上/下へ渡し、期待ARGB、window type、要求/実座標・寸法、ready/draws、バー状態、非適用理由を詳細設定へ表示する。対象アプリの診断スナップショットはプロセス内メモリだけに保持し、設定画面を開いても直前の外部アプリ情報を確認できる。新Service、poll、権限、通信、永続ログは追加していない。Android 6/7の上側はTYPE_PHONE(2002)、下側は既存TYPE_SYSTEM_OVERLAY(2006)のままで、原因未確定の恒久切替は行わない。

自動試験はJVM・Lint・buildに加えAPI23/24/26/28/29の各jobで成功。API24は既知の全体試験終了ハングを避け、BatteryFinishTest＋SettingsUiTestに限定したためNavbar診断と既存full suiteは未実施。API23/26/28/29では、固定白が上下Specへ同一ARGBで到達し暗色化されないこと、外部前面情報が設定画面表示で失われないこと、両Layerのgeometry/type/draw、設定値保持、白→黒プレビュー、既存の画素・角丸・タッチ透過を検査した。独立レビューでP1（設定画面を開くと対象診断が自アプリで上書き、プレビュー試験陳腐化）とP2（attach失敗理由の消失、角丸OFF説明不足）を修正。GitHubレビューのP1/P2（プレビュー、対象スナップショット、時刻競合、外部task復元）も修正し、全スレッドを解決した。最終headとCI SHAを照合済み。

診断上は固定白の上下指定値が一致する。Android 6エミュレータで上2002/下2006と描画経路は確認できるが、`ready=true` / `draws=true` はXperiaのSystemUIより手前に見える証明ではない。Z3TCの実画素で上下が異なる根本原因は未確定で、成功扱いしない。

実機検収: Z3TC Android 6.0.1でChrome固定 `#FFFFFF` とNoRoot Firewallを各々表示し、アプリから離れる前の時刻を控えて詳細診断の対象pkg/取得時刻、上下ARGB、type、要求/実座標・寸法、ready/draws/reasonを実画素と突合する。OS時計・通知アイコン・ナビボタン、タッチ透過、縦横回転、バー表示/非表示、Recents、通常アプリ復帰、権限往復も確認する。Android 7〜10の更新インストール、特大文字、時計/電池の各配置と保存値維持も未検収。エミュレータ結果を実機合格扱いしない。

---

# 設定画面整理・アプリ別設定統合（2026-11-11 JST）

- 起点main `cf1dcbe6749dd5d8171a7d457caa33f9f3ee2845`、仕様PR #28を確認。mainのTASKが前工程のままだったため、#28の最新TASKと今回の明示依頼を採用。電池仕上げは完了済みで再実装しない。社長の実機外観調整完了報告をTODOへ反映。
- [実装PR #29](https://github.com/xiaqui/overlay/pull/29)、branch `work/settings-ui-20261111`。全体/上部情報/角丸/ナビバー/アプリ別設定の5パートへ整理。詳細診断は別ダイアログ、従属UIをOFFで隠す。電池リセットUIだけ削除し、調整値・既存保存キー・移行・時計/電池/角丸描画は変更しない。スクロールYを保持。アプリ別3入口を同じアプリ画面の3編集行へ統合、設定済みを先頭にし、設定済み非ランチャーアプリも保持。Usage Access未許可でも保存/編集可能・未適用理由を表示。
- 唯一のサービス挙動変更: Navbar着色ON時は旧navbar_autoの値によらず既存自動推定を使用。旧キーは消さず、固定色優先/明示「なし」/バー状態/Recents/キャッシュは維持。新Service/poll/権限/ライブラリなし。
- 最終head `ee5f4e4fda27a8c11e5c792c5216b0f87125fda2`、[CI 42856680233](https://github.com/xiaqui/overlay/actions/runs/42856680233) attempt2 success。JVM・Lint・debug/androidTest build成功。API23/26/28/29各17件、API24限定5件（計73件）成功。API24は既知の全体試験終了ハングを回避して電池/UIのみ。Android 7全体回帰、API23画面OFF/ONの従来保留は継承。
- CI初回API29は既存角丸reverse-landscape試験でバー状態更新後のView座標が不一致（1件）。同一headの当該job再実行で17件成功。製品描画を変更して回避していない。原因未確定の断続的不一致であり、テスト待機不足と製品側の非同期レイアウト競合は区別できていない。再試験成功は実機回転保証ではなく、実機確認待ち。前head `1f5e1e5c`の全5OSもsuccess。
- ローカルは差分空白/シェル構文/workflow YAML等の静的検査。SDK/Gradle環境の構築・APKビルドは行わずActionsを利用。
- 独立レビュー: 説明不足P2（全画面判定不能時の表示維持、RecentsのHome併停/不明時維持）を修正。GitHub P2の旧「固定色解除で無着色」試験期待値を、自動推定へ直接移行する待機と画素確認へ修正しthread解決。別担当が最終headを差分再レビュー、新たなP0/P1/P2なし。UIの保存値/表示条件/スクロール/一覧モデルは自動検証。実ダイアログ編集・固定色入力/キャンセル・権限画面往復の自動化不足P2は、保存キー/キャンセル無書込の静的確認と実機操作検収への保留という理由付きdispositionをPR本文に明記。全操作自動合格とは称さない。
- merge/source SHA `077cefaa711fe00abdd5e3bcc33e7f2685b5ccf1`。merge直前にhead/base/CI成功/解決済みthreadを照合。GETのmergeableはunknownのままだったがexpected_head_sha付きmergeをGitHubが成功受付した。
- 署名 [Actions 42859745107](https://github.com/xiaqui/overlay/actions/runs/42859745107) success。Artifact `13785460010`（Overlay-signed）1件、ZIP 676475 bytes / SHA256 `e752b63c2d7ccd46599cc244ecda7c63e2c9499acd6619605553f7b4ebb13a63`。最終mergeから一度だけ署名APKを生成。既存の正式Overlay専用Drive固定ファイルへAPK本体を上書き（2026-11-11 02:00 JST）、ID/親/配送URLを保持し共有変更なし。私有ID/URLはrepoへ記載しない。
- APK `Overlay.apk`: 682086 bytes（前版680106から+1980 bytes、約0.29%）、SHA256 `227750b76a1c55fa03edac083cffe00f585f6d1469cfd381e6fbbf5059fccbbd`。com.xiaqui.overlay / versionCode8 / 0.8.0-settings / min23 / target25 / 非debuggable / INTERNET権限なし。PROVENANCEとAPK内version-control-infoのsourceは上記merge SHAに一致。
- 署名: CI apksignerでv1/v2有効、既知SHAの0.2.0 APKと証明書一致。証明書SHA256 `dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2`。配送前に前版v7とも公開証明書を照合。秘密情報は取得/記録しない。
- Drive再取得: 同一ID、682086 bytes、上記SHA256、ArtifactのAPKとcmp一致。再取得APKのv1 CMS署名と署名済みManifest/14エントリdigestをローカル検証。v2・package/versionはCI検査済みバイナリとのbyte一致で照合。埋込source SHAも再確認。
- 実機未検収: 今回版の上書き更新後の既存調整値、特大文字/回転/スクロール、アプリ別編集と固定色キャンセル、権限往復、OEM固有の表示/常駐。詳細は[SETTINGS_UI_JA.md](SETTINGS_UI_JA.md)。エミュレータを実機検収済みとは扱わない。新規常駐処理なし、実機の消費電力/PSS差は未実測。

本記録は配送後の文書更新。APK sourceは上記merge SHAのままとし、文書更新では再ビルドしない。

---

# 電池表示の最終仕上げ（2026-11-10 JST）

起点main `2d17bb6d0890eca0ae559a7af5b7d55c89473b48`、仕様整理PR #25を確認して実装PR #26（`work/battery-final-20261110`）を作成。旧4工程は完了済みで再実行していない。前回#23後の外観はユーザーが概ねOKと受入した一方、今回追加する3方向・独立調整・柔らかい影の外観はまだ実機未検収。

- 実装: 旧battery_reversedを横右/横左へ互換解釈し縦上を追加。電池専用サイズ/比率/X/Y・初期値復帰。旧共有size/x/yは初回snapshot。時計の描画・位置を保護。外側ぼかし影と内側容量コントラストを分離。
- 最小窓: 従来条件の結合窓を保持し、独立調整時だけ最大2窓。時計・電池OFF時は既存どおり不要窓を削除。新Service/poll/権限/ライブラリなし。
- 自動試験: 幾何単体、設定移行/復帰、時計ピクセル不変、方向/サイズ/比率/fontScale/白黒/全残量/不明/充電の描画と窓座標回帰を追加。最終head `2343c9cab27c441785ab6410a7bcc5f948f448f4` の [CI 42648784393](https://github.com/xiaqui/overlay/actions/runs/42648784393) はsuccess。JVM・Lint・debug/androidTest build、API23/26/28/29各15件＋API24電池限定3件（計63件）成功。API24全体回帰とAPI23の画面OFF/ONは実機保留を継承。
- ローカル: diffチェック、shell構文、workflow YAML解析。SDK/Gradle環境は構築せず、実ビルドとAndroid試験はActionsを使用。
- レビュー: GitHub独立レビューP2（12時間時計の桁増加時に電池anchor未更新）を修正しthread解決。既存minute/time-change経路で再計算し追加pollなし。別担当が最終headを再確認、未解決P0/P1/P2なし（静的）。
- マージ: [実装PR #26](https://github.com/xiaqui/overlay/pull/26)、merge/source SHA `f8ee341f80a4c20ee73378b05cdec2f94c155f19`。merge直前にhead・base・CI・thread・mergeable=cleanを照合。仕様整理PR #25は実装用branchとして流用せず、関連仕様を専用文書へ反映。
- 配送: マージmainで唯一の署名 [Actions 42650378439](https://github.com/xiaqui/overlay/actions/runs/42650378439) がsuccess。Artifact `13688534793`（Overlay-signed）1件、ZIP 674409 bytes / SHA256 `2b4e7d19409140fe5c121d83f601bb17bf274fb3dae05646cd53358439cae768`。中間の配布用署名APKの生成・配送なし（debug/androidTest APKはCIで検証用に生成）。既存正式Overlay専用DriveファイルのID・親・共有状態を維持してAPK本体を上書き（2026-11-10 01:03 JST）。ID/私有URLはrepoへ掲載せず、最終報告で配送先を示す。
- APK: `Overlay.apk`、680106 bytes、SHA256 `8bdf4b4b60309f7e8778c0da3eb754af586aafafc8f0fccc55ee7a7d69eabe15`。com.xiaqui.overlay / versionCode 7 / 0.7.0-battery-finish / min23 / target25 / 非debuggable / INTERNET権限なし。旧配布675835 bytesから+4271 bytes（約0.63%）。
- 署名: Actionsのapksignerでv1/v2が有効、既知SHAの0.2.0 APKと署名鍵証明書一致。証明書SHA256 `dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2`。配送前のDrive旧APKとも同じ指紋をローカル照合。秘密値は取得/記録していない。
- 再取得: 同じDrive IDのAPKを再取得し、680106 bytes、上記SHA、Artifact APKとcmp一致。OpenSSLで再取得APKのv1 CMS署名を検証し、署名済みManifestと14エントリのSHA256を照合。v2はCI apksigner検証済みAPKとのbyte一致で同一性を保証。package/versionはCIのaapt検査済み同一バイナリ、内部version-control-infoおよびPROVENANCEのsource SHAもmerge SHAに一致。
- 常駐負荷: 新Service/poll/大画面Bitmapなし。API23の調整前/極値調整後PSSは24583/24987KiB、API26は31729/31581、API28は40909/41177、API29は44348/45019（いずれも当該テスト条件でstatus2窓、観測窓1.2秒）。CPUはノイズが大きいため回帰率を断定しない。比較前は負数位置で既に独立2窓であり既定1窓との比較ではない。実機の消費電力や長時間リークの保証ではない。
- 実機検収: [BATTERY_FINISH_JA.md](BATTERY_FINISH_JA.md)。実機の外観・長時間メモリ/電池消費・更新インストールは未検収。

本追記は配送後の文書整理であり、APK sourceは上記merge SHAのまま。文書更新を理由にAPKを再生成しない。

---

# 電池残量コントラスト修正・署名APK配送記録（2026-11-08 JST）

- PR #22 を最終head `ceeea40975525f925d043e977de067a7f323038b` から squash merge（main `2d8741947c2ea0ea8e386bf9e95893cd8dff0166`）。白背景で電池の空き容量を半透明暗色で描画し、20/50/80%・通常/反転の画素テストと左右配置PSS/CPU観測ログを追加した。Codex P2「OverlayPerfがlogcatから除外」を修正してthread解決。後続の検証用Kotlin修正・API24の時限実行もPRに含む。
- 最終PR CI: `42484210399`、head `ceeea409` でcheck・API23/26/28/29成功。API24は繰り返すエミュレータ/Gradle停止問題を理由に当該matrixから除外。除外前の実行 `42482981429` ではAPI24のinstrumentation 10/10、失敗・skipゼロを記録したが、終了時のADB/logcat処理が止まりrunはcancelled。したがってAPI24の**最終headの完全なCI成功**とXperia Z4 Tablet実機成功は主張しない。Android 7の製品対応は維持。
- mainへ一回限りの署名ビルドを起動するworkflow更新 `65a60f5ba3961c190bd6d4053a5ac4a3ddda1d58` を適用。Actions run `42484885333` はsuccess。Artifact `13619202042`（`Overlay-signed`）、ZIP 669845 bytes、SHA-256 `842b8d8ff06e011e14eee1e239f154cd033b347d755ca461cd5f5a2ea87e9aeb`。同梱 `Overlay.apk` は675678 bytes、SHA-256 `d907ec8fcd50fc1a323c7ce577b4990c37e11b54216acf054561570f60ccc3bd`、`com.xiaqui.overlay`、versionCode 6 / 0.6.0-display-controls、minSdk23、targetSdk25、v1/v2署名。署名証明書は既存配布版と一致することをCI検査。
- Google Driveの既存共用フォルダへ上記ZIPを新規配送し、再取得したZIPとGitHub Artifactのbyte-for-byte一致・SHA-256を確認。その後、Artifact ZIPから抽出・照合した `Overlay.apk` をGoogle Drive接続の `update_file` に実在ローカル絶対パスとして渡し、**既存Overlay固定配布ファイルIDを維持したままAPK本体を上書き更新した（2026-11-08 JST）**。更新後に同じIDから再取得したAPKは675678 bytes、SHA-256 `d907ec8fcd50fc1a323c7ce577b4990c37e11b54216acf054561570f60ccc3bd`、元ArtifactのAPKとbyte-for-byte一致。MIME `application/vnd.android.package-archive`、ファイル名 `Overlay.apk`。ZIPの新規保管とAPK固定ID更新は別工程。DriveのファイルID・private URL・認証情報は本repoに記録しない。
- 実機：白/黒背景、残量20/50/80%、端子反転、充電表示、Android6〜10更新インストール・長時間性能は未検収。CI PSS/CPUはエミュレータの参考値であり実機の電池消費を保証しない。

---

# 表示仕上げ4工程・連続作業記録（2026-11-06）

最新mainから工程ごとに別branch/PRを作り、各最終headでJVM/Lint/buildとAPI 23/24/26/28/29を実行してから順次マージした。中間mainの旧自動Release jobは休止し、署名APKは工程4マージ後の専用Artifact 1件だけとする。

| 工程 | PR / 最終head | CI / レビュー | merge |
|---|---|---|---|
| 1. 時計・電池の影 | #18 / `045d783a8ecd63039cd0a60438fbf0f39b7af4d1` | run 42173448002 success。P2の実画素不足を修正、最終P0/P1/P2なし | `b21d695b685905ee119bcb760f32c8f606615f63` |
| 2. 時計の左右配置 | #19 / `7881318959f11aeb5852de42f01a9cb9bef55ecd` | run 42175179091 success。P2のlayout前座標を修正、最終P0/P1/P2なし | `c7f0223f35d41d93fe3f80d85312046a9c5caeea` |
| 3. 電池方向 | #20 / `f2b214e507189243f161ee2578be08a52f3be795` | run 42198103784 success。P2の充電bolt検証を修正、最終P0/P1/P2なし | `4c17cd4f1b932daff8512d3191d24ccd1b51262e` |
| 4. 全画面例外の部分表示 | #21 / `157cd33c85ade32feac73ef7b205f33f5e737d56` | 旧4工程の完了済み履歴。JVM/API別試験・独立レビューは#21で照合 | `da6f44a7b93e990d44d82591d83a7c5f91b568a1` |

工程1は影を0.55dp blur、右0.55dp、下0.65dp、黒alpha B8へ変更。工程2は既定右を維持し、左選択時だけ時計左・電池右へ分離。工程3はOS決め打ちを避け、通常／反転を手動選択し、端子・残量・充電boltを一貫して鏡像化。工程4は旧display値0/1/2を維持し、3=時計のみ、4=電池のみを追加する。全体OFFと常時表示の意味を優先し、不要なStatus窓はWindowManagerから除去する。

電池残量の白背景視認性は、今回の着手前の実装状態で社長がXperia実機確認済みと明示したため再実装せずTODOだけ整合した。新しい影、左右配置、反転方向、部分表示はXperia/Rakuten各実機で未検収。手順はDEVICE_TEST_JA.md。

---

# 画面表示仕上げ PR作業記録（2026-10-23）

基準は安定版候補 `19f5ea73c933cf150e864d6d4a934bddeb1560bc`、作業起点はその上の文書更新だけを含む最新main `c4c0108ca23a874df971c1b1ed33210f72e58a44`。新ブランチで時計・電池の負数位置、横電池、影、fontScale、アプリ別着色「なし」の5点だけを変更する。mainへのマージ・署名APK Releaseはメンテナーの最終確認後に実施し、このPR作業では行わない。

| 項目 | 現在値 |
|---|---|
| applicationId / version | com.xiaqui.overlay / versionCode 5 / 0.5.0-display-finish |
| PR / ブランチ | https://github.com/xiaqui/overlay/pull/12 / `work/display-finish-20261023` |
| コード検証SHA / CI | `7f2c33ed290dbee89a50ea1ed4a3d43f78006712` / https://github.com/xiaqui/overlay/actions/runs/39909393091 — success |
| 自動試験 | JVM・Lint・debug/androidTest build成功。API23/24/26/28/29で各7 instrumentation成功 |
| 独立レビュー | P2 1件（「なし」よりRecentsを先に評価すると透明下帯が残る）を修正。再レビューで追加P1/P2なし（静的） |
| 実機 | 添付Z3TC画像4枚は外観基準として確認。新版APKのXperia 5機種実表示は未検証 |
| 配送 | 対象外。既存Releaseと署名情報を変更しない |

初回run 39908662350はAPI24で、細い半透明影をRGB<120だけで数える試験閾値が失敗した。アプリの影パラメータは変えず、白背景との差（RGB<245）を数える試験へ訂正し、最終コードSHAで全5 OSを再実行して成功した。失敗runを合格扱いしない。設計、保存互換、実機手順は [DISPLAY_FINISH_JA.md](DISPLAY_FINISH_JA.md)。

---

# Navbar技術検証版・署名配布記録（2026-10-21）

初期OFFのアプリ別固定色／任意のテーマ・アイコン推定色を追加。既存Service・UsageStats・pollを再利用し、
着色矩形→黒い下隅を同じCanvasに描画。元の時計・電池・全画面判定を維持し、OFF/対象外/未描画は旧角丸へ復帰。
正式統合の成功とは扱わず、優先5実機の検収は未実施。方式・実機手順・判断基準は [NAVBAR_PROTOTYPE_JA.md](NAVBAR_PROTOTYPE_JA.md)。

| 項目 | 結果 |
|---|---|
| 起点main | 22e029c512e0721503401b5538a219c1fcf05618 |
| 実装PR #7 / merge | https://github.com/xiaqui/overlay/pull/7 / 44f639b4b1609e99b01d2cb9f70cdea7338a7d3d |
| テスト限定PR #8 / merge | https://github.com/xiaqui/overlay/pull/8 / 84dfbea72fce62ad0a5c6d6bde21e5ef1e5f33cc |
| #8最終head / CI | 71a03ee02865548bcffefb608c210a6cd485d749 / https://github.com/xiaqui/overlay/actions/runs/39566829656 — success |
| APK source SHA | 84dfbea72fce62ad0a5c6d6bde21e5ef1e5f33cc（文書追記SHAと区別） |
| 最終main CI / 配送 | https://github.com/xiaqui/overlay/actions/runs/39605030431 — success / attempt 2 |
| テスト | JVM15、Lint、debug/androidTest/release build成功。API23/24/26/28/29各6 instrumentation成功 |
| Release | https://github.com/xiaqui/overlay/releases/tag/overlay-navbar-test-84dfbea7 |
| APK | https://github.com/xiaqui/overlay/releases/download/overlay-navbar-test-84dfbea7/Overlay-Navbar-Test.apk |
| 公開状態 / 日時 | prerelease=true、draft=false / 2026-10-21 22:21:39 JST |
| Release / APK asset ID | 419586098 / 662581909 |
| 容量 / 0.2.0との差 | 661372 bytes（645.87 KiB）/ +15953 bytes（+2.47%）、新規ライブラリなし |
| APK SHA256 | 069d4b365788150bf9d365f772921dc8fa6235d5072d25777dff079b1df6ac05 |
| app | com.xiaqui.overlay / versionCode 3 / 0.3.0-navbar-test / min23 / target25 |
| 署名job | 119245545762 — success |
| 公開証明書SHA256 | dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2 |
| 署名・配送検証 | 既存keystore・既知hashの0.2.0 APKのSigner証明書と一致。添付後再取得cmp/SHA256/apksigner verify成功 |
| 公開後Source照合 | tag/target/本文がsource SHAに一致。SOURCE_SHA.txtの期待digest 15dc5819f0955102c43f9a0d95eaa91ec7a1ca537c089b07463f86a90a756936 とasset digest一致 |
| 公開後checksum照合 | SHA256SUMSの期待digest 807605401b8930fb12af98463f385762670d733aed157899f7ecff5490375a5d とasset digest一致 |
| 既存0.2.0 | Release本文/target/更新日時、全asset ID/容量/digest不変。上書き配布なし |

独立レビューでP1（不可視rootのままSurfaceなし・下隅所有移行が早い）、P2（Usage再許可cursor、
遅延イベント取りこぼし対策）を修正確認。テスト限定PR #8も独立レビュー済み、追加P1/P2なし。
初回2006/異種別/flags比較の失敗は、後のNO_SURFACE診断によりレイヤー不可能と断定しないよう訂正した。
最終main attempt 1はAPI24旧Smokeの10秒fullscreen画素待ちが失敗し、他4OSとNavbar機能試験は成功。
同一SHAの失敗jobを一度再実行したattempt 2ではAPI24全6件成功。初回失敗を消さず、安定成功とは扱わない。
製品Navbarの実画素成功と、テスト用Activityのfocus/バー遷移の未確定な一時性を区別する。

公開後にWorkでRelease metadata/hash/sourceを照合し、バイナリ再取得・暗号署名はActionsで実測した。
秘密鍵・パスワードは表示せず、署名材料はjob終了時に削除。Artifactアップロードは不使用。
実機未検証: XZ1 Compact最優先、Z3TC/Z4T/Mini/Handの可視性・更新インストール・ボタン色コントラスト・
IME/OEM/transient動作・長時間CPU/電池。エミュレータ成功をXperia成功と同一視しない。
本体コードの作業は完了し、正式統合は実機検収後に判断する。以下は過去の0.2.0/初版記録。

---

# Issue #4 修正・配布記録（2026-10-21）
正本: https://github.com/xiaqui/overlay/issues/4#issuecomment-6298032616 。初版のXperia基本機能は良好とのユーザー報告を受け、既存実装を維持して2点を局所修正。修正版の実機検収は未実施。以下の初版記録にある「全実機未検証」は初版配布時点の履歴。

## 実装とソース
- 全画面のみ: 旧Y座標判定を廃止し、透明な縦横1px観測窓のcurrent Insetsと実フレーム差からstatus/navを測定。通常/透明バー時は時計・電池を隠し、status非表示時は表示。常時表示は維持。unknown時は表示維持、全画面モード用のアプリ別表示/非表示例外を追加。
- アプリ別角丸: 「なし / 通常 / 上だけ内側 / 下だけ内側 / 上下とも内側」の5択。未設定=通常、非表示バーoffset0。全体上下ON/OFF・半径を維持。実View位置を補正し、下端基準の二重移動と逆横左navの親原点移動を解消。
- 設定移行: 旧app:*の0→なし、1/2/3→通常をcorner2:*へ一度だけ原子的に移行。旧キーと全体設定を残し、既存新キーを上書きしない。

| 項目 | 値 |
|---|---|
| 起点main | b1d7e1800fb95ebfe75749b4e029912672f28a47 |
| 実装PR | https://github.com/xiaqui/overlay/pull/5 — マージ済み |
| 最終PR head | 73d85d61a54d6813f6a8f8298a77fc3101bc306f |
| #5 merge / APK source SHA | 2f942bbaebadc7bee7666c0c9beb9ef1bc88280c |
| PR CI | https://github.com/xiaqui/overlay/actions/runs/39481565220 — success |
| main配送CI | https://github.com/xiaqui/overlay/actions/runs/39482559225 — success |
| app | com.xiaqui.overlay / versionName 0.2.0 / versionCode 2 |
| ビルド環境 | Workで編集、ActionsのJava17 / Gradle8.7 / compile35 / min23 / target25。Codex専用セットアップ不使用 |

## 自動試験・独立レビュー
PR最終CIとmain再試験はJVM11件、assembleDebugAndroidTest、lintDebug、assembleDebug成功。ソフトナビバー付きNexus 4のAPI23/24/26/28/29で各4件、計20件のinstrumentationがPR・main双方で成功。設定移行、通常/透明/没入型/statusのみ非表示、別アプリ(Settings)→新しい全画面Activity、縦/横/逆横、5モード、上下内側の実画素、実View座標、アプリ別例外、常時表示、白黒画面、時計・電池、タッチ透過、停止、2038互換方式を検証。既存ActivityへのBACK復帰を確認したとは扱わない。API23/24は逆横でも右nav、26以降は左navの実測を必須にした。

試験途中の失敗も追跡した。hardware mainkeysのdefault AVDではnav=0が正しいため、ソフトnav付き構成へ変更。ナビ付き実画素試験で下端の二重移動を検出して修正した。ActivityMonitorの古いonResume参照をstartActivitySyncへ変更し、実ServiceRecord消失待ちで試験を分離。OS初回の没入型案内はCI環境だけで確認済みに設定し、実際のタップ後Activity終了assertは維持した。失敗runを合格扱いせず、最終SHAで全OSを再実行した。

独立担当とGitHub自動レビューを実施。P1（navフレームをInsetsだけで見失う）、P2（側面cutout誤認、逆横左navの親原点）を修正。最終差分の重点再レビューで追加P1/P2なし。GitHubのP2スレッドは返信・解決済み。詳細はREVIEW_JA.md。

## APK・署名・配布
| 項目 | 実測値・検査 |
|---|---|
| Release URL | https://github.com/xiaqui/overlay/releases/tag/overlay-0.2.0-2f942bba |
| tag / Release ID | overlay-0.2.0-2f942bba / 418788813 |
| 状態 | 公開prerelease、draft=false（修正分の実機検収前） |
| APK URL | https://github.com/xiaqui/overlay/releases/download/overlay-0.2.0-2f942bba/Overlay.apk |
| APK asset ID / 容量 | 660271200 / 645419 bytes（630.29 KiB） |
| APK SHA256 | a5fbf73e08e5e985a6b11c12fc81078d7f52f134cd3633b93c9a3892b472fe1b |
| Release job | 118838378222 — success |
| releaseビルド | testDebugUnitTest / lintDebug / assembleRelease — success |
| パッケージ検査 | aaptでcom.xiaqui.overlay / min23 / target25 / 非debuggable / INTERNET権限なしを照合 |
| 署名検査 | apksigner verify成功。既存keystore公開証明書と一致。初版APKを既知SHA256 d53d2c9d1d9d0f0780410ace4e2353d5921d990d8908c4d630d8ec3326348ce1で確認し、そのSigner証明書とも一致 |
| 配送再取得 | Actionsでdraft添付APKを認証付き再取得し、cmp / sha256sum -c / apksigner verify成功後に公開 |
| Source照合 | Actions head SHA、Release target、tagのcommit、Release本文、SOURCE_SHA.txtが上記#5 merge SHAと一致 |
| 添付資料 | APK-VERIFICATION.txt / SHA256SUMS / SOURCE_SHA.txt |
| Artifact | アップロード不使用。Releaseへ直接添付 |

WorkでもRelease metadataを独立照合した。APK asset digestは上記SHA256と一致。SOURCE_SHA.txt（merge SHA＋改行、41 bytes）の期待digestは53fc5d1bb1c780f995a89d0c154754b0a599350e27a79673f34dd1fb8f932ddcでasset digestと一致。SHA256SUMS（上記APK hash＋2空白＋Overlay.apk＋改行、78 bytes）の期待digest d34f33237f8565f44db61959a97a9e360fe596eefa6dbb3b587e059ef59973e1も一致した。バイナリ再取得・署名の実測はActionsの同一Release jobで行い、ログの初版APK/新APKのchecksum OKと成功を確認した。秘密値は記録せず署名材料はjob終了時に削除。

本記録の追記は文書のみ。配布APKのソースは#5 merge SHAに固定し、後続文書マージSHAとは区別する。

## 実機未検証・動作の限界
修正版のXperia、Android9/10各実機、OEM固有バー、3ボタン/2ボタン/ジェスチャー、IME、transientバー、切り欠き、長時間常駐/消費電力、更新インストールの実機受入は未実施。手順はDEVICE_TEST_JA.md。

Insets観測は他アプリflagsの直接取得APIではない。OEMや一時表示バーが反映されない場合がある。上下左右いずれかにcutoutがあるとunknownとして表示維持＋offset0へフォールバックし、必要なアプリへ例外を設定する。可視システムバーが2006/2038窓の手前になるOSでは「通常」の角丸はバーに隠れることがある。これらを自動判定完全対応とは扱わない。時計/電池の描画・影・タッチ透過・常駐処理は既存実装を保持し、自動回帰試験を通した。

---

# Overlay 作業進捗・初版配布記録
更新: 2026-10-21（日本時間）

実装・テスト・独立レビュー・P1/P2修正・PRマージ・署名APKのGitHub Releases配布まで完了。正式な実機受入は保留。自動画像推定は仕様の許容に従い保留し、M3は手動調整で成立させた。

## コミットとビルド
| 項目 | 結果 |
|---|---|
| リポジトリ | xiaqui/overlay |
| 仕様 PR #1 | マージ済み。main SHA 988e6d0170df81a824e1dcc52d5d95a29449a3d9 |
| 実装 PR #2 | マージ済み。最終head 46eaea39041c609cf662a9e6521763ad2717b962 |
| APK source / #2 merge SHA | b2ed53a80db5625097321144274adf7d47b319a0 |
| PR最終CI | https://github.com/xiaqui/overlay/actions/runs/39454973748 — success |
| main配布CI | https://github.com/xiaqui/overlay/actions/runs/39456054288 — success |
| Release job ID | 118746603808 |
| ビルド | Java17 / Gradle8.7 / compileSdk35 / minSdk23 / targetSdk25 |
| app | com.xiaqui.overlay / versionName 0.1.0 / versionCode 1 / 非debuggable release |
| JVM / Lint / build | testDebugUnitTest（4テスト）・lintDebug・assembleDebug成功。署名jobでもtest/lint/assembleRelease成功 |
| OS別実行試験 | API23 / 24 / 26 / 28 / 29の全5エミュレータで成功（各1件の複合試験） |

## APK・署名・配布
| 項目 | 値 |
|---|---|
| Release URL | https://github.com/xiaqui/overlay/releases/tag/overlay-0.1.0-b2ed53a8 |
| tag / Release ID | overlay-0.1.0-b2ed53a8 / 418619570 |
| 状態 | 公開prerelease（draft=false、実機受入前の初版） |
| APK | https://github.com/xiaqui/overlay/releases/download/overlay-0.1.0-b2ed53a8/Overlay.apk |
| APK asset ID / bytes | 659848355 / 637265 |
| APK SHA256 | d53d2c9d1d9d0f0780410ace4e2353d5921d990d8908c4d630d8ec3326348ce1 |
| 署名照合 | 既存KEYSTORE_* Secretsのkeystore公開証明書SHA256とapksignerのSigner #1証明書SHA256が一致 |
| パッケージ検査 | package/minSdk23/targetSdk25/非debuggable/INTERNET権限なしをaaptで照合 |
| 再取得検査 | 同一jobでdraftへ直接添付後、認証付きで再取得。cmp・sha256sum -c・apksigner verify成功後にprerelease公開 |
| 添付検証資料 | SHA256SUMS / SOURCE_SHA.txt / APK-VERIFICATION.txt |
| Artifact | 使用せず。容量制限を回避 |
| Drive | 今回の依頼対象外、未配送 |

Release本文のSHA256、GitHub asset digest、CIの再取得検証結果が一致。APKのローカル再ダウンロードはこの接続では実施していない。バイナリ再取得・署名検証はActionsの同一Release jobで実測した。秘密値は記録せず、署名鍵はjob終了時に削除。

## M0〜M4
- M0: 添付APK3本のManifest/DEXをAndroguard 4.1.4で静的解析。浮遊時計はtarget指定なし・タッチ透過時TYPE_SYSTEM_OVERLAY(2006)、タッチ有効時2003。AOSPのtarget<26互換許可分岐を確認。min23/target25で独自実装し、旧2006優先・26以降で拒否時2038へフォールバック。根拠と参考APK SHAは M0_APK_RESEARCH_JA.md。
- M1: Xperia風の右上時計・縦型電池、薄い黒影、透明最小矩形・タッチ透過。分境界・時刻変更・電池イベント更新、消灯時タイマー停止、起動停止・通知・再起動復元・権限エラー停止。
- M2: 黒角丸を4個の小窓で描画。半径/上下/アプリ別のなし・上・下・両方・共通設定。前面判定は任意のUsage Access、未許可時は共通設定。AccessibilityService不使用。
- M3: 提供スクリーンショットの右上時計・縦型電池を参照し、位置/サイズ/色の手動調整、白黒の全画面確認を実装。自動推定/MediaProjectionは未実装・保留。画面を収集・送信しない。
- M4: JVM4テスト、Lint、debug/releaseビルド、全5OSの実WindowManager試験、独立レビューと修正、署名Release配布完了。

## レビュー
独立担当の静的レビュー、GitHub Codexレビューを実施。P1（Release再試行不可）1件、P2（権限許可復帰、poll例外、SDKツール明示、試験偽陽性、trap別シェル）5件を修正し、差分の重点再レビューで追加P1/P2なし。GitHubのP1スレッドは返信・解決済み。詳細は REVIEW_JA.md。

CI初回の廃止済みtoolsパッケージ指定と、API29仮想ディスク作成時の容量不足も修正。起動前FATALをアプリ不具合や試験合格と混同せず、容量確保後に全OSを再実行して成功確認した。旧runは取消済み。文書だけの変更ではAPKを再ビルドしない。

## 検証済み範囲と未検証
エミュレータで確認済み: 2006ウィンドウ作成、26以降の2038作成、白黒の没入型全画面で背景・四隅、黒背景で右上表示の明色ピクセル、実際にシステムバーが隠れたこと、時計の上のタップが背面Activityへ届き終了すること、Service停止。

未実施・保留:
- Xperia Z3TC(Android6)/Z4T(7)/XZ1 Compact(8)を含む全実機でのインストール・外観一致・標準バーとの二重化/重なり・タッチ・OEM省電力。
- 実機/エミュレータでの長時間常駐、再起動復元、消灯復帰、権限取消との競合、充電/時刻変更、個別ON/OFF、アプリ別切替の受入。これらは実装・静的確認済みだが、今回の自動試験の範囲外。
- 画面回転、切り欠き、ナビバー、複数ウィンドウ、常駐CPU/メモリの実測。
- 全画面のみ表示は試験機能。透明バー/OEMで誤判定し得るため初期値は常時表示。2038はシステムバーより背面。旧2006も実際のバーとの重なりは機種依存。
- 自動スクリーンショット解析は保留。手動微調整が現在の採用仕様。
- Android11以降は保証外。ストア配布は対象外。

次の検収: ReleaseのOverlay.apkを実機へインストールし「重ねて表示」許可→「表示を開始」→白/黒の全画面確認。詳細は DEVICE_TEST_JA.md。実機未合格を理由にAPK配布を止めず、今回の指示どおり可能な工程を完了した。

この記録を追加する文書コミットと、APKを生成したsource SHAは異なる。上のAPK source SHAが配布バイナリの正本。





