package yassine.app.smart_note.models

import com.google.gson.annotations.SerializedName

data class AskResponse (
    @SerializedName("response", alternate = ["answer", "message", "text"])
    val response: String? = null,
    val context_used: List<String>? = null
)
