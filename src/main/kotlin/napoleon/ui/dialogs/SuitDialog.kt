package napoleon.ui.dialogs

import napoleon.core.Suit
import javax.swing.JButton
import javax.swing.JFrame

fun showSuitDialog(
    owner: JFrame,
    defaultSuit: Suit,
): Suit {
    val suits = Suit.realEntries.reversed()

    var selectedSuit = defaultSuit
    val buttons =
        suits.indices.map { i ->
            JButton(suits[i].displayName).apply {
                addActionListener { selectedSuit = suits[i] }
            }
        }

    showButtonListDialog(owner, "スート指定", buttons, suits.indexOf(defaultSuit))
    return selectedSuit
}
