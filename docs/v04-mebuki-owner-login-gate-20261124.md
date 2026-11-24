# めぶき実サイト認証導線・本人検収手順（2026-11-24）

**判定：Discordの資格情報入力画面まで実サイトで到達。本人操作待ち。実認証完了・実投稿は未実施。**

## 1. 匿名時の実測

| 経路 | 結果 |
|---|---|
| GET / | 200 HTML |
| GET /api/auth/get-session | 200、JSON null |
| GET /api/catalog | 302、/ へ |
| GET /api/trial/trending | 200 JSON |
| GET /auth/callback（認証情報なし） | 302、/ へ |

この5経路では Set-Cookie を観測しなかった。公開クライアントの signIn.social は callbackURL=/auth/callback、errorCallbackURL=/?error=failed。直接の匿名OAuth開始POSTはX／Google／DiscordともCloudflare 403。回避は行わない。実際のアプリ内WebViewでは公式トップページのX／Google／Discordボタンを表示し、Discordを押して Email or Phone Number、Password、Log In の入力画面まで到達した。資格情報入力、プロバイダーの承認、認証済みcallback、投稿はいずれも未実施。

最初のWebView起動でANRが1回発生。スタックは WebView初期化→Chromiumのネットワーク監視登録→ConnectivityManager.listenForNetwork のBinder待ち。アプリを1回再起動して実サイト表示を確認。端末固有の初期化遅延であり恒久修正を確認したとは主張しない。

### Cookieの属性（匿名OAuth開始時のみ）

Discord導線を開始した後の、アプリ自身のWebViewのCookieストアを**値の列を参照せず**メタデータだけ読み取った。

- __Secure-mebuki.state：host=mebuki.moe、Path=/、Secure=true、HttpOnly=true、SameSite=Lax（Chromium enum 1）、永続Cookie、観測した有効期限 2026-11-24 18:40:52 JST。
- _ga と _ga_R9M4MJTZFD も名前のみ観測。
- state CookieはOAuth開始用。認証済みセッションCookieの名前・期限・属性は未確認。Cookie値、暗号化値、OAuth state値、認証コードは取得・記録しない。

## 2. 実装とローカルE2E

MebukiSessionを追加し、保存済みCookieの存在だけでなくGET /api/auth/get-sessionで再検証。nullはANONYMOUS、保存なしはNOT_SAVED、通信失敗や未知の応答はUNVERIFIED（通信失敗で保存内容を消さない）。user/session両オブジェクトを認識する判定は既存の模擬契約に基づき、実認証済み応答での確認はまだ必要。WebView Cookieの保存は本人の明示操作時のみ、アプリ専用SharedPreferencesへ保存する。

認証済み読取の契約確認画面を追加。保存済みセッションの検証後、既知のGET /api/auth/get-session、GET /api/catalog、GET /api/thread/{slug}/messagesに限定してアクセスし、HTTP status、MIME、長さ、JSONの項目名と型、観測されたSet-Cookieの属性のみを表示する。値は出さない。未知の形式は非対応と明示。**認証済みカタログ／messagesのふたば形式への変換は実物契約を確認するまで未実装。**

ローカル18768番、当該UIDの外部IPv4/IPv6遮断、偽Cookieのみで実測：
- 保存なし NOT_SAVED、偽有効Cookie AUTHENTICATED、プロセス再起動後 AUTHENTICATED、偽無効Cookie ANONYMOUS：すべてHTTP 200かつ期待どおり。
- 契約確認UIからsession/catalog/messagesのGETが各200で到達。合成データの項目名・型を表示し、本文・Cookie値を表示しないことを確認。
- テスト専用MPP 748030 bytes／SHA-256 10ce1f98fdab30cccb507a6f0c6c35a31c5954cbfee7ff2c447d4fb81f3ae3b5。
- 通常HTTPS版MPP 748064 bytes／SHA-256 88dbc76d98f463aaf405f55a308f7556c3c25c15a40551e3de4db968cafa6e09。テスト版と異なる。
- 偽Cookie設定をハッシュ一致で復元、18768番フィクスチャ停止、追加したforward/reverse・通信遮断を解除。既存18766 reverse保持。

## 3. 所有者の操作手順（ここから本人のみ）

エミュレータ emulator-5554 に通常HTTPS版 owner-live-signed.apk をインストール済み。2026-11-24 18:49 JSTに公式ログイン画面・3つのログインボタン・ネイティブの保存／読取契約ボタンを実物確認。通常のふたったーは変更せず、公開Releaseには含めていない。

1. 拡張のめぶきログイン画面に表示された公式サイトで Discord を選択（入力画面到達を確認済み。X/Googleはボタン表示のみ）。必要に応じて画面内を下へスクロール。資格情報と二段階認証は本人が公式プロバイダー画面だけで入力・承認する。ChatGPTやMUSEに渡さない。
2. めぶきの公式サイトに戻ったことを本人が確認し、上部の「ログイン済みセッションを確認して端末内へ保存」を押す。成功表示が出ることを確認。失敗時は状態表示を報告し、認証を繰り返したりCookieを抜き出したりしない。
3. 再起動して拡張ホームのめぶき状態を確認。AUTHENTICATEDは実get-sessionの成功応答に基づく。UNVERIFIEDは通信または契約未確認。
4. 認証済みサイトで閲覧可能なスレのURLの /t/ 以降のslugを取得し、ログイン画面上部のスレID欄に入力。「保存済み認証で読取契約を確認（値は記録しない）」を押す。表示されるメタデータだけを確認する。必要な捕捉はsession/catalog/messagesのstatus、MIME、構造、項目名・型、添付表現、Cookie名・属性・期限（Set-Cookieで観測できる範囲）。非JSONの場合、形式を特定できる値なしの追加証拠をMUSEが判断する。本人ログイン後のCookieストアについても、所有者の許可下で同じメタデータ限定の読み取りを行う。値や生のDBを共有しない。
5. 実投稿の検収は本人が決めた許容される投稿先で、本人が送信する。返信・新規スレ・添付の契約は公式UIの本人操作時に、必要なら本人管理下のブラウザー開発者ツールで**該当リクエストだけ**を観察する。記録対象はpath、method、Content-Type、multipartのフィールド名・型・必須性、添付方式、応答status・MIME・成功/失敗を識別できる構造・安全な成功フラグ。本文、アカウント情報、Authorization、Cookie値、認証コード、state値、Turnstileトークン、署名URLのクエリ、生HARは共有しない。既存Bridge返信の202/confirmed:falseは実投稿成功ではない。Turnstileは公式UIで本人が通常操作する。

## 4. 残件と停止条件

実サイトの認証済みcallback、セッション確立・復元、認証済み読み取りの変換、返信成功判定、新規スレ／添付はまだ本人検収待ち。新規スレ・添付は契約を確定してから実装する。本人の操作が必要な段階に到達したため停止する。バージョン変更・Releaseなし。証拠：[v04-mebuki-live-anonymous-20261124.json](gate-evidence/v04-mebuki-live-anonymous-20261124.json)。
