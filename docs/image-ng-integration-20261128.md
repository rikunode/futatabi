# 画像NG 統合途中の実装記録 (2026-11-28)

基点: v0.4.4 の公開メタデータ 64b1b027927d67a66a83da9eb637922377889024。
確定した特徴量・保存基盤のローカルコミット: 0e2691394a568a1a9175dde2039286dc7aa0bb27。

## 現在の検証

- ImageNgContractTest: 248 assertions PASS。32合成パターンのJPEG品質55再圧縮32/32、半分縮小32/32、無関係496組の一致0。pHash<=6、dHash<=8、対数縦横比差<=0.08を維持。dHash生成時に1階調以下の差を同値として扱う。実画像での精度は未確認。
- EXIF向き8種類の座標変換、JPEG/PNG/WebPのEXIF各8方向・両エンディアン、無効なコンテナ、GIFの向き既定値、NGプレビューの一致件数をホストで検証。
- Android SDK android.jar を使ったjavacコンパイルPASS。実機のBitmapFactoryによるデコード、ネイティブダイアログ操作は未確認。Gradleによる追加分のコンパイルは既存ビルドの競合が疑われる状態でタイムアウト（3 busy daemonsという出力）。他のプロセスを停止していない。
- ホストのAtomicFileはテスト用スタブ。Android上のAtomicFile動作とは区別する。
- ImageNgDialogsは特徴量の受け渡し口、登録（完全一致・類似・板/全板・メモ）、計算済み候補の一致数、管理（メモ、日時、短縮ハッシュ、無効化・削除）を実装。Flutterからの呼出し・更新通知は未接続。候補画像そのもののプレビューは未実装。
- ImageNgDecoderはメモリ内のJPEG/PNG/WebP/GIFを対象に、最大12,000,000 bytes、最大40MP、各辺16,000、サンプリング後1,048,576画素で制限。Android API23では色空間の明示設定ができない。アニメーションは静止フレーム扱い。実デコードの確認が必要。

## 共通フックの実物

純正 AOT: CatalogProvider._anon_closure 0x6e1e28。URL変換 0x6e207c、Uri.parse 0x6e2088、retry 0x6e22c0、Windows31J decode 0x6e2314、HTML parse 0x6e231c、レコード構築 0x6e2af4〜0x6e2b64。
Catalogのリスト処理 0x735ae4、ListBase._filter 呼出し0x735f84/0x7360f0/0x7364b0、GridView builder 0x736d94、MasonryGridView builder 0x736ea4。
Thumbnail.build 0x86ea90、既存メニューのbuild 0x873ad0、非表示登録 0x874e14、既存ViewerのMyCachedNetworkImage._myCNI 0x92020c とImageCacheManager.getImageFile 0x98fc28。
既存のB2はLocalBridgePatch.ktでstock toast plugin Ln6/e; のメッセージを判定し、BoardRecoveryActivity.tryLaunchへ接続している。登録入口の設計に再利用する候補。ただし画像選択データの受け渡しと新規メニュー項目は未実装。表示結果を返せる双方向フックではない。

## 統合方針

第一候補は共通のFlutterカタログリストに動的な判定結果を反映する方式。メニューから現在の板とスレ画像識別子をネイティブ側へ渡す入口、既存の画像読み込みから特徴量を得る入口、ルール変更と照合完了での再評価、の3つを別々に成立させる。純正のhihyouji永続ストアには書かない。現時点ではGC/async/状態更新まで検証できたAOT往復経路はない。

代替として追加3板の既存HTML生成で計算済み特徴量を判定してセルを除外する方法がある。実コードの位置: AimogeAdapter.catalog 141行付近・thumbnailTag 268行付近・image 297行付近、AnimanchAdapter.catalog 132行付近・image 565行付近、MebukiAdapter.catalog 251行付近・認証画像分岐90行付近。既存画像応答の変換後バイトを使う。追加ダウンロードや認証経路の変更をしない。取得済みの特徴量しか使えず、現行Flutterキャッシュ・表示更新の問題が残る。純正3板にはこの方法は効かないため、単独では要件を満たさない。

純正3板のカタログを新たにローカルブリッジに転送する案は、論理URL、Cookie、Referer、投稿/履歴の整合、安全なpeer確認を新たに検証する必要があり、現時点で採用しない。既存の永続非表示を画像NGへ流用する案も不採用。

次の受入ゲート: 既存長押しメニューで現在の板・画像の識別子を正しく取り出し、ネイティブに渡す最小フックを作り、生成AOT/DEXを検証する。その後、既存キャッシュの画像バイトを登録・表示判定へ渡し、純正のリストを汚さない再評価の経路を作る。現時点で6板とも統合未達。ベータ版の公開は行わない。

## 19:15 JST continuation: host evidence

The earlier core and dialog commits 0e2691394a568a1a9175dde2039286dc7aa0bb27 and e8b25b39d604a85f67024cf50eb4e2bcbf816a97 were pushed to origin/main.
Commit 141e437e89d0a73d0f79c77f35dee1227984efe9 adds the independently executable decision-index and AOT experiments and a dormant adapter-rendering alternative.

- The original v0.4.4 APK libapp.so (isolated-release.apk, SHA-256 of libapp f3a03d8b769c0f817f4fac4f69bde35ed50927158fdff12ddd831a15072e0f0d) supplies the exact field-access code at 0x873880..0x87389c, 0x8738b8..0x8738c0, 0x874114..0x87412c. Unicorn executes those instructions with simulated Dart compressed-pointer objects, then the independent read-only leaf. All 256 cases passed, including HTTP/HTTPS stock aliases, one-/two-byte strings, both thumbnail variants, unknown IDs and boards, pre-existing hidden state.
- The additional experiment branches at the existing title/hidden-list predicate epilogue 0x73786c, reads a Java-generated 288-byte decision fixture, returns the canonical Dart boolean and resumes the real epilogue. 36 cases passed (six boards, two string representations, three visibility/table states). The test uses a synthetic stack/heap and a CAT-variant input, not a running Flutter isolate. Neither proof installs a product hook.
- ImageNgCatalogIndexContractTest: 2153 assertions PASS. Bounded 2048 observations, two variants, six board scopes, stale epoch rejection, dynamic disable/delete. Decision rows are memory-only board ordinal, variant, digits; the binary file is written only by the host test. No image/path/URL stored by the index.
- ImageNgContractTest: 248 assertions PASS again. Existing threshold values and persistence format unchanged.
- CatalogPaginationContractTest --image-ng-probe: actual Aimoge, Animanch, Mebuki adapter rendering with mock upstream and synthetic features, each 7 -> 6 -> 7. Empty/disabled/deleted rule output byte-identical, visible IDs in original order. Mebuki public response unaffected. Optional predicate overloads are not connected to normal routes. ImportCatalogContractTest: nine fixture scenarios PASS. Direct javac compiled the modified Java sources.
- Unchanged release patch definitions and gradle.properties compared with v0.4.4 tag. No APK modification, beta package, live post/del/login or device execution in this continuation. Latest GitHub release remains v0.4.4.

Decision: prefer the read-only common AOT predicate over changing stock catalog networking or writing stock permanent hide rules. The existing SharedPreferences.getInstance (0x456f2c) reuses a cached completer after initialization, and the initial Android getAll is not a runtime refresh channel. Changing Java preferences alone is not a demonstrated round trip. The missing portion is a safe per-process memory transport from Java to the actual Dart callback, capture of actual selected image bytes/variant, and registration/management UI event and rebuild connection. The extra-three fallback only establishes rendering feasibility, not independent complete features. All six boards remain unverified end-to-end. Do not publish these probes as a working beta.
