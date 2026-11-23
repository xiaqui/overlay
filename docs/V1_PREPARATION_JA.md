# 2026-11-24 正式公開への追加確認

- ユーザーが署名済みv1.0.0検収APKの実機合格を報告し、独立レビュー後のmergeと正式Releaseを承認。具体的な端末・操作別の新規明細は提示されていないため、全端末全条件の合格へ拡張しない。
- 別担当がPR #43 head `98b614e1d1d77126737c82e3e7137c2aaaa1761f` の要求適合・許可誘導・互換性・workflowを独立レビュー。具体的なP0/P1/P2なし。追加の昇格workflowとscriptも重点レビューし指摘なし。静的レビューであり、本人による実機・SDK実行とは区別。
- 検収済みsource `9418f6c` とhead98b614eのアプリ本体・build設定・署名workflowは同一。固定証明書・既知APK hashを維持したまま、ファイル名を正式用 `OverlayApps-v1.0.0.apk` にして同じbytesを昇格する。署名鍵の持出しや再ビルドは行わない。
- 最新既存headの[run 45185517843](https://github.com/xiaqui/overlay/actions/runs/45185517843)はAPI29 Issue4Testの「returning app restores its always-display rule」で失敗（前面がcom.android.settings）。他4OS・checkは成功。元の成功run45183451587と区別し、新しい最終headのCIを確認してからmergeする。既存試験が未変更であることだけで原因を断定しない。
- 既存Release jobを昇格専用に変更。mainの明示マーカー `[publish-overlay-v1.0.0]` とcheck/emulator全成功をgateに、元run/source/Artifactのdigest・APK hash/size・証明書/v1/v2署名・manifest・build入力一致を検証し、draftから再取得した同一バイトを確認してstable/latest公開。通常push・PRでは公開しない。秘密鍵を復元せず、既存Release/Assetを変更しない。
- ローカル検証: bash -n、workflow YAML/権限/gate点検、模擬外部コマンドによる成功時のcreate→upload→download→edit順序と、既存Release・壊れたZIP・source不一致・対象外refの公開前停止を確認。模擬試験を実署名検証や実Release成功とは扱わない。
- AppDate（xiaqui/appdate / Release Store）の最新READMEから、draftを除外・正式版設定ではprerelease除外・公開APK Asset対象という取得条件を確認。公開後にAPIと匿名APK取得を照合する予定。AndroidのAppDate実機UI操作は未確認。

以下は2026-11-23時点の検収版作成記録。未実施表記は当時の状態で、上記2026-11-24の更新を優先する。

---

# Overlay Apps v1.0.0 準備・検証（2026-11-23 JST）

## 起点と所属

- repo: xiaqui/overlay
- 実装PR: [#43](https://github.com/xiaqui/overlay/pull/43)。
- 起点main: `a34bec6`。PR #42は2026-11-18にmerge済み（`a03667ac23b1271aecad9176d23288f647504984`）。旧v0.11.0の配布準備を重複実装しない。
- branch: `work/overlay-apps-v1-preparation-20261123`。既存open PRは過去の文書案であり、本タスクの実装PRとは分ける。
- 正本: 最新mainのTASK.md先頭節、AGENTS.md、rinのREADME/STYLE/DEVELOP/DELIVERY。角丸配置の再設計とセキュリティTODOは今回の対象外。

## 変更

1. ナビゲーションバー着色をON、またはアプリ別設定を開く際の使用状況アクセス説明を共通化。未許可時のみ「許可する／後で」。ON状態と権限付与を区別し、「後で」からもアプリ一覧へ進む。既存権限ボタンは維持し、OS設定からのonResumeで許可状態・未適用表示を再取得する。
2. アプリ・常駐通知の表示名をOverlay Appsへ統一。applicationIdと固定署名方式は維持。versionCode 12 / versionName 1.0.0。正式版の公開を意味しない。
3. 「ステータスバー情報補完」「スクリーンの角丸」に表記統一。見出しは黒色に統一。
4. Recents停止によるHome無着色と判定不能時の着色維持の説明をマニュアルに集約。
5. 既存の手動署名Artifact workflowの旧v10固定検査をv12へ更新し、検収APK名をOverlayApps-v1.0.0-preview.apkに変更。実行はmain／本実装branchの明示dispatch、または本実装branchへの専用マーカー `[build-overlay-v1-preview]` 付きworkflow変更commitに限定する。通常push/PRでは署名・公開しない。
6. Settings/Policy/角丸・時計・電池・バー描画の処理、保存形式、poll周期は変更しない。OverlayServiceの変更は表示用文字列のみ。

## 検証の現在地

- ローカル: git diff --check、変更範囲と保存キーの点検。Android SDK/Gradleは既設なし。DELIVERYの方針に従い新規SDK構築はせず、既存Actionsを利用する。
- 自動試験: 既存UI見出し期待値を更新。両入口の「後で」、未許可時の角丸設定保存、OS使用状況アクセス画面遷移・復帰、許可済み入口、許可取消後の表示更新を操作試験に追加。API23/24/26/28/29で追加テスト自体は成功。小画面の一覧を安定して選択するため、保存済みアプリのfixtureを使用する。
- CI: [run 45183451587](https://github.com/xiaqui/overlay/actions/runs/45183451587)、コード・試験head `fe5136390ce16ce2bac032956216a0f9b514210c`。単体テスト・lint・debug/androidTest build成功。API23/26/28/29は各24件、API24は11件成功（計107件、試験skipなし）。run全体success、正式Release jobは意図どおりskip。API24は従来のBatteryFinishTest＋SettingsUiTest限定であり、全回帰とは区別する。
- API26初回: 24件中、既存NavbarTestの時計・電池の濃灰画素判定1件が失敗。追加の権限往復試験は成功。該当NavbarTestと描画コードはmainから未変更。旧PR #42の最終CI（run 44267487075）はsuccessと確認したが、今回と同一runnerでのmain比較試験ではない。同一headの当該jobだけ再実行し、attempt 2のjob `137727114698` は24件成功。描画コードと判定条件は変更していない。再試験成功で初回失敗の原因が確定したわけではなく、断続的な描画判定差として実機検収へ引き継ぐ。
- Gitコミット: 接続APIはauthor/committerの直接指定に非対応。作成後メタデータでnoreplyメールを確認したが、名前はアカウント表示名となり、共通規約のxiaquiへの統一は未達。既存履歴は書き換えない。
- 自己点検: 5要求の対応、未許可時の保存経路、許可状態の再取得、角丸5状態と保存キーの維持、変更外の描画コード一致、workflowのイベント/ref/専用マーカーを点検。確定した未修正P0/P1/P2はなし。ただし初回API26画素判定失敗の原因は未確定。
- 独立レビュー: 未実施。PRのレビュー・未解決threadは現時点で0件であり、指摘ゼロの独立レビュー完了とは扱わない。自己点検を独立レビューと呼ばない。
- 署名APK: [run 45182762592](https://github.com/xiaqui/overlay/actions/runs/45182762592) 成功、Artifact `14821503345`（Overlay-signed、保持3日）。ブラウザー未ログインで手動dispatch不可だったため、既存one-shot方式に合わせた専用マーカーで一度生成した。source `9418f6c492a380b655bc188d74956dbac1ef31e8`。以後の変更は試験のみで、アプリ本体・build設定がhead fe513639と同一であることをgit diffで照合した。
- APK: `OverlayApps-v1.0.0-preview.apk` / 691553 bytes / SHA256 `dbf7f614da5dea30bd778a749f148b8ee99b20251fe36c6dcab1966b815dcf19`。com.xiaqui.overlay / versionCode12 / 1.0.0 / min23 / target25 / 非debuggable / INTERNET権限なし / application label Overlay Apps。
- 署名: v1/v2有効、初期配布APKと証明書SHA256 `dcfd2c8122274ec4148473c96b1853989c8d22e58e445d6ec4889ad5b34dffb2` が一致（CIのapksigner検査）。ZIPを再取得し、685871 bytes / digest `0dd547ae2dc42c35be82747e2f2934d8859d01ec96199e487f49086f022b3bd4`、内包APK hash、PROVENANCEのsource/runを照合。再取得バイトはCI検証済みArtifactと同一であり、ローカルに署名鍵は取得していない。
- 正式Release・実機・旧版からの更新・Release/AppDate取得: 未実施。過去版の実機合格を今回版へ転記しない。

## 実機で残す確認

- 新版を既存アプリへ上書きし、既存の角丸5状態・上下ON/OFF、半径、時計・電池の設定とアプリ別保存値が維持されること。
- 両入口で未許可／許可済み／取消後、許可する／後で／OS設定から戻るを確認。特大文字・回転時の説明ダイアログと一覧・編集、描画の操作感・ちらつき、タッチ透過を確認。
- Android 6〜10のユーザー報告は旧版の実績。Ace IIIはAndroid 14であり全機能保証ではない。横画面時のOS余白との差は既知の許容範囲。

## 次の工程と境界

実装・CI・署名検収APKの作成と再取得照合は完了。コード・試験headは上記fe513639、APK sourceは上記9418f6c、その後の文書のみのheadとは区別する。独立レビューと実機検収、必要な修正を同じPRで継続する。mergeは社長承認後。署名APK、既存版からの更新、Release/AppDate取得の確認を正式リリースの合格条件とする。既存Release workflowはv0.11.0の明示マーカー専用であり、本PRの通常push/文書変更では公開しない。将来のv1.0.0公開は、検収結果と明示的な公開条件を確定してから整備・実行する。
