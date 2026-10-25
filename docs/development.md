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
- `ReleasePolicy`／`VersionOrder`：実験版の選別、登録済みversionCodeと数値バージョン比較。
- `DownloadService`：ユーザー操作で開始するforeground dataSync。順次ダウンロード、キャンセル、部分ファイル削除。
- `ApkVerifier`：ハッシュ、パッケージ、証明書履歴、デバッグ不可、最小OS、更新方向の確認。
- `InstallerActivity`：設定許可とAndroidインストーラーへの受け渡し。まとめて更新する場合は自身を最後にする。
- `ReadyStore`：検証後のAPKだけを内部ストレージに保存。更新済みのファイルを消去。

独自のサイレントインストールは行いません。APKの最終的な暗号学的検証とインストール可否はAndroidインストーラーが担当します。管理アプリ内の事前照合を、Androidの署名検証の代替とは扱いません。

## カタログ

`catalog/apps.json`を編集し、`app/src/main/assets/catalog.json`にも同じ内容を反映してください。起動時の同梱一覧とGitHub上の一覧を同じスキーマで読みます。

`repo`、`packageName`、公開証明書のSHA-256、表示情報、`knownVersions`を登録します。秘密鍵・パスワードは登録しません。アプリ更新時には配布APKから確認したversionCodeとversionNameを対応するタグに追加してください。署名鍵ローテーション時はAndroidの署名履歴と一致する証明書を確認してから更新します。

URLはOneUI-Modsの該当リポジトリ配下のReleaseだけを許可します。リダイレクト先もGitHubのHTTPSホストに限定します。GitHub APIのトークンを端末へ配布しません。

## 検証

単体テストは数値バージョン、実験版、バックポート、同じversionNameの新しいビルド、URL制限を対象にします。実機では一覧、チャンネル切替、リリース表示、ダウンロード、提供元許可、インストールのキャンセルと成功を確認してください。自己更新の試験には、同じ鍵で署名しversionCodeだけが低いrelease variantを使います。接続を維持しているTailADBなどを試験のために更新しないでください。

Javaはgoogle-java-formatを使用します。画面には内部クラス名や例外スタックを表示せず、開発記録は本ドキュメントへ記載します。

## Android 17での初回確認

署名済みrelease variantを実機に導入し、対象14アプリのインストール状況、GitHubからの取得、実験版OFF時に実験版だけのアプリが更新候補から外れること、安定版が引き続き表示されることを確認しました。SystemUIと対象モジュールの再起動は行っていません。
