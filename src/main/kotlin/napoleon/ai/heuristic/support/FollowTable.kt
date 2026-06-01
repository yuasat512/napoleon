package napoleon.ai.heuristic.support

import napoleon.core.GameRules
import napoleon.core.Rank

// リード時に「後続 (自分より後に出す) 全員がそのスートを追従できる確率」の事前計算テーブル。
// FollowOdds が盤面から (手札枚数 handCount, 余り枚数 slack, 当該スートの未確定枚数 outstanding) を
// 求め、ここを引くだけにすることで、超幾何分布の計算を実行時から排除しつつ「何枚で何%か」を
// 一覧できるようにする。値は class ロード時に build() で一度だけ算出する。
//
// 確率モデル: リード時は自分以外の PLAYER_COUNT-1 (=4) 人が全員これから出すので、後続は常に4人、
// 全員 handCount 枚。未確定札 unseen = 4*handCount + slack のうち当該スートが outstanding 枚あるとき、
// 4人全員が1枚以上持つ (=誰も切り札等で割り込めない) 確率を包除原理で求める:
//   P = Σ_{j=0}^{4} (-1)^j C(4,j) · C(unseen - j*handCount, outstanding) / C(unseen, outstanding)
// slack = unseen - 4*handCount は「どの手札にも属さない未確定札」= 埋め札のうち自分から見えない枚数。
// 守備視点で絵札公開なしなら slack=KITTY_SIZE(3)、ナポレオン視点 (埋め札を把握) なら slack=0。
//
// ---- P(後続4人全員フォロー) % (列 K=outstanding, 行 h=handCount。. は unseen<K で発生しない領域) ----
// K が後続人数 (4) 未満なら4人が全員持てず常に 0% なので K=4 以上のみ掲載。slack=1,2 は両者の中間。
//
//  slack=0 (ナポレオン視点: 埋め札を把握)        slack=3 (連合/守備視点: 絵札公開なし)
//  h\K   4   5   6   7   8   9  10  11  12  13    h\K   4   5   6   7   8   9  10  11  12  13
//   10  11  27  44  59  71  79  86  91  94  96     10   8  22  37  52  64  74  81  87  91  94
//    9  11  28  45  60  72  80  87  91  94  97      9   8  22  37  52  64  74  81  87  91  94
//    8  11  28  46  61  73  82  88  92  95  97      8   8  21  37  52  64  74  82  88  92  95
//    7  12  29  47  62  74  83  89  93  96  98      7   8  21  37  52  65  75  83  88  92  95
//    6  12  30  49  65  77  85  91  95  97  99      6   7  21  37  52  65  76  83  89  93  96
//    5  13  32  52  68  80  88  94  97  99  99      5   7  20  37  52  66  77  85  91  95  97
//    4  14  35  56  73  85  92  97  99 100 100      4   7  20  36  53  67  79  87  93  96  98
//    3  16  41  64  82  93  98 100 100 100   .      3   6  19  36  54  70  83  91  96  99 100
//    2  23  57  86 100 100   .   .   .   .   .      2   5  17  36  58  78  93 100 100   .   .
//    1 100   .   .   .   .   .   .   .   .   .      1   3  14  43 100   .   .   .   .   .   .
//
// 要点: 値はほぼ outstanding (残り枚数 K) で決まり手札枚数の影響は小さい。セイム閾値 (連合0.5/ナポ0.6) は
// 守備視点で K=7〜8、ナポレオン視点で K=6〜7 あたりを跨ぐ。
object FollowTable {
    // リード時の後続人数 (自分以外の全員)。
    private const val LATER = GameRules.PLAYER_COUNT - 1

    // 1スートのランク数 (2..A の 13)。outstanding の上限。
    private val RANKS = Rank.entries.size

    // [handCount 0..HAND_SIZE][slack 0..KITTY_SIZE][outstanding 0..RANKS]
    private val table: Array<Array<DoubleArray>> = build()

    // handCount 枚を持つ後続 LATER 人全員が、未確定 (4*handCount+slack) 枚中 outstanding 枚の
    // スートを1枚以上持つ確率。FollowOdds から盤面値を渡して引く。
    fun pAllFollow(
        handCount: Int,
        slack: Int,
        outstanding: Int,
    ): Double {
        if (handCount !in 0..GameRules.HAND_SIZE || slack !in 0..GameRules.KITTY_SIZE || outstanding !in 0..RANKS) {
            error("FollowTable 範囲外: handCount=$handCount slack=$slack outstanding=$outstanding")
        }
        return table[handCount][slack][outstanding]
    }

    private fun build(): Array<Array<DoubleArray>> =
        Array(GameRules.HAND_SIZE + 1) { handCount ->
            Array(GameRules.KITTY_SIZE + 1) { slack ->
                DoubleArray(RANKS + 1) { outstanding -> probability(handCount, slack, outstanding) }
            }
        }

    private fun probability(
        handCount: Int,
        slack: Int,
        outstanding: Int,
    ): Double {
        if (outstanding <= 0) return 0.0
        val unseen = LATER * handCount + slack
        if (unseen <= 0) return 0.0
        var p = 0.0
        for (j in 0..LATER) {
            val sign = if (j % 2 == 0) 1.0 else -1.0
            p += sign * binomial(LATER, j) * subsetRatio(unseen - j * handCount, unseen, outstanding)
        }
        return p.coerceIn(0.0, 1.0)
    }

    // C(a, k) / C(b, k) = Π_{i=0}^{k-1} (a-i)/(b-i)。階乗を経由せず桁あふれを避ける。
    private fun subsetRatio(
        a: Int,
        b: Int,
        k: Int,
    ): Double {
        if (k <= 0) return 1.0
        if (a < k) return 0.0
        var r = 1.0
        for (i in 0 until k) r *= (a - i).toDouble() / (b - i).toDouble()
        return r
    }

    // C(n, k)。n は LATER (=4) 程度なので桁あふれの心配はない。
    private fun binomial(
        n: Int,
        k: Int,
    ): Long {
        var r = 1L
        for (i in 0 until k) r = r * (n - i) / (i + 1)
        return r
    }
}
