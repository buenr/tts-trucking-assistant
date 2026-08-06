# AI Trucking Co-Pilot (TruckerAssistTTS)

Welcome to the **AI Trucking Co-Pilot** repository, an in-cab, voice-activated AI assistant designed specifically for truck drivers. Optimized for the **Samsung Galaxy Tab Active 5** and engineered for low-bandwidth, highly degraded 4G LTE environments, this application provides drivers with a hands-free, safety-focused intelligent companion.

To ensure ultra-low latency and total resilience against spotty or non-existent cellular coverage on highways, the application offloads heavy audio and voice processing (Voice Activity Detection, Speech-to-Text, and Text-to-Speech) to the device’s native, offline Android APIs. The only network payload transmitted is plain text exchanged with Google Cloud Vertex AI (utilizing Gemini 2.5 Flash), reducing bandwidth requirements by over 99% compared to traditional cloud-based voice assistants.

---

## Table of Contents
1. [Core Features](#1-core-features)
2. [Hardware & OS Context](#2-hardware--os-context)
3. [Architecture Overview](#3-architecture-overview)
4. [Component Walkthrough](#4-component-walkthrough)
   - [4.1 Voice Activity Detection (VAD)](#41-voice-activity-detection-vad)
   - [4.2 Offline Speech-to-Text (STT)](#42-offline-speech-to-text-stt)
   - [4.3 Vertex AI Network Gateway](#43-vertex-ai-network-gateway)
   - [4.4 Offline Text-to-Speech (TTS)](#44-offline-text-to-speech-tts)
5. [Getting Started & Configuration](#5-getting-started--configuration)
   - [5.1 Prerequisites](#51-prerequisites)
   - [5.2 Project Setup](#52-project-setup)
   - [5.3 API Credentials Setup](#53-api-credentials-setup)
6. [Testing & Verification](#6-testing--verification)
7. [Production Deployment & MDM Migration](#7-production-deployment--mdm-migration)
8. [UI/UX & Safety Considerations](#8-uiux--safety-considerations)

---

## 1. Core Features

- **Hands-Free / Eyes-Free Co-Pilot:** Operates entirely through voice commands and spoken responses, keeping drivers' hands on the wheel and eyes on the road.
- **Asymmetric Low-Bandwidth Optimization:** Transmits small JSON text payloads (~500 bytes) instead of streaming raw audio over LTE.
- **Answer Mode Toggle:**
  - **SHORT Mode (Default):** Restricts responses to 1-2 sentences maximum, answering only the specific question asked.
  - **LONG Mode:** Provides detailed, contextual responses with additional helpful details (3-5 sentences when appropriate).
- **Intelligent Tool/Function Calling:** Integrates with local trucking APIs to provide real-time updates regarding Driver Dashboards, Truck Equipment Health, Load Information, Route Conditions, Financials/Bonuses, Communications (Dispatch Messages), and Compliance (Hours of Service).
- **Automated Audio Ducking:** Automatically lowers music, radio, or navigation volume during Co-Pilot listening or speaking states.

---

## 2. Hardware & OS Context

- **Target Device:** Samsung Galaxy Tab Active 5.
- **OS:** Android 14 (One UI 6) or higher.
- **Processor:** Exynos 1380 with on-device Neural Processing Unit (NPU) capabilities.
- **Audio Profile:** Native hardware-assisted noise suppression tuned for deep diesel engine hum and highway cabin noise (70–85 dB).
- **Device Management:** MDM-managed (Knox Manage, Intune, Workspace ONE) to enforce the pre-download of high-fidelity Google/Samsung offline language packs.

---

## 3. Architecture Overview

This application follows a highly reactive **Clean Architecture (MVVM)**, utilizing Kotlin Coroutines and Flows to manage asynchronous streams.

```text
[ Microphone ]  
     ↓ (Raw Audio)
[ Native VAD & Noise Suppression ] -- (Silence Truncated) --> [ Offline STT Engine ]
                                                                     ↓ (Text String) 
                                                         [ Co-Pilot Logic Controller ] 
                                                                     ↓ (Text Payload: ~500 bytes) 
[ Text-to-Speech Engine ]  <-- (Text Stream) -------------[ LLM Network Gateway ]  
     ↓ (Audio Stream)                                                ↓↑ (WebSocket/SSE) 
[ Speaker / Headset ]                                       [ Vertex Gemini 2.5 Flash] 
```

### Key Safety and Network Boundaries
1. **Offline Enforcement:** Both STT and TTS are configured to fail closed if on-device packages/engines are unavailable. There is no cloud-based speech processing fallback.
2. **Startup Readiness Check:** Gating ensures the application does not enter "Driving Mode" unless all local offline speech dependencies (STT model, TTS high-fidelity voices) and Vertex AI configuration are fully initialized and verified.
3. **Network Isolation:** Application network calls are strictly reserved for plain text exchange with Google Cloud Vertex AI via the official Gen AI SDK (`VertexAiClient`).

---

## 4. Component Walkthrough

### 4.1 Voice Activity Detection (VAD)
- Combined use of `AudioRecord` along with Android's native hardware `AcousticEchoCanceler` and `NoiseSuppressor`.
- Listens continuously in a lightweight loop to detect speech boundaries, minimizing battery drainage and CPU thermal throttling. Activation is also coupled with hardware button keys (e.g., Active Key, Headset Hook).

### 4.2 Offline Speech-to-Text (STT)
- Implemented using Android's `android.speech.SpeechRecognizer`.
- Offline capability is validated using `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`.
- Rejects cloud fallback paths by utilizing `SpeechRecognizer.createOnDeviceSpeechRecognizer(...)` combined with setting `RecognizerIntent.EXTRA_PREFER_OFFLINE` to `true`.

### 4.3 Vertex AI Network Gateway
- Leverages the official Google Gen AI SDK (`com.google.genai`).
- Interacts with **Gemini 2.5 Flash** on Vertex AI.
- Translates user intent into structured function calls (tools) to query local data endpoints (dashboard, truck info, load parameters, route conditions, financial statements, or messaging interfaces).
- Optimizes LLM-generated output for speech by enforcing strict TTS formatting instructions (spelling out state names, time structures, percentages, currency, measurement units, and applying strategically spaced ellipses `...` for sequences like BOL numbers to allow writing-down pauses).

### 4.4 Offline Text-to-Speech (TTS)
- Uses `android.speech.tts.TextToSpeech` configured with offline-only high-quality English voice packs.
- **Streaming chunks:** Intercepts streamed text responses from Vertex AI at natural punctuation boundaries (`.`, `?`, `!`) and injects them into the TTS queue sequentially. The tablet speaks initial sentences while subsequent tokens are still being processed.

---

## 5. Getting Started & Configuration

### 5.1 Prerequisites
- **Android Studio** Ladybug (or higher).
- **Android SDK 35** (compileSdk & targetSdk is 35, minSdk is 34).
- **Gradle 8.9** (configured via Gradle Wrapper).
- A valid Google Cloud Project with the **Vertex AI API** enabled.

### 5.2 Project Setup
1. Clone this repository.
2. Open the project in Android Studio.
3. Define the local properties in your root `local.properties` file:
   ```properties
   VERTEX_AI_PROJECT_ID="your-gcp-project-id"
   VERTEX_AI_LOCATION="us-central1" # Or another supported Vertex region (e.g., global, us-east1, us-west1)
   VERTEX_AI_MODEL="gemini-2.5-flash"
   ```

### 5.3 API Credentials Setup
For development and local testing, the application looks for a Service Account key in JSON format inside the assets directory:
- Path: `app/src/main/assets/vertex-ai-testing1.json`
- Ensure this file is populated with a valid Google Cloud Service Account JSON file that has `Vertex AI User` permissions.
- *Note: This asset file is excluded in git ignore paths for security reasons.*

---

## 6. Testing & Verification

The repository contains unit tests validating crucial application policies, such as Vertex AI network boundaries and allowed model types.

To run the local unit tests, execute the following Gradle command from the root directory:
```bash
./gradlew test
```

### Detailed Testing Guide
For a full list of available voice commands, tool mappings, parameters, and demo data returned from the mock endpoints (such as HOS clocks, truck fault codes, and dispatch inbox structures), consult **[TESTING_GUIDE.md](TESTING_GUIDE.md)**.

---

## 7. Production Deployment & MDM Migration

Using a bundled Google Cloud Service Account JSON file in your APK assets is acceptable during early testing, but it is **not secure** for production environments.

For enterprise deployment, migration to an MDM-managed configuration is required:
1. Under production builds, the application retrieves the service account credentials dynamically from the secure **Android Restrictions Manager** pocket using Google Play for Work / Managed Configurations.
2. The config key is `vertex_service_account_json`.
3. For step-by-step instructions on setting up configuration policies across your fleet, please refer to the **[Production Migration Guide](ProductionMigration.md)**.

---

## 8. UI/UX & Safety Considerations

- **Visual State Indicator:** Uses a large, high-contrast visual orb that is color-coded for quick glance recognition from the driver's seat:
  - **Green (Pulse):** Listening mode is active.
  - **Yellow (Spin):** Processing / Thinking (fetching database tools or LLM tokens).
  - **Blue (Pulse):** Speaking / TTS playback.
  - **Red:** Offline status, warning, or configuration issue.
- **Error Boundaries:** If a major service error or network dropout occurs, a red warning border surrounds the visual state indicator, and the system falls back to pre-recorded offline audio notifications.
