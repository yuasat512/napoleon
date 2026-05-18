package napoleon.ui.dialogs

import napoleon.core.Bid
import napoleon.core.Suit
import javax.swing.JButton
import javax.swing.JFrame

fun showBidDialog(
    owner: JFrame,
    currentBid: Bid?,
    lastBidIndex: Int,
): Pair<Bid?, Int> {
    val suits = Suit.realEntries.reversed()

    var selectedBid: Bid? = null
    var newLastBidIndex = lastBidIndex

    val buttons = mutableListOf<JButton>()
    buttons.add(JButton("パス"))
    for (suit in suits) {
        val minTarget =
            if (currentBid == null) {
                Bid.MIN_TARGET
            } else {
                currentBid.target + (if (suit <= currentBid.suit) 1 else 0)
            }
        val button = JButton("${suit.displayName}${minTarget}枚")
        button.isEnabled = minTarget <= Bid.MAX_TARGET
        button.addActionListener {
            selectedBid = Bid(suit, minTarget)
            newLastBidIndex = buttons.indexOf(button)
        }
        buttons.add(button)
    }

    showButtonListDialog(owner, "宣言", buttons, lastBidIndex, closable = true)
    return selectedBid to newLastBidIndex
}
