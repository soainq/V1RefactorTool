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

Used only for Android default folders, source sets, and generated structural names that should not be treated as business rename targets.

Chỉ dùng cho Android default folders, source sets, và các tên cấu trúc sinh sẵn của Android Studio. Không dùng file này để chặn business token như `language`, `setting`, `reward`, `bottom_sheet`, `activity`, `fragment`.

Các token mặc định:

- `manifest`
- `res`
- `drawable`
- `layout`
- `values`
- `xml`
- `font`
- `mipmap`
- `menu`
- `anim`
- `animator`
- `color`
- `navigation`
- `raw`
- `main`
- `debug`
- `release`
- `test`
- `androidtest`

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
"tokens": ["manifest", "layout", "debug"]
```

Priority reminder:

- synonym wins over abbreviation
- abbreviation wins over lower-ranked phrase replacement only if ranked above it
- fallback is last resort only

## Gemini Semantic Provider | Bộ semantic provider dùng Gemini

This plugin version also supports an optional semantic rename provider backed by the Google Gemini Developer API free tier.

Version này hỗ trợ thêm một semantic rename provider tùy chọn dùng Google Gemini Developer API free tier.

Important:

- Gemini only helps generate better rename candidates.
- Gemini never applies refactor directly.
- Gemini never bypasses:
  - normalizer
  - candidate quality validator
  - used-name filter
  - conflict checker
  - preview

### Provider modes

- `Rule-based only`
- `Gemini assist`
- `Gemini preferred`

Recommended starting mode:

- `Gemini assist`

### How to add an API key

When Gemini mode requires an API key and no key is available, the plugin shows an API key prompt right after `Scan Settings` and before the scan actually starts.

Khi mode Gemini cần API key mà chưa có key hiệu lực, plugin sẽ hiện popup nhập key ngay sau `Scan Settings` và trước khi scan thực sự bắt đầu.

Prompt fields:

- `API Key`
- `Show / Hide`
- `Save API key`
- `Continue`
- `Cancel`

Behavior:

- if `Save API key` is checked:
  - the key is stored in plugin settings and reused later
- if `Save API key` is not checked:
  - the key is used only for the current IDE session
  - it is not persisted after restart

You can also configure the same values in:

`Tools > Android Studio V1_Refactor Plugin`

### How to verify AI is active in `runIde`

1. Run:

```bash
./gradlew runIde
```

2. In the sandbox IDE, open:
   `Tools > Internal Refactor > Start Reskin Refactor Workflow`
3. In `Scan Settings`, check the new `AI provider` section.
4. Change `Provider mode` to:
   - `Gemini assist`
   - or `Gemini preferred`
5. If no API key is configured yet, the plugin will show the API key popup before scan starts.
6. If `Provider mode` is `Rule-based only`, no API key popup will appear.

If you do not see the AI section in `Scan Settings`, you are still running an older plugin build or an older sandbox plugin cache.

### Free tier behavior

- Free tier is intended for testing and light usage.
- Quota and rate limits are lower than paid tiers.
- If Gemini returns malformed JSON, rate-limit errors, quota failures, or network failures:
  - the plugin does not crash
  - preview still works
  - the plugin falls back to the rule-based provider

### Caching

Gemini responses can be cached locally in:

```text
.internal-refactor-assistant/gemini-cache.json
```

Cache key:

- `type + oldName + context hash`

This helps reduce repeated free-tier calls for the same item/context.

### Key management

- API keys are never hardcoded in source code.
- API key fields are hidden by default.
- The settings screen includes `Clear saved API key`.
- Logging must never print the full key; only masked forms should be shown when needed.

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

The main preview table shows only:

- `Type`
- `Before`
- `After`
- `Status`
- `Reason`
- `Path`

Debug-only data stays hidden by default and only appears in detail/debug panels when needed.

Bảng preview chính chỉ hiển thị:

- `Type`
- `Before`
- `After`
- `Status`
- `Reason`
- `Path`

Các field debug như raw candidate, source, rank, canonical name, group key, normalization details sẽ bị ẩn mặc định.

The preview dialog also summarizes:

- selected item count
- skipped item count
- blocked item count
- selected count by type group
- preview log location

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
  candidate generation, normalization, ranking, and canonical group suggestion
- `src/main/kotlin/com/internal/refactorassistant/suggest/SuggestionDataLoader.kt`
  loads and validates default JSON resources with readable fallback warnings
- `src/main/kotlin/com/internal/refactorassistant/suggest/BuiltinDictionary.kt`
  exposes loaded default data and load warnings
- `src/main/kotlin/com/internal/refactorassistant/suggest/CandidateNormalizer.kt`
  removes duplicate/semantic-duplicate tokens before preview and apply
- `src/main/kotlin/com/internal/refactorassistant/suggest/CandidateQualityValidator.kt`
  rejects forbidden outputs such as `oldName + Core/Screen/Info/...`
- `src/main/kotlin/com/internal/refactorassistant/ai/provider/RuleBasedRenameProvider.kt`
  wraps the existing rule-based suggestion engine
- `src/main/kotlin/com/internal/refactorassistant/ai/provider/GeminiSemanticRenameProvider.kt`
  calls Gemini and parses JSON-only semantic rename responses
- `src/main/kotlin/com/internal/refactorassistant/ai/context/CodeContextCollector.kt`
  collects snippet, symbols, related names, reverse abbreviations, and locale aliases before AI requests
- `src/main/kotlin/com/internal/refactorassistant/ai/provider/HybridSuggestionCoordinator.kt`
  runs Gemini first when AI mode is enabled, then merges rule-based candidates under the same validator/normalizer pipeline
- `src/main/kotlin/com/internal/refactorassistant/ai/prompt/GeminiPromptFactory.kt`
  builds compact structured prompts for Gemini
- `src/main/kotlin/com/internal/refactorassistant/ai/cache/SemanticSuggestionCache.kt`
  caches semantic responses by item context
- `src/main/resources/suggestions/locale-aliases.json`
  stores locale, country, and language aliases such as `fr <-> french/france` and `vi <-> vietnamese/vietnam`
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
  applies rename safely, skips binary-as-text reads, emits apply progress, and isolates per-item failures
- `src/main/kotlin/com/internal/refactorassistant/executor/IdePostProcessor.kt`
  synchronizes VFS/documents and reformats changed files to avoid file cache conflicts
- `src/main/kotlin/com/internal/refactorassistant/ui/ApplyResultDialog.kt`
  shows the final result table with `SUCCESS / FAILED / SKIPPED / BLOCKED`

## Implementation Notes | Ghi chú triển khai

- Grouping:
  items are grouped by `type + normalized oldName`. One canonical new name is selected per group and reused across identical items unless a real item-specific conflict exists.
- Normalization:
  every candidate is normalized before ranking and apply. The normalizer removes exact duplicates, collapses semantic duplicates, keeps valid system tokens, and prevents outputs like `screen_screen`, `info_info`, `MainHomeActivity`, or `ic_setting_preference`.
- AI-first pipeline:
  when AI mode is enabled and a valid API key exists, the plugin collects code context first, calls Gemini first, then merges rule-based candidates, reverse abbreviations, and locale aliases before ranking and canonical selection.
- Apply safety:
  text files are processed as UTF-8 text only for known text extensions. Binary resources such as `.png`, `.webp`, `.jpg`, `.ttf`, `.otf`, `.pdf`, `.mp3`, and `.wav` are never read as text and are only renamed by path/file rename.
- IDE cache safety:
  for in-project text files, the plugin prefers IntelliJ document/VFS updates and then synchronizes documents/VFS after apply so open editors do not drift from on-disk state.
- Apply progress and result:
  during apply, the progress indicator shows processed count, current item, success, failed, and skipped counters. After apply, the plugin keeps the final result dialog open and shows the updated per-item table instead of closing immediately.

### Storage and reporting

- `src/main/kotlin/com/internal/refactorassistant/history/HistoryRepository.kt`
  reads and writes JSON data
- `src/main/kotlin/com/internal/refactorassistant/report/SessionRecordFactory.kt`
  builds preview/apply session records
- `src/main/kotlin/com/internal/refactorassistant/settings/GeminiSettingsService.kt`
  stores Gemini provider configuration in IDE settings
- `src/main/kotlin/com/internal/refactorassistant/settings/GeminiSettingsConfigurable.kt`
  renders the settings UI for API key and Gemini mode

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
