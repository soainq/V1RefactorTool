# Spec

## Goal

Support repeated refactor/reskin passes on the same Android project with:

- safe review before apply
- local history and used-name tracking
- fast selection by type
- automatic suggestion reload after selection changes

## Mandatory rules

- Never rename the root package.
- Never rename Android standard `res` folders.
- Only rename valid files, classes, resource files, and resource items.
- Never auto-apply after scan or suggest.
- Always preview before apply.
- Never use crude global replace across the whole repo.
- Update references after apply.
- Reformat after apply.

## Type groups

- Activity
- Fragment
- ViewModel
- Adapter
- KotlinClassOrFile
- FeaturePackage
- LayoutFile
- DrawableFile
- StringKey
- DimenKey
- OtherResource
- ReviewRequired
- DoNotTouch

## Workflow

1. Load history
2. Scan project
3. Classify by safety level
4. Group by type
5. User selects types or all items
6. Suggestion engine reloads active items
7. Filter used names
8. User reviews and edits names
9. Preview
10. Apply selected items only
11. Update references
12. Reformat
13. Save session log
14. Update history and used-name registry

## Storage

```text
.project-refactor/
  history.json
  used-names.json
  sessions/<session-id>.json
```

## Current milestone

- batch type selection
- select all types
- select all items
- debounce reload
- preview and apply
- session log with selected types
- used-name filtering

Package rename stays blocked for apply in this milestone.
