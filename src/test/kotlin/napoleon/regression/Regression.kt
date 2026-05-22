package napoleon.regression

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.ai.random.RandomStrategy
import napoleon.config.Config
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.GameEngine
import napoleon.engine.GameRecorder
import napoleon.io.PlayerIO
import napoleon.record.GameRecordReader
import napoleon.record.ReplayEntry
import napoleon.replay.ReplayStrategy
import napoleon.session.GameController
import napoleon.session.ScoreKeeper
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

// 変更があったときに一発叩く回帰テスト集。種類別にメソッドを分けてある。
// main から順次呼び、いずれかが check 失敗・例外送出すれば JavaExec が非0終了する。
fun main() {
    replayConsistency()
    heuristicSmoke()
}

// 全員 RandomStrategy・固定シードで 100 ゲームを走らせ、生成ログを
// src/test/resources/debug.txt と完全一致比較する。続けて同記録を ReplayStrategy で
// 再走させ、再度完全一致を要求する (リプレイ整合性回帰)。
// AI/エンジン挙動に影響する変更を入れたら debug.txt の更新が必要。
private fun replayConsistency() {
    val baselinePath = Path("log", "napoleon-regression.txt")
    val replayPath = Path("log", "napoleon-replay.txt")
    try {
        runRandomBaseline(baselinePath)
        val baseline = baselinePath.readText()
        val expected = Path("src/test/resources", "debug.txt").readText()
        check(stripSep(baseline) == stripSep(expected)) { "unmatch with debug.txt" }

        val entries = GameRecordReader.parse(baselinePath)
        for (entry in entries) replayOne(entry, replayPath)
        val replayed = replayPath.readText()
        check(stripSep(baseline) == stripSep(replayed)) { "unmatch with replay" }
    } finally {
        baselinePath.deleteIfExists()
        replayPath.deleteIfExists()
    }
}

private fun runRandomBaseline(logPath: Path) {
    val engine =
        GameEngine(
            Config().apply {
                gameCount = 100
                auto = true
                seed = 12345
            },
        )
    val strategies: Array<PlayerStrategy> = Array(PLAYER_COUNT) { RandomStrategy(engine) }
    val recorder = GameRecorder(engine, logPath)
    val scoreKeeper = ScoreKeeper(engine.players, engine.config, null)
    val controller = GameController(engine, strategies, scoreKeeper, recorder, object : PlayerIO {})
    controller.start()
}

private fun replayOne(
    entry: ReplayEntry,
    replayPath: Path,
) {
    val config =
        Config().apply {
            gameCount = 1
            auto = true
        }
    val engine = GameEngine(config)
    val strategies: Array<PlayerStrategy> =
        Array(PLAYER_COUNT) { pid ->
            val myBids =
                entry.record.bidSequence.filterIndexed { k, _ ->
                    (entry.record.bidFirstPlayerId + k) % PLAYER_COUNT == pid
                }
            ReplayStrategy(pid, engine, entry.record, myBids)
        }
    val recorder = GameRecorder(engine, replayPath)
    val scoreKeeper = ScoreKeeper(engine.players, config, null)
    val controller = GameController(engine, strategies, scoreKeeper, recorder, object : PlayerIO {})
    engine.fixedDeck = entry.deck
    controller.start(entry.record.bidFirstPlayerId)
}

// 全員 HeuristicStrategy で複数シード × 100 ゲームを完走させ、例外なく走り切ることだけを確認する。
// 不正手はエンジンが error() で落とす設計のため、HeuristicStrategy が違法手を選ぶ・空コレクション
// への first { } / 列挙網羅漏れなどのリグレッションを入れれば例外で検知できる。
// 期待値ログは持たないので、HeuristicStrategy の更新ごとに追従する必要はない。
private fun heuristicSmoke() {
    for (seed in 1..10) {
        val config =
            Config().apply {
                gameCount = 100
                auto = true
                this.seed = seed
            }
        val engine = GameEngine(config)
        val strategies: Array<PlayerStrategy> = Array(PLAYER_COUNT) { HeuristicStrategy(engine) }
        val scoreKeeper = ScoreKeeper(engine.players, config, null)
        val controller = GameController(engine, strategies, scoreKeeper, null, object : PlayerIO {})
        controller.start()
    }
}

// 各ゲームの先頭に書かれるタイムスタンプ行 (=== yyyy-MM-dd HH:mm:ss) のうち、
// タイムスタンプだけを落として区切り行 "===" は残す (ゲーム境界のずれを検知できるように)。
private fun stripSep(s: String) = s.replace(Regex("(?m)^=== .*"), "===")
