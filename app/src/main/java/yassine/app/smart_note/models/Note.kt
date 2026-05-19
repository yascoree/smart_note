package yassine.app.smart_note.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String = "",
    @SerialName("user_id")
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    @SerialName("is_favorite")
    val isFavorite: Boolean = false,
    @SerialName("note_type")
    val noteType: String = "Personal",
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        content = content,
        isFavorite = isFavorite,
        noteType = noteType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class NoteEntity(
    val id: String,
    val title: String,
    val content: String,
    val isFavorite: Boolean,
    val noteType: String = "Personal",
    val createdAt: Long,
    val updatedAt: Long
)
