# めぶき実サイト認証導線・本人検収手順（2026-11-24）

**最新判定：PARTIAL。X／Discordのみ対象。e5730b0dの現行APKでDiscord入力画面と両者の中止復帰を確認。Xは認可画面からLog inを押しても入力欄に進まない。Xを表す合成セッションの保存・再起動・契約確認・無効化はPASS。Discord側追加E2Eとエラー表示試験はツールの安全性チェックで停止。実認証・実投稿なし。Googleは対象外・UA変更なし。最新の実測は§7を参照。**

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

1. 共有エミュレータの専有許可後、現行開発ビルドを適用して拡張ホームの「めぶき：X／Discordログイン（本人操作）」を開く。下記の2種の公式導線に従う。資格情報と二段階認証は本人が正規の認証画面だけで入力・承認する。ChatGPTやMUSEに渡さない。Googleは対象外。
2. めぶきの公式サイトに戻ったことを本人が確認し、上部の「ログイン済みセッションを確認して端末内へ保存」を押す。成功表示が出ることを確認。失敗時は状態表示を報告し、認証を繰り返したりCookieを抜き出したりしない。
3. 再起動して拡張ホームのめぶき状態を確認。AUTHENTICATEDは実get-sessionの成功応答に基づく。UNVERIFIEDは通信または契約未確認。
4. 認証済みサイトで閲覧可能なスレのURLの /t/ 以降のslugを取得し、ログイン画面上部のスレID欄に入力。「保存済み認証で読取契約を確認（値は記録しない）」を押す。表示されるメタデータだけを確認する。必要な捕捉はsession/catalog/messagesのstatus、MIME、構造、項目名・型、添付表現、Cookie名・属性・期限（Set-Cookieで観測できる範囲）。非JSONの場合、形式を特定できる値なしの追加証拠をMUSEが判断する。本人ログイン後のCookieストアについても、所有者の許可下で同じメタデータ限定の読み取りを行う。値や生のDBを共有しない。
5. 実投稿の検収は本人が決めた許容される投稿先で、本人が送信する。返信・新規スレ・添付の契約は公式UIの本人操作時に、必要なら本人管理下のブラウザー開発者ツールで**該当リクエストだけ**を観察する。記録対象はpath、method、Content-Type、multipartのフィールド名・型・必須性、添付方式、応答status・MIME・成功/失敗を識別できる構造・安全な成功フラグ。本文、アカウント情報、Authorization、Cookie値、認証コード、state値、Turnstileトークン、署名URLのクエリ、生HARは共有しない。既存Bridge返信の202/confirmed:falseは実投稿成功ではない。Turnstileは公式UIで本人が通常操作する。

## 4. プロバイダー別の手順・制約（19:12 JST追記）

| プロバイダー | 確認した入口／画面 | 本人操作・停止点 |
|---|---|---|
| Discord | 公式ボタン→WebView内のDiscord、Email or Phone Number、Password、Log In（18時台の実測）。めぶき側OAuth state Cookieの属性は§1参照。完全なリダイレクト列と認証後callbackは未観測。 | 専有許可後、公式画面で本人が操作。認証・同意画面、追加認証、めぶきへの復帰は本人確認。 |
| X (Twitter) | 公式ボタン表示のみ。遷移先URL、入力欄、警告、プロバイダー固有Cookieは未観測。 | 実地確認後に正規導線を案内する。公式プロバイダー以外へ遷移した場合や埋め込みブラウザ拒否があれば停止。 |
| Google | 公式ボタン表示のみ。実際のエラー画面・リダイレクト・Cookieは未観測。Google OAuthポリシーは開発者制御の埋め込みユーザーエージェントを禁止する。 | WebView内でGoogle資格情報を入力させる方式を採用しない。外部ブラウザで公式サイトのログインを最初から開始する方式を検討。 |

