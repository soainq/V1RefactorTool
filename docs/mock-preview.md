# Preview Mockup

```text
Internal Project Refactor Assistant
==================================

Request Summary
- Feature: main -> home
- Display: Main -> Home
- Package: com.abc.def.main -> com.abc.def.home
- Modules: app
- Mode: preview / dry-run

Planned Changes
+-----------+---------------+--------------------------------------+--------------------------------------+
| Category  | Kind          | Before                               | After                                |
+-----------+---------------+--------------------------------------+--------------------------------------+
| Package   | package       | com.abc.def.main                     | com.abc.def.home                     |
| Source    | kotlin class  | MainActivity                         | HomeActivity                         |
| Source    | kotlin file   | MainActivity.kt                      | HomeActivity.kt                      |
| Resource  | layout file   | activity_main                        | activity_home                        |
| Resource  | drawable file | ic_main_setting                      | ic_home_setting                      |
| Resource  | string        | main_screen_title                    | home_screen_title                    |
| Resource  | dimen         | main_content_padding                 | home_content_padding                 |
+-----------+---------------+--------------------------------------+--------------------------------------+

Warnings / Conflicts
- Warning: variable/function rename is not enabled in V1 execution
- Warning: build verification requested but deferred to V2
- Conflict: target package already exists in module app

Actions
[Export JSON] [Export Markdown] [Apply] [Cancel]
```
