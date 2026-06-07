package napoleon.engine

import napoleon.core.Card
import napoleon.core.GameRules.KITTY_SIZE

// 場の中央に表向きで配られる 3 枚。ナポレオンが拾い、3 枚を返却する。
// 返却された絵札は 1 トリック目の勝者が総取りする。
class Kitty {
    val cards = mutableListOf<Card>()

    fun receiveDeal(
        deck: List<Card>,
        offset: Int,
    ) {
        cards.clear()
        cards.addAll(deck.subList(offset, offset + KITTY_SIZE))
    }

    fun takeBack(returned: List<Card>) {
        cards.clear()
        cards.addAll(returned)
    }

    // 数札を前に寄せ、絵札 (= 表向きで残るカード) を末尾に揃える表示用ソート。
    fun sortForDisplay() {
        cards.sortWith(compareBy({ it.rank.isHonor }, { it }))
    }
}
