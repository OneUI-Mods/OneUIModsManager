# Kotlin互換性の検証ツール

新しい検証ツールもKotlin。baseline-lock.jsonの元Javaと現Kotlinを別々にコンパイルし、依存JARと出力クラスディレクトリを1行1パスのファイルへ列挙する。JUnitの対照試験には、変更していない元Javaの試験クラスを使う。

```sh
kotlinc Differential.kt RecordContracts.kt -jvm-target 17 -include-runtime -d audit.jar
java -cp audit.jar dev.ryuya.behavioraudit.Differential old-classpath.txt new-classpath.txt pure-classes.txt
java -cp audit.jar dev.ryuya.behavioraudit.RecordContracts old-classpath.txt new-classpath.txt
```

pure-classes.txtには、事前にソースを確認したI/O・端末操作・スレッド起動のないモデルだけを列挙する。任意のクラスへ自動適用しない。比較は値と入力変化と例外型を扱い、アプリ固有の複雑な引数を作れないメソッドは対象外。Record試験はJavaの値を共有して参照のhashを比較する。完全な全入力証明ではない。

TextContractProbe.ktはAndroid SDK 37のandroid.jarをclasspathにしてJARへコンパイルし、d8でDEXへ変換する。専用のAndroid16/17エミュレータへrelease APKとDEXをコピーして、読み取り専用にする。引数はpackage名とAPKの端末パスのペア。

```sh
CLASSPATH=/data/local/tmp/probe.dex app_process / dev.ryuya.behaviorprobe.TextContractProbe dev.ryuya.oneuimods /data/local/tmp/module.apk
```

ウィンドウ・アプリcomponent・カメラ・音声・ネットワーク・設定変更を使わない。GalaxyへテストActivityや未署名APKをインストールしない。製品のKotlin runtimeを確認するためAPKのloaderはboot親から構成する。APK/JAR/DEX・実機ログ・設定・署名鍵はソースへ含めない。
