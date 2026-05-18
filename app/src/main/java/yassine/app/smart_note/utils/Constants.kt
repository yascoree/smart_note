package yassine.app.smart_note.utils

object Constants {
    // API
    const val BASE_URL = "http://192.168.11.127:8000"  // Pour l'émulateur
    // Pour un téléphone physique: "http://192.168.1.x:8000"

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
    const val WELCOME_MESSAGE = "Bonjour! Je suis votre assistant IA.\n\nJe peux vous aider avec:\n📝 Générer des notes\n📄 Résumer des textes\n✏️ Améliorer votre écriture\n✅ Créer des listes de tâches\n\nQue puis-je faire pour vous aujourd'hui?"

    // Note Colors
    val NOTE_COLORS = listOf(
        "#FFFFFF", "#FFCDD2", "#BBDEFB", "#C8E6C9", "#FFF9C4", "#E1BEE7"
    )

    val COLOR_NAMES = listOf(
        "Blanc", "Rouge", "Bleu", "Vert", "Jaune", "Violet"
    )

    //Supabase
    const val SUPABASE_URL = "https://poatgtndaekixwfzonwn.supabase.co"  // Remplace par ton URL
    const val SUPABASE_ANON_KEY = "sb_publishable_dYcmK1jtq1uyjUnQPXJnQw_MInGkiq-"  // Remplace par ta clé anon
}