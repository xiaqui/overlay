# Issue #4 確定仕様と実装
2026-10-21。正本はIssue #4の最新コメント6298032616と今回のユーザー指示。古い2チェックボックス/6択案は撤回済み。

## アプリ別角丸
5択をそのまま使用: なし / 通常 / 上だけ内側 / 下だけ内側 / 上下とも内側。未設定は通常。なしは角丸なし、通常は画面端、上だけ内側は上の2隅のみ現在のstatus insetへ、下だけ内側は下の2隅のみ現在のnav insetへ、上下とも内側はその両方。上/下の全体有効スイッチと半径は保持し、各モードにも全体スイッチを適用する。

非表示のバーはcurrent inset=0。stable insetは可視状態に使わず、IME等の過大値を上限で除くためだけに参照する。横画面でナビが側面へ移った場合、下側の該当隅をその側面の内側へ移す（右ナビなら右下のx、左ナビなら左下のx）。上側はstatusのtopだけで制御。

保存はcorner2:*とcornerSchema=2へ分離。旧app:*の0→なし、1/2/3→通常。旧上のみ/下のみは表示範囲であり内側位置ではないため再解釈しない。旧キーと全体ON/OFFを保持し、1回だけ原子的に移行。新キーを上書きしない。旧版へ戻した場合は旧キーを読む。

## 全画面判定
旧1px窓のY座標だけの方式を廃止。TYPE_PHONE(API23〜25)/TYPE_APPLICATION_OVERLAY(API26〜29)の透明・非focus・非touchな縦横2本の1px観測窓を使用。LAYOUT_IN_SCREEN + LAYOUT_INSET_DECOR、ADJUST_NOTHING。NO_LIMITS/STABLE/FULLSCREENは観測窓へ付けない。縦窓のtop/bottom、横窓のleft/right WindowInsetsを読み、通常の透明バーとバー非表示を区別する設計。

読取不能/初期化中は時計を表示する。常時表示モードを維持。全画面モードのときだけ、Usage Access許可を利用してアプリ別に自動/表示/非表示の例外を選択できる。例外UIは角丸の5択とは別で、6番目の角丸選択肢を増やさない。

観測窓のInsetsは他アプリのflagsを直接読むAPIではない。AOSPの可視バーがdock/contentへ反映される動作に依存するため、OS別の実遷移試験で成立を確認する。transientバーはdockを変えない場合がある。切り欠きとstatus領域が重なる場合は可視状態が曖昧なので自動判定をunknown扱いとし、表示維持＋アプリ例外へフォールバックする。unknown時の上角丸offsetは0。画面/他アプリの内容を収集せず、AccessibilityServiceやMediaProjectionは使わない。

観測はレイアウト/Insets通知と画面ON時500msの軽い再確認。消灯中は停止。時計描画の分単位更新、電池イベント、既存署名、applicationIdは維持。

## 検証
JVMで5状態/上下別offset/側面nav/バー非表示/unknown/例外/旧値移行を検証。API23/24/26/28/29で、通常→透明→没入型→statusのみ非表示→通常、別アプリへの切替、回転、実描画offset、設定移行、常時表示の回帰を確認する。結果はWORK_PROGRESS.mdへ記録し、不成立を成功扱いしない。

参照した一次資料:
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-c2f2-s2-release/services/core/java/com/android/server/wm/DisplayPolicy.java
- https://developer.android.com/reference/android/view/WindowInsets
