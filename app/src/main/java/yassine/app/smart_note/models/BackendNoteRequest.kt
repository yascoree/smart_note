package yassine.app.smart_note.models

import com.google.gson.annotations.SerializedName

data class BackendNoteRequest(
    @SerializedName("text")
    val text: String
)