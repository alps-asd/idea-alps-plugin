# Changelog

## 0.1.1

- Rename the plugin display name to `app-state-diagram`, matching the
  upstream project name used elsewhere (npm package, GitHub org, CLI binary).
  No change to the plugin id or vendor.

## 0.1.0

First release.

- Split-editor preview for ALPS profiles (`alps.json` / `alps.xml`), rendered in
  JCEF from app-state-diagram's `parser/alps-parser` and
  `generator/dot-generator` bundled with `@viz-js/viz`.
- Live re-render ~300 ms after each edit; parse errors are shown in the preview
  while the last good diagram stays visible.
- Click a state node in the diagram to move the caret to that descriptor's `id`
  in the source.
- Compatible with PhpStorm / IntelliJ Platform 2026.1 or newer (`since-build=261`);
  declares the `com.intellij.modules.jcef` dependency required from 2026.2.

Known limitations: cross-file `href` references (`other.json#id`) are not
resolved; only the diagram view is provided (no document/table view).
