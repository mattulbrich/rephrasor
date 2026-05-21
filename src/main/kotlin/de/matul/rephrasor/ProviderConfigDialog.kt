package de.matul.rephrasor

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class ProviderConfigDialog(owner: Frame) :
    JDialog(owner, "Provider Configuration", true) {

    private val tableModel = object : DefaultTableModel(
        arrayOf("Provider", "Base URL", "Chosen Model", "Key"),
        0 ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true }

    private val providerTable = JTable(tableModel)

    init {
        val cp = contentPane
        layout = BorderLayout()
        defaultCloseOperation = DISPOSE_ON_CLOSE
        loadProviders()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val addButton = JButton("Add")
        val removeButton = JButton("Remove")
        val saveButton = JButton("Save")
        val cancelButton = JButton("Cancel")
        addButton.addActionListener {
            tableModel.addRow(arrayOf("", "", ""))
        }
        removeButton.addActionListener {
            val row = providerTable.selectedRow
            if (row >=0) { tableModel.removeRow(row) }
        }
        saveButton.addActionListener {
            writeBack()
            dispose()
        }
        cancelButton.addActionListener {
            dispose()
        }
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(saveButton)
        buttonPanel.add(cancelButton)
        cp.add(buttonPanel, BorderLayout.SOUTH)

        val content = JPanel(BorderLayout())
        content.add(JScrollPane(providerTable), BorderLayout.CENTER)
        cp.add(content, BorderLayout.CENTER)

        setSize(800,400)
        setLocationRelativeTo(owner)
    }

    private fun loadProviders() {
        tableModel.rowCount = 0
        for (provider in Engine.knownProviders.values) {
            tableModel.addRow(
                arrayOf(
                    provider.provider,
                    provider.baseUrl,
                    provider.modelName ?: "",
                    provider.key )
            )
        }
    }

    private fun writeBack() {
        val newProviders =
            (0 until tableModel.rowCount)
                .map { row ->
                    val provider = tableModel.getValueAt(row, 0) as String
                    val baseUrl = tableModel.getValueAt(row, 1) as String
                    val modelName = tableModel.getValueAt(row, 2) as String
                    val key = tableModel.getValueAt(row, 3) as String
                    ModelInfo(provider, baseUrl, modelName, key)
                }
        Engine.saveProviders(newProviders)
    }
}