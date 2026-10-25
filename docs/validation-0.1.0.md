# 0.1.0-experimental 検証記録

公開対象はタグ`v0.1.0-experimental`、commit `c0986cf`、versionCode 2です。タグを移動していません。

- JDK 21 / SDK 37 / Build Tools 37.0.0でreleaseビルド、lint、7件の単体テスト、55件の依存ライセンス、14件のカタログ照合が成功。
- apksignerで署名と固定した証明書SHA-256を確認。デバッグ不可・検証用Activityなし・Xposedエントリなし。
- Android 17実機で14アプリの導入状況を表示。実験版OFFで実験版のみのリリースが候補から外れ、安定版は表示を維持。ONへの復元も確認。
- 同一証明書のrelease variant（versionCode 1）から、GitHub上の公開版（2）を管理アプリ自身が検出。
- HTTPSダウンロード、パッケージ／証明書／SHA-256／versionCodeの事前照合後、提供元許可から端末のパッケージインストーラーへ移動。
- インストールをキャンセルしても検証済みAPKは保持され、再度インストール可能。
- インストーラーから自己更新を実施し、端末のPackageManagerでversionCode 2を確認。再起動後の画面は「最新です」、更新数0。
- GitHub Actions `40154223654`成功。Actions artifactは0件。

複数アプリの連続インストール、Android 16の実機、各通信断の実機再現は未実施です。自己更新以外のモジュール、TailADB、SystemUIはこの試験で更新・再読み込みしていません。
