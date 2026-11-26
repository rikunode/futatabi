# futatabi

**ふたったー 3.1.1（`com.Nanasaki.ftutter`、versionCode 65）専用の Morphe パッチセット。** ARM64 で検証済み。GPL-3.0。

## 導入

1. [Google Play](https://play.google.com/store/apps/details?id=com.Nanasaki.ftutter) からふたったーをインストールし、バージョンが **3.1.1** であることを確認します。
2. [Releases](https://github.com/rikunode/futatabi/releases) から使用するバージョンの .mpp と SHA256SUMS を取得します。配布物の完全性は SHA256SUMS に記載した SHA-256 との照合で確認できます。
3. Morphe Manager に .mpp をパッチソースとして追加し、Expert mode の「Select other apps」からインストール済みのふたったーを選択します。対象バージョン 3.1.1（ARM64 の versionCode 65）はパッチの互換性メタデータに宣言されています。
4. パッチを選択して適用し、Manager の案内に沿って署名・インストールします。既定では元のアプリと共存するクローンになります。

Google Play で対象版を入手できない場合は [APKPure の 3.1.1 XAPK](https://apkpure.net/jp/%E3%81%B5%E3%81%9F%E3%81%A3%E3%81%9F%E3%83%BC-%E3%81%B5%E3%81%9F%E3%81%B0%E5%B0%82%E7%94%A8%E3%82%A2%E3%83%97%E3%83%AA/com.Nanasaki.ftutter/download) が予備の入手先です。Manager の[公式案内](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-process.md)ではスプリット APK の統合に対応していますが、このアプリのインストール済みスプリット／XAPK からの一連の操作は Manager の版・端末構成によって異なり、ここでは実機での再検証は行っていません。手動マージを必須とはしていません。[インストール済みアプリの選択に関する公式案内](https://github.com/MorpheApp/morphe-manager/blob/main/docs/home-screen.md)。

**クローンを使わない場合:** Clone app を OFF にしてください。既定のクローンパッケージ名は `com.Nanasaki.ftutter.morphe` で、元アプリとはデータを共有しません。署名やパッケージ名が変わると上書きできない場合があります。元アプリを不用意に削除しないでください。

## パッチ一覧

| パッチ | 既定 | 内容 |
| --- | --- | --- |
| Block banner ads | ON | 上部広告バナーと余白を除去（ARM64）。リワード広告には干渉しない |
| Board login button | ON | あいもげ・めぶきちゃんねるのカタログの広告ボタンをログインボタンに置換 |
| Clone app | ON | 元アプリと共存するパッケージ名へ変更 |
| Default dark mode | ON | テーマ未設定の場合のみダークモード。保存済み設定を保持 |
| Local board bridge | ON | 板一覧の補完とローカルブリッジによる閲覧・投稿 |
| Restore launcher icon | OFF | 公式アダプティブアイコンの不足する前景を APK 内の公式原画で補完 |
| futatabi branding | ON | アプリ名・ランチャーアイコンを futatabi に変更 |

## 閲覧・ログイン・投稿

初回起動時に二次元裏（img)、二次元裏（may)、あいもげ、あにまん、めぶきちゃんねるを登録します。移行済みでも不足板を補完し、img/may とめぶきちゃんねる（旧名：めぶき）の名前を正規化します。独自板と既存の板設定は保持します。標準5板を削除した場合は次回起動時に再追加されます。純正「ねこ」の削除は未移行かつ純正構成の場合のみです。

通常のふたったーの板一覧・カタログ・スレ画面から閲覧し、純正の返信欄からレスを投稿できます。純正ふたば板の投稿には干渉しません。別のランチャー入口 **futatabi 投稿** からも拡張の投稿・認証・診断機能を利用できます。

| 板 | ログイン | 純正欄でのレス投稿 | 拡張ホームの投稿 |
| --- | --- | --- | --- |
| あいもげ | 公式 PKCE 認証・承認 | 対応 | 新規スレ・レス、画像添付 |
| あにまん | 専用ログインなし・PHP セッション自動保持 | 対応 | 新規スレ・レス、画像添付 |
| めぶきちゃんねる | アプリ内 WebView で X／Discord 認証 | 対応 | テキストレス（新規スレ・添付は対象外） |

あいもげはカタログのログインボタンまたは拡張ホームから公式認証を行います。認証の期限切れ時は再ログインしてください。あにまんは専用ログイン不要です。めぶきちゃんねるはアプリ内の WebView で本人が認証し、公式サイトへ戻った後にセッション確認・保存を行います。Google 認証は対象外です。既存の診断機能も利用できます。投稿結果が不明な場合はスレを更新してから再送を判断してください。

## 端末内データ・通信

- ブリッジは端末の `127.0.0.1:18765` で動作します。認証情報や投稿履歴はアプリの非公開 SharedPreferences に保存されます。外部への独自収集サーバーは使用しません。
- あいもげの公式アプリ認可フローでは、初回生成して端末内に保持するランダム UUID `installation_id` を公式 API の `installationId` として送信します。認可をアプリのインストール単位に関連づけるための永続的な擬似端末識別子です。端末のハードウェア ID ではありません。
- 各板への通信は対応する公式サービスへ行います。画像添付のファイル名は対応 MIME 型に基づく `attachment.<拡張子>` に正規化します。
- 板一覧の書き換え前の値をアプリ内に退避します。クローンのデータは元アプリと分離されます。

## 検証状況

2026-11-26 の所有者実機確認では、**めぶきちゃんねるの純正スレ画面表示・純正欄からの投稿、あにまん・あいもげの純正欄からの投稿がすべて成功**しています。これは所有者からの実機確認報告であり、今回のラウンドでは実投稿・実ログインを再実施していません。

過去のローカル検証は [統合試験記録](docs/bridge-v0.2.0-emulator-gate-20261122.md) および [リリースノート](https://github.com/rikunode/futatabi/releases) を参照してください。

## 対象ファイルの照合情報

| 入力 | SHA-256 |
| --- | --- |
| ふたったー 3.1.1 XAPK | `11dae3fa2c2882f1223cacbdc14b62bcf2d6ed04bc4436d9fc988e98d6ea53dc` |
| ベース APK | `f8093dd9cae64a48b0e74831800782a747045902e2171855a672195ef5690486` |

APK、XAPK、デコンパイル済みソース、個人用署名鍵は配布しません。配布対象はパッチセット（.mpp）および付随する検証・ライセンス文書です。

## ライセンス・出典

- [GPL-3.0](LICENSE)
- [第三者ライセンス・通知](THIRD_PARTY_NOTICES/NOTICE.md)（配布 .mpp の META-INF に同梱）
- Clone app は [Morphe 公式 CloneAppPatch.kt](https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/all/misc/clone/CloneAppPatch.kt) からの GPL-3.0 派生です。著作権・ReVanced 由来・GPL 第7条のファイル固有通知をソースに保持しています。
- [公式あいもげ API 仕様](https://api.nijiurachan.net/ui)／[API 利用ガイド](https://wiki.nijiurachan.net/運営公式/API利用ガイド)
- [あにまんの公開 HTML / main.js](https://bbs.animanch.com/main.js) を参照し独立実装しています。
