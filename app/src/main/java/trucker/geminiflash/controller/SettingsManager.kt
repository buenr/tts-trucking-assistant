package trucker.geminiflash.controller

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        private const val PREF_ANSWER_MODE = "answer_mode"
    }
}
