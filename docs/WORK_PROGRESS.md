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
