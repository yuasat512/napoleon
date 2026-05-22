package napoleon.engine.view

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.Suit

// AI に公開する読み取り専用ビュー。型レベルで他プレイヤーの手札やキティの非公開部分には到達できない。
// AI 実装には GameEngine 自身や、そこから到達可能な参照を渡してはいけない。
interface AiContext {
    val bid: Bid?
    val trump: Suit

    val napoleonId: Int
    val adjutantCard: Card?
    val adjutantRevealed: Boolean

    // 副官カードは公開情報だが、副官 ID は副官カードが場に出るまで隠す。
    val adjutantIdIfRevealed: Int?

    val trickTurn: Int
    val leadSuit: Suit?
    val mightyInTrick: Boolean
    val sameCandidate: Boolean
    val jokerCallActive: Boolean
    val jokerPlayed: Boolean

    val curPlayer: SelfPlayerView
    val publicPlayers: Array<out PublicPlayerView>

    val trickHistory: List<ResolvedTrick>

    // ナポレオンがキティに送り込んだ全カードの記憶。視点プレイヤーがナポレオンのときのみ非 null。
    // 他プレイヤーには裏向き (非絵札) の捨て札を見せてはいけない。
    val napoleonKittyDiscards: List<Card>?

    // ナポレオンの捨て札のうち絵札のみ。1 トリック解決まで全員に公開され、その後も全プレイヤーが知る情報として保持される。
    val kittyHonorCards: List<Card>

    // 各プレイヤーが追従不能 (= 該当スートを持たない) と確定したスート集合。playerId でインデックス。
    val knownVoids: Array<Set<Suit>>

    // ジョーカー請求 (♣3 リード) でジョーカーを出さなかったことから、ジョーカー非保持と確定したプレイヤー。
    // リードした本人は対象外 (自分でジョーカーを持っていた可能性が残る)。
    val knownNoJoker: BooleanArray

    val debug: Boolean

    fun canFollow(handIndex: Int): Boolean

    fun requiresJokerSuitChoice(handIndex: Int): Boolean

    fun strengthOf(card: Card): Int
}
