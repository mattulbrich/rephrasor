# AGENT.md

## Purpose

This repository contains **Rephrasor**, a Kotlin/JVM Swing desktop application that helps improve, rewrite, spell-check, translate, and otherwise refine scientific texts using OpenAI-compatible chat models. It is especially tailored to formal methods / computer science writing.

The app is a two-pane editor:

- Left: editable source text, with optional inline "pragmas" that influence context.
- Right: AI-produced output for a selected region, with visual diff/highlighting.

## Tech Stack

- Language: Kotlin (JVM), Java interop
- UI: Swing
- Build: Gradle Kotlin DSL (`build.gradle.kts`)
- JVM: Toolchain 21
- AI client: `com.cjcrafter:openai`
- HTTP: OkHttp
- JSON: kotlinx-serialization
- Persistence/config: Java `Preferences`
- Packaging: Shadow plugin (fat JAR)

## Key Concepts

### Providers and Models

Configurable providers store:

- `provider`: symbolic provider name (for example `openai`, `local-llm`).
- `baseUrl`: OpenAI-compatible base URL (for example `https://api.openai.com`).
- `modelName`: chosen chat model id; optional, can be set via "Select model ...".
- `key`: API key / bearer token.

They are persisted in the `Preferences` node for `Engine` under `provider-1`, `baseUrl-1`, `modelName-1`, `key-1`, and so on. `Engine.knownProviders` is recomputed from these entries, and `Engine.saveProviders` rewrites them.

Important invariants:

- `Engine.currentProvider` must always refer to a key present in `knownProviders`.
- `Engine.currentModel` reads and writes the `modelName` for the current provider; setting it rewrites the full provider list via `saveProviders`.
- `Engine.makeOpenAIClient()` must respect the configured key and base URL and set a reasonable read timeout (currently 120 seconds).

`Engine.listModels()` performs a raw HTTP GET against `<baseUrl>/v1/models` and parses the JSON to return the list of `id` values.

### Preambles / Commands

`src/main/resources/de/matul/rephrasor/preambles.properties` defines a mapping:

- Key: label shown on toolbar (for example `1-Rephrase`, `2-Check`, `3-Translate`, `4-Rewrite`, `5-Shorten`, plus some German variants).
- Value: a long system prompt instructing the model how to behave for that command.

`Engine.loadPreambles()` loads this into a `Map<String, String>`; `Engine.knownPreambles` is the sorted list of keys.

`MainWindow` uses `engine.knownPreambles` to create toolbar buttons. When the user presses a button:

1. It determines the input region:
   - If a selection exists in the left text, it uses that.
   - Otherwise, if diff highlighting (`Hilighting`) is active, it uses the indicated range.
   - Otherwise, it falls back to the entire document.
2. It optionally lets the user edit the preamble (`Edit Prompt` toggle).
3. It builds a chat request via `Engine.callAI()` with:
   - System messages: preamble, then the derived `context` (see below).
   - User message: the selected input text.
4. It shows a blocking dialog while a background thread calls the model.
5. On success, it updates the right-hand text and recomputes highlighting.

There is a testing hook: if the program is started with a second argument of the form `FAKE:<path>`, `Engine.fakeAnswer` is set to the contents of that file and returned instead of calling the API.

### Context Pragmas

Context is built from inline pragmas in the left text, unless "Ignore Pragmas" is enabled:

- `<rephrasor:context-file>FILENAME</>`

  Reads the given file (path interpreted relative to the current process) and appends its contents to the context.

- `<rephrasor:copy> ... </rephrasor:copy>`

  Copies the enclosed text verbatim into the context, but it also remains in the main input.

- `<rephrasor:add>LINE</>`

  Adds the given single line of text to the context.

These are parsed with a regex in `MainWindow.makeContext()`. The context is appended as a separate system message.

### Diffing and Highlighting

The diff flow is:

1. Tokenisation: `Diffing.tokenize(text)` splits text into whitespace-delimited tokens with position metadata.
2. Optional segmentation into sentences: `Diffing.toSentences(tokens)` groups tokens by sentence-ending punctuation.
3. For each aligned sentence pair, `Diffing.minEditDistanceWithActions()` computes a minimal edit script (`INSERT`, `DELETE`, `COPY`, `REPLACE`) at token level.
4. `Diffing.consolidateActions()` merges these into coarser-grained `TokenAction` values, each describing a continuous copy region or a replacement region (with a `TokenRange`).

