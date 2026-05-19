package yassine.app.smart_note.utils

import android.os.Build

object Constants {
    // API
    // Default LAN URL (change to your machine IP when testing on a real device)
    private const val LAN_BASE_URL = "http://192.168.11.127:8000/"
    // Emulator loopback address for Android emulator
    private const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"

    /**
     * Returns the appropriate base URL depending on whether the app runs on an emulator.
     * - Emulator: `10.0.2.2`
     * - Real device: change `LAN_BASE_URL` to your machine IP
     */
    fun getBaseUrl(): String {
        return if (isRunningOnEmulator()) EMULATOR_BASE_URL else LAN_BASE_URL
    }

    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("vbox")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic"))
    }

    const val CONNECTION_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // Database
    const val DATABASE_NAME = "smart_note_db"
    const val NOTES_TABLE = "notes"

    // SharedPreferences
    const val PREF_NAME = "SmartNotePrefs"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_ID = "user_id"

    // AI Assistant
    const val WELCOME_MESSAGE = "🤖 **Assistant IA - Smart Note**\n\nJe peux vous aider avec:\n\n📝 **Générer une note**\n   \"Génère une note sur le développement mobile\"\n\n📄 **Résumer un texte**\n   \"Résume ce texte: [votre texte]\"\n\n✏️ **Améliorer l'écriture**\n   \"Améliore ce texte: [votre texte]\"\n\n✅ **Créer une to-do list**\n   \"Crée une liste pour mon projet\"\n\nComment puis-je vous aider aujourd'hui?"

    // Note Colors
    val NOTE_COLORS = listOf(
        "#FFFFFF", "#FFCDD2", "#BBDEFB", "#C8E6C9", "#FFF9C4", "#E1BEE7"
    )

    val COLOR_NAMES = listOf(
        "Blanc", "Rouge", "Bleu", "Vert", "Jaune", "Violet"
    )

}