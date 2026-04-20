# Implementation Plan: Gemini Interactions API Migration

## Overview

This implementation plan migrates the GeminiLive Android trucking assistant app from the Vertex AI `generateContent` API to the new Gemini Interactions API. The migration leverages server-side conversation state management via `previous_interaction_id`, eliminating client-side history management and reducing memory overhead.

**Key Changes:**
- API endpoint migration from `streamGenerateContent` to `interactions.create`
- Server-side state management replaces client-side `conversationHistory`
- API key authentication as primary method with OAuth2 fallback
- Updated response format parsing for `outputs` array with typed content blocks

**Implementation Language:** Kotlin (Android)

---

## Tasks

- [x] 1. Phase 1: Data Models (GeminiModels.kt)
  - [x] 1.1 Add Interaction request/response models
    - Add `InteractionRequest` data class with model, input, previous_interaction_id, system_instruction, generation_config, and tools fields
    - Add `InputContent` data class supporting text, audio, image, and function_result types
    - Add `InteractionResponse` data class with id, status, outputs, error, and usage fields
    - Add `OutputContent` data class with type, text, id, name, and arguments fields
    - _Requirements: 1.2, 1.3, 1.5_

  - [x] 1.2 Add supporting models for configuration and tools
    - Add `InteractionGenerationConfig` with temperature and max_output_tokens fields
    - Add `InteractionTool` with function_declarations list
    - Add `InteractionFunctionDeclaration` with name, description, and parameters
    - Add `InteractionError` and `UsageInfo` data classes
    - _Requirements: 10.1, 10.2, 11.1, 11.2_

  - [ ]* 1.3 Write property test for request construction schema compliance
    - **Property 1: Request Construction Schema Compliance**
    - **Validates: Requirements 1.2, 4.3, 5.2, 11.1, 11.5**
    - Test that audio input is base64 encoded with correct MIME type
    - Test that previous_interaction_id is included when present
    - Test that function_result inputs contain call_id, name, and result

  - [ ]* 1.4 Write property test for response parsing correctness
    - **Property 2: Response Parsing Correctness**
    - **Validates: Requirements 1.3, 1.5, 4.1, 4.5, 8.1**
    - Test that interaction.id is extracted from any response
    - Test that status field determines response type
    - Test that text outputs are extracted from outputs array
    - Test that function call outputs extract id, name, and arguments

- [x] 2. Phase 2: API Client (GeminiRestClient.kt)
  - [x] 2.1 Implement createInteraction method
    - Update base URL to `https://generativelanguage.googleapis.com/v1beta`
    - Implement request construction with audio input (base64 encoded, PCM MIME type)
    - Include previous_interaction_id when provided
    - Include system instruction loaded from system-prompt.md asset
    - Include generation config (temperature=0.7, maxOutputTokens=1024)
    - Include tool declarations for all 15 trucking tools
    - _Requirements: 1.1, 1.2, 1.4, 5.2, 9.1, 9.2, 9.3, 10.1, 10.2, 11.1, 11.5_

  - [x] 2.2 Implement response parsing for Interactions API
    - Parse `outputs` array to extract typed content blocks
    - Handle `status` field: completed, requires_action, failed
    - Extract text from outputs where type == "text"
    - Extract function_call data (id, name, arguments) from outputs
    - Extract error message from error field
    - Return appropriate GeminiResponse sealed class variants
    - _Requirements: 1.3, 1.5, 4.1, 8.1, 8.2, 8.3_

  - [x] 2.3 Implement sendFunctionResults method
    - Construct request with function_result input type
    - Include call_id, name, and result for each function result
    - Include previous_interaction_id referencing the interaction that requested functions
    - Parse response and return GeminiResponse
    - _Requirements: 4.3, 4.5_

  - [x] 2.4 Implement authentication with API key
    - Add `x-goog-api-key` header to requests
    - Use API key from BuildConfig.GEMINI_API_KEY
    - Keep OAuth2 bearer token as fallback method
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 2.5 Implement streaming support (optional enhancement)
    - Implement SSE (Server-Sent Events) handling for incremental responses
    - Handle `content.delta` events for text accumulation
    - Handle `interaction.complete` event to extract interaction ID
    - Handle `function_call` output during streaming
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 2.6 Write property test for streaming text accumulation
    - **Property 3: Streaming Text Accumulation**
    - **Validates: Requirements 7.2**
    - Test that content.delta events contribute to accumulated text
    - Test that text chunks are concatenated in reception order
    - Test that empty chunks don't affect the result

- [x] 3. Checkpoint - Verify API client functionality
  - Ensure all tests pass, ask the user if questions arise.
  - Verify request construction with audio input
  - Verify response parsing for all status types
  - Verify function result submission

