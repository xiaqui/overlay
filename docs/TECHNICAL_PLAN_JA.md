# Overlay 技術方針・検証計画
更新: 2026-10-21

## 現行テンプレート確認済み事項
- app/build.gradle.kts: compileSdk=35, minSdk=26, targetSdk=35, namespace/applicationId=com.xiaqui.androidtemplate。着手時に変更。
- .github/workflows/build-android-apk.yml: gradle testDebugUnitTest assembleDebug、Artifact 3日保管。
- Actionsの署名Secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD。Gradle実行時環境: ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD。
- Codex環境想定: ANDROID_API_LEVEL=35、ANDROID_BUILD_TOOLS=35.0.0。minSdk=23と混同しない。
- この段階では署名Secretsの登録状態は未確認。鍵の値をIssue/ログ/ドキュメントに貼らない。

## 推奨設計
- Kotlin、Android標準API中心。小さな設定Activity、表示オーバーレイの管理Service、boot receiver、時計/電池イベント購読を分離。
- WindowManagerウィンドウは時計、電池、角丸の必要部分のみ。OSバージョンで TYPE_PHONE/TYPE_SYSTEM_ALERT/TYPE_APPLICATION_OVERLAY などの使用可否・階層を正確に切り分ける。非公開API、root、adb特権を前提にしない。
- SYSTEM_ALERT_WINDOWのユーザー同意を扱い、拒否・取り消し時に安全停止。画面全体のタッチ遮断を避ける。
- OS 8+ のバックグラウンド実行制限/Foreground Service/通知、端末再起動、電池最適化を考慮。
- 丸角のアプリ別判定はUsageStatsやAccessibilityServiceなどの必要性を調査。権限の目的と取得条件を説明し、不要な場合は導入しない。
- スクリーンショット支援はMediaProjectionの都度同意、ローカル解析、一時データ破棄。初期段階では手動合わせでMVP成立を先行。
- 標準ステータスバー上への完全上書きが通常権限で不可能な版では「全画面で代替表示」を優先し、成立しない機能を約束しない。

## テスト観点
| 項目 | ケース |
| --- | --- |
| 表示 | ステータスバーあり／非表示／透明／没入型、白背景／黒背景 |
| 入力 | 端タップ、スクロール、ゲーム操作、権限画面 |
| サイズ | 横画面、回転、ナビゲーションバー、画面密度差 |
| 常駐 | 消灯復帰、タスク切替、再起動、サービス再作成、電池最適化 |
| 個別設定 | 時計のみ、電池のみ、角丸のみ、各機能OFF |
| アプリ別角丸 | 対象の切替、上のみ／下のみ／両方／なし、判定権限拒否 |
| 配送 | package ID、minSdk 23、署名証明書、APK SHA256、Release再取得照合 |

Android 9/10端末がない場合はエミュレータを利用し、実機合格と書かない。APK参考実装から得た事実・推定・未検証は区別。

## リリース運用
変更はブランチ+PR、独立レビュー、P1/P2対応後にマージ。GitHub Actions Artifact容量上限ならupload-artifactエラーを理由に開発を停止せず、専用Release workflowを構築して直接添付する。署名済み成果物のSHA256と取得検証を記録し、実機インストール成功は別途確認。

## 未確定事項
- Android 6〜10のWindowManagerで標準表示と同じ場所に重ねられる正確な条件。
- 全画面状態の堅牢な検知法と制限。手動常時表示のフォールバック。
- アプリ別角丸の検出方法/権限、およびシステムバー領域の描画範囲。
- 許容する通知表示・常駐メモリの実測値。