- 直接HTTPのsocial開始POSTは3種とも前回403（Cloudflare）。プロバイダー側の認証拒否とは区別する。公式クライアントJSの共通callbackURLは `/auth/callback`。認証済みリダイレクト列は未確認。
- Googleの `disallowed_useragent` は公式に記載された制限エラーであり、今回めぶきで実測した文言ではない。参照：https://developers.google.com/identity/protocols/oauth2/policies 、https://developers.google.com/identity/siwg/best-practices 、https://developers.google.com/identity/protocols/oauth2/web-server 。
- Custom Tabsは既定ブラウザのセッションを使う。WebViewとブラウザのCookieは別ストア。途中からブラウザへOAuthを移してもstateやセッションの連続性は保証されないため、代替フローは外部ブラウザで公式サイトから開始する。アプリへの正規のcallbackとセッション交換が必要。めぶきがこれを提供する証拠はまだない。外部ブラウザのCookie抽出・コピー、UA偽装、制限の迂回は実施しない。外部ブラウザでの閲覧だけならネイティブBridge認証とは別機能として扱う。参照：https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs 、https://developer.android.com/identity/passkeys/architecture 、https://datatracker.ietf.org/doc/html/rfc8252 。

### 共通の状態表示と検収

MebukiSessionはプロバイダーを引数にせず、同じめぶきHTTPSホスト・get-session・アプリ専用保存領域を使用する。既存の模擬E2Eは共通処理の検証であり、3種類の実ログイン成功を証明しない。各プロバイダーで正規に確立しためぶきセッションについて、§3の保存→再起動→契約確認を本人認証後に検収する。正規の外部ブラウザからのセッション引き継ぎが確立しない場合は未対応のまま扱う。

| 状態 | 意味・対応 |
|---|---|
| NOT_SAVED | アプリ側に利用可能なCookieが保存されていない。公式サイト復帰後、保存操作を確認。 |
| ANONYMOUS | get-sessionがHTTP 200 / null。保存値があってもログイン済みと扱わない。 |
| UNVERIFIED | 通信失敗、HTTP非200、または未認識の応答。再試行を連発せず原因を確認。 |
| AUTHENTICATED | get-sessionにuser/session両オブジェクトを認識。実ログイン時の応答契約は本人検収が必要。 |

### 追加実地試験の停止記録

19:11 JST、e3d5f05・通常HTTPS版APK・保存済みめぶきセッションなし、全3ボタンを持つ公式ページを確認。19:12:18 JST、Android activity eventで別案件 `com.hatena.android.bookmark.morphe` のPreferenceActivityへの切替を確認。MUSEに調整を依頼し、専有順序が確定するまで端末操作を停止する指示を受領。X・Googleのボタンクリック、外部ブラウザ実地試験は未実施。実機OPPOにも触れていない。コード／APK／認証設定／通信ルールの変更なし。

### 所有者判断の追記（2026-11-24）

MUSEから、めぶき開発者兼所有者の判断としてログイン専用WebViewのUA変更によるGoogle認証制限の突破を承認する旨の通知を受領した。ただし、めぶきの所有権はGoogle側の埋め込み認証に対するセキュリティ要件を解除するものではない。制限突破を目的とするUA変更は実装せず、実地試験も行っていない。

**正規ルートの設計候補（未実装）**：めぶきサーバー側でアプリ向け認証開始・復帰・セッション交換を正式に提供する。既定ブラウザのCustom Tabs／Auth Tabで公式OAuthを開始し、認証完了後は検証済みのアプリ向けcallbackへ戻る。短寿命・一回限り・開始したアプリの認証要求に紐づくコードをサーバーで交換し、めぶきの正規アプリ用セッションを確立する。既存のWebView CookieやGoogle資格情報を外部ブラウザから抽出しない。state・PKCE・redirect URIの検証を維持し、コードや資格情報をログに出さない。Better Authには公式モバイル連携と一回限りのトークンプラグインがあるが、めぶきでの導入状況・対応バージョン・正規契約は未確認で、プラグインの存在のみを実装可能性の証明とはしない。

参考：Google OAuthポリシー https://developers.google.com/identity/protocols/oauth2/policies 、Android認証ブラウザ https://developer.android.com/identity/passkeys/architecture 、Better Authモバイル連携 https://better-auth.com/docs/integrations/expo 、一回限りのトークン https://better-auth.com/docs/plugins/one-time-token 。

