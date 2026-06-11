# Requirements Document

## Introduction

This document specifies the requirements for migrating the GeminiLive Android trucking assistant app from the Vertex AI `generateContent` API to the new Gemini Interactions API. The migration will leverage server-side conversation state management, improve tool orchestration, and maintain all existing trucking-specific functionality while simplifying the client architecture.

## Glossary

- **Interactions API**: The new unified Gemini API for interacting with models and agents, featuring server-side state management via `previous_interaction_id`
- **previous_interaction_id**: A server-managed identifier that maintains conversation context across multiple API calls, eliminating the need for client-side history management
- **Interaction**: A single request-response exchange with the Gemini model, containing inputs and outputs
- **Output**: A typed content block in an Interaction response (text, function_call, function_result, etc.)
- **GeminiRestClient**: The existing REST client class that handles Gemini API communication
- **GeminiViewModel**: The Android ViewModel that manages UI state and orchestrates audio processing
- **TruckingTools**: The set of 15 function declarations and handlers for trucking-specific operations
- **ContentPart**: The sealed class representing conversation history entries (Text or Audio)
- **PCM**: PCM Audio File Format - the container format for audio data sent to the API
- **SSE (Server-Sent Events)**: The streaming protocol used by the Interactions API for incremental responses
- **Background Execution**: Interactions API capability for running long-running tasks asynchronously

## Requirements

### Requirement 1: Interactions API Client Migration

**User Story:** As a developer, I want to migrate from the `generateContent` endpoint to the `interactions.create` endpoint, so that the app can leverage the new Interactions API capabilities.

#### Acceptance Criteria

1. WHEN the GeminiRestClient sends a request to the Gemini API, THE Client SHALL use the `interactions.create` endpoint instead of `streamGenerateContent`
2. THE Client SHALL construct request payloads conforming to the Interactions API schema
3. THE Client SHALL parse Interactions API response format, extracting outputs from the `outputs` array
4. WHEN a request is sent, THE Client SHALL include the model identifier `gemini-2.5-pro`
5. THE Client SHALL handle the `interaction.id` field returned in responses for subsequent stateful requests

### Requirement 2: Server-Side Conversation State Management

**User Story:** As a developer, I want to use `previous_interaction_id` for conversation state, so that I can eliminate client-side conversation history management and reduce memory overhead.

#### Acceptance Criteria

1. WHEN the GeminiViewModel processes a user interaction, THE ViewModel SHALL store the returned `interaction.id`
2. WHEN a subsequent interaction is created, THE ViewModel SHALL include the stored interaction ID as `previous_interaction_id`
3. THE ViewModel SHALL remove the `conversationHistory` mutable list and all associated client-side history management logic
4. WHEN a new session starts, THE ViewModel SHALL clear the stored interaction ID to begin a fresh conversation
5. WHEN the session stops, THE ViewModel SHALL discard the stored interaction ID

### Requirement 3: Authentication Migration

**User Story:** As a developer, I want to update authentication to work with the Interactions API, so that API requests are properly authorized.

#### Acceptance Criteria

1. WHEN making Interactions API requests, THE VertexAuth module SHALL provide valid authentication credentials
2. THE Client SHALL support API key authentication as the primary method for Interactions API
3. IF API key authentication is not configured, THE Client SHALL fall back to OAuth2 token authentication using the existing service account flow
4. WHEN OAuth2 authentication is used, THE Client SHALL include the bearer token in the Authorization header
5. WHEN API key authentication is used, THE Client SHALL include the key in the appropriate request header or query parameter

### Requirement 4: Function Calling Preservation

**User Story:** As a truck driver, I want all existing trucking tools to continue working after the migration, so that I can still access load status, HOS clocks, and other critical information.

#### Acceptance Criteria

