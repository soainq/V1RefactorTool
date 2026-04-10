# Internal Project Refactor Assistant

Android Studio plugin for repeated refactor/reskin passes on the same Android project.

Plugin Android Studio hỗ trợ chạy nhiều lần quy trình refactor/reskin trên cùng một dự án Android.

## Overview | Tổng quan

### English

This plugin helps teams rename Android project artifacts in controlled batches. It scans supported files and symbols, groups them by type and old name, generates stable rename suggestions, lets the user review and edit them, previews the final plan, then applies only the selected changes.

Main goals:

- safe review before apply
- stable group-based suggestions
- reuse tracking through local history
- fast batch selection by type
- preview-first workflow
- exact-token reference updates in scanned files

### Tiếng Việt

Plugin này giúp team đổi tên các thành phần trong dự án Android theo lô, có kiểm soát. Plugin sẽ quét các file và symbol được hỗ trợ, gom nhóm theo loại và theo old name, sinh suggestion ổn định, cho phép review và sửa tay, preview kế hoạch cuối cùng, rồi chỉ apply các thay đổi đã chọn.

Mục tiêu chính:

- luôn review trước khi apply
- suggestion ổn định theo group
- theo dõi tên đã dùng qua local history
- chọn nhanh theo loại
- workflow ưu tiên preview
- cập nhật exact-token reference trong các file đã quét

## Menu | Cách mở

`Tools > Internal Refactor > Start Reskin Refactor Workflow`

## What The Plugin Handles | Plugin xử lý những gì

### Supported scan types | Các loại hiện quét được

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

### Supported apply types | Các loại hiện apply được

- Kotlin file rename
- Kotlin class rename
- Activity / Fragment / ViewModel / Adapter rename
- layout file rename
- drawable file rename
- string key rename
- dimen key rename
- exact-token reference updates inside scanned files
- IDE reformat after apply

### Not applied yet | Chưa apply ở milestone hiện tại

- package child rename is scanned and reviewed, but apply is blocked
- root package rename is never allowed
- Android standard `res` folders are never renamed

## Workflow | Quy trình hoạt động

### English

1. Load `.project-refactor/history.json`
2. Load `.project-refactor/used-names.json`
3. Scan selected modules
4. Classify items into `SAFE_AUTO`, `REVIEW_REQUIRED`, `DO_NOT_TOUCH`
5. Group items by type and by normalized old name
6. Generate canonical suggestions for each old-name group
7. Filter or mark used-before names
8. Let the user review, edit, and override names
9. Build preview rows and validation warnings
10. Apply selected items only
11. Update references inside scanned files
12. Reformat changed files
13. Save preview/apply session logs
14. Update history and used-name registry

### Tiếng Việt

1. Tải `.project-refactor/history.json`
2. Tải `.project-refactor/used-names.json`
3. Quét các module đã chọn
4. Phân loại item thành `SAFE_AUTO`, `REVIEW_REQUIRED`, `DO_NOT_TOUCH`
5. Gom nhóm theo loại và theo normalized old name
6. Sinh canonical suggestion cho từng nhóm old name
7. Lọc hoặc đánh dấu các tên đã từng dùng
8. Cho phép người dùng review, sửa tay và override
9. Tạo preview rows và warning validation
10. Chỉ apply các item đã chọn
11. Cập nhật reference trong các file đã quét
12. Reformat các file đã thay đổi
13. Lưu log preview/apply
14. Cập nhật history và used-name registry

## Safety Model | Mô hình an toàn

### Safety levels | Mức an toàn

- `SAFE_AUTO`: can be suggested and selected by default
- `REVIEW_REQUIRED`: allowed, but needs user review
- `DO_NOT_TOUCH`: shown for awareness, not meant for apply

### Review status | Trạng thái trong màn hình review

- `READY`: the item has a valid selected new name and can proceed
- `SKIPPED`: the item is not selected or is `DO_NOT_TOUCH`
- `BLOCKED`: all candidate strategies failed, or validation still fails

### Mandatory rules | Rule bắt buộc

- never rename the root package
- never rename Android standard `res` folders
- never auto-apply after scan
- always preview before apply
- never use crude repo-wide replace
- only update exact-token references inside scanned files in this milestone

## Suggestion Engine | Suggestion engine

### English

The rename engine is dictionary-driven and group-based. Items with the same `type + normalized oldName` are grouped together. The plugin generates candidates once per group, chooses one canonical new name, and reuses it across the group unless a specific item cannot use it because of a real conflict.

Candidate priority:

1. `EXACT_PHRASE`
2. `TOKEN_SYNONYM`
3. `TOKEN_ABBREVIATION`
4. `WHOLE_PHRASE_REPLACEMENT`
5. `RULE_FALLBACK`

