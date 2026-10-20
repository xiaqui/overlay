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