1. WHEN the Interactions API returns a `function_call` output, THE Client SHALL extract the function name, ID, and arguments from the output
2. THE Client SHALL invoke the corresponding handler in TruckingTools for each function call
3. WHEN sending function results back, THE Client SHALL construct a new interaction with `function_result` output containing the call ID, name, and result
4. THE Client SHALL preserve all 15 existing function declarations: getDriverProfile, getLoadStatus, getHoursOfServiceClocks, getTrafficAndWeather, getDispatchInbox, getCompanyFAQs, getPaycheckInfo, findNearestSwiftTerminal, checkSafetyScore, getFuelNetworkRouting, getContacts, getNextLoadDetails, getMentorFAQs, getOwnerOperatorFAQs
5. WHEN multiple function calls are returned in a single response, THE Client SHALL execute all calls and return all results in a single follow-up interaction

### Requirement 5: Audio Input Support

**User Story:** As a truck driver, I want to continue using voice input for hands-free interaction, so that I can safely operate the app while driving.

#### Acceptance Criteria

1. WHEN audio is recorded, THE Client SHALL use PCM audio data with 16kHz sample rate
2. WHEN sending audio input, THE Client SHALL include the audio data in the Interactions API input format (inline data with MIME type)
3. THE Client SHALL preserve the existing AudioConfig settings for input sample rate (16000), channel configuration, and chunk timing
4. WHEN audio is sent to the API, THE Client SHALL handle the audio/PCM data as needed
5. THE AudioRecorder SHALL continue to accumulate audio data and provide it to the ViewModel for processing

### Requirement 6: Text-to-Speech Output Preservation

**User Story:** As a truck driver, I want the app to continue speaking responses aloud, so that I can receive information without looking at the screen.

#### Acceptance Criteria

1. WHEN the Interactions API returns a text output, THE ViewModel SHALL pass the text to the existing TtsManager
2. THE TtsManager SHALL continue using the Android TextToSpeech API for audio playback
3. WHEN the AI is speaking, THE ViewModel SHALL set the UI state to `GeminiState.SPEAKING`
4. WHEN TTS playback completes, THE ViewModel SHALL transition to the appropriate next state
5. THE Client SHALL NOT use any built-in audio output capabilities of the Interactions API; text-to-speech SHALL remain client-side

### Requirement 7: Streaming Response Handling

**User Story:** As a developer, I want to implement streaming for Interactions API responses, so that the app can provide responsive feedback during long-running model responses.

#### Acceptance Criteria

1. WHEN the Client sends a request with streaming enabled, THE Client SHALL use Server-Sent Events (SSE) to receive incremental responses
2. WHEN a `content.delta` event is received, THE Client SHALL accumulate text chunks for the response
3. WHEN an `interaction.complete` event is received, THE Client SHALL finalize the response and extract the interaction ID
4. WHEN a `function_call` output is received during streaming, THE Client SHALL buffer the call for execution
5. IF streaming is not required, THE Client SHALL support non-streaming requests that return the complete interaction

### Requirement 8: Error Handling Migration

**User Story:** As a developer, I want to update error handling for the new response format, so that users receive appropriate feedback when issues occur.

#### Acceptance Criteria

1. WHEN the Interactions API returns an error status, THE Client SHALL parse the error details from the response
2. WHEN an interaction has status `failed`, THE ViewModel SHALL display an appropriate error message to the user
3. WHEN an interaction has status `requires_action`, THE ViewModel SHALL process the required function calls
4. WHEN a network error occurs, THE Client SHALL return a GeminiResponse.Error with a descriptive message
5. WHEN an authentication error occurs, THE ViewModel SHALL prompt the user to check configuration or retry

### Requirement 9: System Instruction Configuration

**User Story:** As a developer, I want to configure system instructions for each interaction, so that the AI maintains its trucking copilot persona.

#### Acceptance Criteria

1. WHEN creating an interaction, THE Client SHALL include the system instruction defining the Swift Transportation trucking copilot persona
2. THE System instruction SHALL specify the tool selection guidance for all 15 trucking tools
3. THE System instruction SHALL include the requirement to respond in English only
4. WHEN the system instruction is sent, THE Client SHALL format it according to Interactions API requirements
5. THE Client SHALL load the system instruction from the existing system-prompt.md asset file

