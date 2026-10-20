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
