package napoleon.ui

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.config.Config
import napoleon.config.RecordStore
import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.GameRules.INVALID_INDEX
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit
import napoleon.engine.GameEngine
import napoleon.engine.GameRecorder
import napoleon.io.PlayerIO
import napoleon.record.GameRecordReader
import napoleon.record.ReplayEntry
import napoleon.session.GameController
import napoleon.session.ScoreKeeper
import napoleon.ui.dialogs.showAdjutantDialog
import napoleon.ui.dialogs.showBidDialog
import napoleon.ui.dialogs.showOptionDialog
import napoleon.ui.dialogs.showRecordDialog
import napoleon.ui.dialogs.showScrollListDialog
import napoleon.ui.dialogs.showSuitDialog
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Robot
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter

private enum class InputPhase { NONE, SWAP, PLAY }

// Swing メインウィンドウ。PlayerIO を実装し人間プレイヤー (player 0) の入出力を担う。
// GameController からのフェーズ通知 (onDeal/onBid/...) は GameView に転送して再描画し、
// 入力待ちフェーズ (SWAP/PLAY) では promptKittySwap/promptPlay が一旦 null を返して進行を止め、
// マウス・キー操作で確定したあと controller.step() で再開させる。
class GameFrame(
    private val config: Config,
    private val recordStore: RecordStore,
) : JFrame("Napoleon"),
    PlayerIO {
    private val engine = GameEngine(config)
    private val strategies: List<PlayerStrategy> = List(PLAYER_COUNT) { HeuristicStrategy(engine) }
    private val view = GameView(engine, config)
    private val recorder = if (config.gameLog) GameRecorder(engine) else null
    private val scoreKeeper = ScoreKeeper(engine.players, config, recordStore)
    private val controller = GameController(engine, strategies, scoreKeeper, recorder, this)

    private var inputPhase = InputPhase.NONE
    private var inputReady = false
    private val selectedCards = IntArray(KITTY_SIZE) { INVALID_INDEX }
    private var timer: Timer? = null
    private var lastBidIndex = 0
    private var waitingForPressed = false

    private var replay: ReplaySession? = null

    private val robot = Robot()

    private val isAcceptingInput: Boolean get() = inputPhase != InputPhase.NONE

    private lateinit var menuStart: JMenuItem
    private lateinit var menuReplay: JMenuItem
    private lateinit var menuTitle: JMenuItem

    private val panel =
        object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val img = replay?.view?.image ?: view.image
                g.drawImage(img, 0, 0, CANVAS_W, CANVAS_H, 0, 0, CANVAS_W, CANVAS_H, null)
            }
        }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = false

        jMenuBar = buildMenuBar()

        panel.preferredSize = Dimension(CANVAS_W, CANVAS_H)
        contentPane.add(panel)
        pack()
        setLocationRelativeTo(null)

        javaClass.classLoader.getResourceAsStream("4_Icon.png")?.let {
            iconImage = ImageIO.read(it)
        }

        installInputListeners()

        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    config.save()
                    recordStore.save()
                }
            },
        )

        isVisible = true
    }

    private fun buildMenuBar(): JMenuBar =
        JMenuBar().apply {
            add(
                JMenu("ゲーム(G)").apply {
                    setMnemonic('G')
                    menuStart =
                        JMenuItem("開始(S)").apply {
                            setMnemonic('S')
                            addActionListener { startGame() }
                        }
                    add(menuStart)
                    menuTitle =
                        JMenuItem("タイトルに戻る(T)").apply {
                            setMnemonic('T')
                            isEnabled = false
                            addActionListener { if (replay != null) endReplay() else resetToTitle() }
                        }
                    add(menuTitle)
                    menuReplay =
                        JMenuItem("リプレイ(L)...").apply {
                            setMnemonic('L')
                            addActionListener { startReplay() }
                        }
                    add(menuReplay)
                    addSeparator()
                    add(
                        JMenuItem("成績表(R)...").apply {
                            setMnemonic('R')
                            addActionListener {
                                showRecordDialog(this@GameFrame, "成績表 - 通算", recordStore.records[0])
                            }
                        },
                    )
                    add(
                        JMenuItem("設定(O)...").apply {
                            setMnemonic('O')
                            addActionListener { showOptionDialog(this@GameFrame, config) }
                        },
                    )
                    addSeparator()
                    add(
                        JMenuItem("終了(X)").apply {
                            setMnemonic('X')
                            addActionListener {
                                this@GameFrame.dispatchEvent(
                                    WindowEvent(this@GameFrame, WindowEvent.WINDOW_CLOSING),
                                )
                            }
                        },
                    )
                },
            )
        }

    private fun installInputListeners() {
        panel.addMouseListener(
            object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1 && isAcceptingInput) handleClick()
                }

                override fun mousePressed(e: MouseEvent) {
                    replay?.let { r ->
                        r.advanceIfWaiting()
                        return
                    }
                    if (waitingForPressed) {
                        waitingForPressed = false
                        controller.step()
                        return
                    }
                    if (e.button == MouseEvent.BUTTON3 && isAcceptingInput) handleRightClick()
                }
            },
        )
        panel.addMouseMotionListener(
            object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    if (isAcceptingInput) handleHover(e.x, e.y)
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (isAcceptingInput) handleHover(e.x, e.y)
                }
            },
        )
        panel.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    replay?.let { r ->
                        r.advanceIfWaiting()
                        return
                    }
                    if (waitingForPressed) {
                        waitingForPressed = false
                        controller.step()
                        return
                    }
                    if (!isAcceptingInput) return
                    when (e.keyCode) {
                        KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> handleKeyNav(e.keyCode)
                        KeyEvent.VK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_DOWN -> handleClick()
                        KeyEvent.VK_UP -> handleRightClick()
                    }
                }
            },
        )
        panel.isFocusable = true
        panel.requestFocusInWindow()
    }

    private fun startReplay() {
        val chooser =
            JFileChooser(File("log")).apply {
                dialogTitle = "リプレイファイルを選択"
                fileFilter = FileNameExtensionFilter("テキストファイル (*.txt)", "txt")
                isAcceptAllFileFilterUsed = true
            }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val logPath = chooser.selectedFile.toPath()
        val entries: List<ReplayEntry> =
            try {
                GameRecordReader.parse(logPath)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "ログの読み込みに失敗しました:\n${e.message}")
                return
            }
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ログにゲームが見つかりません。")
            return
        }
        val ordered = entries.reversed()
        val idx = showScrollListDialog(this, "ゲーム選択", ordered.map { it.label }, 0)
        val entry = ordered.getOrNull(idx) ?: return

        menuStart.isEnabled = false
        menuTitle.isEnabled = true
        menuReplay.isEnabled = false
        replay =
            ReplaySession(
                entry = entry,
                repaint = panel::repaint,
                onEnd = { errorMessage ->
                    if (errorMessage != null) {
                        JOptionPane.showMessageDialog(this, errorMessage)
                    }
                    endReplay()
                },
            )
        panel.requestFocusInWindow()
    }

    private fun endReplay() {
        replay = null
        resetToTitle()
    }

    override fun promptBid(): Bid? {
        val (bid, idx) = showBidDialog(this, engine.bid, lastBidIndex)
        lastBidIndex = idx
        return bid
    }

    override fun promptAdjutant(): Card = showAdjutantDialog(this, engine.trump)

    override fun promptKittySwap(): List<Int>? {
        if (inputReady) {
            inputReady = false
            return selectedCards.toList()
        }
        selectedCards.fill(INVALID_INDEX)
        handleHover()
        inputPhase = InputPhase.SWAP
        return null
    }

    override fun promptPlay(): Pair<Int, Suit?>? {
        if (inputReady) {
            inputReady = false
            val handIndex = selectedCards[0]
            val suit =
                if (engine.requiresJokerSuitChoice(handIndex)) {
                    showSuitDialog(this, engine.trump)
                } else {
                    null
                }
            return handIndex to suit
        }
        selectedCards.fill(INVALID_INDEX)
        handleHover()
        inputPhase = InputPhase.PLAY
        return null
    }

    private fun repaintAfter(block: GameView.() -> Unit) {
        view.block()
        panel.repaint()
    }

    override fun onDeal() {
        lastBidIndex = 0
        repaintAfter { onDeal() }
    }

    override fun onBid() = repaintAfter { onBid() }

    override fun onRedeal() = repaintAfter { onRedeal() }

    override fun onAppointPre() = repaintAfter { onAppointPre() }

    override fun onAppoint() = repaintAfter { onAppoint() }

    override fun onDraw() = repaintAfter { onDraw() }

    override fun onSwap() = repaintAfter { onSwap() }

    override fun onPlay() = repaintAfter { onPlay() }

    override fun onTrick() = repaintAfter { onTrick() }

    override fun onDeclare() = repaintAfter { onDeclare() }

    override fun onResult() = repaintAfter { onResult() }

    override fun onEnd(finished: Boolean) {
        if (!finished) return
        val totals = IntArray(PLAYER_COUNT) { engine.players[it].points }
        val rank = 1 + totals.count { it > totals[0] }
        showRecordDialog(this, "成績表 - 今回", scoreKeeper.sessionRecords[0], rank)
        resetToTitle()
    }

    override fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean {
        if (wait <= 0) return true
        timer?.stop()
        if (config.wait == 0) {
            if (userInputNext) return true
            waitingForPressed = true
            return false
        }
        timer =
            Timer(wait * config.wait) {
                timer?.stop()
                controller.step()
            }.apply {
                isRepeats = false
                start()
            }
        return false
    }

    private fun resetToTitle() {
        timer?.stop()
        timer = null
        inputPhase = InputPhase.NONE
        inputReady = false
        waitingForPressed = false
        menuStart.isEnabled = true
        menuReplay.isEnabled = true
        menuTitle.isEnabled = false
        view.reset()
        panel.repaint()
    }

    private fun startGame() {
        timer?.stop()
        inputPhase = InputPhase.NONE
        inputReady = false
        waitingForPressed = false
        menuStart.isEnabled = false
        menuReplay.isEnabled = false
        menuTitle.isEnabled = true
        controller.start()
    }

    private fun handleHover(
        ax: Int? = null,
        ay: Int? = null,
    ) {
        val x = ax ?: panel.mousePosition?.x ?: 0
        val y = ay ?: panel.mousePosition?.y ?: 0
        var idx = view.hitTestHand(x, y)
        if (idx != INVALID_INDEX && !engine.canFollow(idx)) idx = INVALID_INDEX
        if (selectedCards[0] != idx) {
            selectedCards[0] = idx
            view.updateHand(engine.curPlayer.id, 0, selectedCards)
            panel.repaint()
        }
    }

    private fun handleClick() {
        when (inputPhase) {
            InputPhase.PLAY -> {
                if (selectedCards[0] == INVALID_INDEX) return
                confirmInput()
            }
            InputPhase.SWAP -> handleSwapClick()
            else -> {}
        }
    }

    private fun handleSwapClick() {
        when {
            selectedCards[0] == selectedCards[1] -> selectedCards[1] = INVALID_INDEX
            selectedCards[0] == selectedCards[2] -> selectedCards[2] = INVALID_INDEX
            selectedCards[1] == INVALID_INDEX -> selectedCards[1] = selectedCards[0]
            selectedCards[2] == INVALID_INDEX -> selectedCards[2] = selectedCards[0]
            selectedCards[0] != INVALID_INDEX -> confirmInput()
        }
    }

    private fun confirmInput() {
        inputReady = true
        inputPhase = InputPhase.NONE
        controller.step()
    }

    private fun handleRightClick() {
        if (inputPhase == InputPhase.SWAP) {
            selectedCards[2] = INVALID_INDEX
            selectedCards[1] = INVALID_INDEX
            engine.sortCurrentHand()
            view.updateHand(engine.curPlayer.id, 0, selectedCards)
            panel.repaint()
        }
    }

    private fun handleKeyNav(code: Int) {
        var i = selectedCards[0]
        if (i == INVALID_INDEX) {
            i = if (code == KeyEvent.VK_LEFT) engine.curPlayer.handCount else -1
        }
        do {
            if (code == KeyEvent.VK_LEFT) i-- else i++
            if (i < 0) {
                i = engine.curPlayer.handCount - 1
            } else if (i >= engine.curPlayer.handCount) {
                i = 0
            }
        } while (!engine.canFollow(i))

        selectedCards[0] = i
        view.updateHand(engine.curPlayer.id, 0, selectedCards)
        panel.repaint()

        val (cx, cy) = view.getCardCenter(i)
        val loc = panel.locationOnScreen
        robot.mouseMove(loc.x + cx, loc.y + cy)
    }
}
