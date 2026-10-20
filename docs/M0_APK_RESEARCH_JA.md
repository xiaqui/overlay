# M0: 参考APKの調査と実装判断
2026-10-21。添付APKをAndroguard 4.1.4でManifest/DEX静的解析。コード・素材の転用なし。解析ダンプ・APK・画像は公開しない。実機での実行は未実施。

| APK | min/target | 観測したWindowManager方式 |
|---|---|---|
| 浮遊時計 1.1.5(16) | 4 / target指定なし | FloatClockService.onAction_ShowでタッチOFFなら2006、タッチONなら2003。初期flags=296、タッチOFFで16を追加。透明形式-3。 |
| Round Corners 2.9(19) | 17 / 27 | RoundRServiceに2006/2038のSDK分岐と四隅の独立ウィンドウ。 |
| バッテリーメーター5.7.0(570) | 21 / 34 | OverlayService.onStartCommandに2038、qにAPI26未満で2003/2006を切り替える経路。充電等のイベント購読。 |

浮遊時計のtarget指定なしはminに従う古い互換動作。Android8以降でも旧方式を使えるという実機情報と整合する。Android10 AOSP PhoneWindowManager.checkAddPermissionはtarget>=26の旧alert typeにINTERNAL_SYSTEM_WINDOWを要求し、target<26はSYSTEM_ALERT_WINDOWとAppOps許可を評価する。したがって今回はtarget25を明示し2006を優先する。ただしOEMの許可/実際の階層は実機で確認する必要がある。一般的な2038だけの説明では本件を説明し切れない。

一次資料:
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/policy/PhoneWindowManager.java （checkAddPermission）
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams （2006/2038、flags、cutout）
- https://developer.android.com/about/versions/oreo/android-8.0-changes （alert windows）

## OS別判断
API23/24/25は2006、API26/28/29もtarget25の互換経路で2006を試す。SecurityException/BadTokenで拒否された場合のみ26以降は2038に再試行。設定で2038を直接選択可能。2038は標準バーより背面なので、バー上の角丸は保証しない。API28以降は公開cutout設定SHORT_EDGES。回転時は全ウィンドウを再構築。ノッチ領域やナビバーとの重なりは未実機検証。

背景TRANSLUCENT、NOT_FOCUSABLE/NOT_TOUCHABLE/LAYOUT_IN_SCREEN/LAYOUT_NO_LIMITS。四隅は半径サイズの4窓、時計電池は最小矩形。全画面透明窓は使わない。画面消灯時は非表示・タイマー停止。時計は次の分境界と時刻/タイムゾーン変更、電池はBATTERY_CHANGEDで更新。WakeLockなし。

全画面のみは1px透明・非タッチの通常inset窓のY位置を調べる試験機能。透明ステータスバー・OEM差による誤判定があるため初期値は常時表示。通常バーとの二重化は位置調整/試験モードで検収する。正確な全アプリ判定を保証しない。

アプリ別角丸はUsageEventsの前面遷移を使用し、画面ONかつルールあり・許可ありの場合のみ1秒間隔。アクセス拒否時は共通設定へ戻す。再起動後・初回は過去24hから最新前面を復元（プロセス内だけ、保存/送信しない）。24h以上前から前面を維持する条件や複数ウィンドウには制限がある。

M3は製品仕様に沿って手動微調整を採用。画像の自動推定/MediaProjectionは未実装で保留。幅864pxの参考画面右上に縦型電池＋23:46があることを目視確認し、右端4dp・文字14dp・縦型電池を初期値にした。ピクセル完全一致や端末のdpiは未確定。スクリーンショットは収集しない。

参考APK SHA256:
- 浮遊時計: 8c8a57379b946b70c47c00e977453d787c0ae6b8b7cb00d5f3b28e0b08a7d3df
- Round Corners: b7b568deb7a16b42b31eab6304424f5e98682acc2ddce6cb14088afac24b5c52
- 電池: 942b3bd67420dba921d4405099cd354249a586af5f306551f75020d237616a62