`Hilighting` then:

- Converts `TokenAction` values into `Markup` objects that map to concrete character index ranges in the left text.
- Adds Swing highlights:
  - `OUTSIDE`: non-selected regions.
  - `CHANGE`: regions that differ between input and output.
  - `BEGIN`: optional "begin indicator".
- Updates markups when the underlying document changes.
- Can highlight corresponding ranges on the right side using `highlightRight()`.

User interactions:

- Right-click in the left pane on a highlighted region applies that replacement into the left text.
- Control + right-click in left and right panes is used to define or refine highlight regions.

The "Copy" button can replace the entire selected region with the right-hand output via `Hilighting.replaceAll()`.

### UI / Entry Points

- `MainWindow.kt`

  Main application window and entry point:

  - Class `MainWindow : JFrame` sets up menus, toolbars, key bindings, text areas, and bound actions.
  - Handles file operations (`open`, `reload`, `save`, `saveAs`).
  - Manages `ignorePragmas`, `alignForSentences`, `currentProvider`, and provider or model selection UI.
  - Controls the creation of `Hilighting` and diff behaviour.

- `main(args: Array<String>)` (in `MainWindow.kt`)

  Program entry:

  - Prints `Rephrasor (Version: <timestamp-from-version.txt>)`.
  - Instantiates `MainWindow`, optionally opens a file, and optionally:
    - Sets up a fake answer file (`FAKE:<path>`), or
    - Jumps and selects specific lines (`+<line>`, `+<endLine>`).

- `LineNumberComponent.kt`

  Swing component that renders line numbers in the left-text scroll panel.

- `ProviderConfigDialog.kt`

  Modal dialog for adding or removing providers and editing provider properties in a table, persisting via `Engine.saveProviders()`.

- `Diffing.kt`, `Hilighting.kt`

  Encapsulate diff computation and Swing highlighting.

### Build and Run

- Main build file: `build.gradle.kts`.
  - Plugins: `kotlin("jvm")`, `java`, `application`, `com.github.johnrengelman.shadow`.
  - `application.mainClass` is `de.matul.rephrasor.MainWindowKt`.
  - Task `generateVersionResource` writes `build/generated/resources/version.txt`, used at runtime via `readVersionResource()` and copied into the main resources during `processResources`.

Typical operations for an agent (not executed automatically):

- Build: `./gradlew build`.
- Run from Gradle: `./gradlew run --args="path/to/file.txt"`.
- Run from fat JAR (example): `java -jar build/libs/rephrasor-1.1-SNAPSHOT-all.jar path/to/file.txt`.

## Guidelines for Future Changes

When modifying or extending this project:

1. Preserve prompt semantics.

   Changes to `preambles.properties` affect model behaviour. Keep:

   - Domain focus (formal methods and scientific text).
   - No new content beyond user input.
   - Preservation of LaTeX or Markdown markup.
   - ASCII-only constraint for English prompts (as currently expressed).

2. Keep provider configuration stable.

   - Do not rename preference keys or their numbering scheme without a migration plan.
   - Maintain invariants around `Engine.knownProviders`, `currentProvider`, and `currentModel`.

3. Respect diff and highlighting invariants.

   - `TokenRange` assumes positions are valid character indices into the source or output text.
   - Any change that alters how tokens or sentences are computed should be tested with typical "rephrase" outputs to ensure markings remain correct.

4. Avoid blocking the Event Dispatch Thread.

   - All network calls should remain off the Swing Event Dispatch Thread.
   - UI updates must continue to be marshalled back onto the EDT with `SwingUtilities.invokeLater`.

5. Keep context pragmas backwards compatible.

   - If you extend the pragma syntax, do not break existing `<rephrasor:context-file>`, `<rephrasor:copy>`, or `<rephrasor:add>` tags.

This document is intended to help future agents and contributors quickly understand the structure and constraints of Rephrasor before performing code changes.
