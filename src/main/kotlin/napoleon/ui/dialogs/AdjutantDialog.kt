package napoleon.ui.dialogs

import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.WindowConstants

fun showAdjutantDialog(
    owner: JFrame,
    trump: Suit,
): Card {
    val options = arrayOf("ジョーカー", "マイティ", "正ジャック", "裏ジャック", "その他")
    val suitNames =
        Suit.realEntries
            .reversed()
            .map { it.displayName }
            .toTypedArray()
    val rankNames =
        Rank.entries
            .reversed()
            .map { it.shortName }
            .toTypedArray()

    var selectedCard = Card.JOKER
    val dialog = JDialog(owner, "副官指定", true)
    dialog.isResizable = false
    dialog.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE

    val group = ButtonGroup()
    val radios = Array(5) { JRadioButton(options[it]).also { r -> group.add(r) } }
    radios[0].isSelected = true
    val suitCombo = JComboBox(suitNames).apply { isEnabled = false }
    val rankCombo = JComboBox(rankNames).apply { isEnabled = false }
    val okButton = JButton("OK")

    val confirm = {
        selectedCard =
            when {
                radios[0].isSelected -> Card.JOKER
                radios[1].isSelected -> Card.MIGHTY
                radios[2].isSelected -> Card.rightBower(trump)
                radios[3].isSelected -> Card.leftBower(trump)
                else -> {
                    val suit = Suit.realEntries.reversed()[suitCombo.selectedIndex]
                    val rank = Rank.entries.reversed()[rankCombo.selectedIndex]
                    Card.of(suit, rank)
                }
            }
        dialog.dispose()
    }

    for (r in radios) {
        r.addActionListener {
            val custom = radios[4].isSelected
            suitCombo.isEnabled = custom
            rankCombo.isEnabled = custom
        }
        r.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) confirm()
                }
            },
        )
    }
    okButton.addActionListener { confirm() }

    val gridPanel = JPanel(GridBagLayout())
    gridPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    val gbc =
        GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 2, 2, 2)
        }
    for (i in 0 until 4) {
        gbc.gridx = 0
        gbc.gridy = i
        gbc.gridwidth = 3
        gridPanel.add(radios[i], gbc)
    }
    gbc.gridwidth = 1
    gbc.gridy = 4
    gbc.gridx = 0
    gridPanel.add(radios[4], gbc)
    gbc.gridx = 1
    gridPanel.add(suitCombo, gbc)
    gbc.gridx = 2
    gridPanel.add(rankCombo, gbc)

    val buttonPanel = JPanel()
    buttonPanel.add(okButton)

    dialog.contentPane =
        JPanel(BorderLayout()).apply {
            add(gridPanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    radios[0].requestFocusInWindow()
    dialog.isVisible = true
    return selectedCard
}
