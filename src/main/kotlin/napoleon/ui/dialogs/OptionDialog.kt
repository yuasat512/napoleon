package napoleon.ui.dialogs

import napoleon.config.Config
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.WindowConstants

fun showOptionDialog(
    owner: JFrame,
    config: Config,
) {
    val gameSpinner = JSpinner(SpinnerNumberModel(config.gameCount, 0, 100, 1))
    val waitSpinner = JSpinner(SpinnerNumberModel(config.wait, 0, 1000, 10))
    val autoCheck = JCheckBox("自動プレイ", config.auto)
    val playThroughCheck = JCheckBox("最終トリックまで続行", config.playThrough)
    val gameLogCheck = JCheckBox("ゲームログ", config.gameLog)

    var accepted = false
    val dialog = JDialog(owner, "設定", true)
    dialog.isResizable = false
    dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val okButton = JButton("OK")
    val cancelButton = JButton("キャンセル")
    okButton.addActionListener {
        accepted = true
        dialog.dispose()
    }
    cancelButton.addActionListener { dialog.dispose() }

    val gridPanel = JPanel(GridBagLayout())
    gridPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    val gbc =
        GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 2, 2, 2)
        }
    gbc.gridx = 0
    gbc.gridy = 0
    gridPanel.add(JLabel("ゲーム数:"), gbc)
    gbc.gridx = 1
    gridPanel.add(gameSpinner, gbc)

    gbc.gridx = 0
    gbc.gridy = 1
    gridPanel.add(JLabel("待ち時間:"), gbc)
    gbc.gridx = 1
    gridPanel.add(waitSpinner, gbc)

    gbc.gridx = 0
    gbc.gridy = 2
    gbc.gridwidth = 2
    gridPanel.add(autoCheck, gbc)

    gbc.gridy = 3
    gridPanel.add(playThroughCheck, gbc)

    gbc.gridy = 4
    gridPanel.add(gameLogCheck, gbc)

    val buttonPanel = JPanel()
    buttonPanel.add(okButton)
    buttonPanel.add(cancelButton)

    dialog.contentPane =
        JPanel(BorderLayout()).apply {
            add(gridPanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    okButton.requestFocusInWindow()
    dialog.isVisible = true

    if (accepted) {
        config.gameCount = gameSpinner.value as Int
        config.wait = waitSpinner.value as Int
        config.auto = autoCheck.isSelected
        config.playThrough = playThroughCheck.isSelected
        config.gameLog = gameLogCheck.isSelected
    }
}
