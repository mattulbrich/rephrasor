package de.matul.rephrasor

import de.matul.rephrasor.de.matul.rephrasor.Diffing
import de.uka.ilkd.key.util.PreferenceSaver
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter


val TITLE = "Rephrasor"
val FONT_SIZES = listOf(11, 12, 14, 16, 18, 20)
val DEFAULT_FONT_SIZE = 16

class MainWindow : JFrame() {

    private var rightText: JTextArea
    private var leftText: JTextArea

    private val engine = Engine()
    private val preambles = engine.knownPreambles
    private var hilighting: Hilighting? = null

    var modified = false
    var filename: String? = null

    val prefSaver = PreferenceSaver(Preferences.userNodeForPackage(MainWindow::class.java))

    init {
        title = TITLE
        name = "mainWindow"
        setSize(800, 600)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        layout = BorderLayout()

        // Create Text Areas
        leftText = object : JTextArea() {
            override fun getToolTipText(event: MouseEvent): String? {
                val pos = viewToModel(Point(event.x, event.y))
                val markup = hilighting?.findMarkup(pos)
                return markup?.replacement // super.getToolTipText(event)
            }
        }
        leftText.wrapStyleWord = true
        leftText.lineWrap = true
        (leftText.document as AbstractDocument).documentFilter = MyDocumentFilter(this)
        (leftText.document as AbstractDocument).addDocumentListener(DocListener(this))
        leftText.addMouseListener(MouseClickAdapter { mouse(it) })
        leftText.toolTipText = ""

        rightText = JTextArea()
        rightText.wrapStyleWord = true
        rightText.lineWrap = true
        rightText.isEditable = false

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(leftText), JScrollPane(rightText))
        splitPane.dividerLocation = 400
        splitPane.name = "splitPane"
        add(splitPane, BorderLayout.CENTER)

        // Create Menu Bar
        val menuBar = JMenuBar()
        val fileMenu = JMenu("File")
        val open = JMenuItem("Open")
        open.addActionListener { open() }
        fileMenu.add(open)
        val save = JMenuItem("Save")
        save.addActionListener { save() }
        fileMenu.add(save)
        val saveAs = JMenuItem("Save As ...")
        saveAs.addActionListener { saveAs() }
        fileMenu.add(saveAs)
        val fontMenu = JMenu("Font")
        val fonts = ButtonGroup()
        for (fontSize in FONT_SIZES) {
            val item = JRadioButtonMenuItem("$fontSize pt Font")
            item.addActionListener { setTextFont(fontSize) }
            fonts.add(item)
            fontMenu.add(item)
            if (fontSize == DEFAULT_FONT_SIZE) {
                item.isSelected = true
                setTextFont(fontSize)
            }
        }
        val settingsMenu = JMenu("Settings")
        val keyItem = JMenuItem("Set OpenAI Key")
        keyItem.addActionListener { setKeY() }
        settingsMenu.add(keyItem)
        menuBar.add(fileMenu)
        menuBar.add(fontMenu)
        menuBar.add(settingsMenu)
        jMenuBar = menuBar

        // Create Tool Bar
        val toolBar = JToolBar()
        var no = 1
        for (preamble in preambles) {
            val button = JButton(preamble)
            addActionListeners(button, "alt " + (no++)) { callAI(preamble) }
            toolBar.add(button)
        }
        toolBar.add(JToolBar.Separator())
        val clearButton = JButton("Clear")
        addActionListeners(clearButton, "alt 0") {
            if(hilighting != null)
                hilighting!!.uninstall()
            hilighting = null
            rightText.text = ""
        }
        toolBar.add(clearButton)

        val recheckButton = JButton("Recheck")
        addActionListeners(recheckButton, "alt 0") {
            if(hilighting != null) {
                localComp()
            }
        }
        toolBar.add(recheckButton)

        val copyButton = JButton("Copy")
        copyButton.addActionListener {
            val hl = hilighting
            if(hl != null) hl.replaceAll(rightText.text)
        }
        toolBar.add(copyButton)
        val saveButton = JButton("Save")
        saveButton.addActionListener { save() }
        toolBar.add(saveButton)
        val reloadButton = JButton("Reload")
        reloadButton.addActionListener { reload() }
        toolBar.add(reloadButton)

        add(toolBar, BorderLayout.NORTH)

