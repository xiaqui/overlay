# 上下バー統一着色・Recents停止 技術検証版

## 位置付け

2026-10-22。Overlay 0.2.0とNavbar試験版の既存動作を維持したまま、次の2項目だけを初期OFFの試験設定として追加する。

1. Navbarで決定した色をステータスバー背景にも描く「上下バーを同色にする」
2. 検出できたRecentsでは上下の**着色だけ**を止める

Recentsでも角丸5状態、角丸の大きさ・配置、時計、電池、影は変更しない。正式統合の成否、とくにXperia上のステータスバー画素とRecents検出は、本書の実機検収が終わるまで断定しない。

## 実装方式

- 新規Service、独立poll、権限、外部ライブラリ、通信、Accessibility、root、ADB常用、画面キャプチャは追加しない。既存`OverlayService`、UsageStats、500ms poll、`BarObservation`を再利用する。
- 上帯は画面幅×観測済みstatus insetだけの非focus・非touch透過window。API23〜25は`TYPE_PHONE` (2002)、API26〜29は既存`activeType`を使用する。
- 上帯のwindowを時計・電池より先に登録する。透明1×1で待機し、色とバー高が確定した後も同じViewを更新する。初回`onDraw`、実座標(0,0)、実寸が一致するまでactiveにしない。
- OSごとの同種window Z順だけに依存しないよう、物理上端にある黒い上隅は上帯と同じCanvasで色の後に再描画する。上だけ内側／上下とも内側の上隅は帯の外にあるため、従来の角丸windowをそのまま使う。既存角丸mask、半径、位置計算は変更しない。
- 上帯の追加・更新・座標検証に失敗しても、その層だけを停止する。既存Service、下帯、時計・電池、角丸は停止しない。
- 下側は既存`NavbarBandView`の同一Canvasを維持し、色矩形を先、黒い下隅を後に描く。Recents停止時は色矩形だけを描かず、同じ帯が下隅を所有し続ける。したがって角丸窓への移行中に下隅が欠ける経路を作らない。
- 全画面やstatus inset 0/unknownでは上帯を1×1透明待機へ戻す。nav inset 0/unknownでは下帯を外して従来角丸へ戻す。上・下の片方だけが非表示なら、見えている側だけを安全に継続できる。
- 新設定2個は既存Navbar masterがOFFなら評価されず、上帯もRecents分類も開始しない。既存設定キー、applicationId、署名鍵は維持する。

## 色の決定とキャッシュ

統一ON時は1個の`NavbarColor`を解決し、その暗色化後の同一ARGBを上下へ渡す。

1. アプリ別固定色（暗色化しない）
2. `colorPrimaryDark`
3. `colorPrimary`
4. 32×32に描いた公開アプリアイコンの代表色
5. 無着色

結果（失敗を含む）は既存32件LRUへキャッシュする。統一OFFではNavbar試験版との互換性のため従来どおり`colorPrimary`を優先し、キャッシュkeyも統一ON/OFFで分ける。動的Activity色、画面内容、Web/動画、独自night themeには追従しない。固定色以外は既存の明るさ抑制を維持する。標準status iconのlight/dark appearanceは他アプリから制御できないため、低コントラスト時は固定暗色または統一OFFが必要である。

## Recents判定

UsageEventsの最新`MOVE_TO_FOREGROUND`からpackage、class、timestampを保持する。次だけを着色停止とする。

- `com.android.systemui/com.android.systemui.recents.RecentsActivity`の完全一致
- framework内部resource `config_recentsComponentName`から妥当に解決できたcomponentの完全一致
- そのcomponentのpackageが既定HOME packageと同じQuickstep構成。この場合だけHOMEも無着色にする

SystemUI packageだけ、class欠落、部分文字列、古い推測では停止しない。誤って通常アプリの色を消すより、検出不能なRecentsで色が残るfalse negativeを選ぶ。通常アプリの次foreground eventで既存poll内に自動復元する。通知シェードとロック画面をSystemUI packageだけでRecents扱いしない。

