package de.matul.rephrasor

import de.matul.rephrasor.de.matul.rephrasor.Diffing
import java.awt.*
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

    init {
        title = TITLE
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
            item.addActionListener {
                val font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
                leftText.font = font
                rightText.font = font
            }
            fonts.add(item)
            fontMenu.add(item)
            if (fontSize == DEFAULT_FONT_SIZE) {
                item.isSelected = true
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
        for (preamble in preambles) {
            val button = JButton(preamble)
            button.addActionListener { callAI(preamble) }
            toolBar.add(button)
        }
        val clearButton = JButton("Clear")
        clearButton.addActionListener {
            if(hilighting != null)
                hilighting!!.uninstall()
            hilighting = null
            rightText.text = ""
        }
        toolBar.add(clearButton)
        val saveButton = JButton("Save")
        saveButton.addActionListener { save() }
        toolBar.add(saveButton)

        add(toolBar, BorderLayout.NORTH)

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
            filename = fileChooser.selectedFile.absolutePath
            title = "$TITLE - $filename"
            modified = false
            leftText.text = fileChooser.selectedFile.readText()
            rightText.text = ""
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
            if (offset <= hl.start || offset >= hl.end) {
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

fun main() {
    SwingUtilities.invokeLater {
        val mainWindow = MainWindow()
        mainWindow.isVisible = true
    }
}

fun loadPreambles(): Map<String, String> {
    return mapOf(
        "rephrase" to
                "You are an editor for a computer science journal. " +
                "You are an expert on Formal Methods in Computer Science, in particular in logics and deduction. " +
                "Your job is it to improve scientific quality and the language of text. " +
                "Do not add new content to the text, but rephrase text such that it sounds more like typical scientific texts of the formal methods domain. " +
                "You do not repeat the query presented to you. " +
                "You keep all latex or markdown annotations unchanged. " +
                "You prefer British over American English.",
        "check" to "You are an editor for a computer science journal. " +
                "You are an expert on Formal Methods in Computer Science, in particular in logics and deduction. " +
                "Your job is it to improve scientific quality and the language of text. " +
                "Do not add new content to the text, but only spellcheck text and make sure the English grammar is correct. " +
                "You do not repeat the query presented to you. " +
                "You keep all latex or markdown annotations unchanged. " +
                "You prefer British over American English."
    )
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
