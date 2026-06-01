package napoleon.engine

import napoleon.config.Config
import napoleon.core.Bid
import napoleon.core.BidOutcome
import napoleon.core.Card
import napoleon.core.CardStrength
import napoleon.core.GameResult
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.ScoreTable
import napoleon.core.Suit
import napoleon.engine.view.AiContext
import napoleon.engine.view.HonorEstimate
import napoleon.engine.view.PublicPlayerView
import napoleon.engine.view.ResolvedTrick
import napoleon.engine.view.TrickPlay
import kotlin.random.Random

// ナポレオン1局の状態機械。AiContext を実装することで AI に公開する読み取り面を兼ね、
// エンジン状態と AI ビューを別アダプタなしで同期させている。
class GameEngine(
    val config: Config,
) : AiContext {
    val players = Array(PLAYER_COUNT) { Player(it) }
    val kitty = Kitty()

    // リプレイ/テストでデッキを差し込むための注入口。次の dealCards で消費・クリアされる。
    var fixedDeck: Array<Card>? = null

    private val rng = if (config.seed != 0) Random(config.seed.toLong()) else Random.Default

    override val publicPlayers: Array<out PublicPlayerView> get() = players

    override var curPlayer: Player = players[0]
        private set

    var gameNumber = 0
        private set
    override var bid: Bid? = null
        private set
    override val trump: Suit get() = bid!!.suit
    val bidTarget: Int get() = bid!!.target
    override var adjutantCard: Card = Card.JOKER
        private set
    override var napoleonId = 0
        private set
    var adjutantId = 0
        private set
    override var adjutantRevealed = false
        private set
    override val adjutantIdIfRevealed: Int? get() = if (adjutantRevealed) adjutantId else null
    override var jokerPlayed = false
        private set

    val honorCards = arrayOfNulls<Card>(HONOR_COUNT)

    private var bidPassCount = 0

    override var trickTurn = 0
        private set
    override var leadSuit: Suit = Suit.NONE
        private set
    var jokerDeclaredSuit: Suit? = null
        private set
    override var mightyInTrick = false
        private set
    override var sameCandidate = false
        private set
    override var jokerCallActive = false
        private set

    private val trickLog = mutableListOf<ResolvedTrick>()
    private val currentTrickPlays = mutableListOf<TrickPlay>()
    private var napoleonDiscards: List<Card>? = null
    override val trickHistory: List<ResolvedTrick> get() = trickLog
    override val napoleonKittyDiscards: List<Card>?
        get() = napoleonDiscards?.takeIf { curPlayer.id == napoleonId }

    override val kittyHonorCards: List<Card>
        get() = napoleonDiscards?.filter { it.rank.isHonor } ?: emptyList()

    // 進行中・完了済トリックから派生する推論キャッシュ。AI 呼び出しのたび参照され、
    // カードプレイ・トリック解決・配り直しの各タイミングで無効化する。
    private var knownVoidsCache: Array<Set<Suit>>? = null
    private var knownNoJokerCache: BooleanArray? = null

    override val knownVoids: Array<Set<Suit>>
        get() {
            knownVoidsCache?.let { return it }
            val result = Array(PLAYER_COUNT) { mutableSetOf<Suit>() }
            forEachTrick { plays, jokerDeclared ->
                if (plays.isEmpty()) return@forEachTrick
                val leadCard = plays[0].card
                val leadSuit =
                    when {
                        jokerDeclared != null -> jokerDeclared
                        leadCard.isJoker() -> return@forEachTrick
                        else -> leadCard.suit
                    }
                for (i in 1 until plays.size) {
                    val played = plays[i].card
                    if (played.isJoker()) continue
                    if (played.suit != leadSuit) result[plays[i].playerId] += leadSuit
                }
            }
            @Suppress("UNCHECKED_CAST")
            return (result as Array<Set<Suit>>).also { knownVoidsCache = it }
        }

    override val debug: Boolean get() = config.debug

    override val knownNoJoker: BooleanArray
        get() {
            knownNoJokerCache?.let { return it }
            val result = BooleanArray(PLAYER_COUNT)
            forEachTrick { plays, _ ->
                if (plays.isEmpty() || !plays[0].card.isJokerCall()) return@forEachTrick
                for (i in 1 until plays.size) {
                    if (!plays[i].card.isJoker()) result[plays[i].playerId] = true
                }
            }
            knownNoJokerCache = result
            return result
        }

    private inline fun forEachTrick(action: (plays: List<TrickPlay>, jokerDeclared: Suit?) -> Unit) {
        for (rec in trickLog) action(rec.plays, rec.jokerDeclaredSuit)
        action(currentTrickPlays, jokerDeclaredSuit)
    }

    private fun invalidateInferenceCache() {
        knownVoidsCache = null
        knownNoJokerCache = null
    }

    fun startSession(initialCurPlayerId: Int = 0) {
        gameNumber = 1
        for (p in players) p.points = 0
        curPlayer = players[initialCurPlayerId]
    }

    fun dealCards() {
        val deck = fixedDeck?.also { fixedDeck = null } ?: Card.ALL.toTypedArray().also { it.shuffle(rng) }

        resetGameState()

        for (i in 0 until PLAYER_COUNT) {
            players[i].apply {
                bid = null
                honorsTaken = 0
                handCount = HAND_SIZE
                deck.copyInto(hand, 0, i * HAND_SIZE, (i + 1) * HAND_SIZE)
                sortHand()
            }
        }
        kitty.receiveDeal(deck, PLAYER_COUNT * HAND_SIZE)
    }

    fun placeBid(newBid: Bid?): BidOutcome {
        if (newBid != null) {
            check(newBid.target in config.bidMinTarget..Bid.MAX_TARGET) {
                "bid target out of range [${config.bidMinTarget}..${Bid.MAX_TARGET}]: ${newBid.target}"
            }
            val current = bid
            check(current == null || newBid > current) {
                "bid not stronger than current: new=$newBid, current=$current"
            }
        }
        if (newBid == null) {
            bidPassCount++
        } else {
            bidPassCount = 0
            bid = newBid
        }
        curPlayer.bid = newBid
        curPlayer = players[playerIdAt(1)]

        // 競りの終了条件: 何らかの宣言が出たあとは PLAYER_COUNT-1 回連続パスで終了。
        // 宣言がまだ出ていない場合は全員2巡パスで配り直し (ALL_PASSED) となる。
        val threshold = if (bid != null) PLAYER_COUNT - 1 else PLAYER_COUNT * 2
        return when {
            bidPassCount < threshold -> BidOutcome.CONTINUE
            bid != null -> {
                napoleonId = curPlayer.id
                BidOutcome.WON
            }
            else -> BidOutcome.ALL_PASSED
        }
    }

    fun advanceDealer() {
        curPlayer = players[playerIdAt(1)]
    }

    fun designateAdjutant(card: Card) {
        check(napoleonId == curPlayer.id) { "napoleonId must be set at bid won: napoleonId=$napoleonId, curPlayer=${curPlayer.id}" }
        adjutantCard = card
        adjutantId =
            players
                .firstOrNull { p -> (0 until HAND_SIZE).any { p.hand[it] == card } }
                ?.id ?: curPlayer.id
    }

    fun drawKitty() {
        kitty.cards.copyInto(curPlayer.hand, HAND_SIZE, 0, KITTY_SIZE)
        curPlayer.handCount += KITTY_SIZE
    }

    fun swapKitty(discardIndices: IntArray) {
        check(discardIndices.size == KITTY_SIZE) {
            "kitty swap size must be $KITTY_SIZE: got ${discardIndices.size} (${discardIndices.toList()})"
        }
        for (i in discardIndices) {
            check(i in 0 until curPlayer.handCount) {
                "kitty swap index out of range [0..${curPlayer.handCount - 1}]: $i (${discardIndices.toList()})"
            }
        }
        check(discardIndices.toHashSet().size == discardIndices.size) {
            "kitty swap has duplicate indices: ${discardIndices.toList()}"
        }
        val sorted = discardIndices.sortedArray()
        val hand = curPlayer.hand

        val returned = Array(KITTY_SIZE) { hand[sorted[it]] }
        kitty.takeBack(returned)
        napoleonDiscards = returned.toList()

        var keepCursor = 0
        var discardCursor = 0
        for (src in 0 until HAND_SIZE + KITTY_SIZE) {
            if (discardCursor < KITTY_SIZE && sorted[discardCursor] == src) {
                discardCursor++
            } else {
                hand[keepCursor++] = hand[src]
            }
        }
        curPlayer.handCount = HAND_SIZE
        curPlayer.sortHand()

        kitty.sortForDisplay()
        if (adjutantCard.rank.isHonor) {
            adjutantRevealed = kitty.cards.any { it == adjutantCard }
        }
    }

    fun playCard(
        handIndex: Int,
        jokerSuit: Suit? = null,
    ): Boolean {
        check(handIndex in 0 until curPlayer.handCount) {
            "play index out of range [0..${curPlayer.handCount - 1}]: $handIndex"
        }
        check(canFollow(handIndex)) {
            val c = curPlayer.hand[handIndex]
            "play violates must-follow: card=$c, leadSuit=$leadSuit, jokerCall=$jokerCallActive"
        }
        if (requiresJokerSuitChoice(handIndex)) {
            check(jokerSuit != null && jokerSuit != Suit.NONE) {
                "joker lead requires suit declaration (got $jokerSuit)"
            }
        } else {
            check(jokerSuit == null) {
                "jokerSuit must be null for non-joker-lead play (card=${curPlayer.hand[handIndex]}, jokerSuit=$jokerSuit)"
            }
        }
        val card = curPlayer.hand[handIndex]
        curPlayer.hand.copyInto(curPlayer.hand, handIndex, handIndex + 1, curPlayer.handCount)
        curPlayer.handCount--
        curPlayer.playedCard = card
        currentTrickPlays += TrickPlay(curPlayer.id, card)

        if (trickTurn == 0) {
            jokerDeclaredSuit = jokerSuit
            leadSuit =
                when {
                    jokerSuit != null -> jokerSuit
                    card.isJoker() -> Suit.NONE
                    else -> card.suit
                }
            // セイム成立 (§6-10) は「2トリック目以降・全員同一スート・役札不在」の3条件。
            // handCount は既にデクリメント済みなので "< HAND_SIZE - 1" で「2トリック目以降」を表す。
            sameCandidate = curPlayer.handCount < HAND_SIZE - 1 && !card.isPowerCard(trump)
            mightyInTrick = false
            jokerCallActive = card.isJokerCall()
        } else {
            if (card.suit != leadSuit || card.isPowerCard(trump)) sameCandidate = false
        }

        if (card == adjutantCard) adjutantRevealed = true
        when {
            card.isMighty() -> mightyInTrick = true
            card.isJoker() -> jokerPlayed = true
        }

        curPlayer = players[playerIdAt(1)]
        trickTurn++
        invalidateInferenceCache()
        return trickTurn == PLAYER_COUNT
    }

    fun resolveTrick(): GameResult? {
        var honorGain = 0
        var topStrength = INVALID_STRENGTH

        fun collectHonor(card: Card) {
            if (card.rank.isHonor) {
                honorGain++
                honorCards[card.honorIndex] = card
            }
        }
        for (p in players) {
            val strength = strengthOf(p.playedCard)
            if (topStrength < strength) {
                topStrength = strength
                curPlayer = p
            }
            collectHonor(p.playedCard)
        }

        // 1トリック目の勝者はキティに残った絵札も総取りする。
        // 全員1枚ずつ出した直後なので "handCount == HAND_SIZE - 1" が1トリック目の判定。
        if (curPlayer.handCount == HAND_SIZE - 1) {
            for (card in kitty.cards) collectHonor(card)
        }

        curPlayer.honorsTaken += honorGain
        trickLog += ResolvedTrick(currentTrickPlays.toList(), jokerDeclaredSuit, curPlayer.id)
        currentTrickPlays.clear()
        trickTurn = 0
        invalidateInferenceCache()

        if (config.playThrough && trickLog.size < HAND_SIZE) return null

        // トリックごとの早期終了判定:
        //   PERFECT_WIN: ナポレオン軍が絵札 HONOR_COUNT 枚に到達した時点
        //   LOSS: 連合軍が HONOR_COUNT - bidTarget を超えた時点
        //   WIN: ナポレオン軍が bidTarget に到達し、かつ連合軍に絵札が1枚以上ある時点
        //        (連合軍 0 枚なら PERFECT_WIN を狙ってプレイ続行)
        val estimate = estimateHonors(adjutantId)
        val napHonors = estimate.napoleonHonors
        val allyHonors = estimate.allyHonors
        val undecided =
            napHonors != HONOR_COUNT &&
                allyHonors <= HONOR_COUNT - bidTarget &&
                (allyHonors == 0 || napHonors < bidTarget)
        if (undecided) return null
        val verdict =
            when {
                napHonors == HONOR_COUNT -> ScoreTable.Verdict.PERFECT_WIN
                napHonors >= bidTarget -> ScoreTable.Verdict.WIN
                else -> ScoreTable.Verdict.LOSS
            }
        return GameResult(verdict, napoleonId, adjutantId)
    }

    fun finishGame(): Boolean {
        curPlayer = players[adjutantId]
        gameNumber++
        return config.gameCount in 1..<gameNumber
    }

    fun playerIdAt(offset: Int): Int = (curPlayer.id + offset + PLAYER_COUNT) % PLAYER_COUNT

    override fun requiresJokerSuitChoice(handIndex: Int): Boolean =
        curPlayer.hand[handIndex].isJoker() && trickTurn == 0 && curPlayer.handCount > 1

    fun sortCurrentHand() {
        curPlayer.sortHand()
    }

    override fun canFollow(handIndex: Int): Boolean {
        val cp = curPlayer
        val card = cp.hand[handIndex]
        if (trickTurn == 0 || card.isJoker()) return true
        // ジョーカー請求 (♣3 リード) ではジョーカー保持者は強制的にジョーカーを出す必要がある。
        // 手札はソート済みなので、ジョーカーを持つなら必ず末尾に位置する。
        if (jokerCallActive && cp.hand[cp.handCount - 1].isJoker()) return false
        if (card.suit == leadSuit) return true
        return (0 until cp.handCount).none { cp.hand[it].suit == leadSuit }
    }

    fun isTrickLeader(pid: Int): Boolean {
        val target = players[pid]
        val strength = strengthOf(target.playedCard)
        return players.none { other ->
            other !== target && other.handCount == target.handCount && strengthOf(other.playedCard) > strength
        }
    }

    override fun strengthOf(card: Card): Int = CardStrength.evaluate(card, trump, leadSuit, mightyInTrick, sameCandidate)

    // viewerId の情報集合に基づく絵札合計の推定:
    //   副官が確定している (公開済み or 視点が副官) → 厳密値、unknown=0
    //   副官未確定 → 副官が誰かは不明なので、ナポレオン軍合計を「他プレイヤーの最小値」で下限化し、
    //                振れ幅 (max - min) を unknownHonors として返す。副官公開時にどちらに転ぶかわからない分を表す。
    fun estimateHonors(viewerId: Int): HonorEstimate {
        if (adjutantRevealed || viewerId == adjutantId) {
            val total = players.sumOf { it.honorsTaken }
            val napTotal =
                players[napoleonId].honorsTaken +
                    if (napoleonId == adjutantId) 0 else players[adjutantId].honorsTaken
            return HonorEstimate(napTotal, total - napTotal, 0)
        }

        var total = 0
        var maxOther = 0
        var minOther = Int.MAX_VALUE
        for (p in players) {
            total += p.honorsTaken
            if (p.id == viewerId || p.id == napoleonId) continue
            if (p.honorsTaken > maxOther) maxOther = p.honorsTaken
            if (p.honorsTaken < minOther) minOther = p.honorsTaken
        }
        val diff = maxOther - minOther
        val napTotal = players[napoleonId].honorsTaken + minOther
        return HonorEstimate(napTotal, total - napTotal - diff, diff)
    }

    private fun resetGameState() {
        bid = null
        adjutantCard = Card.JOKER
        napoleonId = 0
        adjutantId = 0
        adjutantRevealed = false
        jokerPlayed = false
        honorCards.fill(null)

        bidPassCount = 0
        trickTurn = 0
        leadSuit = Suit.NONE
        jokerDeclaredSuit = null
        mightyInTrick = false
        sameCandidate = false
        jokerCallActive = false

        trickLog.clear()
        currentTrickPlays.clear()
        napoleonDiscards = null
        invalidateInferenceCache()
    }

    companion object {
        private const val INVALID_STRENGTH = Int.MIN_VALUE
    }
}
