package napoleon.core

enum class Suit(
    val order: Int,
    val shortName: String,
    val displayName: String,
) {
    CLUBS(1, "C", "クラブ"),
    DIAMONDS(0, "D", "ダイヤ"),
    HEARTS(2, "H", "ハート"),
    SPADES(3, "S", "スペード"),
    NONE(4, "*", ""),
    ;

    // 同色スート (裏ジャック判定用)。xor 3 で CLUBS↔SPADES, DIAMONDS↔HEARTS を入れ替える。NONE では未定義。
    val sisterSuit: Suit get() = entries[ordinal xor 3]

    companion object {
        val realEntries: List<Suit> = entries.dropLast(1)
    }
}

enum class Rank(
    val shortName: String,
) {
    RANK_2("2"),
    RANK_3("3"),
    RANK_4("4"),
    RANK_5("5"),
    RANK_6("6"),
    RANK_7("7"),
    RANK_8("8"),
    RANK_9("9"),
    RANK_T("T"),
    RANK_J("J"),
    RANK_Q("Q"),
    RANK_K("K"),
    RANK_A("A"),
    ;

    val isHonor: Boolean get() = this >= RANK_T
}

class Card private constructor(
    id: Int,
) : Comparable<Card> {
    val suit: Suit = Suit.entries[id / 13]
    val rank: Rank = Rank.entries[id % 13]

    // 4スート × 5ランク (T..A) の絵札グリッド上の位置。絵札以外では未定義。
    val honorIndex: Int = rank.ordinal - Rank.RANK_T.ordinal + suit.order * 5

    // 表示順 (D/C/H/S) のソートキー。enum 宣言順に従う格納用 id とは別物。
    private val orderId: Int = rank.ordinal + suit.order * 13

    override fun compareTo(other: Card): Int = orderId.compareTo(other.orderId)

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = orderId

    fun isJoker(): Boolean = this == JOKER

    fun isMighty(): Boolean = this == MIGHTY

    fun isSlip(): Boolean = this == SLIP

    fun isJokerCall(): Boolean = this == JOKER_CALL

    fun isRightBower(trump: Suit): Boolean = this == rightBower(trump)

    fun isLeftBower(trump: Suit): Boolean = this == leftBower(trump)

    fun isRankTwo(): Boolean = rank == Rank.RANK_2

    // 役札 (マイティ・ジョーカー・正ジャック・裏ジャック) 判定。
    fun isPowerCard(trump: Suit): Boolean = isMighty() || isJoker() || isRightBower(trump) || isLeftBower(trump)

    // 役札 + 切り札A。脅威評価で「ほぼ非役札では超えられないカード」をまとめて扱うため。
    fun isHighRoleCard(trump: Suit): Boolean = isPowerCard(trump) || (suit == trump && rank == Rank.RANK_A)

    companion object {
        const val CARD_COUNT = 53

        val ALL = List(CARD_COUNT, ::Card)

        fun of(
            suit: Suit,
            rank: Rank,
        ): Card = ALL[rank.ordinal + suit.ordinal * 13]

        fun rightBower(trump: Suit) = of(trump, Rank.RANK_J)

        fun leftBower(trump: Suit) = of(trump.sisterSuit, Rank.RANK_J)

        // 53枚目のジョーカーは未使用スロット (NONE × RANK_2) に格納している。
        val JOKER = of(Suit.NONE, Rank.RANK_2)

        // マイティ (♠A): よろめきが同一トリックに出ない限り常に最強。
        val MIGHTY = of(Suit.SPADES, Rank.RANK_A)

        // よろめき (♥Q): マイティと同一トリックに出たときのみマイティに勝つ。
        val SLIP = of(Suit.HEARTS, Rank.RANK_Q)

        // ジョーカー請求札 (♣3): リード時にジョーカー保持者を強制的に出させる。
        val JOKER_CALL = of(Suit.CLUBS, Rank.RANK_3)
    }
}
