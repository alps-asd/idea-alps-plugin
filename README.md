# app-state-diagram — PhpStorm Plugin

Renders an [ALPS](https://alps-io.github.io/) profile (`alps.json` / `alps.xml`) as a
live state-diagram preview inside PhpStorm, split next to the text editor —
the same pattern as the Markdown plugin's preview.

Rendering reuses [app-state-diagram](https://github.com/alps-asd/app-state-diagram)'s
own `parser/alps-parser` and `generator/dot-generator`, executed in-process inside a
JCEF browser (no external Node/CLI process, no network access at runtime).

**Scope**: diagram preview only. No document/table view, no tag filters, no links
panel (those live in app-state-diagram's own web editor).

## Requirements

- PhpStorm (or any IntelliJ Platform IDE) **2026.1 or newer** (`since-build=261`),
  with the bundled "Web Browser (JCEF)" plugin enabled. The preview falls back
  to a plain message if `JBCefApp.isSupported()` is false.
  - JCEF became a separate bundled plugin in 2026.2, so `plugin.xml` declares
    `<depends>com.intellij.modules.jcef</depends>`. That id exists as a core
    alias from 2025.3.1 and not before, which is why older IDEs are out of range.
  - The build compiles against 2026.1 on purpose: 2026.2's platform jars are
    Java 25 class files, while the plugin must stay Java 21 bytecode to load
    on the IDE's JBR. JetBrains' guidance is to build with 2026.1 and rely on
    runtime compatibility with 2026.2.
- A JDK to run Gradle itself (Gradle 9.7.1 supports 17+). The Kotlin/Java
  *compile* toolchain is pinned to JDK 21 by the IntelliJ Platform Gradle
  plugin (`--release 21`, matching PhpStorm's bundled JBR) — if no JDK 21 is
  installed, `settings.gradle.kts`'s foojay-resolver auto-downloads one; no
  manual setup needed either way.
- Node.js 20+ to build the bundled preview script (`web/`).

## Repository layout

```
build.gradle.kts              Gradle/IntelliJ Platform plugin build
src/main/kotlin/…             AlpsFileDetector, AlpsFileEditorProvider, AlpsPreviewFileEditor
src/main/resources/META-INF/  plugin.xml
src/main/resources/web/       asd.bundle.js + index.html (generated, see below)
src/test/kotlin/…             Headless BasePlatformTestCase (no JCEF/GUI required)
web/                          esbuild bundle: parser + dot-generator + @viz-js/viz
```

## Building

### 1. Web bundle

`web/src/main.ts` exposes `globalThis.asd.render(text, labelMode)`. It deliberately
imports only `parser/alps-parser.js` and `generator/dot-generator.js` from
`@alps-asd/app-state-diagram`, calling `@viz-js/viz`'s `instance()` directly —
**not** `generator/svg-generator.js`, which imports Node's `child_process` at
module scope and would break the browser bundle.

`@alps-asd/app-state-diagram` is currently pinned to an **unpublished** version
(`2.1.0`; npm's latest is `2.0.0`) via a `file:` dependency on a sibling checkout:

```json
"@alps-asd/app-state-diagram": "file:../../app-state-diagram/packages/app-state-diagram"
```

Clone it next to this repo and build its `dist/` before building the web bundle
(track the canonical upstream `2.x` branch, not a personal fork, so this
doesn't depend on a working branch that could disappear):

```bash
cd .. && git clone --branch 2.x https://github.com/alps-asd/app-state-diagram.git
cd app-state-diagram && pnpm install && pnpm --filter @alps-asd/app-state-diagram build
```

Then:

```bash
cd idea-alps-plugin/web
npm install
npm run check   # tsc --noEmit
npm run build   # -> ../src/main/resources/web/{asd.bundle.js,index.html}
npm run smoke   # evaluates the bundle in Node against fixtures/alps.json
```

Once `@alps-asd/app-state-diagram@2.1.0` is published to npm, switch the
dependency to a version range and drop the sibling-checkout step.

### 2. Plugin

```bash
./gradlew test          # headless BasePlatformTestCase (provider wiring only)
./gradlew buildPlugin    # -> build/distributions/idea-alps-plugin-<version>.zip
./gradlew verifyPlugin   # IntelliJ Plugin Verifier against the compile-target PhpStorm

# Also verify against an installed (possibly newer) IDE without downloading it:
./gradlew verifyPlugin -PlocalIdePath=~/Applications/PhpStorm.app
```

Caveat: the Plugin Verifier resolves classes IDE-wide and does not model
plugin-classloader isolation. It reported "Compatible" on 2026.2 for a build
that was missing `<depends>com.intellij.modules.jcef</depends>` and crashed
there with `NoClassDefFoundError`. Treat a green `verifyPlugin` as API-level
evidence only; missing-dependency bugs are only proven by running the IDE.

Install the built zip via **Settings → Plugins → ⚙ → Install Plugin from Disk…**.

## Known limitations

- Cross-file `href` references (`"href": "other.json#id"`) are not resolved; only
  same-file `#id` references render. app-state-diagram's `FileResolver` uses
  `fs`/`path` and isn't browser-safe — a VFS-backed resolver is future work.
- Live JCEF rendering and click-to-navigate cannot be exercised headlessly
  (`./gradlew runIde` needs a desktop session). To check in a real IDE, install
  the zip, open `web/fixtures/alps.json`, then:
  1. Confirm the split editor shows the diagram on the right.
  2. Change a state's `id` (e.g. `"About"` → `"AboutUs"`, updating its `#About`
     references too) or a transition's `type`/`rt`. Do **not** use `title`: the
     default `labelMode` is `id`, so a title edit leaves the diagram visually
     unchanged and looks like a broken live update. The diagram should redraw
     within ~300 ms.
  3. Click a state node; the caret should jump to that descriptor's `"id"` line.
