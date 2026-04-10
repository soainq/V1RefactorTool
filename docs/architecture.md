# Architecture Proposal

## Packages

### `action/`

Entrypoint orchestration only.

### `scan/`

Scans Kotlin files, resource files, and values XML while skipping protected folders such as `build`, `.idea`, and `.project-refactor`.

### `classify/`

Maps scan results into `SAFE_AUTO`, `REVIEW_REQUIRED`, and `DO_NOT_TOUCH`.

### `suggest/`

Builds candidate names from dictionary-first rules.

### `selection/`

- `TypeGrouping` maps scanned items to UI type groups.
- `ReviewSelectionCoordinator` owns:
  - selected groups
  - select-all state
  - item selection overrides
  - manual name overrides
  - rebuild logic for the active set

### `preview/`

- `ReviewValidationService` checks conflicts, invalid names, and used-before warnings.
- `PreviewBuilder` creates preview rows and per-type counts.

### `executor/`

- `FilesystemApplyEngine` performs file rename and exact-token reference updates on scanned files.
- `IdePostProcessor` reformats changed files in the IDE.

### `history/`

Reads and writes JSON history, used-name registry, and session logs.

### `report/`

Builds preview/apply session records with selected type metadata.

### `ui/`

Four dialogs:

1. `ScanSettingsDialog`
2. `SuggestionReviewDialog`
3. `PreviewPlanDialog`
4. `ApplyResultDialog`

`SuggestionReviewDialog` uses debounced pooled-thread reloads and preserves manual edits through the coordinator.