The plugin tries to keep items in `READY` by automatically skipping bad candidates and trying the next one when needed:

- same as old name
- invalid for the item type
- already exists in the scanned project
- target path already exists
- used before, if a better unused candidate exists

### Tiếng Việt

Rename engine hiện chạy theo dictionary và theo group. Các item có cùng `type + normalized oldName` sẽ được gom lại. Plugin chỉ generate candidate một lần cho mỗi group, chọn một canonical new name, rồi dùng lại cho cả group, trừ khi một item cụ thể không thể dùng canonical đó vì conflict thật sự.

Thứ tự ưu tiên candidate:

1. `EXACT_PHRASE`
2. `TOKEN_SYNONYM`
3. `TOKEN_ABBREVIATION`
4. `WHOLE_PHRASE_REPLACEMENT`
5. `RULE_FALLBACK`

Plugin cố gắng đưa item sang `READY` bằng cách tự bỏ qua candidate xấu và thử candidate tiếp theo khi cần:

- trùng old name
- sai naming convention theo loại
- trùng tên đã tồn tại trong scan result
- target path đã tồn tại
- là tên đã dùng trước đó trong khi vẫn còn candidate tốt hơn chưa dùng

## Canonical Group Behavior | Hành vi canonical theo group

### English

Inside a single refactor session:

- same old name should produce the same canonical new name
- the canonical name is selected once at group level
- all items in the group receive that canonical name by default
- only item-specific conflicts may force a secondary candidate
- a group edit should propagate to all children
- a child override should affect only that child

### Tiếng Việt

Trong cùng một refactor session:

- cùng old name phải ra cùng canonical new name
- canonical name được chọn một lần ở cấp group
- tất cả item trong group mặc định dùng canonical đó
- chỉ khi item cụ thể bị conflict thì mới dùng candidate phụ
- sửa group cha phải propagate xuống tất cả item con
- override ở item con chỉ tác động lên item đó

## Default Data Files | Bộ dữ liệu mặc định

Plugin ships with default JSON resources so it can work immediately after installation.

Plugin có sẵn bộ JSON mặc định để dùng ngay sau khi cài.

Location:

```text
src/main/resources/suggestions/
  dictionary.json
  abbreviations.json
  do-not-replace.json
```

### `dictionary.json`

Used for:

- `exactPhrases`
- `tokenSynonyms`
- `wholePhraseFallbacks`

Ví dụ:

- `main -> home`
- `setting -> preference`
- `tip_and_trick_detail -> guide_info`

### `abbreviations.json`

Used for meaningful abbreviations such as:

- `icon -> ic`
- `image -> img`
- `button -> btn`
- `language -> lang`, `lg`
- `setting -> set`, `pref`
- `description -> desc`
- `information -> info`
- `background -> bg`
- `navigation -> nav`

### `do-not-replace.json`

Used for tokens that should stay stable unless the user manually overrides them.

Các token mặc định không nên bị đổi bừa:

- `base`
- `core`
- `app`
- `data`
- `di`
- `ui`
- `util`

## How To Extend Data | Cách thêm dữ liệu mới

### Add exact phrase | Thêm exact phrase

```json
"tip_and_trick": ["guide"]
```

### Add token synonym | Thêm token synonym

```json
"setting": ["preference", "option", "config"]
```

### Add whole phrase replacement | Thêm whole phrase replacement

```json
"tip_and_trick_detail": ["guide_info", "hint_overview"]
```

### Add abbreviation | Thêm abbreviation

```json
"language": ["lang", "lg"]
```

### Block replacement for a token | Chặn thay một token

```json
"tokens": ["base", "core", "ui"]
```

Priority reminder:

- synonym wins over abbreviation
- abbreviation wins over lower-ranked phrase replacement only if ranked above it
- fallback is last resort only

## Review UI | Màn hình review

The review table currently includes item-level and group-level metadata.

Bảng review hiện hiển thị cả metadata theo item và theo group.

Main controls:

- `Select All Types`
- `Select All Items`
- `Show previously used names`
- per-type checkboxes with counts
- per-item selection

Important columns:

- `Type`
- `Old name`
- `Group Key`
- `Canonical New Name`
- `Group Size`
- `Override Status`
- `Suggested names`
- `Suggestion source`
- `Candidate rank`
- `Selected new name`
- `Status`
- `Warning`

Override behavior:

- `CANONICAL`: this item uses the group default
- `OVERRIDDEN`: this item uses a child-specific value

## Preview UI | Màn hình preview

The preview dialog summarizes:

