package de.matul.rephrasor

import de.uka.ilkd.key.util.PreferenceSaver
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Point
import java.awt.event.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import javax.swing.undo.AbstractUndoableEdit
import javax.swing.undo.UndoManager


val TITLE = "Rephrasor"
val FONT_SIZES = listOf(11, 12, 14, 16, 18, 20)
val DEFAULT_FONT_SIZE = 16

class MainWindow : JFrame() {

    private var editPrompt: Boolean = false
    private var rightText: JTextArea
    private var leftText: JTextArea
    private val undoManager = UndoManager()

    internal val engine = Engine()
    private val preambles = engine.knownPreambles
    private var hilighting: Hilighting? = null

    var ignorePragmas =
        Preferences.userNodeForPackage(Engine::class.java).getBoolean("ignore-pragmas", false)
        set(value) {
            Preferences.userNodeForPackage(Engine::class.java).putBoolean("ignore-pragmas", value)
        }

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
        leftText = JTextArea()
        leftText.wrapStyleWord = true
        leftText.lineWrap = true
        (leftText.document as AbstractDocument).documentFilter = MyDocumentFilter(this)
        (leftText.document as AbstractDocument).addDocumentListener(DocListener(this))
        leftText.addMouseListener(MouseClickAdapter { mouse(it) })
        leftText.addMouseMotionListener(MouseMovedAdapter { moveMouse(it) })
        leftText.toolTipText = ""
        // Add DocumentListener to track changes for undo/redo
        leftText.document.addUndoableEditListener { undoManager.addEdit(it.edit) }

