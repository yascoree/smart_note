package yassine.app.smart_note.models

data class User(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String? = null
)