- [x] 4. Phase 3: ViewModel Updates (GeminiViewModel.kt)
  - [x] 4.1 Remove conversation history and add interaction ID field
    - Delete `conversationHistory: MutableList<ContentPart>` field
    - Add `interactionId: String?` field for server-side state
    - Remove all references to conversationHistory throughout the class
    - _Requirements: 2.1, 2.3_

  - [x] 4.2 Update processAudio to use new client methods
    - Call `geminiClient.createInteraction(audioData, apiKey, interactionId)`
    - Store returned `interactionId` from GeminiResponse.Text
    - Store `interactionId` from GeminiResponse.NeedsFunctionCall
    - Pass `interactionId` to `sendFunctionResults` call
    - Handle all GeminiResponse variants (Text, NeedsFunctionCall, Error)
    - _Requirements: 2.1, 2.2_

  - [x] 4.3 Update function calling flow
    - Extract function calls from GeminiResponse.NeedsFunctionCall
    - Execute tool handlers via TruckingTools
    - Construct FunctionResult list with callId, name, and result
    - Call sendFunctionResults with interactionId
    - Handle final text response after function execution
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 4.4 Update session lifecycle methods
    - Clear `interactionId` in `start()` method for new session
    - Clear `interactionId` in `stop()` method when session ends
    - Ensure proper resource cleanup on session stop
    - _Requirements: 2.4, 2.5, 13.1, 13.2, 13.3, 13.4, 13.5_

  - [x] 4.5 Update UI state management
    - Verify aiState transitions: IDLE → LISTENING → THINKING → WORKING → SPEAKING
    - Ensure sound effects play during THINKING and WORKING states
    - Ensure TTS speaks text responses
    - Update error handling to display user-friendly messages
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 14.1, 14.2, 14.3, 14.4, 14.5_

  - [ ]* 4.6 Write property test for log entry limit
    - **Property 4: Log Entry Limit**
    - **Validates: Requirements 15.4**
    - Test that adding entries when log has < 100 items increases size
    - Test that adding entries when log has 100 items maintains size at 100
    - Test that oldest entries are removed when limit is reached

- [x] 5. Phase 4: Authentication (VertexAuth.kt)
  - [x] 5.1 Add API key methods
    - Add `getApiKey()` method returning BuildConfig.GEMINI_API_KEY
    - Add `hasApiKey()` method checking if API key is configured
    - Keep existing `getAccessToken()` for OAuth2 fallback
    - Keep existing `getProjectId()` method
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 6. Checkpoint - Verify ViewModel integration
  - Ensure all tests pass, ask the user if questions arise.
  - Verify interaction ID is stored and passed correctly
  - Verify function calling flow works end-to-end
  - Verify session lifecycle clears state properly

- [x] 7. Phase 5: Testing & Cleanup
  - [x] 7.1 Write unit tests for GeminiRestClient
    - Test createInteraction with audio builds correct request
    - Test parseInteractionResponse extracts text from completed status
    - Test parseInteractionResponse handles requires_action status
    - Test sendFunctionResults includes previous_interaction_id
    - Test error handling for network and API errors
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 4.3, 8.1, 8.4_

  - [x] 7.2 Write unit tests for GeminiViewModel state management
    - Test interactionId is cleared on session start
    - Test interactionId is cleared on session stop
    - Test interactionId is stored after successful response
    - Test UI state transitions during interaction flow
    - _Requirements: 2.1, 2.4, 2.5, 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 7.3 Remove unused legacy code
    - Remove old `sendAudio` and `sendFunctionResults` methods from GeminiRestClient (legacy versions)
    - Remove unused data models: BidiGenerateContentClientMessage, BidiGenerateContentSetup, BidiGenerateContentRealtimeInput, BidiGenerateContentServerMessage
    - Remove ContentPart sealed class (no longer needed)
    - Remove any feature flags used during migration
    - _Requirements: 2.3_

  - [x] 7.4 Verify all 15 trucking tools work correctly
    - Test getDriverProfile function
    - Test getLoadStatus function
    - Test getHoursOfServiceClocks function
    - Test getTrafficAndWeather function
    - Test getDispatchInbox function
    - Test getCompanyFAQs function
    - Test getPaycheckInfo function
    - Test findNearestSwiftTerminal function
    - Test checkSafetyScore function
    - Test getFuelNetworkRouting function
    - Test getContacts function
    - Test getNextLoadDetails function
    - Test getMentorFAQs function
    - Test getOwnerOperatorFAQs function
    - _Requirements: 4.4_

- [x] 8. Final checkpoint - Complete migration verification
  - Ensure all tests pass, ask the user if questions arise.
  - Verify audio input/output works correctly
  - Verify all trucking tools function properly
  - Verify session lifecycle works as expected
  - Verify error handling provides user-friendly feedback

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The design document contains detailed implementation code snippets for reference
- TruckingTools.kt, AudioConfig.kt, AudioRecorder.kt, TtsManager.kt, and SoundManager.kt remain unchanged
