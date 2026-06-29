package de.matul.rephrasor

import com.cjcrafter.openai.chat.ChatMessage.Companion.toSystemMessage
import com.cjcrafter.openai.chat.ChatMessage.Companion.toUserMessage
import com.cjcrafter.openai.chat.ChatUser
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.StyleConstants
import java.awt.Color
import java.io.File

class ChatDialog(val mainWindow: MainWindow, val selectedText: String) : JDialog(mainWindow, "AI Chat", true) {
    
    private val engine = mainWindow.engine
    private val roleSelector: JComboBox<String>
    private val contextFileList: JList<String>
    private val contextFileListModel: DefaultListModel<String>
    private val transcriptArea: JTextPane
    private val inputArea: JTextArea
    private val sendButton: JButton
    private val proposeButton: JButton
    private val chatHistory = mutableListOf<ChatMessage>()
    
    private val preferences = Preferences.userNodeForPackage(ChatDialog::class.java)
    
    init {
        layout = BorderLayout()
        size = Dimension(700, 600)
        setLocationRelativeTo(mainWindow)
        
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            val rolePanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JLabel("Role: "))
                roleSelector = JComboBox<String>(engine.knownChatRoles.toTypedArray()).apply {
                    val lastRole = preferences.get("last-chat-role", "")
                    if (lastRole.isNotEmpty() && engine.knownChatRoles.contains(lastRole)) {
                        selectedItem = lastRole
                    }
                    addActionListener {
                        preferences.put("last-chat-role", selectedItem.toString())
                    }
                }
                add(roleSelector)
            }
            add(rolePanel)
            
            add(Box.createVerticalStrut(10))
            
            val contextPanel = JPanel().apply {
                layout = BorderLayout()
                border = BorderFactory.createTitledBorder("Context Files")
                
                contextFileListModel = DefaultListModel<String>()
                val lastFiles = preferences.get("last-chat-files", "").split("|").filter { it.isNotEmpty() }
                lastFiles.forEach { contextFileListModel.addElement(it) }
                
                contextFileList = JList(contextFileListModel).apply {
                    visibleRowCount = 4
                }
                add(JScrollPane(contextFileList), BorderLayout.CENTER)
                
                val buttonPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    val addButton = JButton("Add File...").apply {
                        addActionListener { addContextFile() }
                    }
                    add(addButton)
                    add(Box.createVerticalStrut(5))
                    val removeButton = JButton("Remove").apply {
                        addActionListener { removeContextFile() }
                    }
                    add(removeButton)
                }
                add(buttonPanel, BorderLayout.EAST)
            }
            add(contextPanel)
        }
        add(topPanel, BorderLayout.NORTH)
        
        transcriptArea = JTextPane().apply {
            isEditable = false
            preferredSize = Dimension(700, 300)
        }
        add(JScrollPane(transcriptArea), BorderLayout.CENTER)
        
        val bottomPanel = JPanel().apply {
            layout = BorderLayout()
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            inputArea = JTextArea().apply {
                rows = 4
                lineWrap = true
                wrapStyleWord = true
                document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) { updateButtons() }
                    override fun removeUpdate(e: DocumentEvent?) { updateButtons() }
                    override fun changedUpdate(e: DocumentEvent?) { updateButtons() }
                })
            }
            add(JScrollPane(inputArea), BorderLayout.CENTER)
            
            val buttonPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                
                sendButton = JButton("Send").apply {
                    isEnabled = false
                    addActionListener { sendMessage() }
                }
                add(sendButton)
                add(Box.createVerticalStrut(5))
                
                proposeButton = JButton("Propose Rephrasing").apply {
                    addActionListener { proposeRephrasing() }
                }
                add(proposeButton)
                add(Box.createVerticalStrut(5))
                
                val closeButton = JButton("Close").apply {
                    addActionListener { dispose() }
                }
                add(closeButton)
            }
            add(buttonPanel, BorderLayout.EAST)
        }
        add(bottomPanel, BorderLayout.SOUTH)
        
        appendToTranscript("system", "Chat started. Selected text length: ${selectedText.length} characters")
        updateButtons()
    }
    
    private fun addContextFile() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
        }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFiles.forEach { file ->
                if (!contextFileListModel.contains(file.absolutePath)) {
                    contextFileListModel.addElement(file.absolutePath)
                }
            }
            saveContextFiles()
        }
    }
    
    private fun removeContextFile() {
        val selectedIndex = contextFileList.selectedIndex
        if (selectedIndex >= 0) {
            contextFileListModel.remove(selectedIndex)
            saveContextFiles()
        }
    }
    
    private fun saveContextFiles() {
        val files = (0 until contextFileListModel.size).map { contextFileListModel.getElementAt(it) }
        preferences.put("last-chat-files", files.joinToString("|"))
    }
    
    private fun updateButtons() {
        sendButton.isEnabled = inputArea.text.isNotBlank()
    }
    
    private fun appendToTranscript(role: String, content: String) {
        val doc = transcriptArea.styledDocument
        val style = transcriptArea.addStyle(role, null)
        
        when (role) {
            "user" -> StyleConstants.setForeground(style, Color.BLUE)
            "assistant" -> StyleConstants.setForeground(style, Color.BLACK)
            "system" -> StyleConstants.setForeground(style, Color.GRAY)
        }
        
        doc.insertString(doc.length, "${role.uppercase()}: ", style)
        val contentStyle = transcriptArea.addStyle("${role}-content", null)
        StyleConstants.setForeground(contentStyle, when (role) {
            "user" -> Color.BLUE
            "assistant" -> Color.BLACK
            "system" -> Color.GRAY
            else -> Color.BLACK
        })
        doc.insertString(doc.length, "$content\n\n", contentStyle)
        
        transcriptArea.caretPosition = doc.length
    }
    
    private fun sendMessage() {
        val userMessage = inputArea.text.trim()
        if (userMessage.isBlank()) return
        
        inputArea.text = ""
        chatHistory.add(ChatMessage("user", userMessage))
        appendToTranscript("user", userMessage)
        
        val progress = JOptionPane(
            "Contacting AI",
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            arrayOf<Any>("Cancel"),
            null
        )
        val dialog: JDialog = progress.createDialog(this, "AI")
        
        val thread = object : Thread("AI Chat Call") {
            override fun run() {
                try {
                    val messages = buildChatMessages()
                    val response = engine.callChat(messages)
                    SwingUtilities.invokeLater {
                        chatHistory.add(ChatMessage("assistant", response))
                        appendToTranscript("assistant", response)
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this@ChatDialog,
                            "Call error: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                } finally {
                    SwingUtilities.invokeLater { dialog.isVisible = false }
                }
            }
        }
        thread.start()
        dialog.isVisible = true
        thread.interrupt()
    }
    
    private fun buildChatMessages(): List<com.cjcrafter.openai.chat.ChatMessage> {
        val roleName = roleSelector.getSelectedItem().toString()
        val rolePrompt = engine.getChatRole(roleName)
        
        val messages = mutableListOf<com.cjcrafter.openai.chat.ChatMessage>()
        messages.add(rolePrompt.toSystemMessage())
        
        val contextContent = buildContext()
        if (contextContent.isNotBlank()) {
            messages.add(contextContent.toSystemMessage())
        }
        
        for (msg in chatHistory) {
            when (msg.role) {
                "user" -> messages.add(msg.content.toUserMessage())
                "assistant" -> messages.add(com.cjcrafter.openai.chat.ChatMessage(ChatUser.ASSISTANT, msg.content))
            }
        }
        
        return messages
    }
    
    private fun buildContext(): String {
        val sb = StringBuilder()
        for (i in 0 until contextFileListModel.size) {
            val filePath = contextFileListModel.getElementAt(i)
            try {
                val content = File(filePath).readText()
                sb.append("FILE: $filePath\n")
                sb.append(content)
                sb.append("\n\n")
            } catch (e: Exception) {
                sb.append("FILE: $filePath (ERROR: ${e.message})\n\n")
            }
        }
        return sb.toString()
    }
    
    private fun proposeRephrasing() {
        val progress = JOptionPane(
            "Generating Rephrasing",
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            arrayOf<Any>("Cancel"),
            null
        )
        val dialog: JDialog = progress.createDialog(this, "AI")
        
        val thread = object : Thread("AI Rephrasing Call") {
            override fun run() {
                try {
                    val messages = buildRephrasingMessages()
                    val response = engine.callChat(messages)
                    SwingUtilities.invokeLater {
                        applyRephrasing(response)
                        dispose()
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        dialog.isVisible = false
                        JOptionPane.showMessageDialog(
                            this@ChatDialog,
                            "Call error: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                } finally {
                    SwingUtilities.invokeLater { dialog.isVisible = false }
                }
            }
        }
        thread.start()
        dialog.isVisible = true
        thread.interrupt()
    }
    
    private fun buildRephrasingMessages(): List<com.cjcrafter.openai.chat.ChatMessage> {
        val roleName = roleSelector.getSelectedItem().toString()
        val rolePrompt = engine.getChatRole(roleName)
        
        val messages = mutableListOf<com.cjcrafter.openai.chat.ChatMessage>()
        messages.add(rolePrompt.toSystemMessage())
        
        val contextContent = buildContext()
        if (contextContent.isNotBlank()) {
            messages.add(contextContent.toSystemMessage())
        }
        
        for (msg in chatHistory) {
            when (msg.role) {
                "user" -> messages.add(msg.content.toUserMessage())
                "assistant" -> messages.add(com.cjcrafter.openai.chat.ChatMessage(ChatUser.ASSISTANT, msg.content))
            }
        }
        
        val rephrasePrompt = """
Now rephrase the selected section according to our discussion. Constraints:
- Preserve LaTeX/Markdown markup
- Do not add new content
- Prefer British English

Selected text:
"""
        messages.add((rephrasePrompt + "\"\"\"\n$selectedText\n\"\"\"").toUserMessage())
        
        return messages
    }
    
    private fun applyRephrasing(response: String) {
        val start = mainWindow.leftText.selectionStart
        val end = mainWindow.leftText.selectionEnd
        val actualStart = if (start == end) 0 else start
        val actualEnd = if (start == end) mainWindow.leftText.text.length else end
        
        mainWindow.makeHighlighter(selectedText, response, actualStart, actualEnd)
    }
}
