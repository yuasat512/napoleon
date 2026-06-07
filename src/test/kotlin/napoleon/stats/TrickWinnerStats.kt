package napoleon.stats

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.config.Config
import napoleon.core.Card
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.GameEngine
import napoleon.engine.view.ResolvedTrick
import napoleon.io.PlayerIO
import napoleon.session.GameController
import napoleon.session.ScoreKeeper

// 各トリックの「勝ち札」を分類別に集計する統計ハーネス。
// サンプリング条件は AiMatchStats と同等 (全員 HeuristicStrategy・seed 12345・
// 副官あり DEFAULT_GAMES 局、ソロ局は除外、playThrough=true で全 10 トリック消化)。
// 分類: ジョーカー / マイティ / 正J / 裏J / 切り札 A..2 / 非切り札 A..2。
// 役札 (joker/mighty/正J/裏J) を最優先で判定するので、切り札 J は常に正J、
// 同色 J は常に裏J、♠A は常にマイティに吸収され、各ランク行には現れない。
// 実行: ./gradlew :runTrickStats
fun main() {
    // Windows の既定 stdout は SJIS で日本語が化けるため UTF-8 に差し替える。
    System.setOut(utf8Stdout())

    val special = IntArray(WinnerCategory.entries.size)
    val trumpRank = IntArray(Rank.entries.size)
    val nonTrumpRank = IntArray(Rank.entries.size)
    val trumpDist = IntArray(Suit.entries.size)

    val io =
        runSession { trump, tricks ->
            trumpDist[trump.ordinal]++
            for (trick in tricks) {
                val card = trick.plays.first { it.playerId == trick.winnerId }.card
                tally(card, trump, special, trumpRank, nonTrumpRank)
            }
        }
    val collectedTotal = io.collected
    val soloTotal = io.soloSkipped

    val totalTricks = special.sum() + trumpRank.sum() + nonTrumpRank.sum()
    println("=== Trick-winning card distribution (all HeuristicStrategy) ===")
    println("games (with adjutant): $collectedTotal   (solo excluded: $soloTotal, seed: $SEED)")
    println("total tricks         : $totalTricks")
    if (totalTricks == 0) return

    println()
    println("[トランプ分布]")
    for (suit in Suit.realEntries) {
        println("  ${padEndDisp(suit.displayName, 10)}: ${pct(trumpDist[suit.ordinal], collectedTotal)}")
    }

    println()
    println("[役札]")
    for (c in WinnerCategory.entries) {
        println("  ${padEndDisp(c.label, 12)}: ${pct(special[c.ordinal], totalTricks)}")
    }

    println()
    println("[ランク別]   ${padEndDisp("切り札", 22)}非切り札")
    for (rank in Rank.entries.reversed()) {
        val trumpCell = pct(trumpRank[rank.ordinal], totalTricks)
        val nonTrumpCell = pct(nonTrumpRank[rank.ordinal], totalTricks)
        println("  ${padEndDisp(rank.shortName, 10)}${padEndDisp(trumpCell, 22)}$nonTrumpCell")
    }
}

private const val DEFAULT_GAMES = 50000
private const val SEED = 12345

private enum class WinnerCategory(
    val label: String,
) {
    JOKER("ジョーカー"),
    MIGHTY("マイティ"),
    RIGHT_J("正J"),
    LEFT_J("裏J"),
}

// 勝ち札 1 枚を分類して該当カウンタを 1 増やす。役札を先に判定し、残りを切り札/非切り札 × ランクで分ける。
private fun tally(
    card: Card,
    trump: Suit,
    special: IntArray,
    trumpRank: IntArray,
    nonTrumpRank: IntArray,
) {
    when {
        card.isJoker() -> special[WinnerCategory.JOKER.ordinal]++
        card.isMighty() -> special[WinnerCategory.MIGHTY.ordinal]++
        card.isRightBower(trump) -> special[WinnerCategory.RIGHT_J.ordinal]++
        card.isLeftBower(trump) -> special[WinnerCategory.LEFT_J.ordinal]++
        card.suit == trump -> trumpRank[card.rank.ordinal]++
        else -> nonTrumpRank[card.rank.ordinal]++
    }
}

// GameController を駆動し、副官あり局の確定時に (切り札, 全トリック) をコールバックへ渡す。
// 目標数に達したら次の遷移を止めて start() を抜けさせる (AiMatchStats と同じ停止方式)。
private class TrickCollectorIO(
    private val engine: GameEngine,
    private val targetNonSolo: Int,
    private val onGame: (trump: Suit, tricks: List<ResolvedTrick>) -> Unit,
) : PlayerIO {
    var collected = 0
        private set
    var soloSkipped = 0
        private set

    private var pauseRequested = false

    override fun onResult() {
        if (engine.napoleonId == engine.adjutantId) {
            soloSkipped++
            return
        }
        onGame(engine.trump, engine.trickHistory)
        collected++
        if (collected >= targetNonSolo) pauseRequested = true
    }

    override fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean = !pauseRequested
}

private fun runSession(onGame: (trump: Suit, tricks: List<ResolvedTrick>) -> Unit): TrickCollectorIO {
    val config =
        Config().apply {
            gameCount = DEFAULT_GAMES * 3 + 100
            auto = true
            playThrough = true
            gameLog = false
            seed = SEED
        }
    val engine = GameEngine(config)
    val strategies: List<PlayerStrategy> = List(PLAYER_COUNT) { HeuristicStrategy(engine) }
    val io = TrickCollectorIO(engine, DEFAULT_GAMES, onGame)
    val scoreKeeper = ScoreKeeper(engine.players, config, null)
    val controller = GameController(engine, strategies, scoreKeeper, null, io)
    controller.start()
    if (io.collected < DEFAULT_GAMES) {
        System.err.println(
            "warning: only collected ${io.collected} games (target $DEFAULT_GAMES) before gameCount cap.",
        )
    }
    return io
}

private fun pct(
    num: Int,
    den: Int,
): String = if (den == 0) "-" else "%6.2f%% (%d)".format(100.0 * num / den, num)
