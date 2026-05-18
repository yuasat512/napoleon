**English** | [日本語](./README.ja.md)

# Napoleon

A Kotlin + Swing desktop app for **Napoleon**, a 5-player Japanese trick-taking card game with bidding, played against built-in AI opponents.

> The in-game UI is in Japanese.

![Screenshot](docs/images/screen-layout.jpg)

> [!NOTE]
> The AI is a work in progress and may occasionally make weak or unnatural plays.

## Requirements

- Run (Fat JAR): JDK 17 or later
- Build via Gradle: JDK 25 (auto-provisioned by Gradle Toolchains)

## Run

```bash
./gradlew run
```

## Build (distributable Fat JAR)

```bash
./gradlew shadowJar
```

This produces `build/libs/napoleon-all.jar`. On Windows, you can launch it by double-clicking the bundled `Napoleon.vbs`.

## Manual

See [docs/manual.md](docs/manual.md) (English) or [docs/manual.ja.md](docs/manual.ja.md) (Japanese).

## Rules

See [docs/game-spec.md](docs/game-spec.md) (English) or [docs/game-spec.ja.md](docs/game-spec.ja.md) (Japanese).

## Contributing

This is a personal project. Issues and pull requests will not be reviewed. Feel free to fork.

## Assets

The card face images (`src/main/resources/{1_DeckM,2_DeckS}.png`) are derived from sources generated with ChatGPT (OpenAI), located in `src/test/resources/deck-source/`.

## License

[MIT License](LICENSE). Free for commercial use, modification, and redistribution. See [NOTICE](NOTICE) for third-party licenses.
