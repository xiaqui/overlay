# Overlay 技術方針・検証計画
更新: 2026-10-21

Issue #4の最新コメント6298032616を優先。現在の全画面/角丸仕様・実装・制限はISSUE4_JA.md、検証結果はWORK_PROGRESS.md。以下の初版計画は履歴として残し、古い仮説やCodex環境の想定を現行の前提にしない。Workで編集し、GitHub ActionsのJava17/Gradle8.7/SDK35/Build Tools35.0.0で検証・署名配送する。

## 初版着手前のテンプレート記録（履歴）
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
| アプリ別角丸 | 指定5択、未設定=通常、上下/左右navの内側位置、旧値移行、判定権限拒否 |
| 配送 | package ID、minSdk 23、署名証明書、APK SHA256、Release再取得照合 |

Android 9/10端末がない場合はエミュレータを利用し、実機合格と書かない。APK参考実装から得た事実・推定・未検証は区別。

## リリース運用
変更はブランチ+PR、独立レビュー、P1/P2対応後にマージ。GitHub Actions Artifact容量上限ならupload-artifactエラーを理由に開発を停止せず、専用Release workflowを構築して直接添付する。署名済み成果物のSHA256と取得検証を記録し、実機インストール成功は別途確認。

## 未確定事項
- Android 6〜10のWindowManagerで標準表示と同じ場所に重ねられる正確な条件。
- 全画面状態の堅牢な検知法と制限。手動常時表示のフォールバック。
- アプリ別角丸の検出方法/権限、およびシステムバー領域の描画範囲。
- 許容する通知表示・常駐メモリの実測値。


## 実装時の決定（2026-10-21）
- min23 / target25 / compile35。2006を優先、API26以降は拒否時2038。根拠はM0_APK_RESEARCH_JA.md。
- M3は手動位置・サイズ・色と白/黒全画面確認を採用。自動解析は保留。
- アプリ別判定はUsageStats。AccessibilityServiceは使わない。
- APKは非debuggable release、署名照合と再取得検証後GitHub prerelease公開。Artifactなし。