必要な次の設計判断は、めぶきサーバーの担当者による正規アプリ認証契約の確定。共有エミュレータの操作停止を維持する。Xの入口試験は専有許可後、Discordは既存の入口到達証拠を再利用し、全プロバイダーの実認証済みセッション検収は未実施とする。

## 5. 現行のX／Discord専用方針（所有者判断、後続指示）

この節が以前の3種対応・Google代替設計に関する記録より優先される。Googleは未対応のまま対象外とし、UA変更・ポリシー迂回・外部ブラウザのCookie取得は行わない。以前のGoogleに関する観測・判断は履歴として保持する。

### Xの本人操作手順

- 公式めぶきページのX (Twitter)ボタンを選択する。現行APKでは `Authorize app / X`、`Log in to X to continue.` を表示したが、`Log in` を押しても認可画面から移動しなかった。入力欄には未到達で、本人ログイン手順としては未確定。公式ドメインを示す案内だけで認証成功とは扱わず、中止ボタンで戻る。
- 認証エラーや未知のページが表示された場合は入力せず、公式画面の警告を確認して停止。本人が操作を中止する場合は「認証を中止してめぶきへ戻る」を押す。保存済み認証は変更しない。
- 本人による認証・同意・追加認証後、めぶきへ戻った場合は共通手順§3の保存→ホームへの復帰／再起動→読取契約確認を実施する。callback表示のみを認証成功と扱わない。

### Discordの本人操作手順

- 公式めぶきページのDiscordボタンを選択する。現行APKでも `Welcome back!`、`Email or Phone Number`、`Password`、`Log In` の表示と入力欄まで到達した。認証・承認・追加認証は本人だけが行う。
- エラーや中止の場合はXと同じ共通案内に従う。めぶきへ戻った場合は§3の保存→ホームへの復帰／再起動→読取契約確認を実施する。

### 現行コードの状態表示とホスト試験

- MebukiLoginFlowの分類でX（x.com、twitter.com）／Discord（discord.com、discordapp.com）／めぶきの通常ページ・callback・error付き復帰を区別。認証プロバイダーの画面やcallbackを見ただけでは認証成功と表示しない。未知のページは入力せず戻るよう案内する。Googleの認証先へのメインフレーム遷移を対象外として中止する。UAは変更しない。
- ページ通信エラー、メインフレームのHTTPエラー、TLSエラー（中止）を表示する。HTTP 200で表示されるプロバイダー固有のエラー・追加認証は本人が画面で確認する。エラー文言の実地確認は未実施。
- 保存・消去・契約確認・中止操作の競合を同一画面内で防止。認証済みでも保存失敗なら失敗と表示する。契約確認ではNOT_SAVED／ANONYMOUS／UNVERIFIEDを区別。ホームへ戻ったときもget-sessionを再照会する。保存情報の消去はWebView内のログアウトとは異なる。
- Android Java全ソースのコンパイルPASS。ホストJVMの遷移分類13ケースPASS。ローカルHTTPフィクスチャでX／Discordを表す別の偽Cookieについて、共通get-session・catalog・messagesの応答、無効Cookieと既存フィクスチャの契約を確認（Python unittest 3件PASS）。この試験はプロバイダーのOAuth実通信、Android UIコールバック、実セッション保存の新規E2Eではない。前回の共通処理の隔離エミュレータE2Eを参照し、現行APKでの再試験は専有許可後。
- 通常GradleビルドPASS、開発MPP 751051 bytes、SHA-256 a1a6818c1905bde62f8a2a1722546fe8fb5e822a1d186710f622947f55d4dc0e。内部バージョン0.3.0のまま。端末へ適用していない。

### 専有許可後の検収

現行開発APKを適用し、X／Discordそれぞれについて公式ボタンから入力画面までの実遷移・警告・Cookie名と属性のみを観測する。中止時の公式サイト復帰、エラー時の状態表示を確認する。クエリ値、OAuth code/state、Cookie値、認証情報は記録しない。本人の操作が必要な地点では停止する。両プロバイダーの認証済み共通処理の実証は本人の認証後に行う。Googleは試験対象外。

## 6. 残件と停止条件

