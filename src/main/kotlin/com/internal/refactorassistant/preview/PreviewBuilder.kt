package com.internal.refactorassistant.preview

import com.internal.refactorassistant.model.PreviewPlan
import com.internal.refactorassistant.model.PreviewRow
import com.internal.refactorassistant.model.PreviewSummary
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.selection.TypeGrouping

class PreviewBuilder(
    private val validator: ReviewValidationService,
) {
    fun build(reviewItems: List<ReviewItemState>): PreviewPlan {
        val warnings = mutableListOf<String>()
        var skipped = 0
        var blocked = 0

        val rows = reviewItems.map { item ->
            val validation = validator.validate(item, reviewItems)
            val status = when {
                !item.applySelected -> {
                    skipped += 1
                    "SKIPPED"
                }

                validation.blocked -> {
                    blocked += 1
                    "BLOCKED"
                }

                else -> "READY"
            }
            warnings += validation.warnings
            val reason = when {
                !item.applySelected && item.item.safetyLevel == com.internal.refactorassistant.model.SafetyLevel.DO_NOT_TOUCH -> "Do not touch"
                !item.applySelected -> "User skipped"
                validation.blocked -> validation.warnings.firstOrNull().orEmpty().ifBlank { "Real conflict" }
                validation.warnings.isNotEmpty() -> validation.warnings.first()
                else -> "OK"
            }

            PreviewRow(
                type = item.item.type,
                safetyLevel = item.item.safetyLevel,
                before = item.item.oldName,
                after = item.selectedNewName,
                rawCandidate = item.suggestions.firstOrNull { it.value == item.selectedNewName }?.rawValue ?: item.selectedNewName,
                normalizationNote = item.suggestions.firstOrNull { it.value == item.selectedNewName }?.normalizationNote.orEmpty(),
                groupKey = item.groupKey,
                canonicalNewName = item.canonicalNewName,
                groupSize = item.groupSize,
                overrideStatus = if (item.overrideApplied) "OVERRIDDEN" else "CANONICAL",
                suggestionSource = item.selectedSuggestionSource?.name ?: "",
                candidateRank = item.selectedSuggestionSource?.rank?.toString().orEmpty(),
                moduleName = item.item.moduleName,
                path = item.item.displayPath,
                status = status,
                warning = reason,
            )
        }

        val summary = PreviewSummary(
            selectedCount = rows.count { it.status == "READY" },
            skippedCount = skipped,
            blockedCount = blocked,
            selectedCountByGroup = rows
                .filter { it.status == "READY" }
                .groupingBy { row ->
                    TypeGrouping.primaryGroup(
                        reviewItems.first {
                            it.item.type == row.type &&
                                it.item.oldName == row.before &&
                                it.item.displayPath == row.path
                        }.item
                    )
                }
                .eachCount(),
        )
        return PreviewPlan(rows = rows, warnings = warnings.distinct(), summary = summary)
    }
}
