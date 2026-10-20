# Overlay作業進捗
更新: 2026-10-21

- PR #1: 仕様3本をレビュー確認後マージ。main=988e6d0170df81a824e1dcc52d5d95a29449a3d9。
- RIN.md読了。AGENTS.mdはmainに存在しない。テンプレート全ファイルとActionsを確認。
- M0: 参考APK3本のManifest/DEX調査完了。docs/M0_APK_RESEARCH_JA.md。浮遊時計は旧target + TYPE_SYSTEM_OVERLAY。実機再現は未実施。
- M1: 独自Canvas時計・縦型電池、黒影、最小透明窓、タッチ透過、イベント更新、消灯停止、通知停止、再起動復元、権限エラー停止を実装。
- M2: 小窓4個の黒角丸、上下/半径、アプリ別上/下/なし/共通、Usage Access拒否時フォールバックを実装。
- M3: 仕様が許容する手動微調整と白/黒の全画面確認を採用。自動画像推定は保留、キャプチャ収集なし。
- M4: JVM4テストとCI test/lint/buildを準備。独立レビュー中。実機/エミュレータ未実施。
- 署名: 既存KEYSTORE_*をRelease jobにのみ注入、APK証明書をkeystore公開証明書SHAと照合。Artifactを使用せずdraft Release→再取得バイト/SHA/署名照合→prerelease公開。
- 完成ソースSHA / CI run / レビュー結果 / APK SHA256 / Release URL: CI・レビュー・配布後に追記予定。現時点で成功を宣言しない。
- 未検証: docs/DEVICE_TEST_JA.mdの全実機項目、機種別2006階層、バー重複、全画面判定、色/配置、常駐メモリ・CPU。
