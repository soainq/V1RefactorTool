package com.internal.refactorassistant.ui

import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.preview.ReviewValidationService
import javax.swing.table.AbstractTableModel

class SuggestionTableModel(
    initialRows: List<ReviewItemState>,
    private val validator: ReviewValidationService,
    private val onSelectionChanged: (String, Boolean) -> Unit,
    private val onManualNameChanged: (String, String) -> Unit,
) : AbstractTableModel() {
    private val columns = listOf(
        "Selected",
        "Type",
        "Old name",
        "Suggested names",
        "Selected new name",
        "Safety level",
        "Used before",
        "Last used version",
        "Last used timestamp",
        "Status",
        "Warning",
    )

    private val rows = initialRows.toMutableList()

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0 || columnIndex == 4

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        val validation = validator.validate(row, rows)
        val usedMetadata = validator.usedMetadata(row.item.type, row.selectedNewName.trim())
        return when (columnIndex) {
            0 -> row.applySelected
            1 -> row.item.type.name
            2 -> row.item.oldName
            3 -> row.suggestions.joinToString(", ") { suggestion ->
                if (suggestion.usedMetadata.usedBefore) "${suggestion.value} (used)" else suggestion.value
            }

            4 -> row.selectedNewName
            5 -> row.item.safetyLevel.name
            6 -> if (usedMetadata.usedBefore) "Yes" else "No"
            7 -> usedMetadata.lastUsedVersion.orEmpty()
            8 -> usedMetadata.lastUsedTimestamp.orEmpty()
            9 -> when {
                !row.applySelected -> "Skip"
                validation.blocked -> "Blocked"
                else -> row.status
            }

            10 -> validation.warnings.joinToString(" ")
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            0 -> {
                val selected = (aValue as? Boolean) == true
                row.applySelected = selected
                onSelectionChanged(row.item.id, selected)
            }

            4 -> {
                val value = aValue?.toString()?.trim().orEmpty()
                row.selectedNewName = value
                onManualNameChanged(row.item.id, value)
            }
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    fun replaceRows(newRows: List<ReviewItemState>) {
        rows.clear()
        rows += newRows
        fireTableDataChanged()
    }

    fun items(): List<ReviewItemState> = rows.map { row ->
        row.copy(
            suggestions = row.suggestions.toList(),
        )
    }
}
