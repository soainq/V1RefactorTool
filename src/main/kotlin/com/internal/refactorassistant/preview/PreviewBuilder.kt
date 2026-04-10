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
                    "Skipped"
                }

                validation.blocked -> {
                    blocked += 1
                    "Blocked"
                }

                else -> "Selected"
            }
            warnings += validation.warnings

            PreviewRow(
                type = item.item.type,
                safetyLevel = item.item.safetyLevel,
                before = item.item.oldName,
                after = item.selectedNewName,
                moduleName = item.item.moduleName,
                path = item.item.displayPath,
                status = status,
                warning = validation.warnings.joinToString(" "),
            )
        }

        val summary = PreviewSummary(
            selectedCount = rows.count { it.status == "Selected" },
            skippedCount = skipped,
            blockedCount = blocked,
            selectedCountByGroup = rows
                .filter { it.status == "Selected" }
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
