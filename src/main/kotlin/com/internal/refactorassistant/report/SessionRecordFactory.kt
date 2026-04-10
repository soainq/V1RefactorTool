package com.internal.refactorassistant.report

import com.internal.refactorassistant.model.ApplyExecutionOutcome
import com.internal.refactorassistant.model.PreviewPlan
import com.internal.refactorassistant.model.RefactorSelectionGroup
import com.internal.refactorassistant.model.ReviewItemState
import com.internal.refactorassistant.model.ScanSettings
import com.internal.refactorassistant.model.SessionItemRecord
import com.internal.refactorassistant.model.SessionItemResult
import com.internal.refactorassistant.model.SessionMode
import com.internal.refactorassistant.model.SessionRecord
import com.internal.refactorassistant.model.SessionSummary
import com.internal.refactorassistant.util.TimeUtil

object SessionRecordFactory {
    fun preview(
        settings: ScanSettings,
        selectedGroups: Set<RefactorSelectionGroup>,
        reviewItems: List<ReviewItemState>,
        previewPlan: PreviewPlan,
    ): SessionRecord {
        val itemResults = previewPlan.rows.associateBy { row ->
            reviewItems.first { it.item.oldName == row.before && it.item.type == row.type && it.item.displayPath == row.path }.item.id
        }

        return SessionRecord(
            sessionId = TimeUtil.newSessionId(),
            timestamp = TimeUtil.nowIso(),
            mode = SessionMode.PREVIEW,
            versionLabel = settings.versionLabel,
            userNote = settings.note,
            selectedTypes = selectedGroups.sortedBy { it.ordinal },
            selectedItemCount = reviewItems.count { it.applySelected },
            summary = SessionSummary(
                totalItems = reviewItems.size,
                selectedItems = previewPlan.summary.selectedCount,
                skippedItems = previewPlan.summary.skippedCount,
                blockedItems = previewPlan.summary.blockedCount,
                appliedItems = 0,
                failedItems = 0,
            ),
            items = reviewItems.map { item ->
                val row = itemResults[item.item.id]
                val result = when (row?.status) {
                    "Selected" -> SessionItemResult.PREVIEWED
                    "Blocked" -> SessionItemResult.BLOCKED
                    else -> SessionItemResult.SKIPPED
                }
                SessionItemRecord(
                    type = item.item.type,
                    oldName = item.item.oldName,
                    suggestedNames = item.suggestions.map { it.value },
                    selectedNewName = item.selectedNewName.takeIf { it.isNotBlank() },
                    result = result,
                    warning = row?.warning?.takeIf { it.isNotBlank() },
                )
            },
        )
    }

    fun apply(
        settings: ScanSettings,
        selectedGroups: Set<RefactorSelectionGroup>,
        reviewItems: List<ReviewItemState>,
        outcome: ApplyExecutionOutcome,
    ): SessionRecord {
        val applied = outcome.itemResults.values.count { it == SessionItemResult.APPLIED }
        val failed = outcome.itemResults.values.count { it == SessionItemResult.FAILED }
        val blocked = outcome.itemResults.values.count { it == SessionItemResult.BLOCKED }
        val skipped = outcome.itemResults.values.count { it == SessionItemResult.SKIPPED }

        return SessionRecord(
            sessionId = TimeUtil.newSessionId(),
            timestamp = TimeUtil.nowIso(),
            mode = SessionMode.APPLY,
            versionLabel = settings.versionLabel,
            userNote = settings.note,
            selectedTypes = selectedGroups.sortedBy { it.ordinal },
            selectedItemCount = reviewItems.count { it.applySelected },
            summary = SessionSummary(
                totalItems = reviewItems.size,
                selectedItems = reviewItems.count { it.applySelected },
                skippedItems = skipped,
                blockedItems = blocked,
                appliedItems = applied,
                failedItems = failed,
            ),
            items = reviewItems.map { item ->
                SessionItemRecord(
                    type = item.item.type,
                    oldName = item.item.oldName,
                    suggestedNames = item.suggestions.map { it.value },
                    selectedNewName = item.selectedNewName.takeIf { it.isNotBlank() },
                    result = outcome.itemResults[item.item.id] ?: SessionItemResult.SKIPPED,
                    warning = outcome.warningsByItemId[item.item.id]?.takeIf { it.isNotBlank() },
                )
            },
        )
    }
}