        rightText = JTextArea()
        rightText.wrapStyleWord = true
        rightText.lineWrap = true
        rightText.isEditable = false
        rightText.addMouseListener(MouseClickAdapter { mouseRight(it) })

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(leftText), JScrollPane(rightText))
        splitPane.dividerLocation = 400
        splitPane.name = "splitPane"
        add(splitPane, BorderLayout.CENTER)

        // Create Menu Bar
        val menuBar = JMenuBar()
        val fileMenu = JMenu("File")
        val open = JMenuItem("Open")
        open.accelerator = KeyStroke.getKeyStroke("control O")
        open.addActionListener { open() }
        fileMenu.add(open)
        val reload = JMenuItem("Reload")
        reload.addActionListener { reload() }
        fileMenu.add(reload)
        val save = JMenuItem("Save")
        save.addActionListener { save() }
        save.accelerator = KeyStroke.getKeyStroke("control S")
        fileMenu.add(save)
        val saveAs = JMenuItem("Save As ...")
        saveAs.addActionListener { saveAs() }
        fileMenu.add(saveAs)
        fileMenu.addSeparator()
        val exit = JMenuItem("Exit")
        exit.addActionListener { processWindowEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING)) }
        exit.accelerator = KeyStroke.getKeyStroke("control Q")
        fileMenu.add(exit)

        val editMen = JMenu("Edit")
        val undo = JMenuItem("Undo")
        undo.addActionListener { if (undoManager.canUndo()) undoManager.undo() }
        undo.accelerator = KeyStroke.getKeyStroke("control Z")
        editMen.add(undo)
        val redo = JMenuItem("Redo")
        redo.addActionListener { if (undoManager.canRedo()) undoManager.redo() }
        redo.accelerator = KeyStroke.getKeyStroke("control Y")
        editMen.add(redo)

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

        val sentencesItem = JCheckBoxMenuItem("Align Sentences", engine.alignForSentences)
        sentencesItem.addActionListener { engine.alignForSentences = sentencesItem.isSelected }
        settingsMenu.add(sentencesItem)

        val ignorePragmasItem = JCheckBoxMenuItem("Ignore Pragmas", ignorePragmas)
        ignorePragmasItem.addActionListener { ignorePragmas = ignorePragmasItem.isSelected }
        settingsMenu.add(ignorePragmasItem)

        menuBar.add(fileMenu)
        menuBar.add(editMen)
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
        
        toolBar.addSeparator()
        
        val editPromptButton = JToggleButton("Edit Prompt")
        editPromptButton.addActionListener { editPrompt = editPromptButton.isSelected }
        toolBar.add(editPromptButton)
        
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
            val pos = leftText.viewToModel2D(Point(e.x, e.y))
            if(e.isControlDown) {
                hl.beginIndicator = pos
                hl.refresh()
            } else {
                val markup = hl.findMarkup(pos)
                if (markup != null) {
//                    undoManager.addEdit(ReplacementEdit(leftText, markup.start,
//                        markup.replacement.toString(),
//                        leftText.text.substring(markup.start, markup.end)))
                    leftText.document.remove(markup.start, markup.end - markup.start)
                    leftText.document.insertString(markup.start, markup.replacement.toString(), null)
                    hl.clearMarkup(markup)
                    hl.refresh()
                }
            }
        }
    }


    private fun moveMouse(e: MouseEvent) {
        val hl = hilighting
        if(hl != null) {
            val pos = leftText.viewToModel2D(Point(e.x, e.y))
            val markup = hl.findMarkup(pos)
            if (markup != null) {
                val range = markup.replacement
                leftText.toolTipText = range.toString()
                hl.highlightRight(rightText, range)
            } else {
                leftText.toolTipText = null
                hl.highlightRight(rightText, null)
            }
        }
    }

    private fun mouseRight(e: MouseEvent) {
        val hl = hilighting
        if(SwingUtilities.isRightMouseButton(e) && e.isControlDown && hl != null && hl.beginIndicator >= 0) {
            val pos = rightText.viewToModel2D(Point(e.x, e.y))
            val input = leftText.text.substring(hl.beginIndicator, hl.end)
            val output = rightText.text.substring(pos)
            makeHighlighter(input, output, hl.beginIndicator, hl.end)
        }
    }

    private fun callAI(command: String) {
        var start = leftText.selectionStart
        var end = leftText.selectionEnd
        if (start == end) {
            start = 0;
            end = leftText.text.length;
        }
        val input = leftText.text.substring(start, end)

        val preambleOverride = if(editPrompt) {
            val ta = JTextArea(15, 80)
            ta.text = engine.getPreamble(command)
            ta.lineWrap = true
            when (JOptionPane.showConfirmDialog(null, JScrollPane(ta), "Edit prompt", JOptionPane.OK_CANCEL_OPTION)) {
                JOptionPane.OK_OPTION -> ta.getText()
                else -> return
            }
        } else null

        val progress = JOptionPane(
            "Contacting AI",
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            arrayOf<Any>("Cancel"),
            null
        )
        val dialog: JDialog = progress.createDialog(this, "AI")

        val thread = object : Thread("AI Call") {
            override fun run() {
                val output = engine.callAI(command, makeContext(), input, preambleOverride)
                SwingUtilities.invokeLater { makeHighlighter(input, output, start, end); dialog.isVisible = false }
            }
        }
        thread.start()
        dialog.isVisible = true
        thread.interrupt()
    }

    private fun makeContext(): String {
        if(ignorePragmas) return ""
        val pattern = "<rephrasor:context-file>(.*?)</>".toRegex()
        val matchResult = pattern.find(leftText.text)
        return if (matchResult != null) {
            val filename = matchResult.groupValues[1]
            val fileContent = java.io.File(filename).readText()
            fileContent
        } else {
            ""
        }
    }

    private fun makeHighlighter(input: String, output: String, start: Int, end: Int) {
        makeHighlighter(input, output, start, end, start, end)
    }

    private fun makeHighlighter(input: String, output: String, start: Int, end: Int, hiLightStart: Int, hiLightEnd: Int) {
        rightText.text = output

        val t1 = Diffing.tokenize(input)
        val s1 = if(engine.alignForSentences) Diffing.toSentences(t1) else listOf(t1)
        val s2 = if(engine.alignForSentences) Diffing.toSentences(Diffing.tokenize(output)) else listOf(Diffing.tokenize(output))
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
        hilighting = Hilighting(leftText, t1, allActions, hiLightStart, hiLightEnd)
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

class MouseMovedAdapter(val function: (MouseEvent) -> Unit) : MouseMotionAdapter() {
    override fun mouseMoved(e: MouseEvent?) {
        if(e != null) function(e);
    }
}

fun readVersionResource(): String {
    val classLoader = Thread.currentThread().contextClassLoader
    classLoader.getResourceAsStream("version.txt").use { inputStream ->
        return inputStream?.bufferedReader(StandardCharsets.UTF_8)?.readText() ?: "Resource not found"
    }
}

fun main(args: Array<String>) {
    println("Rephrasor (Version: ${readVersionResource()})")
    SwingUtilities.invokeLater {
        val mainWindow = MainWindow()
        mainWindow.isVisible = true
        if(args.size > 0) mainWindow.load(args[0])
        if(args.size > 1) mainWindow.engine.fakeAnswer = Files.readString(Paths.get(args[1]))
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

class ReplacementEdit(val ta: JTextArea, val offset: Int, val newtext: String, val oldtext: String): AbstractUndoableEdit() {
    override fun undo() {
        super.undo()
        ta.document.remove(offset, newtext.length)
        ta.document.insertString(offset, oldtext, null)
    }

    override fun redo() {
        super.redo()
        ta.document.remove(offset, oldtext.length)
        ta.document.insertString(offset, newtext, null)
    }
}