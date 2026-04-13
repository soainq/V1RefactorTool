# Known Limitations

1. Package rename is scanned and reviewed, but apply is still blocked.
2. Reference updates are exact-token replacements inside scanned files, not full PSI rename for Kotlin/Java.
3. `ReviewRequired` and `DoNotTouch` are exposed as groups, but the UI still uses plain checkboxes rather than a tri-state tree.
4. Suggestion sources are local dictionary plus rules only.
5. String and dimen parsing is regex-based for `values/*.xml`.
