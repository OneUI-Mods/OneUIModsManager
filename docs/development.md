# 開発者向け

## ビルド

JDK 21、Android SDK 37、Build Tools 37.0.0を使用します。minSdkは36です。

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:writeRuntimeDependencyList
python3 tools/check_license_catalog.py
python3 tools/check_catalog.py
python3 tools/package_release.py --check-release-apk app/build/outputs/apk/release/app-release-unsigned.apk
```

通常のビルドには署名鍵は不要です。CIは未署名release APKの構造を検査し、APKの配布やActions artifactへの保存は行いません。

## 構成

- `Catalog`：同梱／キャッシュ／GitHubのアプリ一覧。無効な取得結果は既存カタログへ戻す。
- `ManagerState`：リリースのキャッシュとETag、取得状態。ネットワーク処理はワーカースレッド。
- `ReleasePolicy`／`VersionOrder`：実験版の選別、APK由来のversionCodeとタグの数値バージョン比較。
- `DownloadService`：ユーザー操作で開始するforeground dataSync。順次ダウンロード、キャンセル、部分ファイル削除。
- `ApkVerifier`：ハッシュ、パッケージ、証明書履歴、デバッグ不可、最小OS、更新方向の確認。
- `InstallerActivity`：設定許可とAndroidインストーラーへの受け渡し。まとめて更新する場合は自身を最後にする。
- `ReadyStore`：検証後のAPKだけを内部ストレージに保存。更新済みのファイルを消去。

独自のサイレントインストールは行いません。APKの最終的な暗号学的検証とインストール可否はAndroidインストーラーが担当します。管理アプリ内の事前照合を、Androidの署名検証の代替とは扱いません。

## カタログ

管理対象の追加・表示情報・パッケージ名・公開証明書の更新は、`catalog/apps.json`だけを編集してmainへpushします。Java・Manifest・同梱用JSONの手動編集、管理アプリの再ビルド・再インストール・新しいReleaseの作成は不要です。

端末の「更新を確認」、または起動時の自動確認で、`https://raw.githubusercontent.com/OneUI-Mods/OneUIModsManager/main/catalog/apps.json`を取得します。JSONを検証してからキャッシュへ保存し、無効なデータや通信失敗では最後に使えた一覧を維持します。自動確認は設定に従い前回の完了から6時間以上空いたときに行います。手動確認はこの間隔を待ちません。

初回オフライン用の`assets/catalog.json`は、ビルド時の`generateCatalogAssets`が公開JSONから自動生成します。生成先は`app/build/generated/catalogAssets`で、Gitへ登録しません。APK構造検査で同梱データと元の公開JSONが一致することも照合します。

`repo`、`packageName`、公開証明書のSHA-256、表示情報だけを登録します。`knownVersions`のようなリリース別の値は登録しません。新しいリリースの版・説明・公開日・実験版区分・APK・ハッシュは各リポジトリのGitHub Release APIから取得します。秘密鍵・パスワードは登録しません。署名鍵ローテーション時はAndroidの署名履歴と一致する証明書を確認してから更新します。

更新の流れは次のとおりです。

1. 新しいアプリの署名済みrelease APK・パッケージ名・公開証明書・バージョンを確認する。
2. `catalog/apps.json`へ登録する。同じリポジトリ・パッケージ名を重複登録しない。
3. `python3 tools/check_catalog.py`で検証し、mainへpushする。
4. 既存の管理アプリで「更新を確認」を実行して反映を確認する。

URLはOneUI-Modsの該当リポジトリ配下のReleaseだけを許可します。リダイレクト先もGitHubのHTTPSホストに限定します。GitHub APIのトークンを端末へ配布しません。

## 検証

単体テストは数値バージョン、実験版、バックポート、同じversionNameの新しいビルド、URL制限を対象にします。実機では一覧、チャンネル切替、リリース表示、ダウンロード、提供元許可、インストールのキャンセルと成功を確認してください。自己更新の試験には、同じ鍵で署名しversionCodeだけが低いrelease variantを使います。接続を維持しているTailADBなどを試験のために更新しないでください。

Javaはgoogle-java-formatを使用します。画面には内部クラス名や例外スタックを表示せず、開発記録は本ドキュメントへ記載します。

## Android 17での初回確認

署名済みrelease variantを実機に導入し、対象14アプリのインストール状況、GitHubからの取得、実験版OFF時に実験版だけのアプリが更新候補から外れること、安定版が引き続き表示されることを確認しました。SystemUIと対象モジュールの再起動は行っていません。

## リリースのビルド情報

管理一覧とリリースは分離します。各リポジトリの公開Releaseへ`release-metadata.json`を添付すると、タグだけでなくAPKのversionCodeで比較できます。情報がない旧リリースもタグによる比較を継続します。ダウンロード後は、すべてのAPKをパッケージ・署名・ハッシュ・実際のversionCodeで再検証します。

`tools/release_metadata.py`は署名済みAPKからパッケージ、versionCode、versionName、ファイル名、SHA-256を自動生成します。公開カタログの署名と一致する非debuggable APKだけを受け付け、秘密鍵は使用しません。管理アプリ自身の配布スクリプトも自動生成します。

公開済みReleaseへ追加する場合は、次を実行できます。`--dry-run`では照合だけを行います。APK・タグ・リリース文は変更しません。

```sh
python3 tools/publish_release_metadata.py --repo OneUI-Mods/対象リポジトリ --tag 対象タグ
```

`.github/workflows/release-metadata.yml`は、この処理をRelease公開後に呼び出すための再利用可能なworkflowです。APKの署名を検証してJSONを添付するだけで、ReleaseやAPKを自動作成しません。署名鍵をCIへ渡しません。

管理アプリは安定版・実験版の候補に限って小さいJSONを取得し、GitHubが返すJSON自身とAPKのdigest、登録済みパッケージとファイル名を照合します。キャッシュは端末内で保持し、起動時のキャッシュ読み込みでは通信しません。版情報のためだけにAPK本体を取得する処理はありません。