        prefSaver.load(this)
    }

    private fun addActionListeners(button: JButton, keystroke: String, al: ActionListener) {
        button.addActionListener(al)
        button.inputMap.put(KeyStroke.getKeyStroke(keystroke), "doClick")
        leftText.inputMap.put(KeyStroke.getKeyStroke(keystroke), "doClick")
        button.actionMap.put("doClick", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                button.doClick()
            }
        })
    }

    private fun setTextFont(fontSize: Int) {
        val font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
        leftText.font = font
        rightText.font = font
    }

    private fun setKeY() {
        val oldVal = Preferences.userNodeForPackage(Engine::class.java).get("openai-key", "<undefined>")
        val newVal = JOptionPane.showInputDialog("Enter your OpenAI API Key", oldVal)
        if(newVal != null) {
            Preferences.userNodeForPackage(Engine::class.java).put("openai-key", newVal)
        }
    }

    private fun mouse(e: MouseEvent) {
        val hl = hilighting
        if(SwingUtilities.isRightMouseButton(e) && hl != null) {
            val pos = leftText.viewToModel(Point(e.x, e.y))
            val markup = hl.findMarkup(pos)
            if(markup != null) {
                leftText.document.remove(markup.start, markup.end - markup.start)
                leftText.document.insertString(markup.start, markup.replacement, null)
                hl.clearMarkup(markup)
                hl.refresh()
            }
        }
    }

    // TODO unify with callAI
    private fun localComp() {
        val start = leftText.selectionStart
        val end = leftText.selectionEnd
        val input = leftText.text.substring(start, end)
        val t1 = Diffing.tokenize(input)
        val s1 = Diffing.toSentences(t1)

        val start2 = rightText.selectionStart
        val end2 = rightText.selectionEnd
        val output = rightText.text.substring(start2, end2)
        val t2 = Diffing.tokenize(output)
        val s2 = Diffing.toSentences(t2)

        val allActions = mutableListOf<Diffing.TokenAction>()
        for (i in s1.indices.intersect(s2.indices)) {
            println("Sentence 1: " + s1[i])
            println("Sentence 2: " + s2[i])
            val actions = Diffing.minEditDistanceWithActions(s1[i], s2[i])
            println("Actions: $actions")
            val consolidated = Diffing.consolidateActions(actions)
            println("Consolidated: " + consolidated)
            allActions.addAll(consolidated)
        }
        val hl = hilighting!!
        hl.updateMarkingsFrom(leftText, t1, allActions)
    }

    private fun callAI(command: String) {
        var start = leftText.selectionStart
        var end = leftText.selectionEnd
        if(start == end) {
            start = 0;
            end = leftText.text.length;
        }
        val input = leftText.text.substring(start, end)
        val output =  engine.callAI(command, input)
        rightText.text = output

        val t1 = Diffing.tokenize(input)
        val s1 = Diffing.toSentences(t1)
        val s2 = Diffing.toSentences(Diffing.tokenize(output))
        val allActions = mutableListOf<Diffing.TokenAction>()
        for (i in s1.indices.intersect(s2.indices)) {
            println("Sentence 1: " + s1[i])
            println("Sentence 2: " + s2[i])
            val actions = Diffing.minEditDistanceWithActions(s1[i], s2[i])
            println("Actions: $actions")
            val consolidated = Diffing.consolidateActions(actions)
            println("Consolidated: " + consolidated)
            allActions.addAll(consolidated)
        }

        val hl = hilighting
        if(hl != null) {
            hl.uninstall();
        }
        hilighting = Hilighting(leftText, t1, allActions, start, end)
    }

    override fun processWindowEvent(e: WindowEvent) {
        if (e.id == WindowEvent.WINDOW_CLOSING) {
            if (modified) {
                val result = JOptionPane.showConfirmDialog(
                    this,
                    "Input has been changed. Do you still want to close?",
                    "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION
                )
                if (result != JOptionPane.YES_OPTION) {
                    return
                }
            }
            prefSaver.save(this)
            System.exit(0)
        } else {
            super.processWindowEvent(e)
        }
    }

    fun documentChanged() {
        if(!modified) {
            modified = true
            title = title + " *"
        }
    }

    private fun open() {
        if (modified) {
            val result = JOptionPane.showConfirmDialog(this,
                "Input has been changed. Lose changes by loading new file?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION)
            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        val fileChooser = JFileChooser()
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(fileChooser.selectedFile.absolutePath)
        }
    }

    fun load(filename: String) {
        leftText.text = java.io.File(filename).readText()
        leftText.caretPosition = 0
        this.filename = filename
        title = "$TITLE - $filename"
        modified = false
        rightText.text = ""
    }

    private fun reload() {
        val filename = filename
        if (filename != null) {
            if (modified) {
                val result = JOptionPane.showConfirmDialog(this,
                    "Input has been changed. Lose changes reloading?",
                    "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION)
                if (result != JOptionPane.YES_OPTION) {
                    return
                }
            }

            load(filename)
        }
    }


        private fun save() {
        val filename = this.filename
        if (filename == null) {
            saveAs()
        } else {
            val file = java.io.File(filename)
            file.writeText(leftText.text)
            title = "$TITLE - $filename"
            modified = false
        }
    }

    private fun saveAs() {
        val fileChooser = JFileChooser()
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            filename = fileChooser.selectedFile.absolutePath
            save()
        }
    }

    fun legalEditPos(offset: Int): Boolean {
        val hl = hilighting
        if(hl != null) {
            if (offset < hl.start || offset > hl.end) {
                return false
            }
        }
        return true
    }

    fun updateHilights(pos: Int, len: Int) {
        hilighting?.updateHighlights(pos, len)
    }
}

class MouseClickAdapter(val function: (MouseEvent) -> Unit) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) {
        if(e != null) function(e);
    }
}

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        val mainWindow = MainWindow()
        mainWindow.isVisible = true
        if(args.size > 0) mainWindow.load(args[0])
    }
}

class MyDocumentFilter(val mw: MainWindow): DocumentFilter() {
    override fun insertString(fb: FilterBypass?, offset: Int, string: String?, attr: AttributeSet?) {
        if (mw.legalEditPos(offset)) {
            super.insertString(fb, offset, string, attr)
            mw.documentChanged()
        }
    }

    override fun remove(fb: FilterBypass, offset: Int, length: Int) {
        if (mw.legalEditPos(offset)) {
            super.remove(fb, offset, length)
            mw.documentChanged()
        }
    }

    override fun replace(fb: FilterBypass?, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
        if (mw.legalEditPos(offset)) {
            super.replace(fb, offset, length, text, attrs)
            mw.documentChanged()
        }
    }
}

class DocListener(val mw: MainWindow) : DocumentListener {
    override fun insertUpdate(e: DocumentEvent?) {
        if(e == null) return
        val pos = e.offset
        val len = e.length
        mw.updateHilights(pos, len);
    }

    override fun removeUpdate(e: DocumentEvent?) {
        if(e == null) return
        val pos = e.offset
        val len = e.length
        mw.updateHilights(pos, -len);
    }

    override fun changedUpdate(e: DocumentEvent?) {
        // apparently no indexes changed ...
    }

}
