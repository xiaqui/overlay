# Overlay 開発引き継ぎ（2026-10-30）

リポジトリ: `xiaqui/overlay`  
対象: Android 6〜10（API 23/24/26/28/29）、Xperia系を含む旧端末向け軽量オーバーレイ  
本書は**今回の作業時点の状態と次にすべきこと**を短く記録する。過去の詳細は `docs/WORK_PROGRESS.md`、画面修正の仕様と実機手順は `docs/DISPLAY_FINISH_JA.md`、既存課題は `TASK.md` を参照。

## 1. 現在の基準 / 成果

- **最新main**: `e20f471ca960ae23138869170d499bd974f3333f`。PR #12「feat: finish clock, battery and per-app tint display」をマージ済み。
  - PR: https://github.com/xiaqui/overlay/pull/12
  - 実装head: `d056d8ddb60cdcf6abb7b508dbe1fa9c18e37bc3`
  - PR最終CI: https://github.com/xiaqui/overlay/actions/runs/39910277859 — **success**。check / API23,24,26,28,29 emulatorすべてsuccess。PR上のrelease jobはskipped（設計どおり）。
  - 独立レビューではアプリ別「なし」に対するRecents判定優先順のP2を修正し、再レビューで追加P1/P2なしとの報告。実機の最終画素一致は別途要検収。
- **mainの最新版APKはまだ未配布**。ユーザーの指示によりGitHub Actionsの利用上限を優先し、PR #12のマージコミットに `[skip ci]` を付けて署名ビルド・Release実行を見送った。**「mainに入った」≠「そのAPKが配布された」**。
- **最後に公開確認できた署名APK**: `overlay-statusbar-test-19f5ea73`（versionCode 4, 0.4.0-statusbar-test）。旧版は保持し上書きしない。
  - Release: https://github.com/xiaqui/overlay/releases/tag/overlay-statusbar-test-19f5ea73
  - APK: https://github.com/xiaqui/overlay/releases/download/overlay-statusbar-test-19f5ea73/Overlay-Statusbar-Test.apk
  - APKサイズ 670,476 bytes。source SHA `19f5ea73c933cf150e864d6d4a934bddeb1560bc`。
- **mainの未配布版のバージョン**: `versionCode=5`, `versionName=0.5.0-display-finish`（`app/build.gradle.kts`で確認）。署名ビルドと実機更新インストールは未検収。
- 既存の色解決はアプリ別固定色 → colorPrimary → アイコン代表色 → 無着色。 `colorPrimaryDark` を優先させて両バーが黒くなる問題はPR #11で修正済み。

## 2. PR #12で完成した5項目（未配布）

1. 時計・電池のX/Yオフセットに**負数**を許可。`-64..100dp` に拡張し、従来設定値/初期値を保持。
2. 電池を**横長・右端子**のXperia風に変更。残量・充電表示を維持。
3. 時計と電池の**黒い影**を共通パラメータで強化。白背景で読めることを重視。
4. OSの**特大文字**に対応。基準sp×fontScaleを一度だけ適用し、電池も文字に追従。
5. アプリ別バー着色に**「なし」**を追加（自動／固定色／なし）。「なし」は透明色塗りではなく、上下の着色窓を表示せずOS本来のバーを保つ。旧固定色設定との互換性維持。

コードと詳細なテスト手順: `docs/DISPLAY_FINISH_JA.md`。新たなService、定期poll、権限、ネットワーク、外部ライブラリは追加しない方針を守った。

## 3. 実機検収（ユーザー報告）

現行**配布済み旧版**の基本動作について以下を報告済み。**PR #12の改良版APKは未配布のため、これらをそのまま新版の実機合格とはみなさない**。

| 実機 | OS | 報告内容 |
| --- | --- | --- |
| Xperia Z3 Tablet Compact (Z3TC) | Android 6 | 基本動作ほぼ問題なし。旧版では電池アイコンが縦向き、OS特大文字への未対応、影と位置調整に改善要望。 |
| Xperia Z4 Tablet (Z4T) | Android 7 | 基本動作確認済み。 |
| Xperia XZ1 Compact (XZ1C) | Android 8 | 基本動作、Navbarの固定色・自動着色と角丸の共存が良好。 |
| Rakuten Mini | Android 9 | 基本動作確認済み。**上下バーの同色着色のみ不完全**（軽微、原因未特定）。純正時計は左。 |
| Rakuten Hand | Android 10 | 通常・全画面で基本動作確認済み、**端末再起動後のOverlay自動起動も実機合格**。純正時計は左、電池は右。 |

