package napoleon.stats

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.config.Config
import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.GameEngine
import napoleon.engine.view.AiContext
import napoleon.io.PlayerIO
import napoleon.session.GameController
import napoleon.session.ScoreKeeper

// AI 同士を固定シードで多数回対戦させて挙動を数値化する統計ハーネス。
// 回帰テストと違い pass/fail はせず、集計結果を標準出力するだけ。
//   バリエーション1: 全員 HeuristicStrategy。宣言枚数の平均・両軍の勝率/獲得枚数の平均を見る。
//   バリエーション2: 連合席のみ副官 ID を透視 (オラクル)。副官特定を完璧にした場合の連合勝率の上限を測る。
// 副官なしの局はノイズとして集計から除外し、副官ありの局を目標数ちょうど集める。
// 実行: ./gradlew :runAiStats
fun main() {
    runBaseline()
    runOracleAllies()
}

private const val DEFAULT_GAMES = 10000
private const val SEED = 12345

// 1 局ぶんの確定結果 (神視点・副官ありの局のみ生成される)。
private class GameSnapshot(
    val declared: Int,
    val napHonors: Int,
    val allyHonors: Int,
    val napWin: Boolean,
)

// GameController を駆動しつつ、各局確定時 (Phase.RESULT の onResult) に副官あり局を集計コールバックへ渡す。
// 目標数に達したら次の遷移で onTransition=false を返してステートマシンを止め、start() を抜けさせる。
private class StatsCollectorIO(
    private val engine: GameEngine,
    private val targetNonSolo: Int,
    private val onGame: (GameSnapshot) -> Unit,
) : PlayerIO {
    var collected = 0
        private set
    var soloSkipped = 0
        private set
    var redeals = 0
        private set

    private var pauseRequested = false

    // 全員2巡パス (宣言なし) で配り直しになった回数。完了せず DEAL へ戻るので onResult は通らない。
    override fun onRedeal() {
        redeals++
    }

    override fun onResult() {
        val nap = engine.napoleonId
        val adj = engine.adjutantId
        if (nap == adj) {
            soloSkipped++
            return
        }
        val total = engine.players.sumOf { it.honorsTaken }
        val napHonors = engine.players[nap].honorsTaken + engine.players[adj].honorsTaken
        val declared = engine.bidTarget
        onGame(
            GameSnapshot(
                declared = declared,
                napHonors = napHonors,
                allyHonors = total - napHonors,
                napWin = napHonors >= declared,
            ),
        )
        collected++
        if (collected >= targetNonSolo) pauseRequested = true
    }

    override fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean = !pauseRequested
}

// 1 セッションを回して副官あり局を targetNonSolo 局集める。strategyFactory は席ごとの戦略を生成する。
private fun runSession(
    strategyFactory: (GameEngine) -> List<PlayerStrategy>,
    onGame: (GameSnapshot) -> Unit,
    targetNonSolo: Int = DEFAULT_GAMES,
): StatsCollectorIO {
    val config =
        Config().apply {
            // ソロ除外で目標に届くよう上限を多めに取る。通常はその手前で pause して止まる。
            gameCount = targetNonSolo * 3 + 100
            auto = true
            playThrough = true
            gameLog = false
            seed = SEED
        }
    val engine = GameEngine(config)
    val strategies = strategyFactory(engine)
    val io = StatsCollectorIO(engine, targetNonSolo, onGame)
    val scoreKeeper = ScoreKeeper(engine.players, config, null)
    val controller = GameController(engine, strategies, scoreKeeper, null, io)
    controller.start()
    if (io.collected < targetNonSolo) {
        System.err.println(
            "warning: only collected ${io.collected} games (target $targetNonSolo) before gameCount cap.",
        )
    }
    return io
}

private fun runBaseline() {
    var declaredSum = 0L
    var napWins = 0
    var napHonorSum = 0L
    var allyHonorSum = 0L

    val io =
        runSession({ engine -> List(PLAYER_COUNT) { HeuristicStrategy(engine) } }, { g ->
            declaredSum += g.declared
            if (g.napWin) napWins++
            napHonorSum += g.napHonors
            allyHonorSum += g.allyHonors
        })

    val n = io.collected
    println("=== Variation 1: all HeuristicStrategy ===")
    println("games (with adjutant): $n   (solo excluded: ${io.soloSkipped}, seed: $SEED)")
    println("redeal rate           : ${redealRate(io)}")
    if (n == 0) return
    println("avg declared target   : %.3f".format(declaredSum.toDouble() / n))
    println("Napoleon side winrate : ${pct(napWins, n)}")
    println("Allies   side winrate : ${pct(n - napWins, n)}")
    println("avg honors  Napoleon  : %.3f / %d".format(napHonorSum.toDouble() / n, HONOR_COUNT))
    println("avg honors  Allies    : %.3f / %d".format(allyHonorSum.toDouble() / n, HONOR_COUNT))
}

// 連合席のみ副官 ID を透視させ、副官特定を完璧にしたときの連合勝率の上限を測る。
// バリエーション1 との差分が「特定強化で得られる伸びしろ」の目安になる。
private fun runOracleAllies() {
    var declaredSum = 0L
    var napWins = 0
    var napHonorSum = 0L
    var allyHonorSum = 0L

    val io =
        runSession({ engine ->
            val oracle = OracleAdjutantContext(engine)
            List(PLAYER_COUNT) { HeuristicStrategy(oracle) }
        }, { g ->
            declaredSum += g.declared
            if (g.napWin) napWins++
            napHonorSum += g.napHonors
            allyHonorSum += g.allyHonors
        })

    val n = io.collected
    println()
    println("=== Variation 2: allies see adjutant id (oracle) ===")
    println("games (with adjutant): $n   (solo excluded: ${io.soloSkipped}, seed: $SEED)")
    if (n == 0) return
    println("avg declared target   : %.3f".format(declaredSum.toDouble() / n))
    println("Napoleon side winrate : ${pct(napWins, n)}")
    println("Allies   side winrate : ${pct(n - napWins, n)}")
    println("avg honors  Napoleon  : %.3f / %d".format(napHonorSum.toDouble() / n, HONOR_COUNT))
    println("avg honors  Allies    : %.3f / %d".format(allyHonorSum.toDouble() / n, HONOR_COUNT))
}

// 連合席 (= 自分がナポレオンでも副官でもない席) が手番のときだけ副官 ID を透視させる AiContext デコレータ。
// ナポレオン軍席には実際の公開状況をそのまま見せるので、両軍に差がつくのは連合の副官特定能力だけになる。
private class OracleAdjutantContext(
    private val engine: GameEngine,
) : AiContext by engine {
    override val adjutantIdIfRevealed: Int?
        get() {
            val viewer = engine.curPlayer.id
            val viewerIsAlly = viewer != engine.napoleonId && viewer != engine.adjutantId
            if (viewerIsAlly) return engine.adjutantId
            return if (engine.adjutantRevealed) engine.adjutantId else null
        }
}

// 配り直し率 = 配り直し回数 / 総配局数。総配局数は完了局 (副官あり + ソロ) と配り直しの和。
private fun redealRate(io: StatsCollectorIO): String {
    val deals = io.collected + io.soloSkipped + io.redeals
    return pct(io.redeals, deals)
}

private fun pct(
    num: Int,
    den: Int,
): String = if (den == 0) "-" else "%6.2f%% (%d/%d)".format(100.0 * num / den, num, den)
