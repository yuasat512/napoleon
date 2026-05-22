package napoleon.ui

import napoleon.ai.PlayerStrategy
import napoleon.config.Config
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.GameEngine
import napoleon.io.PlayerIO
import napoleon.record.ReplayEntry
import napoleon.replay.ReplayStrategy
import napoleon.session.GameController
import napoleon.session.ScoreKeeper
import javax.swing.SwingUtilities

class ReplaySession(
    entry: ReplayEntry,
    private val repaint: () -> Unit,
    private val onEnd: (errorMessage: String?) -> Unit,
) : PlayerIO {
    private val replayConfig =
        Config().apply {
            gameCount = 1
            wait = 0
            auto = true
            playThrough = entry.record.tricks.size == HAND_SIZE
            showAllCards = true
            bidMinTarget = 1
        }
    private val replayEngine = GameEngine(replayConfig)
    val view = GameView(replayEngine, replayConfig)
    private val replayScoreKeeper = ScoreKeeper(replayEngine.players, replayConfig, null)
    private val controller: GameController
    private var waitingForAdvance = false
    private var resultShown = false

    init {
        val strategies =
            Array<PlayerStrategy>(PLAYER_COUNT) { pid ->
                val myBids =
                    entry.record.bidSequence.filterIndexed { k, _ ->
                        (entry.record.bidFirstPlayerId + k) % PLAYER_COUNT == pid
                    }
                ReplayStrategy(pid, replayEngine, entry.record, myBids)
            }
        replayEngine.fixedDeck = entry.deck
        controller = GameController(replayEngine, strategies, replayScoreKeeper, null, this)
        guard { controller.start(entry.record.bidFirstPlayerId) }
        repaint()
    }

    fun advanceIfWaiting() {
        if (waitingForAdvance) {
            waitingForAdvance = false
            guard { controller.step() }
        }
    }

    // ログは手書き編集可能なため不正手で例外を投げ得る。捕捉してタイトルに戻す。
    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            SwingUtilities.invokeLater {
                onEnd("不正な手を検出しました。タイトルに戻ります。")
            }
        }
    }

    private fun update(block: GameView.() -> Unit) {
        view.block()
        repaint()
    }

    override fun onDeal() = update { onDeal() }

    override fun onBid() = update { onBid() }

    override fun onRedeal() = update { onRedeal() }

    override fun onAppointPre() = update { onAppointPre() }

    override fun onAppoint() = update { onAppoint() }

    override fun onDraw() = update { onDraw() }

    override fun onSwap() = update { onSwap() }

    override fun onPlay() = update { onPlay() }

    override fun onTrick() = update { onTrick() }

    override fun onDeclare() = update { onDeclare() }

    override fun onResult() {
        resultShown = true
        update { onResult() }
    }

    override fun onEnd(finished: Boolean) {
        if (!finished) return
        SwingUtilities.invokeLater { onEnd(null) }
    }

    // RESULT → END の待ちは showAllCards=true で画面変化がないためスキップする。
    override fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean {
        if (wait <= 0 || resultShown) return true
        waitingForAdvance = true
        return false
    }
}
