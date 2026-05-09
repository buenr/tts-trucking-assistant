package trucker.geminiflash.controller

import android.content.Context
import android.content.SharedPreferences
import trucker.geminiflash.audio.NoiseProfile

/**
 * Persists user preferences for the Co-Pilot.
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("copilot_settings", Context.MODE_PRIVATE)

    /**
     * Retrieves the saved answer mode, defaulting to LONG.
     */
    fun getAnswerMode(): AnswerMode {
        val modeName = prefs.getString(PREF_ANSWER_MODE, AnswerMode.LONG.name) ?: AnswerMode.LONG.name
        return try {
            AnswerMode.valueOf(modeName)
        } catch (e: Exception) {
            AnswerMode.LONG
        }
    }

    /**
     * Saves the selected answer mode.
     */
    fun setAnswerMode(mode: AnswerMode) {
        prefs.edit().putString(PREF_ANSWER_MODE, mode.name).apply()
    }

    /**
     * Retrieves the saved noise profile, defaulting to LOUD_TRUCK.
     */
    fun getNoiseProfile(): NoiseProfile {
        val profileName = prefs.getString(PREF_NOISE_PROFILE, NoiseProfile.LOUD_TRUCK.name) ?: NoiseProfile.LOUD_TRUCK.name
        return try {
            NoiseProfile.valueOf(profileName)
        } catch (e: Exception) {
            NoiseProfile.LOUD_TRUCK
        }
    }

    /**
     * Saves the selected noise profile.
     */
    fun setNoiseProfile(profile: NoiseProfile) {
        prefs.edit().putString(PREF_NOISE_PROFILE, profile.name).apply()
    }

    companion object {
        private const val PREF_ANSWER_MODE = "answer_mode"
        private const val PREF_NOISE_PROFILE = "noise_profile"
    }
}

