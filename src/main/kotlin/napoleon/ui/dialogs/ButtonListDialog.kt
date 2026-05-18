package napoleon.ui.dialogs

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.WindowConstants

fun showButtonListDialog(
    owner: JFrame,
    title: String,
    buttons: List<JButton>,
    defaultIndex: Int,
    closable: Boolean = false,
) {
    val dialog = JDialog(owner, title, true)
    dialog.isResizable = false
    dialog.defaultCloseOperation = if (closable) WindowConstants.DISPOSE_ON_CLOSE else WindowConstants.DO_NOTHING_ON_CLOSE

    val enabledButtons = buttons.filter { it.isEnabled }
    enabledButtons.forEachIndexed { idx, btn ->
        btn.addActionListener { dialog.dispose() }
        btn.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        VK_UP, VK_LEFT -> enabledButtons[(idx - 1).mod(enabledButtons.size)].requestFocusInWindow()
                        VK_DOWN, VK_RIGHT -> enabledButtons[(idx + 1).mod(enabledButtons.size)].requestFocusInWindow()
                    }
                }
            },
        )
    }

    val panel = JPanel(GridBagLayout())
    panel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    val gbc =
        GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 2, 2, 2)
        }
    for (i in buttons.indices) {
        gbc.gridy = i
        panel.add(buttons[i], gbc)
    }

    dialog.contentPane = panel
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    val focusTarget = buttons.getOrNull(defaultIndex)?.takeIf { it.isEnabled }
    (focusTarget ?: enabledButtons.firstOrNull())?.requestFocusInWindow()
    dialog.isVisible = true
}

fun showScrollListDialog(
    owner: JFrame,
    title: String,
    items: List<String>,
    defaultIndex: Int = 0,
): Int {
    var result = -1
    val dialog = JDialog(owner, title, true)
    dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val model = DefaultListModel<String>()
    items.forEach { model.addElement(it) }
    val list = JList(model)
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.selectedIndex = defaultIndex.coerceIn(0, items.size - 1)

    val scrollPane = JScrollPane(list)
    scrollPane.preferredSize = Dimension(360, 260)

    val okButton = JButton("OK")
    val cancelButton = JButton("キャンセル")

    fun confirm() {
        val idx = list.selectedIndex
        if (idx >= 0) {
            result = idx
            dialog.dispose()
        }
    }

    okButton.addActionListener { confirm() }
    cancelButton.addActionListener { dialog.dispose() }

    list.addMouseListener(
        object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) confirm()
            }
        },
    )
    list.addKeyListener(
        object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) confirm()
            }
        },
    )

    val buttonPanel =
        JPanel().apply {
            add(okButton)
            add(cancelButton)
        }

    val mainPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(scrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

    dialog.contentPane = mainPanel
    dialog.pack()
    dialog.setLocationRelativeTo(owner)
    list.ensureIndexIsVisible(list.selectedIndex)
    list.requestFocusInWindow()
    dialog.isVisible = true
    return result
}
