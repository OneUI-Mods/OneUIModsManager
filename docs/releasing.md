# リリース手順

## 署名鍵

非公開の`.signing/release.keystore`と`.signing/password`を用意します。aliasは`oneuimods-release`です。鍵とパスワードはGit・CI・配布ファイル・ログに含めません。フォルダは700、ファイルは600で保持してください。

公開証明書 SHA-256：

```text
f37313a1ddbe006257653b4772e9a2f43f0ddd5c0bcbf6dfe0f71c2fbd9aad73
```

## 作成

1. versionCodeを増やし、versionNameを更新する。カタログへリリース別の版情報を登録しない。
2. テスト・lint・releaseビルド・依存ライセンス・カタログ検査を実行する。
3. READMEの対応環境・制限を確認し、開発用の検証記録を分けて記載する。
4. 変更をコミットし、作業ツリーをクリーンにする。
5. JDK 21とAndroid SDKの環境で実行する。

```sh
python3 tools/package_release.py --source-ref HEAD
```

スクリプトがrelease variantをビルドし、zipalign、apksigner署名検証、公開証明書の照合、APK構造検査を行います。`dist/`に署名済みAPK・同じコミットのソースZIP・署名検証結果・SHA256SUMSを作成します。

6. 署名済みrelease APKを実機で確認する。試験用Activityを追加しない。
7. mainへpushしてCIを確認し、同じコミットに新規タグを作成する。既存タグを移動しない。
8. 外部の利用者向けのRelease本文とともに4種類の配布物と自動生成したrelease-metadata.jsonを公開する。実験版はprereleaseにする。
9. Actions artifactを残さない。Release assetsは保持する。

Release本文には利用者が得られる動作、対応OS、導入・更新時の操作、既知の制限を記載します。内部クラス名、個人の設定値、hook件数、CIやテスト作業の報告は記載しません。
