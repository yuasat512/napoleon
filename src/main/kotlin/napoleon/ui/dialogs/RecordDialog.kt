package napoleon.ui.dialogs

import napoleon.core.ScoreTable
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.WindowConstants

fun showRecordDialog(
    owner: JFrame,
    title: String,
    records: IntArray,
    rank: Int? = null,
) {
    val roleCounts = IntArray(3)
    val roleWins = IntArray(3)
    var totalCount = 0
    var totalWin = 0
    var totalPoints = 0
    for (i in 0 until ScoreTable.ENTRY_COUNT) {
        totalCount += records[i]
        roleCounts[i % 3] += records[i]
        if (ScoreTable[i] > 0) {
            totalWin += records[i]
            roleWins[i % 3] += records[i]
        }
        totalPoints += records[i] * ScoreTable[i]
    }
    val average = if (totalCount > 0) totalPoints.toDouble() / totalCount else 0.0

    fun pct(
        hit: Int,
        total: Int,
    ) = if (total == 0) "0.0%" else "${hit * 100 / total}.${hit * 1000 / total % 10}%"

    fun rightLabel(text: String) = JLabel(text).apply { horizontalAlignment = JLabel.RIGHT }

    val dialog = JDialog(owner, title, true)
    dialog.isResizable = false
    dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val summaryPanel = JPanel(GridBagLayout())
    summaryPanel.border = BorderFactory.createTitledBorder("総合")
    val sg = GridBagConstraints().apply { insets = Insets(2, 4, 2, 4) }

    sg.gridy = 0
    sg.gridx = 0
    sg.anchor = GridBagConstraints.WEST
    summaryPanel.add(JLabel("勝率"), sg)
    sg.gridx = 1
    sg.anchor = GridBagConstraints.EAST
    summaryPanel.add(rightLabel(pct(totalWin, totalCount)), sg)
    sg.gridx = 2
    sg.anchor = GridBagConstraints.EAST
    summaryPanel.add(JLabel("($totalWin / $totalCount)"), sg)

    sg.gridy = 1
    sg.gridx = 0
    sg.anchor = GridBagConstraints.WEST
    if (rank == null) {
        summaryPanel.add(JLabel("平均得点"), sg)
        sg.gridx = 1
        sg.anchor = GridBagConstraints.EAST
        summaryPanel.add(rightLabel("%.2f".format(average)), sg)
    } else {
        summaryPanel.add(JLabel("得点"), sg)
        sg.gridx = 1
        sg.anchor = GridBagConstraints.EAST
        summaryPanel.add(rightLabel("$totalPoints"), sg)
        sg.gridx = 2
        sg.anchor = GridBagConstraints.EAST
        summaryPanel.add(JLabel("${rank}位"), sg)
    }

    val detailPanel = JPanel(GridBagLayout())
    detailPanel.border = BorderFactory.createTitledBorder("詳細")
    val rg = GridBagConstraints().apply { insets = Insets(2, 4, 2, 4) }

    val colHeaders = arrayOf("ナポ", "副官", "連合")
    rg.gridy = 0
    rg.gridx = 0
    detailPanel.add(JLabel(""), rg)
    for (c in colHeaders.indices) {
        rg.gridx = c + 1
        rg.anchor = GridBagConstraints.EAST
        detailPanel.add(rightLabel(colHeaders[c]), rg)
    }

    val rowLabels =
        arrayOf(
            "ナポレオン軍完全勝利",
            "ナポレオン軍勝利",
            "ナポレオン軍敗北",
            "副官なし完全勝利",
            "副官なし勝利",
            "副官なし敗北",
        )
    for (row in 0 until 6) {
        rg.gridy = row + 1
        rg.gridx = 0
        rg.anchor = GridBagConstraints.WEST
        detailPanel.add(JLabel(rowLabels[row]), rg)
        for (col in 0 until 3) {
            rg.gridx = col + 1
            rg.anchor = GridBagConstraints.EAST
            val text = if (row >= 3 && col == 1) "-" else "${records[row * 3 + col]}"
            detailPanel.add(rightLabel(text), rg)
        }
    }

    rg.gridy = 7
    rg.gridx = 0
    rg.anchor = GridBagConstraints.WEST
    detailPanel.add(JLabel("勝率"), rg)
    for (c in 0 until 3) {
        rg.gridx = c + 1
        rg.anchor = GridBagConstraints.EAST
        detailPanel.add(rightLabel(pct(roleWins[c], roleCounts[c])), rg)
    }

    val okButton = JButton("OK")
    okButton.addActionListener { dialog.dispose() }
    val buttonPanel = JPanel()
    buttonPanel.add(okButton)

    val mainPanel = JPanel(GridBagLayout())
    mainPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    val mg =
        GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 2, 2, 2)
        }
    mg.gridy = 0
    mainPanel.add(summaryPanel, mg)
    mg.gridy = 1
    mainPanel.add(detailPanel, mg)

    dialog.contentPane =
        JPanel(BorderLayout()).apply {
            add(mainPanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    okButton.requestFocusInWindow()
    dialog.isVisible = true
}