- selected item count
- skipped item count
- blocked item count
- selected count by type group
- preview log location

It also shows per-row details:

- before
- after
- group key
- canonical new name
- override status
- suggestion source
- candidate rank
- status
- warning
- path

## Project Files And Session Logs | File dữ liệu của project và session

```text
.project-refactor/
  history.json
  used-names.json
  sessions/
    <session-id>.json
```

### `history.json`

Stores applied rename history.

Lưu lịch sử rename đã apply.

### `used-names.json`

Stores names already used in previous refactor versions.

Lưu các tên đã từng dùng ở các lần refactor trước.

### `sessions/<session-id>.json`

Stores preview/apply session logs.

Lưu log của từng phiên preview/apply.

## Source Layout | Cấu trúc source

### Core flow

- `src/main/kotlin/com/internal/refactorassistant/action/StartReskinRefactorAction.kt`
  entrypoint orchestration
- `src/main/kotlin/com/internal/refactorassistant/scan/ProjectScanner.kt`
  scans project files and resources
- `src/main/kotlin/com/internal/refactorassistant/classify/ItemClassifier.kt`
  maps scanned items into safety levels

### Suggestion and selection

- `src/main/kotlin/com/internal/refactorassistant/suggest/SuggestionService.kt`
  candidate generation, ranking, canonical group suggestion
- `src/main/kotlin/com/internal/refactorassistant/suggest/SuggestionDataLoader.kt`
  loads default JSON resources
- `src/main/kotlin/com/internal/refactorassistant/suggest/BuiltinDictionary.kt`
  exposes loaded default data
- `src/main/kotlin/com/internal/refactorassistant/selection/TypeGrouping.kt`
  maps items into review groups
- `src/main/kotlin/com/internal/refactorassistant/selection/ReviewSelectionCoordinator.kt`
  owns selected groups, overrides, and rebuild logic

### Validation, preview, apply

- `src/main/kotlin/com/internal/refactorassistant/preview/ReviewValidationService.kt`
  validates names, conflicts, duplicates, used-before warnings
- `src/main/kotlin/com/internal/refactorassistant/preview/PreviewBuilder.kt`
  builds preview rows and summary
- `src/main/kotlin/com/internal/refactorassistant/executor/FilesystemApplyEngine.kt`
  renames files and updates references
- `src/main/kotlin/com/internal/refactorassistant/executor/IdePostProcessor.kt`
  reformats changed files

### Storage and reporting

- `src/main/kotlin/com/internal/refactorassistant/history/HistoryRepository.kt`
  reads and writes JSON data
- `src/main/kotlin/com/internal/refactorassistant/report/SessionRecordFactory.kt`
  builds preview/apply session records

### UI

- `src/main/kotlin/com/internal/refactorassistant/ui/ScanSettingsDialog.kt`
- `src/main/kotlin/com/internal/refactorassistant/ui/SuggestionReviewDialog.kt`
- `src/main/kotlin/com/internal/refactorassistant/ui/PreviewPlanDialog.kt`
- `src/main/kotlin/com/internal/refactorassistant/ui/ApplyResultDialog.kt`
- `src/main/kotlin/com/internal/refactorassistant/ui/SuggestionTableModel.kt`

## Build And Run | Build và chạy

## Requirements | Yêu cầu

- JDK 21
- Android Studio compatible with `pluginSinceBuild=253`
- Gradle wrapper included in this repo

The build can resolve Android Studio from `androidStudioVersion=2025.3.2.6`, or use a local installation through `androidStudioLocalPath`.

Quá trình build có thể tự resolve Android Studio từ `androidStudioVersion=2025.3.2.6`, hoặc dùng bản cài sẵn trên máy qua `androidStudioLocalPath`.

### macOS

Use:

```bash
./gradlew buildPlugin
./gradlew runIde
./gradlew test
```

Optional local Android Studio path:

```properties
androidStudioLocalPath=/Applications/Android Studio.app/Contents
```

### Windows

Use:

```powershell
.\gradlew.bat buildPlugin
.\gradlew.bat runIde
.\gradlew.bat test
```

Optional local Android Studio path:

```properties
androidStudioLocalPath=C:/Program Files/Android/Android Studio
```

## Known Limitations | Giới hạn hiện tại

- package child rename is still blocked during apply
- reference updates are exact-token replacements, not full PSI rename
- review UI is still table-based, not a tri-state tree
- string and dimen parsing is still regex-based for `values/*.xml`
- full integration tests with sample Android projects are not added yet

## Docs | Tài liệu chi tiết

- `docs/spec.md`
- `docs/architecture.md`
- `docs/known-limitations.md`
- `docs/next-steps.md`
