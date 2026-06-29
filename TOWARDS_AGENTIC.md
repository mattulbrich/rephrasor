# Towards Agentic Rephrasor: Chat Window Implementation Plan

## Goal

Add an AI discussion (chat) window that allows users to:
1. Select a section of text in the main editor.
2. Open a chat dialog with predefined roles and optional context files.
3. Chat with the AI to refine instructions.
4. Trigger "Propose Rephrasing" to generate output in the right pane with existing diff/highlight support.

Replacement granularity (word/phrase/paragraph) is deferred to a later phase.

---

## Phase 1: Chat Window Foundation

### 1.1 New Engine Method for Chat

**File:** `src/main/kotlin/de/matul/rephrasor/Engine.kt`

Add a method that accepts an explicit list of chat messages:

```kotlin
data class ChatMessage(val role: String, val content: String)

fun callChat(messages: List<ChatMessage>): String
```

**Implementation details:**
- Reuse `makeOpenAIClient()` and model selection logic.
- Convert `ChatMessage` to `com.cjcrafter.openai.chat.ChatMessage`.
- Support fake answer mode (reuse `fakeAnswer`).
- Log messages as in existing `callAI`.

---

### 1.2 Chat Roles Configuration

**File:** `src/main/resources/de/matul/rephrasor/chat_roles.properties`

Create a new properties file separate from `preambles.properties`:

```properties
Scientific Editor=You are a scientific editor specializing in formal methods...
Translator=You are a translator between German and English...
Reviewer=You are a critical reviewer who identifies weaknesses...
```

**Engine changes:**
- Add `loadChatRoles(): Map<String, String>` method.
- Expose `knownChatRoles: List<String>` sorted list.

---

### 1.3 Chat Dialog UI

**File:** `src/main/kotlin/de/matul/rephrasor/ChatDialog.kt` (new)

**UI Components:**
- **Role selector**: JComboBox populated from `engine.knownChatRoles`
- **Context files panel**:
  - JList showing selected file paths
  - "Add File..." button (JFileChooser)
  - "Remove" button
- **Transcript area**:
  - JTextArea or custom component showing conversation history
  - Different styling for user vs assistant messages
- **Input area**: JTextArea for typing messages
- **Buttons**:
  - "Send": sends user message to AI
  - "Propose Rephrasing": triggers rephrase using current selection
  - "Close": closes dialog

**State management:**
- `selectedText: String` (captured on dialog open)
- `contextFiles: MutableList<String>` 
- `chatHistory: MutableList<ChatMessage>`
- `selectedRole: String`

**Persistence:**
- Save last selected role in Preferences
- Save last context file list in Preferences

---

### 1.4 Integration into MainWindow

**File:** `src/main/kotlin/de/matul/rephrasor/MainWindow.kt`

**Changes:**
1. Add "Chat..." menu item in Edit menu or new "Chat" menu
2. Add toolbar button for quick access
3. Action handler:
   - Capture current selection (or highlight region fallback)
   - Instantiate and show `ChatDialog`
4. Provide callback from `ChatDialog` to `MainWindow` for rephrase result

---

### 1.5 Message Structure for Rephrasing

When "Propose Rephrasing" is clicked, build messages as:

```
[System] <role prompt from chat_roles.properties>
[System] Context files:
  FILE: <path1>
  <content1>
  
  FILE: <path2>
  <content2>
[User] <chat turn 1>
[Assistant] <response 1>
[User] <chat turn 2>
[Assistant] <response 2>
...
[User] Now rephrase the selected section according to our discussion. Constraints:
- Preserve LaTeX/Markdown markup
- Do not add new content
- Prefer British English

Selected text:
"""
<selectedText>
"""
```

This ensures the model uses the full conversation context.

---

## Phase 2: Refinements (Future)

### 2.1 Replacement Granularity
- Extend `Hilighting` to support phrase/paragraph-level acceptance
- Add context menu options on highlighted regions
- Implement paragraph segmentation in `Diffing.kt`

### 2.2 Session Persistence
- Save chat sessions to disk
- Allow reloading previous sessions

### 2.3 Multi-file Context Suggestions
- Automatically suggest relevant files based on content similarity

---

## File Summary

| File | Action | Purpose |
|------|--------|---------|
| `Engine.kt` | Modify | Add `callChat()` method and `loadChatRoles()` |
| `chat_roles.properties` | Create | Define chat role prompts |
| `ChatDialog.kt` | Create | Chat window UI and logic |
| `MainWindow.kt` | Modify | Add chat menu/toolbar action |

---

## Testing Strategy

1. **Unit test** `Engine.callChat()` with fake answer mode
2. **Manual test** chat dialog with various roles
3. **Integration test** full flow: select → chat → rephrase → diff
4. **Verify** context files are correctly appended
5. **Verify** persistence of role and file selections

---

## Notes

- Keep existing `callAI()` unchanged for backward compatibility
- Chat history remains in memory only (no disk persistence in Phase 1)
- Use existing diff/highlight infrastructure without modification
- Maintain EDT compliance: all network calls off Event Dispatch Thread
