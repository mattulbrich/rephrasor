package de.matul.rephrasor

import de.matul.rephrasor.de.matul.rephrasor.Diffing
import java.awt.Color
import javax.swing.JTextArea
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter

val OUTSIDE = DefaultHighlightPainter(Color.lightGray)
val CHANGE = DefaultHighlightPainter(Color.green.brighter())

class Hilighting(val leftText: JTextArea, tokens: List<Diffing.Token>, allActions: MutableList<Diffing.TokenAction>, val start: Int, var end: Int) {

    private var markups: List<Markup>

    init {
        markups = computeMarkups(allActions, tokens)
        install()
    }

    private fun install() {
        for (markup in markups) {
            println(markup)
            leftText.highlighter.addHighlight(markup.start, markup.end, CHANGE)
        }
        leftText.highlighter.addHighlight(0, start-1, OUTSIDE)
        leftText.highlighter.addHighlight(end+1, leftText.text.length, OUTSIDE)
    }

    fun uninstall() {
        leftText.highlighter.removeAllHighlights()
    }

    private fun computeMarkups(allActions: MutableList<Diffing.TokenAction>, tokenPositions: List<Diffing.Token>): MutableList<Markup> {

        var tokenCnt = 0;
        val result = mutableListOf<Markup>()
        for (action in allActions) {
            if(action.replacement == null) {
                // copy
                tokenCnt += action.tokenCount
            } else {
                // replace
                val t1 = tokenPositions[tokenCnt]
                if(action.tokenCount > 0) {
                    tokenCnt += action.tokenCount
                    val t2 = tokenPositions[tokenCnt - 1]
                    result.add(Markup(t1.from + start, t2.after + start, action.replacement))
                } else {
                    result.add(Markup(t1.from + start - 1, t1.from + start, " ${action.replacement} "))
                }
            }
        }
        return result
    }

    fun updateHighlights(pos: Int, len: Int) {
        markups = markups.map {
            if(it.start > pos) {
                Markup(it.start + len, it.end + len, it.replacement)
            } else if(it.end > pos) {
                Markup(it.start, it.end + len, it.replacement)
            } else {
                it
            }
        }
        end += len
    }

    fun findMarkup(pos: Int): Markup? {
        return markups.find { it.start <= pos && it.end >= pos }
    }

    fun clearMarkup(markup: Markup) {
        markups = markups.filter { it != markup }
    }

    fun clearMarkups() {
        markups = emptyList()
    }

    fun refresh() {
        uninstall()
        install()
    }

    fun replaceAll(text: String) {
        leftText.document.remove(start, end - start)
        leftText.document.insertString(start,text, null)
    }

    fun updateMarkingsFrom(leftText: JTextArea, tokens: List<Diffing.Token>, allActions: MutableList<Diffing.TokenAction>) {
        uninstall()
        markups = computeMarkups(allActions, tokens)
        install()
    }

}

data class Markup(val start: Int, val end: Int, val replacement: String)

