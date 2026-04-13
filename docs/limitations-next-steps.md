# Known Limitations And Next Steps

## V1 Limitations

- Package moves are implemented as source-root-aware file moves plus controlled package/reference rewrites. This is intentionally narrow and deterministic, but it is not yet a full language-aware package-move implementation across every supported Android/IntelliJ reference kind.
- V1 renames Kotlin classes/files plus layout, drawable, string, and dimen resources only.
- Safe variable/function rename is exposed in the dialog but intentionally deferred until the project supplies explicit deterministic mappings and stronger symbol-level safety checks.
- Font, anim, raw, style/theme, navigation XML, and menu XML support are planned but not executed in this milestone.
- Build verification is surfaced in settings and reports as an extension point, but no Gradle task runner is executed yet.
- Git integration currently uses command-line git through IntelliJ process APIs instead of richer IDE-native VCS abstractions.
- Preview export writes to `.internal-refactor-assistant/` inside the project rather than prompting for arbitrary file destinations.
- The generated tests were scaffolded but not executed in this workspace because Java and Gradle are unavailable here.

## Recommended V2 Work

1. Replace the package move fallback with deeper IntelliJ move-refactoring integration for multi-root Android projects.
2. Add Android resource support for `font`, `anim`, `raw`, and style/theme entries plus preview grouping for those resource kinds.
3. Add navigation/menu/manifest-specific scanners and reference handlers.
4. Introduce explicit, rule-backed safe symbol rename support for functions and variables.
5. Run configurable Gradle tasks after apply and attach build/test outcomes to the final report.
6. Improve preview UX with richer grouping, filtering, conflict resolution, and diff views.
7. Add more platform-heavy tests that execute against multi-module Android sample projects.
