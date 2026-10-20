# Overlay
Android 6〜10 / Xperia向けの軽量時計・電池・黒い角丸。通信・広告・分析SDKなし。

初回は「重ねて表示」を許可し「表示を開始」。画面右上に時計と縦型電池を表示します。サイズ・上端・右端距離、白/灰色を調整できます。黒い角丸は上下独立、アプリ別に上/下/両方/なし/共通設定を選べます。アプリ別設定だけ使用状況アクセスが必要です。消灯中はタイマーを停止します。

全画面のみ表示は試験機能。透明バー等で誤判定する場合は常時表示を使用してください。標準は旧ウィンドウ方式（targetSdk 25）、拒否時はAPI26以降で2038にフォールバック。互換方式はステータスバー・ナビバーより背面になります。root・adb権限・非公開APIは使いません。Android11以降およびストア配布は対象外です。

設定画面の白/黒の全画面確認で、影とタッチ透過を検収してください。実機未検証事項は docs/WORK_PROGRESS.md と docs/DEVICE_TEST_JA.md を参照。

ビルド: Java17 / Gradle8.7 / Android SDK35。`gradle testDebugUnitTest lintDebug assembleDebug`。署名は既存KEYSTORE_* SecretsをActionsに渡し、mainでテスト・署名照合・再ダウンロード検証後にGitHub prereleaseへ直接配布。Artifactアップロードは使いません。秘密鍵・参考APK・ユーザースクリーンショットはリポジトリに含めません。
