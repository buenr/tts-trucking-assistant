# Vertex AI Migration Summary

## Overview
Successfully migrated the TTS Trucking Assistant app from Gemini Interactions API to Google Cloud Vertex AI using the official Gen AI SDK for Java.

## Key Changes

### 1. Dependencies
- **Added**: `com.google.genai:google-genai:0.3.0` (Gen AI SDK for Java)
- **Removed**: Direct OkHttp REST client usage for Gemini Interactions API

### 2. Authentication
- **Before**: API key via `secrets.properties` → `BuildConfig.GEMINI_API_KEY`
- **After**: Application Default Credentials (ADC) with automatic fallback to service account JSON
- **Method 1 (ADC)**: Set `GOOGLE_APPLICATION_CREDENTIALS` environment variable to service account JSON path
- **Method 2 (Assets)**: Place `vertex-ai-testing1.json` in `app/src/main/assets/`
- **Location**: `global` (automatic routing to available regions)
- **Model**: `gemini-2.5-flash`

### 3. API Client Architecture
- **Before**: `GeminiRestClient` using OkHttp + Interactions API (`/interactions` endpoint)
- **After**: `VertexAiClient` using Gen AI SDK with `generateContent` API
- **Key Difference**: Interactions API is NOT supported in Vertex AI - using standard `generateContent` instead

### 4. Conversation State Management
- **Before**: `interactionId` string for multi-turn conversations (Interactions API pattern)
- **After**: `List<Content>` conversation history maintained in controller
- **Benefit**: More explicit control over conversation context

### 5. Files Modified

| File | Changes |
|------|---------|
| `gradle/libs.versions.toml` | Added `googleGenai = "0.3.0"` and library reference |
| `app/build.gradle.kts` | Added Gen AI SDK dependency, removed API key handling |
| `network/VertexAuth.kt` | Complete rewrite for service account auth |
| `network/VertexAiClient.kt` | **NEW** - Replaces GeminiRestClient |
| `network/GeminiModels.kt` | Removed Interactions API models, kept shared tool types |
| `network/GeminiRestClient.kt` | **DELETED** |
| `controller/CoPilotController.kt` | Updated to use VertexAiClient + conversation history |
| `GeminiViewModel.kt` | Removed GeminiRestClient reference |
| `tools/TruckingTools.kt` | Added `getVertexTools()` for SDK format conversion |
| `startup/StartupReadinessManager.kt` | Added service account existence check |

## Setup Instructions

### Option 1: Application Default Credentials (ADC) - Recommended

1. **Download service account JSON** from Google Cloud Console:
   - Navigate to: APIs & Services > Credentials > Service Accounts
   - Create or select existing service account
   - Generate JSON key

2. **Set environment variable**:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account.json"
   ```

### Option 2: Service Account in Assets (Fallback)

1. **Download service account JSON** from Google Cloud Console
2. **Rename to `vertex-ai-testing1.json`**
3. **Place file in assets**:
   ```
   app/src/main/assets/vertex-ai-testing1.json
   ```
4. **Add to `.gitignore`**:
   ```
   app/src/main/assets/vertex-ai-testing1.json
   ```

### Required Permissions

- **Enable Vertex AI API** in your Google Cloud project
- **Ensure service account has permissions**:
  - `aiplatform.user` or `aiplatform.admin`

## API Differences

### Interactions API (Old)
```kotlin
// Stateful with interaction IDs
POST /v1beta/interactions
{
  "model": "models/gemini-...",
  "input": [...],
  "previous_interaction_id": "..."
}
```

### Vertex AI generateContent (New)
```kotlin
// Stateless with conversation history
client.models.generateContent(
  model = "gemini-3-flash-preview",
  contents = listOf(
    Content.fromText("Hello"),
    Content.fromText("Hi there!"),
    Content.fromText("What's my load status?")
  ),
  config = GenerateContentConfig.builder()
    .tools(tools)
    .build()
)
```

## Testing Checklist

- [ ] Service account JSON loads correctly from assets
- [ ] Startup readiness check passes for Vertex AI
- [ ] Text generation works end-to-end
- [ ] Streaming responses work for low-latency TTS
- [ ] Function calling (tools) works for all trucking features
- [ ] Multi-turn conversation maintains context
- [ ] Error handling works for network failures
- [ ] Offline voice models still work (STT/TTS)

## Benefits of Migration

1. **Enterprise-grade**: Vertex AI provides enterprise SLA and support
2. **Unified SDK**: Single SDK for multiple AI tasks
3. **Better function calling**: Native function calling support
4. **Improved security**: OAuth2 instead of API keys
5. **Cloud integration**: Better integration with GCP services

## Notes

- The app now requires Google Cloud project with Vertex AI enabled
- **ADC (Application Default Credentials)** is the recommended approach for production
- ADC is automatically detected - no code changes needed when using environment variables
- Service account JSON should be kept secure and NOT committed to version control
- The app will automatically fall back to assets if ADC is not configured
