# futatabi 書き込みフロー：段階2〜4エミュレータE2Eの到達点（2026-11-23）

**判定：PARTIAL（段階2〜4のコードは個別コミット済み、フィクスチャE2Eの全検収条件は未達）。Releaseは作成しない。**

## 段階別の実装コミット（main）

| 段階 | commit | 内容 |
| --- | --- | --- |
| 段階1訂正 | `52afa10` | ループバックredirectUriのHTTP 201、presign入出力とpresigned PUTの契約を追記 |
| 2 Bridge | `bc83241` | トークン保護 `/ext/*`、スレ一覧、あいもげPKCE開始・callback／code交換、投稿、presign→PUT、投稿履歴 |
| 3 UI | `77f5c06` | 独立ランチャーActivity、拡張ホーム、スレ選択、投稿フォーム、ログイン導線 |
| 4 あにまん | `4c861f3` | 独自HTML読み取り、スレ変換、preCheck→comment.php→upload/ 3段階代行 |

コードが存在し、前段のビルド・APK署名・Activity起動・部分的なBridgeリクエストが通っていても、**実投稿相当の全経路がPASSしたとみなさない**。

## ローカル専用フィクスチャでの今回の実測

Windowsの `127.0.0.1:18766` で `tests/write_flow_fixture.py` を稼働し、エミュレータ `emulator-5554` の `adb reverse tcp:18766 tcp:18766` で接続。模擬フィクスチャは外部への転送を行わない。ログにはURLパス・メソッド・本文のSHA-256・長さのみを残す。

**重要な安全策**：実際にインストールされていた前段のデバッグAPKではGETが実サイトを読んでおり、これを投稿試験に用いるのは危険だった。そのためテスト専用の拡張DEXだけで3つのAPI通信先を `127.0.0.1:18766` に一時切替え、パッチ→デバッグ署名して利用した。検証中はパッケージUID 10222の外部宛IPv4/IPv6 OUTPUTを遮断した。テスト通信先変更をGitへコミットせず、**元のJavaソースはすべて復元し、通常の.mppを再ビルド**した。

| 検証 | 実測結果 |
| --- | --- |
| 素のアプリとクローンの共存 | **PASS**（テストクローン `com.Nanasaki.ftutter.morphe`） |
| 拡張Activity起動 | **PASS**（Android `dumpsys activity` に resumed activity） |
| 無認証 `GET /ext/status` | **HTTP 403 PASS** |
| インストール固有トークンを付けた `GET /ext/status` | **HTTP 200 PASS**（3板の状態フラグ） |
| `GET /ext/threads?board=aimoge` | **HTTP 200 PASS**、フィクスチャの仮スレ1件 |
| `GET /ext/threads?board=animanch` | **HTTP 200 PASS**、フィクスチャの仮スレ1件 |
| `POST /ext/login/aimoge/start` | **HTTP 200 PASS**、フィクスチャに `POST /app-authorizations` 到達。架空の `example.invalid` 承認URLは受け取ったが、**開いていない** |
| 偽コールバック → 偽code交換 | **未検証** |
| 偽トークンでのあいもげ投稿／返信 | **未検証** |
| presign → ローカル模擬PUT | **未検証** |
| あにまんpreCheck→comment→upload | **未検証** |
| UI操作による投稿完了のE2E | **未検証** |

模擬コールバックから投稿・presign PUTまでの一括検証操作は安全性チェックによりブロックされ、**回避・再送していない**。従ってPOST成功・画像投稿成功を主張しない。既存の開発ステータス報告書にもあるとおり、本番の署名付きアップロードURLや認証Cookieの契約は所有者の実操作で別検収が必要。

### 隔離・復旧

- ローカル専用テストバンドル：`apk-inspection/v03-fixture-e2e/local-only-test.mpp`、739,314 bytes、SHA-256 `a35108a284e6070601f1710a007776e579af55f255bb9963dd75bb883ea196e6`（**公開・コミット対象外**）。
- フィクスチャアクセス証拠：`apk-inspection/v03-fixture-e2e/requests.jsonl`（Git管理外）。GET `/client-policy`、GET `/catalog/aimg`、GET `/`、POST `/app-authorizations` の到達を確認。
- Javaソース3件の通信先を元の公式HTTPSへ戻し、通常ビルドは **BUILD SUCCESSFUL**。テスト用一時worktreeは削除済み。
- エミュレータへ以前の通常デバッグ署名APK `v03-integration-debugsigned.apk` を再インストール。UID別の追加OUTPUT遮断ルールをすべて削除。 `adb forward tcp:18767` も除去。
- ポート18766は元の `tests/adb_reverse_fixture.py --port 18766` に復旧。実サービスへの投稿・OAuth認証・実トークン発行は**0件**。

**停止判断**：段階2〜4の実装コードと基本通信経路は存在するが、MUSEの指定した投稿・添付・あにまん3段階の全E2Eには到達していない。リリースせず、未検証部分についてMUSEの次判断を待つ。
