# Internal Project Refactor Assistant

Minimal Android Studio plugin for previewing rename candidates in a template project.

## Current Scope

The plugin only does four things:

1. adds a menu at `Tools > Internal Refactor > Preview Template Rename`
2. shows a small input dialog
3. scans the open project for rename candidates
4. shows a preview list

It does **not** modify files.

## Input Fields

- old package
- new package
- old token
- new token
- old display token
- new display token

## Preview Output

- package declarations or manifest packages that match the old package
- Kotlin file names that match the tokens
- Kotlin class/interface/object names that match the tokens
- layout XML file names that match the tokens

## Design Goals

- smallest possible plugin surface
- easy to debug
- no rename execution
- no report/export layer
- no settings persistence
- no Kotlin plugin dependency in `plugin.xml` or Gradle dependencies

## Build

From the project root:

```powershell
.\gradlew.bat buildPlugin
```

To run inside a sandbox IDE:

```powershell
.\gradlew.bat runIde
```

## Install

1. Build the plugin ZIP.
2. Open Android Studio.
3. Go to `Settings > Plugins`.
4. Choose `Install Plugin from Disk...`.
5. Select the ZIP from `build/distributions`.

## Notes

- This version is preview-only.
- No file changes are applied.
- Kotlin scanning is plain text based on `.kt` files, not Kotlin PSI.
