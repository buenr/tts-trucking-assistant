# Answer Mode Feature

## Overview
Added a user-configurable answer mode that allows drivers to choose between short, concise responses and longer, more detailed responses from the AI copilot.

## Changes Made

### 1. UI Components (MainActivity.kt)
- **Answer Mode Button**: Added a settings button in the top-right corner displaying the current mode ("Short" or "Long")
- **Answer Mode Dialog**: Created a modal dialog that appears when the button is tapped, allowing users to switch between modes
- **Visual Feedback**: The dialog shows both options with clear descriptions and highlights the currently selected mode

### 2. System Prompt Modifications (VertexAiClient.kt)
The `buildSystemInstruction()` method now accepts an `AnswerMode` parameter and generates different prompts:

#### SHORT Mode (Default)
- Enforces 1-2 sentence maximum responses
- Focuses on answering only the specific question asked
- Avoids listing multiple items or reciting raw tool data
- Optimized for hands-free, eyes-free driving scenarios

#### LONG Mode
- Allows 3-5 sentences when appropriate
- Includes relevant context and additional helpful information
- Organizes multiple pieces of information clearly
- Still maintains conciseness where appropriate

### 3. State Management
- **CoPilotController**: Added `answerMode` state flow and `setAnswerMode()` method
- **GeminiViewModel**: Exposed `setAnswerMode()` to the UI layer
- **CopilotUiState**: Added `answerMode` field to track current mode in UI state
- **VertexAiClient**: Updated `sendMessageStream()` and `sendFunctionResults()` to accept and use the answer mode parameter

## User Experience

### Accessing the Feature
1. Tap the settings button in the top-right corner (shows current mode: "Short" or "Long")
2. A dialog appears with two options:
   - **Short Answers**: "Brief, 1-2 sentence responses. Best for quick questions while driving."
   - **Detailed Answers**: "Comprehensive responses with additional context and information."
3. Tap the desired mode to select it
4. The dialog closes and the new mode takes effect immediately

### Visual Design
- The answer mode button uses the app's green accent color (#00E676)
- The dialog has a dark theme matching the app's aesthetic
- Selected option is highlighted with a green border and checkmark icon
- Clear, driver-friendly descriptions explain each mode

## Technical Details

### Prompt Engineering
The system instruction is dynamically built based on the selected mode:
- **SHORT mode**: Adds strict brevity constraints with examples of good vs bad responses
- **LONG mode**: Relaxes constraints while maintaining focus on relevance and clarity

### Integration Points
- Answer mode is passed through the entire request chain:
  1. UI → ViewModel → Controller → VertexAiClient
  2. Applied when building the `GenerateContentConfig` for each API call
  3. Affects both streaming responses and function result processing

### Default Behavior
- The app defaults to SHORT mode for safety (less distraction while driving)
- Mode preference is stored in memory for the session
- Future enhancement: Could persist preference to SharedPreferences

## Benefits
1. **Safety**: Drivers can choose shorter responses to minimize distraction
2. **Flexibility**: Users who want more detail (e.g., during breaks) can switch to LONG mode
3. **User Control**: Empowers drivers to customize their experience
4. **Context-Aware**: The AI adjusts its verbosity based on user preference while maintaining quality

## Future Enhancements
- Persist mode preference across app restarts
- Add a "Medium" mode option
- Context-aware auto-switching (e.g., SHORT while moving, LONG when parked)
- Voice command to switch modes ("Use detailed answers")
