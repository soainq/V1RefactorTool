# Internal Project Refactor Assistant

Android Studio plugin for repeated refactor/reskin runs on the same Android project.

Main priorities:

- safety first
- review before apply
- no root package rename
- no Android standard `res` folder rename
- avoid reusing names from previous versions
- fast batch selection by type
- debounced suggestion reload

## Menu

`Tools > Internal Refactor > Start Reskin Refactor Workflow`

## Workflow

1. Load `.project-refactor/history.json`
2. Load `.project-refactor/used-names.json`
3. Scan selected modules
4. Classify items into `SAFE_AUTO`, `REVIEW_REQUIRED`, `DO_NOT_TOUCH`
5. Group items by type
6. Let the user select types, select all types, or select all items
7. Reload suggestions for the active set with debounce
8. Filter and mark previously used names
9. Let the user review and edit names
10. Preview before -> after
11. Apply selected items only
12. Update references in scanned files
13. Reformat changed files
14. Save preview/apply session logs
15. Update history and used-name registry

## Current scan scope

- Kotlin file
- Kotlin class
- Activity
- Fragment
- ViewModel
- Adapter
- feature package for review only
- layout XML file
- drawable file
- string key
- dimen key

## Current apply scope

- Kotlin file rename
- Kotlin class rename
- Activity/Fragment/ViewModel/Adapter rename
- layout file rename
- drawable file rename
- string key rename
- dimen key rename
- exact-token reference updates inside scanned files
- IDE reformat after apply

Package rename is still scan/review only in this milestone.

## Type selection UX

The review screen contains:

- `Select All Types`
- `Select All Items`
- `Show previously used names`
- per-type checkboxes with counts
- per-item selection in the suggestion table

When the user changes:

- a type checkbox
- select all types
- select all items
- an item selection

the plugin reloads the active item set and regenerates suggestions with debounce.

Manual edits are preserved when unrelated groups reload.

## Project files

```text
.project-refactor/
  history.json
  used-names.json
  sessions/
    <session-id>.json
```

## Source layout

- [action/StartReskinRefactorAction.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/action/StartReskinRefactorAction.kt)
- [scan/ProjectScanner.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/scan/ProjectScanner.kt)
- [classify/ItemClassifier.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/classify/ItemClassifier.kt)
- [suggest/SuggestionService.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/suggest/SuggestionService.kt)
- [selection/TypeGrouping.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/selection/TypeGrouping.kt)
- [selection/ReviewSelectionCoordinator.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/selection/ReviewSelectionCoordinator.kt)
- [preview/ReviewValidationService.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/preview/ReviewValidationService.kt)
- [preview/PreviewBuilder.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/preview/PreviewBuilder.kt)
- [executor/FilesystemApplyEngine.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/executor/FilesystemApplyEngine.kt)
- [executor/IdePostProcessor.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/executor/IdePostProcessor.kt)
- [history/HistoryRepository.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/history/HistoryRepository.kt)
- [report/SessionRecordFactory.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/report/SessionRecordFactory.kt)
- [ui/ScanSettingsDialog.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/ui/ScanSettingsDialog.kt)
- [ui/SuggestionReviewDialog.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/ui/SuggestionReviewDialog.kt)
- [ui/PreviewPlanDialog.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/ui/PreviewPlanDialog.kt)
- [ui/ApplyResultDialog.kt](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/src/main/kotlin/com/internal/refactorassistant/ui/ApplyResultDialog.kt)

## Build

```powershell
.\gradlew.bat buildPlugin
```

## Run in sandbox IDE

```powershell
.\gradlew.bat runIde
```

## Run tests

```powershell
.\gradlew.bat test
```

## Docs

- [Spec](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/spec.md)
- [Architecture](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/architecture.md)
- [Known Limitations](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/known-limitations.md)
- [Next Steps](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/next-steps.md)