実サイトの認証済みcallback、セッション確立・復元、認証済み読み取りの変換、返信成功判定、新規スレ／添付はまだ本人検収待ち。新規スレ・添付は契約を確定してから実装する。専有許可後に現行ビルドで追加試験を実施した結果は§7のとおり。Googleは対象外。バージョン変更・Releaseなし。証拠：[v04-mebuki-live-anonymous-20261124.json](gate-evidence/v04-mebuki-live-anonymous-20261124.json)。

## 7. 専有許可後の現行APK実測（2026-11-24 20時台 JST）

e5730b0dベースの通常MPP（前回記録のSHA一致）からAPKを生成・署名・適用。初回起動でInput dispatching timeoutによるANRが発生したが、アプリ再起動1回で公式サイトの表示に到達した。恒久修正済みとは判定しない。

| 項目 | 結果 |
|---|---|
| X | 公式ボタン→Xドメイン判定→Authorize app / X→Log in to X to continue.。Log inを押した後も同画面で、資格情報入力欄に未到達。明示的な警告文は観測なし。原因未確定。 |
| Discord | 公式ボタン→Discordドメイン判定→Welcome back!→Email or Phone Number／Password／Log In。入力欄までPASS。明示的警告なし。 |
| 中止 | XとDiscordの双方で中止ボタンを押し、公式めぶきページへの復帰と中止案内を確認。 |
| リダイレクト | UI上の遷移順は観測済み。個別のHTTPリダイレクトURL・ステータス列は未捕捉。 |
| Cookie | アプリのWebViewストアから名前・host・path・Secure・HttpOnly・SameSite・永続性・期限のみ観測。証拠JSONに記録。値は参照しない。既存Cookieと今回新規発行分は区別していない。 |
| エラー表示 | 実プロバイダーから明示的なエラー応答は観測しなかった。ローカルHTTPエラーUIの追加試験は後述の理由で未実施。 |

両プロバイダーの資格情報入力・承認・実投稿は行わなかった。Googleは試験対象外。セッションの成功判定は引き続き本人による認証後のget-sessionで行う。

### 現行APKベースの隔離フィクスチャ

通常ビルドと異なるローカル専用MPPを作成し、LoginActivityとMebukiSessionの宛先・ホスト条件のみを127.0.0.1:18768に一時変更。テスト中は当該クローンUIDの外部IPv4/IPv6通信を遮断。WebViewからXを表す合成Cookieを発行するフィクスチャで以下を実測した。

- 保存ボタンによるWebViewセッションの保存成功表示、/ext/status HTTP 200 / AUTHENTICATED。
- プロセス停止・再起動後もHTTP 200 / AUTHENTICATED。
- 契約確認UIでsession/catalog/messages各HTTP 200、合成本文とCookie値が表示されないこと。
- フィクスチャがget-sessionでnullを返す状態に変更するとHTTP 200 / ANONYMOUS。

上記はXの実OAuth成功を意味しない。Discord合成セッションの現行APKでの追加E2E、両者のHTTPエラー表示検証は実行ツールの安全性チェックで停止したため、再試行せず未実施として残す。以前のホストフィクスチャでは両合成セッションがPASS済み。

### 復旧・残件

通常APKを再インストールし端末上のSHA-256一致を確認。アプリ専用SharedPreferencesは試験前とSHA一致で復元。18768番フィクスチャ、追加したforward/reverse、IPv4/IPv6遮断を撤去し、既存18766 reverseは維持。通常HTTPSソースからGradle再ビルドPASS。ループバックのWebView内合成Cookieの消去は未確認だが、保存されたアプリ専用セッションは復元済みで、合成Cookieはループバック用。アプリは停止状態。バージョン・公開メタデータ・Releaseは変更しない。

**次のゲート**：XのLog inで遷移しない原因の正規導線での切り分け、未捕捉HTTPリダイレクト列、現行APKでのDiscord合成E2Eとエラー表示。Discordの本人認証入口は到達済み。実認証済みCookie・応答契約・実サイト投稿は未検証。詳細は [v04-mebuki-two-provider-live-20261124.json](gate-evidence/v04-mebuki-two-provider-live-20261124.json)。
