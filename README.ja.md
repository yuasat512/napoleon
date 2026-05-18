[English](./README.md) | **日本語**

# Napoleon

5人用トリックテイキングカードゲーム「ナポレオン」のデスクトップアプリケーション。COM 対戦。Kotlin + Swing 製。

![スクリーンショット](docs/images/screen-layout.jpg)

> [!NOTE]
> COM の思考ルーチンは現在改良中です。弱い手や不自然な手を選ぶことがあります。

## 必要環境

- 実行 (Fat JAR): JDK 17 以上
- Gradle によるビルド: JDK 25 (Gradle Toolchains が自動取得)

## 実行

```bash
./gradlew run
```

## ビルド (配布用 Fat JAR)

```bash
./gradlew shadowJar
```

`build/libs/napoleon-all.jar` が生成される。Windows では同梱の `Napoleon.vbs` をダブルクリックで起動できる。

## 操作

[docs/manual.ja.md](docs/manual.ja.md) (日本語) または [docs/manual.md](docs/manual.md) (English) を参照。

## ルール

[docs/game-spec.ja.md](docs/game-spec.ja.md) (日本語) または [docs/game-spec.md](docs/game-spec.md) (English) を参照。

## Contributing

個人プロジェクトのため、Issue や Pull Request の対応は行っておりません。Fork はご自由にどうぞ。

## アセット

カード絵柄 (`src/main/resources/{1_DeckM,2_DeckS}.png`) は、ChatGPT (OpenAI) で生成した画像 (`src/test/resources/deck-source/`) から派生しています。

## ライセンス

[MIT License](LICENSE)。商用・改変・再配布など自由にどうぞ。第三者ライブラリのライセンスは [NOTICE](NOTICE) を参照。
