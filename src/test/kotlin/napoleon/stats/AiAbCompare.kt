package napoleon.stats

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.ai.heuristic.HeuristicVariant
import napoleon.config.Config
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.GameEngine
import napoleon.io.PlayerIO
import napoleon.session.GameController
import napoleon.session.ScoreKeeper

// 席0 の play 系挙動だけを CANDIDATE / BASELINE で切り替えて固定シードで対戦させ、ナポレオン/副官/連合 の
// 各ロールで席0の勝率を比較する A/B ハーネス。改良が勝率に効いている (退行していない) かをロール別に測る。
//
// 重要: 入札・副官選択は variant 非依存なので、同一シードなら CANDIDATE run と BASELINE(対照) run は
// 「全く同じ配牌・同じロール割当・同じソロスキップ」になり、席0は kitty/play でのみ分岐する。よって
// 両 run の席0勝率の差は「同一局面での改良手の純粋な効果」= 席順バイアスを含まないペア比較になる。
// (席0固定の素の比較には席順バイアスがある。実測でナポ役は約 -1.9pt 席0が不利なので、必ず対照との差で見る。)
// 副官兼任 (ソロ) の局はノイズとして除外。実行: ./gradlew :runAbCompare  (-Dgames= -Dseed= で上書き可)
fun main() {
    val games = System.getProperty("games")?.toIntOrNull() ?: DEFAULT_GAMES
    val seed = System.getProperty("seed")?.toIntOrNull() ?: DEFAULT_SEED

    val cand = runConfig(HeuristicVariant.CANDIDATE, games, seed)
    val ctrl = runConfig(HeuristicVariant.BASELINE, games, seed)
    report(cand, ctrl, games, seed)
}

private const val DEFAULT_GAMES = 20000
private const val DEFAULT_SEED = 12345
private const val ROLE_COUNT = 3
private val ROLE_LABELS = arrayOf("Napoleon", "Adjutant", "Ally")

private class RoleTally {
    var games = 0L
    var wins = 0L
}

// 1 run の結果。seat0[role] = 席0がそのロールのときの勝敗、others[role] = 席1–4 プールの勝敗。
private class RunResult {
    val seat0 = Array(ROLE_COUNT) { RoleTally() }
    val others = Array(ROLE_COUNT) { RoleTally() }
    var collected = 0
    var soloSkipped = 0
}

// 席0 を p0Variant、席1–4 を BASELINE にして 1 セッションを回し、副官あり局を games 局集める。
private fun runConfig(
    p0Variant: HeuristicVariant,
    games: Int,
    seed: Int,
): RunResult {
    val result = RunResult()
    val config =
        Config().apply {
            // ソロ除外で目標に届くよう上限を多めに取る。通常はその手前で pause して止まる。
            gameCount = games * 3 + 100
            auto = true
            playThrough = true
            gameLog = false
            this.seed = seed
        }
    val engine = GameEngine(config)
    val strategies: Array<PlayerStrategy> =
        Array(PLAYER_COUNT) { seat ->
            HeuristicStrategy(engine, if (seat == 0) p0Variant else HeuristicVariant.BASELINE)
        }
    val io = AbCollectorIO(engine, games, result)
    val scoreKeeper = ScoreKeeper(engine.players, config, null)
    val controller = GameController(engine, strategies, scoreKeeper, null, io)
    controller.start()
    if (result.collected < games) {
        System.err.println("warning: only collected ${result.collected} games (target $games) for $p0Variant.")
    }
    return result
}

// 副官あり局を target 局集めたら pause してステートマシンを止める。各局確定時に席ごとのロールと
// 勝敗 (その席が属する陣営が勝ったか) を seat0 / others へ振り分けて加算する。
private class AbCollectorIO(
    private val engine: GameEngine,
    private val target: Int,
    private val result: RunResult,
) : PlayerIO {
    private var pauseRequested = false

    override fun onResult() {
        val nap = engine.napoleonId
        val adj = engine.adjutantId
        if (nap == adj) {
            result.soloSkipped++
            return
        }
        val napHonors = engine.players[nap].honorsTaken + engine.players[adj].honorsTaken
        val napWin = napHonors >= engine.bidTarget
        for (seat in 0 until PLAYER_COUNT) {
            val role =
                when (seat) {
                    nap -> 0
                    adj -> 1
                    else -> 2
                }
            val win = if (seat == nap || seat == adj) napWin else !napWin
            val t = if (seat == 0) result.seat0[role] else result.others[role]
            t.games++
            if (win) t.wins++
        }
        result.collected++
        if (result.collected >= target) pauseRequested = true
    }

    override fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean = !pauseRequested
}

private fun report(
    cand: RunResult,
    ctrl: RunResult,
    games: Int,
    seed: Int,
) {
    println("=== A/B: seat-0 CANDIDATE vs BASELINE play, win rate by role ===")
    println(
        "seed: $seed, target/run: $games (collected ${cand.collected}/${ctrl.collected}, " +
            "solo excluded ${cand.soloSkipped}/${ctrl.soloSkipped})",
    )
    println("paired on identical deals (bid/adjutant are variant-independent); effect = seat0 CAND - seat0 BASE.")
    if (cand.collected == 0 || ctrl.collected == 0) return
    println()
    println(
        "%-9s  %-21s  %-21s  %-9s  %s".format(
            "role",
            "seat0 CANDIDATE",
            "seat0 BASELINE",
            "effect",
            "others CAND->BASE",
        ),
    )
    for (r in 0 until ROLE_COUNT) {
        row(ROLE_LABELS[r], cand.seat0[r], ctrl.seat0[r], cand.others[r], ctrl.others[r])
    }
    row("overall", pool(cand.seat0), pool(ctrl.seat0), pool(cand.others), pool(ctrl.others))
    println()
    println("effect>0: CANDIDATE play raises seat-0 win rate in that role. (note: fewer blunders != higher win rate)")
}

// 席0の CAND vs BASE 勝率差 (effect, pt) を中心に、参考として席1–4 (others) の勝率推移も添える。
private fun row(
    label: String,
    candS0: RoleTally,
    ctrlS0: RoleTally,
    candOthers: RoleTally,
    ctrlOthers: RoleTally,
) {
    val effect = (rate(candS0) - rate(ctrlS0)) * 100
    println(
        "%-9s  %-21s  %-21s  %+7.2fpt  %5.2f -> %5.2f".format(
            label,
            cell(candS0),
            cell(ctrlS0),
            effect,
            rate(candOthers) * 100,
            rate(ctrlOthers) * 100,
        ),
    )
}

private fun pool(byRole: Array<RoleTally>): RoleTally {
    val sum = RoleTally()
    for (t in byRole) {
        sum.games += t.games
        sum.wins += t.wins
    }
    return sum
}

private fun rate(t: RoleTally): Double = if (t.games == 0L) 0.0 else t.wins.toDouble() / t.games

private fun cell(t: RoleTally): String = "%6.2f%% (%d/%d)".format(rate(t) * 100, t.wins, t.games)