## OS別の技術範囲

| OS / 優先実機 | 自動検証対象 | 実機で残る確認 |
|---|---|---|
| Android 6 / Xperia Z3 Tablet Compact | API23、2002上帯、3ボタン縦、完全一致SystemUI候補 | Xperiaのwindow layer、通知icon、Tablet固有Recents |
| Android 7 / Xperia Z4 Tablet | API24、同上 | Xperia/SystemUI、分割画面は保証外 |
| Android 8 / Xperia XZ1 Compact | API26、既存activeTypeと追加順 | 最優先。上下実RGB、通知icon、時計・電池、Recents復元 |
| Android 9 / Rakuten Mini | API28、Quickstep候補 | 2ボタン/gestureは保証外、HOME同時停止 |
| Android 10 / Rakuten Hand | API29、Quickstep候補 | gesture/animation/contrast scrimは保証外 |

横画面、分割画面、ジェスチャーナビゲーション、IME、通知シェード、ロック画面は今回の完全対応範囲ではない。判定不能時は上帯を残留させず、Recents分類だけ不能なら誤停止せず通常着色を維持する。

## 自動テストとレビュー

- JVM: RecentsのAOSP完全一致、設定component、Quickstep/HOME限定fallback、SystemUIの別class・null class・無効時の非抑止。
- instrumentation: 既存時計・電池・全画面・角丸5状態・設定移行を維持。上下同一RGB、上隅黒、標準色と分けたOverlay固有の濃灰時計・電池画素、色なしでも下隅黒、上帯失敗の局所化、全画面解除後の復元、設定master OFFを検査する。
- API23/24/26/28/29で同じsuiteを実行する。エミュレータの画素成功はXperia実機成功とは扱わない。Recents componentがUsageEventsに現れないイメージでは、非検出を記録し、純粋policy testを代替証拠として区別する。
- 独立レビューでは、Recents時に角丸を消さないこと、下隅所有の連続性、上帯失敗の局所化、統一OFF時のNavbar色互換、SystemUI package-only誤検出禁止をP1/P2条件とした。

CI、PR、merge、APK容量・SHA256・署名・Release URLは配送完了後の最終記録で追記する。

## 実機検収手順

1. 既存0.2.0またはNavbar試験版へ上書きし、設定・applicationIdが維持されることを確認する。
2. Navbar着色、固定色、上下同色をON。縦・3ボタンで白/黒背景および複数アプリを切替え、上下中央をスクリーンショット採色して同一RGBを確認する。
3. status左の通知icon、右の標準時計/電池、Overlay時計/電池、上2隅、下2隅が全て見えることを目視と拡大画素で確認する。通知シェードの上下操作、戻る/Home/Recentsが通ることも確認する。
4. 固定色解除＋自動ONで`colorPrimaryDark`候補、取得不能アプリでアイコン候補、auto OFF＋未設定で無着色を確認する。
5. Recents停止をON。通常アプリ→Recentsで上下色だけが消え、4隅・時計・電池が残ること、Recents→通常アプリで色が復元することを10回確認する。HOME、通知シェード、ロック画面を別に記録する。
6. immersive全画面、statusだけ非表示、navだけ非表示、回転、画面OFF/ON、Usage Access取消→再許可を行い、色残留や既存表示停止がないことを確認する。
7. 新設定を全てOFFにし、Navbar試験版の固定/自動色、角丸5状態、全画面時計・電池が従来どおりであることを確認する。

## 正式統合の判断基準

最優先XZ1 Compactで上下同色と標準icon/既存表示の共存、Android 6/7で2002がiconを覆わないこと、5対象端末でRecentsの停止または明記可能なbest effort挙動、誤抑止なし、通常アプリ復元、全画面・消灯・権限変更時の残留なしを満たした場合のみ正式統合候補とする。どれかで既存表示を壊す場合は統一着色を端末別に停止するか、Navbar単独の既存試験版へ戻す。
