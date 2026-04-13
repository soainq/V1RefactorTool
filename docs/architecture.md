# Architecture Proposal

## Goals

The plugin is designed as a plan-first refactoring assistant:

1. collect structured user intent
2. scan the selected project scope
3. build a deterministic `RefactorPlan`
4. preview the plan with conflicts and warnings
5. execute the plan phase by phase
6. generate machine-readable and human-readable reports

The design keeps actions stateless and pushes persistence plus orchestration into services and dedicated components.

## Runtime Flow

### 1. Action entrypoint

`DuplicateTemplateProjectAction` is the only entrypoint registered in `plugin.xml`.

- validates project availability
- opens the request dialog
- hands off work to the planner pipeline

### 2. Request capture

`RefactorRequestDialog` collects:

- old/new feature names
- old/new display names
- old/new package prefixes
- pasted JSON/YAML synonym dictionary
- included modules
- V1 and V2 option flags

Defaults come from `InternalRefactorAssistantSettingsService`.

### 3. Scan phase

`ProjectStructureScanner` walks the selected modules and produces a `ScanResult`.

It records:

- package-bearing Kotlin and Java files
- Kotlin classes and files
- Android `res/layout` and `res/drawable*` file resources
- `values/*.xml` string and dimen resources
- candidate reference-bearing files (`.kt`, `.java`, `.xml`)
- source-root and package-root information

The scanner only gathers facts. It does not decide renames.

### 4. Planning phase

`RefactorPlanner` combines the scan result, request, and `NamingRuleEngine`.

It emits a `RefactorPlan` with:

- package operations
- Kotlin symbol and file operations
- resource operations
- controlled reference rewrite operations
- conflicts
- warnings
- summary counts

Planning is deterministic:

- no AI suggestions
- no inferred semantic rewrites beyond exact token maps and configured regex rules

### 5. Preview phase

`RefactorPlanPreviewDialog` renders the plan as a flat, sortable table grouped by category plus a warning/conflict panel.

The preview also supports exporting the plan to:

- JSON
- Markdown

Dry-run mode stops here after export/report generation.

### 6. Execution phase

`RefactorExecutor` applies the plan in ordered phases:

- Phase A: package moves and package declarations
- Phase B: Kotlin class and file renames
- Phase C: resource declaration/file renames
- Phase D: controlled reference rewrites
- Phase E: reformat and optional optimize imports
- Phase F: save and refresh

Each phase returns a `PhaseResult`.

On failure:

- execution stops
- errors are collected
- the final report marks partial completion

### 7. Post steps

`GitCommitService` is invoked only when:

- git commit is enabled
- execution finished without errors
- the project is inside a git repository

The initial implementation uses command-line git through IntelliJ process APIs and keeps that integration isolated so it can later be swapped with richer VCS integration.

### 8. Reporting

`ReportRenderer` and `ReportExporter` serialize:

- preview plans
- final execution reports

Supported formats:

- JSON
- Markdown

Reports are written under `.internal-refactor-assistant/`.

## Package Responsibilities

### `action/`

- action system entrypoints only
- no persisted mutable state

### `ui/`

- dialogs
- table models
- form validation
- user-triggered export actions

### `settings/`

- persistent application-level defaults
- recent request history
- rename group defaults
- commit template
- Gradle task defaults
- custom naming rules

### `model/`

- shared immutable DTOs
- request/plan/report contracts
- operation enums and preview rows

### `scan/`

- project traversal
- PSI/VFS file discovery
- scan snapshots

### `planner/`

- deterministic plan generation
- conflict detection
- warning generation
- summary counts

### `rules/`

- naming normalization
- tokenization
- exact token replacements
- regex rule application
- identifier/resource/package validation helpers

### `executor/`

- phase orchestration
- write-action safe modifications
- reformat/save/refresh

### `vcs/`

- git repo detection
- add/commit execution
- result capture

### `report/`

- JSON serialization
- Markdown rendering
- export directory management

### `util/`

- JSON/YAML parsing
- file/path helpers
- validation helpers
- notification helpers

## Design Choices

### Plan-first architecture

The planner is separate from execution so the plugin can support:

- dry-run
- preview export
- safer conflict surfacing
- future approval workflows

### Narrow, deterministic rename engine

V1 only performs renames when they are supported by:

- exact old/new tokens
- explicit synonym mappings
- configured regex rules

This avoids accidental semantic drift.

### PSI-first where practical

V1 uses PSI/refactoring APIs for:

- Kotlin class renames
- Kotlin file renames
- package declaration updates
- XML resource declaration updates

For package/reference rewrite gaps where PSI coverage is less direct, V1 falls back to tightly-scoped literal rewrites of known reference forms only.

### Extension points for V2

Interfaces and placeholders are kept around future work:

- more Android resource kinds
- navigation/menu XML support
- build verification
- richer package move APIs
- explicit variable/function rename strategies
