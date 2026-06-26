# Rephrasor

Rephrasor is a small desktop application that uses OpenAI-compatible models to improve the language of scientific texts. It is intended for computer science and formal methods writing, and focuses on rewriting, checking, shortening, and translating LaTeX or Markdown documents without changing their technical content.

The application is a two-pane editor:

- Left pane: your editable source text.
- Right pane: the AI-generated version of the selected text, together with change highlighting.

You keep full control over which changes you accept.

## Features

- Rephrase and polish scientific text.
- Spell-check and grammar-check without changing meaning.
- Shorten text while keeping the main points.
- Translate between German and English.
- Preserve LaTeX and Markdown markup.
- Optional sentence-based diffing for clearer highlighting.
- Simple context annotations inside the text (see below).
- Support for multiple providers and models via OpenAI-compatible APIs.

## Requirements

- Java 21 or later (JRE or JDK).
- Network access to an OpenAI-compatible API endpoint (for example OpenAI, or a self-hosted compatible service).

## Building from Source

Clone the repository and build it with Gradle:

```bash
git clone <this-repo-url>
cd rephrasor
./gradlew shadowJar
```

This produces a fat JAR under:

```text
build/libs/rephrasor-<version>-all.jar
```

You can also run the application directly via Gradle:

```bash
./gradlew run
```

## Running Rephrasor

Run from the fat JAR (recommended for end users):

```bash
java -jar build/libs/rephrasor-*-all.jar
```

Or, to open a specific file on startup:

```bash
java -jar build/libs/rephrasor-*-all.jar path/to/file.tex
```

Optional command line arguments:

- `FAKE:path/to/file` as second argument: use the content of the given file as a fixed "fake" answer instead of calling the model (useful for testing the diff and highlighting).
- `+<line>` and `+<endLine>`: jump to a line and optionally select a range on startup, for example:

  ```bash
  java -jar build/libs/rephrasor-*-all.jar paper.tex +120 +140
  ```

## First Start and Provider Configuration

On first start, Rephrasor does not know any model providers. You must configure at least one provider before you can use the AI functions.

1. Start Rephrasor.
2. Open the menu `Settings` → `Config providers ...`.
3. In the dialog, each row describes one provider with the columns:
   - **Provider**: a short name (for example `openai`).
   - **Base URL**: the API base URL (for example `https://api.openai.com`).
   - **Chosen Model**: the model id (for example `gpt-4o-mini`). You can also leave this empty and pick a model later.
   - **Key**: your API key or token.
4. Add one or more rows, then press **Save**.

After saving, the configured providers are available under the `Settings` menu as radio buttons ("Provider: <name>"). You can switch between them at any time.

### Selecting a Model

To change the model for the current provider:

1. Choose the provider under `Settings`.
2. Use `Settings` → `Select model ...`.
3. Rephrasor queries `<baseUrl>/v1/models` and shows the available model ids (if your provider supports this endpoint).
4. Select a model and confirm.

The chosen model is stored together with the provider configuration.

## Using Rephrasor

### Basic Workflow

1. Open an existing text file (`File` → `Open`) or paste your text into the left pane.
2. Optionally select a region in the left pane. If nothing is selected, Rephrasor will use either the active highlight region or the entire document.
3. Click one of the toolbar buttons at the top to choose what you want to do:
   - `1-Rephrase`: rephrase and improve language and style.
   - `2-Check`: spell-check and grammar-check only.
   - `3-Translate`: translate into English.
   - `4-Rewrite`: rewrite for precision and conciseness.
   - `5-Shorten`: shorten the text while preserving main points.
   - Additional German variants exist (for example `7-Umformulieren`).
4. Wait while the AI is contacted. A small dialog shows that a request is in progress.
5. The AI answer appears in the right pane, and changed regions are highlighted in the left pane.

You can save the edited left text at any time (`File` → `Save` or the **Save** button / `Ctrl+S`).

### Accepting Changes

There are two ways to accept changes from the AI output:

- **Accept all changes for the selected region**: use the **Copy** button in the toolbar. The left text in the processed region is replaced by the entire right-hand output.
- **Accept individual changes**: move the mouse over highlighted regions in the left pane. Right-click on a highlighted region to apply just that change.

Use the **Clear** button to remove the right-hand output and all highlights.

### Editing the Prompt

The default behaviour for each toolbar button is controlled by a corresponding prompt (stored in `preambles.properties`). These prompts encode rules such as:

- Do not add new content.
- Preserve LaTeX and Markdown.
- Prefer British English.
- Keep output in ASCII.

If you enable the **Edit Prompt** toggle in the toolbar, clicking a command button will open a dialog where you can see and edit the system prompt before sending the request. This is useful if you want to temporarily adjust how strict or verbose the model should be.

## Context Pragmas

You can instruct Rephrasor to add additional context for the model without changing your main input text. This is done with special tags embedded in the left text. Unless you enable `Ignore Pragmas` in the `Settings` menu, Rephrasor recognises the following:

```text
<rephrasor:context-file>FILENAME</>
```

Interpret `FILENAME` as a file name and append its file content to the context sent to the model.

```text
<rephrasor:copy>
  ...
</rephrasor:copy>
```

Copy the enclosed text into the context. The text also remains as part of the normal input.

```text
<rephrasor:add> ... </>
```

Add the single-line text `...` to the context.

The accumulated context is sent as an additional system message alongside the main system preamble. This can be used, for example, to include related sections, definitions, or style guides without cluttering the main passage you are editing.

If you enable `Ignore Pragmas` in the `Settings` menu, these tags are ignored and no extra context is added.

## Other Settings

- **Align Sentences**: When enabled, Rephrasor tries to align sentences between input and output before computing diffs. This can make highlighting more intuitive when larger edits occur.
- **Font**: Use the `Font` menu to choose from a few predefined font sizes shared by both panes.

## Notes on Privacy

All text in the selected region, plus any additional context from pragmas, is sent to the configured provider. Make sure you are allowed to upload the content you are working on to that provider, and that you understand its data handling policies.

Running a self-hosted OpenAI-compatible service is one way to keep data on infrastructure you control.

## License

This project is licensed under the terms of the license file `LICENSE` in this repository.
