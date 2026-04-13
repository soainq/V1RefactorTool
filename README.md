# Internal Project Refactor Assistant

Internal Android Studio plugin for safely duplicating a template Android project and refactoring package names, Kotlin source names, and selected Android resources through a previewable plan.

The first milestone in this repository is V1:

- package rename/move
- Kotlin class and file rename
- layout, drawable, string, and dimen rename
- controlled reference updates
- preview and dry-run
- reformat and optional optimize imports
- optional git commit

See [docs/architecture.md](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/architecture.md) for the architecture proposal, [docs/mock-preview.md](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/mock-preview.md) for a UI sketch, and [docs/limitations-next-steps.md](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/docs/limitations-next-steps.md) for the V1 limitations and V2 roadmap.

## Target Platform

This project targets Android Studio Panda 2 `2025.3.2.6`, which is based on IntelliJ Platform `253.30387.90`. The build is configured with:

- IntelliJ Platform Gradle Plugin `2.5.0`
- Java toolchain `21`
- `since-build` `253`
- `until-build` `253.*`

This aligns with the official Android Studio plugin-development guidance and Android Studio release listings from JetBrains.

## Project Layout

- `src/main/kotlin/com/internal/refactorassistant/action/`
- `src/main/kotlin/com/internal/refactorassistant/ui/`
- `src/main/kotlin/com/internal/refactorassistant/settings/`
- `src/main/kotlin/com/internal/refactorassistant/model/`
- `src/main/kotlin/com/internal/refactorassistant/scan/`
- `src/main/kotlin/com/internal/refactorassistant/planner/`
- `src/main/kotlin/com/internal/refactorassistant/rules/`
- `src/main/kotlin/com/internal/refactorassistant/executor/`
- `src/main/kotlin/com/internal/refactorassistant/vcs/`
- `src/main/kotlin/com/internal/refactorassistant/report/`
- `src/main/kotlin/com/internal/refactorassistant/util/`
- `src/test/kotlin/com/internal/refactorassistant/test/`
- `src/test/resources/testData/`
- `samples/`
- `docs/`

## Setup

1. Install JDK 21.
2. Use Gradle `8.10.2+`.
3. Open the project in IntelliJ IDEA or Android Studio.
4. Run `gradle runIde` to launch the Android Studio development instance.

## Run And Debug

- `gradle runIde`: launches a sandbox Android Studio instance with the plugin installed.
- `gradle test`: runs unit and platform-scaffolded tests.
- `gradle buildPlugin`: produces the distributable ZIP under `build/distributions/`.
- Attach a debugger to the `runIde` process from IntelliJ IDEA or Android Studio when stepping through refactor execution.

Because this workspace does not currently have Java or Gradle installed, the project could not be compiled or executed during generation.

## Main Workflow

1. Open a template Android project in Android Studio.
2. Run `Tools > Internal Refactor > Duplicate Template Project`.
3. Fill in the old/new feature names, display words, package prefixes, module scope, and options.
4. Generate a `RefactorPlan`.
5. Review the preview table, conflicts, and warnings.
6. Export the preview as JSON or Markdown if needed.
7. Apply the plan or keep it as dry-run only.
8. Review the final report and optional git commit result.

## Verification

Recommended local verification commands once Java and Gradle are installed:

```bash
gradle test
gradle buildPlugin
gradle runIde
```

## Sample Artifacts

- [samples/refactor-request.sample.yaml](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/samples/refactor-request.sample.yaml)
- [samples/refactor-plan.sample.json](/C:/Users/Admin/Desktop/V1/1.%20Figma/Codex/Refactor_plugin/samples/refactor-plan.sample.json)

## Known Limitations

- V1 does not implement automated font, anim, raw, style/theme, menu, navigation, or Gradle build verification execution yet.
- Package reference rewrites use targeted exact-match updates in supported source/XML files after structural moves; this is intentionally narrow and not a crude global replace, but it is still less comprehensive than full language-aware package-move support.
- Safe function and variable renames are exposed in the UI but deferred to a future milestone unless explicit deterministic rules are provided.
- The test suite is scaffolded but could not be executed in this environment because Java and Gradle are unavailable.