- Android 6〜10 **5機種で旧版の基本動作が確認された**。ただし各端末で全項目・長時間睡眠・電池消費を一律に検収したわけではない。
- Z3TCの参考画像4枚（元祖浮遊時計12:46、Xperia純正ステータスバー13:00、自作Overlay13:09と拡大後13:10）は**チャット添付**でありリポジトリに同梱されていない可能性がある。勝手に公開リポジトリに画像や識別情報をアップロードしない。
- XZ1CなどXperiaの従来配置（時計右）を壊さず、Android 9/10の「純正時計左」を参考にした配置選択を次期候補とする。

## 4. 開いているPR / 未着手の課題

- **PR #13**: https://github.com/xiaqui/overlay/pull/13 — Android 10・Rakuten Handの**純正時計は左**という実機知見と、Overlay時計の左右配置のTODOを`TASK.md`に追加するドキュメントPR。現時点で**open、未マージ**。旧PR #12コメントの「Android 10の時計は右」という記載は訂正対象であり、ソース・仕様の根拠にしない。
- 時計の**左／右配置を端末に合わせて選べる機能**は未実装。Androidのバージョンだけで決め打ちせず、手動で選択・元へ戻せるようにする。PR #13を確認してから別タスクに着手する。
- **Rakuten Mini(Android 9)の上下バー同色化不良**: 軽微な表示課題。3ボタン/ジェスチャーナビ、OS/OEMのWindowManager合成順、上下バーの検出を実機で切り分ける。原因をAndroid 9だけと決めつけない。
- 後続の構想（今回は未実装）: 上端から上スワイプ→先頭、下端から下スワイプ→末尾の**エッジジェスチャー**、全画面時左上の**戻るボタン**。Accessibility権限とタッチ透過との競合を調査してから、別フェーズで扱う。
- 安定版1.0.0の確定は、**改良版APKの署名配布・既存版からの更新・5端末の実機検収後**に判断する。現時点では1.0.0を名乗らない。

## 5. 次に実行する手順（担当引き継ぎ）

1. **GitHub Actions利用枠の回復後**、main `e20f471c...` の最新版を**署名してAPKをリリース**する。既存のWorkflow `.github/workflows/build-android-apk.yml` を確認し、必要なら保留ビルド用の安全な手順を追加する。既存の `versionCode 4`・過去の署名APKを上書きしない。
   - **重要**: 現在のCI/配布Workflowはmain pushで走る設計。単に失敗runを再実行しただけで必ず「PR #12の最新main」を配布できるとは限らない。配布対象ソースSHA・versionCode 5・Releaseタグ・SOURCE_SHA.txt・SHA256SUMS・署名証明書を実際に照合する。
   - Actions上限中に無駄なCI、Artifacts uploadを行わない。Workやローカルビルドに切り替える場合もユーザー承認・署名材の安全な取り扱いが前提。
2. **倫**が最終コード・PR/CI・Releaseのsource SHA・APK署名とダウンロードを確認する。**Work**は複数ファイルの実装・独立レビューを担当し、原則PR止まり。次の作業はGitHub変更を**PRで見せる**。
3. **ユーザーによる実機検収**: まずZ3TCで負数・横向き電池・黒影・fontScaleを4枚の比較画像と照らして確認。続いてZ4T/XZ1C/Mini/Handでバー同色、Recents、時計位置、画面回転、全画面判定、再起動復帰を点検する。未検収は未検収として残す。
4. 結果を `docs/WORK_PROGRESS.md` / `docs/DEVICE_TEST_JA.md` へ追記する。長い旧記録は改ざんせず新しい検収履歴を先頭に追加する。

## 6. 事故防止の原則

- 実機の成功報告を、自動試験だけで全機種・全機能合格に拡大解釈しない。
- 署名Secrets（`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`）をログや文書へ出さない。
- GitHub Releaseへ直接APKと検証情報を添付する方式を維持。GitHub Actions Artifact容量は増やさない。
- 過去の安定版候補 `overlay-statusbar-test-19f5ea73` を保全。