### Requirement 10: Generation Configuration

**User Story:** As a developer, I want to configure generation parameters for each interaction, so that model responses are consistent with the existing behavior.

#### Acceptance Criteria

1. WHEN creating an interaction, THE Client SHALL include generation configuration with temperature set to 0.7
2. THE Client SHALL set maxOutputTokens to 1024 to match existing behavior
3. WHEN additional generation parameters are needed, THE Client SHALL support the Interactions API configuration schema
4. THE Generation configuration SHALL be included with each interaction request
5. THE Client SHALL NOT include response modalities configuration since output is text-only

### Requirement 11: Tool Declaration Format Migration

**User Story:** As a developer, I want to update tool declarations for the Interactions API format, so that function calling works correctly with the new API.

#### Acceptance Criteria

1. WHEN sending tool declarations, THE Client SHALL format them according to the Interactions API schema
2. THE Client SHALL include all function declarations with name, description, and parameters schema
3. WHEN a function has no parameters, THE Client SHALL include an empty object schema
4. WHEN a function has parameters, THE Client SHALL include the full JSON schema for the parameters
5. THE Tool declarations SHALL be included in each interaction request as interaction-scoped configuration

### Requirement 12: UI State Management Preservation

**User Story:** As a truck driver, I want the UI to continue showing the same states during interaction, so that I understand what the app is doing.

#### Acceptance Criteria

1. WHEN recording audio, THE ViewModel SHALL set `aiState` to `GeminiState.LISTENING`
2. WHEN waiting for API response, THE ViewModel SHALL set `aiState` to `GeminiState.THINKING`
3. WHEN executing function calls, THE ViewModel SHALL set `aiState` to `GeminiState.WORKING`
4. WHEN playing TTS output, THE ViewModel SHALL set `aiState` to `GeminiState.SPEAKING`
5. WHEN idle and ready for input, THE ViewModel SHALL set `aiState` to `GeminiState.IDLE`

### Requirement 13: Session Lifecycle Management

**User Story:** As a truck driver, I want to start and stop sessions cleanly, so that I can control when the app is actively listening.

#### Acceptance Criteria

1. WHEN the user toggles connection on, THE ViewModel SHALL initialize a new session with cleared state
2. WHEN the user toggles connection off, THE ViewModel SHALL cancel any in-progress operations and release resources
3. WHEN a session starts, THE ViewModel SHALL clear any stored interaction ID from previous sessions
4. WHEN a session stops, THE ViewModel SHALL stop audio recording, TTS playback, and sound effects
5. WHEN the ViewModel is cleared (app termination), THE ViewModel SHALL release all resources

### Requirement 14: Sound Effect Feedback Preservation

**User Story:** As a truck driver, I want to hear sound effects during processing, so that I know the app is working even when I'm not looking at the screen.

#### Acceptance Criteria

1. WHEN the app enters the THINKING state, THE SoundManager SHALL play the thinking loop sound
2. WHEN the app enters the WORKING state, THE SoundManager SHALL play the working loop sound
3. WHEN the app exits THINKING or WORKING state, THE SoundManager SHALL stop the loop
4. THE SoundManager SHALL continue using the existing sound resource files
5. WHEN the session stops, THE SoundManager SHALL stop any playing sounds

### Requirement 15: Logging and Debugging

**User Story:** As a developer, I want to maintain logging capabilities, so that I can debug issues with the Interactions API migration.

#### Acceptance Criteria

1. WHEN an interaction is created, THE ViewModel SHALL log the interaction ID
2. WHEN a function call is received, THE ViewModel SHALL log the function name
3. WHEN an error occurs, THE ViewModel SHALL log the error message
4. THE ViewModel SHALL maintain the log display in the UI with the 100-message limit
5. WHEN a new session starts, THE ViewModel SHALL clear the log history
