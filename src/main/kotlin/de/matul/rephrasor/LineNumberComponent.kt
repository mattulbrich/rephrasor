package de.matul.rephrasor

import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


// Line number component
class LineNumberComponent(private val textArea: JTextArea) : JComponent() {
    private val fontMetrics: FontMetrics
    private val padding = 5

    init {
        this.fontMetrics = textArea.getFontMetrics(textArea.getFont())
        textArea.getDocument().addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                repaint()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                repaint()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                repaint()
            }
        })

        textArea.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                repaint()
            }
        })

        setFont(textArea.getFont())
    }

    override fun getPreferredSize(): Dimension {
        val lineCount = textArea.getLineCount()
        val width = fontMetrics.stringWidth(lineCount.toString()) + padding * 2
        return Dimension(width, textArea.getHeight())
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val clip = g.getClipBounds()
        val startOffset = textArea.viewToModel2D(Point(0, clip.y))
        val endOffset = textArea.viewToModel2D(Point(0, clip.y + clip.height))

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.setColor(Color.GRAY)
        for (line in getLineAtOffset(startOffset)..getLineAtOffset(endOffset)) {
            try {
                val y = textArea.modelToView2D(textArea.getLineStartOffset(line)).getBounds().y
                val lineNumber = (line + 1).toString()
                val x = getPreferredSize().width - fontMetrics.stringWidth(lineNumber) - padding
                val baseline = y + fontMetrics.getAscent()
                g.drawString(lineNumber, x, baseline + 5)
            } catch (e: Exception) {
                // Ignore bad locations
            }
        }
    }

    private fun getLineAtOffset(offset: Int): Int {
        try {
            return textArea.getLineOfOffset(offset)
        } catch (e: Exception) {
            return -1
        }
    }
}
