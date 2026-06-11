# Vertex AI Migration Summary

## Overview
Successfully migrated the TTS Trucking Assistant app from Gemini Interactions API to Google Cloud Vertex AI using the official Gen AI SDK for Java.

## Key Changes

### 1. Dependencies
- **Added**: `com.google.genai:google-genai:0.3.0` (Gen AI SDK for Java)
- **Removed**: Direct OkHttp REST client usage for Gemini Interactions API

### 2. Authentication & Configuration
- **Before**: API key via `secrets.properties` → `BuildConfig.GEMINI_API_KEY`
- **After**: Secrets Gradle Plugin with service account JSON
- **Configuration**: Project ID, location, model, and service account JSON via `local.properties`
- **Plugin**: `com.google.android.libraries.mapsplatform.secrets-gradle-plugin:2.0.1`
- **BuildConfig Fields**: `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_LOCATION`, `VERTEX_AI_MODEL`, `VERTEX_AI_SERVICE_ACCOUNT_JSON`
- **Authentication**: Service account JSON loaded from secrets at runtime

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
| `gradle/libs.versions.toml` | Added `googleGenai` and `secretsGradlePlugin` libraries |
| `app/build.gradle.kts` | Added Gen AI SDK and Secrets Gradle Plugin configuration |
| `network/VertexAuth.kt` | Uses BuildConfig secrets for all config and service account auth |
| `network/VertexAiClient.kt` | **NEW** - Replaces GeminiRestClient |
| `network/GeminiModels.kt` | Removed Interactions API models, kept shared tool types |
| `network/GeminiRestClient.kt` | **DELETED** |
| `controller/CoPilotController.kt` | Updated to use VertexAiClient + conversation history |
| `GeminiViewModel.kt` | Removed GeminiRestClient reference |
| `tools/TruckingTools.kt` | Added `getVertexTools()` for SDK format conversion |
| `startup/StartupReadinessManager.kt` | Added service account existence check |

## Setup Instructions

### Step 1: Configure Secrets (Required)

1. **Copy the template file**:
   ```bash
   cp local.properties.template local.properties
   ```

2. **Get your Project ID** from Google Cloud Console:
   - Navigate to: https://console.cloud.google.com/
   - Copy the Project ID from the project selector dropdown

3. **Download service account JSON**:
   - APIs & Services > Credentials > Service Accounts
   - Create or select existing service account
   - Generate JSON key

4. **Edit `local.properties`** with your values:
   ```properties
   VERTEX_AI_PROJECT_ID=your-actual-project-id
   VERTEX_AI_LOCATION=global
   VERTEX_AI_MODEL=gemini-2.5-flash
   VERTEX_AI_SERVICE_ACCOUNT_JSON={"type":"service_account","project_id":"your-project",...}
   ```

   > **Note**: The service account JSON should be minified to a single line. You can use a JSON minifier tool or manually remove line breaks.

### Step 2: Required Permissions

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
- Service account JSON is embedded in the app via BuildConfig at build time
- Service account JSON should be kept secure and NOT committed to version control
- Add `local.properties` to `.gitignore` to prevent accidental commits
- The service account JSON is parsed at runtime from BuildConfig.VERTEX_AI_SERVICE_ACCOUNT_JSON
