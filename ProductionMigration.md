# Production Migration Guide: Vertex AI Credentials (MDM)

This document outlines the steps to move from a bundled Service Account key to a secure, MDM-managed configuration for production rollout.

## Why this is necessary
Bundling a Service Account JSON file in the APK assets is a security risk. If the APK is extracted, the key is exposed. Using **Managed Configurations** allows the key to be delivered securely to the device at runtime by your MDM (Mobile Device Management) provider.

---

## Phase 1: Code Changes

### 1. Create the Restrictions Definition
Create a new resource file at `app/src/main/res/xml/app_restrictions.xml`. This tells the Android system (and the MDM) which configuration keys our app accepts.

```xml
<?xml version="1.0" encoding="utf-8"?>
<restrictions xmlns:android="http://schemas.android.com/apk/res/android">
    <restriction
        android:key="vertex_service_account_json"
        android:title="Vertex Service Account JSON"
        android:restrictionType="string"
        android:description="The full JSON content of the Google Cloud Service Account key." />
</restrictions>
```

### 2. Update the Android Manifest
Register the restrictions file in `app/src/main/AndroidManifest.xml` inside the `<application>` tag:

```xml
<application ...>
    <meta-data
        android:name="android.content.APP_RESTRICTIONS"
        android:resource="@xml/app_restrictions" />
    ...
</application>
```

### 3. Update `VertexCredentialsManager.kt`
Modify the `loadServiceAccountJson` method to prioritize the MDM "pocket" over the local assets folder.

```kotlin
private fun loadServiceAccountJson(context: Context): String {
    // 1. Check for Managed Configurations (MDM)
    val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? android.content.RestrictionsManager
    val appRestrictions = restrictionsManager?.applicationRestrictions
    
    if (appRestrictions?.containsKey("vertex_service_account_json") == true) {
        val mdmJson = appRestrictions.getString("vertex_service_account_json")
        if (!mdmJson.isNullOrBlank()) {
            return mdmJson // Use the secure key from MDM
        }
    }

    // 2. Fallback to assets (Development/Local testing only)
    return context.assets.open(ASSET_FILENAME).use { it.bufferedReader().readText() }
}
```

---

## Phase 2: MDM Configuration (IT Admin)

Once the app is uploaded to your MDM (e.g., Microsoft Intune, VMware Workspace ONE, SOTI), follow these steps:

1.  Find the **App Configuration** or **Managed Configuration** section for this app.
2.  You will see a setting labeled **Vertex Service Account JSON**.
3.  Paste the **entire text** from your production Google Cloud Service Account `.json` file into this box.
4.  Save and deploy the policy to the tablets.

---

## Phase 3: Verification
1.  Deploy the app via MDM to a test device.
2.  Check the app logs. It should successfully initialize Vertex AI using the MDM key.
3.  If the MDM key is missing, the app will gracefully fall back to the bundled asset file (if it exists).
