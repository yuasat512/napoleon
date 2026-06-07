package napoleon.session

import napoleon.ai.PlayerStrategy
import napoleon.core.BidOutcome
import napoleon.engine.GameEngine
import napoleon.engine.GameRecorder
import napoleon.io.PlayerIO

// 1 セッションをフェーズ遷移で進めるドライバ。AI 席は strategies に問い合わせ、
// 人間席 (非 auto モード時の player 0) は PlayerIO.prompt* で入力を待つ。
// transition(...) は UI が進行を中断したとき false を返し、ユーザ入力後に UI が step() を呼んで再開する。
class GameController(
    private val engine: GameEngine,
    private val strategies: List<PlayerStrategy>,
    private val scoreKeeper: ScoreKeeper,
    private val recorder: GameRecorder?,
    private val io: PlayerIO,
) {
    private val strategy: PlayerStrategy get() = strategies[engine.curPlayer.id]

    private enum class Phase {
        START,
        DEAL,
        BID,
        REDEAL,
        APPOINT_PRE,
        APPOINT,
        DRAW,
        SWAP,
        PLAY,
        TRICK,
        DECLARE,
        RESULT,
        END,
    }

    private var phase = Phase.START
    private var initialCurPlayerId = 0

    fun start(initialCurPlayerId: Int = 0) {
        phase = Phase.START
        this.initialCurPlayerId = initialCurPlayerId
        step()
    }

    fun step() {
        while (advance()) Unit
    }

    // 人間が座るのは player 0 のみ。auto モードでは全員 AI 扱い。
    private fun awaitsUser(): Boolean = !engine.config.auto && engine.curPlayer.id == 0

    private fun advance(): Boolean =
        when (phase) {
            Phase.START -> {
                engine.startSession(initialCurPlayerId)
                scoreKeeper.resetSession()
                phase = Phase.DEAL
                true
            }
            Phase.DEAL -> {
                recorder?.onGameBegin()
                engine.dealCards()
                io.onDeal()
                transition(Phase.BID, 1)
            }
            Phase.BID -> {
                val bid = if (awaitsUser()) io.promptBid() else strategy.chooseBid()
                recorder?.onBid(bid)
                val outcome = engine.placeBid(bid)
                io.onBid()
                transition(
                    when (outcome) {
                        BidOutcome.ALL_PASSED -> Phase.REDEAL
                        BidOutcome.WON -> Phase.APPOINT_PRE
                        BidOutcome.CONTINUE -> Phase.BID
                    },
                    1,
                )
            }
            Phase.REDEAL -> {
                io.onRedeal()
                engine.advanceDealer()
                transition(Phase.DEAL, 2)
            }
            Phase.APPOINT_PRE -> {
                io.onAppointPre()
                phase = Phase.APPOINT
                true
            }
            Phase.APPOINT -> {
                val card = if (awaitsUser()) io.promptAdjutant() else strategy.chooseAdjutant()
                engine.designateAdjutant(card)
                recorder?.onAppoint()
                io.onAppoint()
                transition(Phase.DRAW, if (awaitsUser()) 1 else 2)
            }
            Phase.DRAW -> {
                recorder?.onKittyDraw()
                engine.drawKitty()
                io.onDraw()
                transition(Phase.SWAP, if (awaitsUser()) 0 else 1)
            }
            Phase.SWAP -> {
                val discards =
                    if (awaitsUser()) {
                        io.promptKittySwap() ?: return false
                    } else {
                        strategy.chooseKittySwap()
                    }
                recorder?.onKittySwap(discards)
                engine.swapKitty(discards)
                io.onSwap()
                transition(Phase.PLAY, if (awaitsUser()) 0 else 1)
            }
            Phase.PLAY -> {
                val choice =
                    if (awaitsUser()) {
                        io.promptPlay() ?: return false
                    } else {
                        strategy.choosePlay()
                    }
                val (handIndex, jokerSuit) = choice
                recorder?.onPlay(handIndex)
                val trickDone = engine.playCard(handIndex, jokerSuit)
                io.onPlay()
                transition(if (trickDone) Phase.TRICK else Phase.PLAY, 1)
            }
            Phase.TRICK -> {
                recorder?.onTrickBegin()
                val result = engine.resolveTrick()
                if (result != null) scoreKeeper.applyScores(result)
                recorder?.onTrickEnd()
                val gameEnd = result != null
                if (gameEnd) recorder?.onGameEnd()
                io.onTrick()
                transition(if (gameEnd) Phase.DECLARE else Phase.PLAY, 1)
            }
            Phase.DECLARE -> {
                io.onDeclare()
                transition(Phase.RESULT, 2)
            }
            Phase.RESULT -> {
                io.onResult()
                transition(Phase.END, 4)
            }
            Phase.END -> {
                val finished = engine.finishGame()
                io.onEnd(finished)
                if (!finished) transition(Phase.DEAL, 0) else false
            }
        }

    private fun transition(
        next: Phase,
        wait: Int,
    ): Boolean {
        phase = next
        val userInputNext = next in USER_INPUT_PHASES && awaitsUser()
        return io.onTransition(wait, userInputNext)
    }

    private companion object {
        val USER_INPUT_PHASES = setOf(Phase.BID, Phase.SWAP, Phase.PLAY)
    }
}
