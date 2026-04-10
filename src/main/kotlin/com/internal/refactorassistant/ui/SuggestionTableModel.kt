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
        "Group Key",
        "Canonical New Name",
        "Group Size",
        "Override Status",
        "Suggested names",
        "Suggestion source",
        "Candidate rank",
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

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0 || columnIndex == 10

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        val validation = validator.validate(row, rows)
        val usedMetadata = validator.usedMetadata(row.item.type, row.selectedNewName.trim())
        return when (columnIndex) {
            0 -> row.applySelected
            1 -> row.item.type.name
            2 -> row.item.oldName
            3 -> row.groupKey
            4 -> row.canonicalNewName
            5 -> row.groupSize.toString()
            6 -> if (row.overrideApplied) "OVERRIDDEN" else "CANONICAL"
            7 -> row.suggestions.joinToString(", ") { suggestion ->
                buildString {
                    append(suggestion.value)
                    append(" [")
                    append(suggestion.source.name)
                    append("]")
                    if (suggestion.usedMetadata.usedBefore) append(" (used)")
                }
            }

            8 -> row.selectedSuggestionSource?.name.orEmpty()
            9 -> row.selectedSuggestionSource?.rank?.toString().orEmpty()
            10 -> row.selectedNewName
            11 -> row.item.safetyLevel.name
            12 -> if (usedMetadata.usedBefore) "Yes" else "No"
            13 -> usedMetadata.lastUsedVersion.orEmpty()
            14 -> usedMetadata.lastUsedTimestamp.orEmpty()
            15 -> when {
                !row.applySelected -> "SKIPPED"
                validation.blocked -> "BLOCKED"
                else -> row.status
            }

            16 -> validation.warnings.joinToString(" ")
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

            10 -> {
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
