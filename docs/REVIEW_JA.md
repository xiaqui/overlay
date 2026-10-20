# Issue #4 独立レビュー（2026-10-21）
実装担当とは別の担当がIssue #4の確定仕様、実装、試験、署名配送差分を確認。GitHub自動レビューの指摘も照合した。

| 優先度 | 指摘 | 修正・再確認 |
|---|---|---|
| P1 | WindowInsetsだけではWindowManagerが先にクリップしたナビバー領域を見失う。ポリシー値だけの試験では偽陽性になる | 窓の画面位置/サイズと実画面との差をcurrent insetへ合成。通常時nav>0と移動後の下隅の実画素を検査 |
| P2 | 逆横画面の左navで親フレーム原点も移動し、左隅がずれる | 物理画面から目標座標を計算し、getLocationOnScreenとの差を補正。API26以降のREVERSE_LANDSCAPEでleft nav>0（API23/24は右nav継続）、左下内側の実画素と上左端/通常四隅の実View座標の検査を追加 |
| P2 | 横回転後、側面のcutoutをnavと誤認し得る | 縦横観測窓の上下左右すべてのsafe insetを確認。cutout時はunknown、時計表示維持、角丸offset=0。実機手順に制限を明記 |

修正後の重点再レビューで追加P1/P2なし。GitHubのP2スレッドへ対応を返信して解決済み。試験はソフトナビバー付きNexus 4プロファイルを使用。別アプリ→新しい全画面Activityの遷移を確認し、既存ActivityへのBACK復帰成功とは扱わない。試験間は実ServiceRecordの消失を待ち、古いActivityをCLEAR_TASKで除いて汚染を防ぐ。操作対象はstartActivitySyncで得た実インスタンスに限定し、OS初回のimmersive案内はCI環境で確認済みにする。製品のバー状態や描画結果を模擬する設定ではない。初版APKの既知SHA256と証明書を新APKに照合する配送検査も独立確認済み。CI結果と実機未検証事項はWORK_PROGRESS.mdに記録する。

---

# M4 独立レビュー
2026-10-21。実装担当とは別のレビュー担当による静的レビューと、GitHub Codex自動レビューを実施。

| 優先度 | 指摘 | 対応 |
|---|---|---|
| P2 | 使用状況アクセスを許可して戻ってもアプリ別判定が開始しない | 有効中のMainActivity.onResumeでServiceを再評価 |
| P2 | poll中のウィンドウ追加が権限取消と競合すると例外 | SecurityException/BadTokenExceptionを捕捉して安全停止 |
| P2 | 検証に使うBuild Tools 35.0.0のインストールが暗黙 | check/releaseでSDKとBuild Toolsを明示導入 |
| P2 | 全画面試験がシステムバーの色でも合格し得る、タップ後の終了未確認 | ActivityMonitor、画面中央/上下端色、時計領域タップ後Activity終了を確認 |
| P1 | Release途中失敗後に同一tagへ再配布できない | 同一SHA draftを再利用、clobber添付。公開済みは改変せずSOURCE/SHA/証明書/package/min照合で再利用。Release jobを同一SHAで直列化 |
| P2 | emulator-runnerの複数行scriptでtrapが別シェルになる | 外部bashスクリプト1行呼出しにしてtrapとGradleを同じシェルで実行 |

すべて修正後の差分を独立担当が重点再確認し、追加P1/P2なし。GitHubのP1スレッドにも対応コミット1ea29aaを返信し解決済み。古いSHAのレビューサマリーだけで未解決と判断せず、最新threadと差分を確認する。

レビューとは別に、CIでJVM testDebugUnitTest/lintDebug/assembleDebug、API23/24/26/28/29の実WindowManager instrumentationを実行。最終結果はWORK_PROGRESS.mdへ記録。Xperia実機、長時間常駐、OEM固有UIの受入試験は未実施。

CIの追加調整: API29起動前にuserdata作成の空き容量不足（7304.52MBに対し7372.8MB必要）を確認。未使用のhosted runner .NET SDKを除去し、AVDディスクを2048Mに明示して全5OSの試験が成功した。最初の終了ハング推定は今回の直接原因ではなかった。旧PR runのみの取消しとPR concurrencyで資源占有を防止。これらの差分も独立レビュー済み。
