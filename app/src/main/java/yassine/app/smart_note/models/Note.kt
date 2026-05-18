package yassine.app.smart_note.models

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val color: String = "#FFFFFF",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        content = content,
        color = color,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class NoteEntity(
    val id: String,
    val title: String,
    val content: String,
    val color: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)