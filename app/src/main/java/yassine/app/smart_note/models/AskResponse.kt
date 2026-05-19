package yassine.app.smart_note.models

import com.google.gson.annotations.SerializedName

data class AskResponse (
    @SerializedName("answer")
    val answer: String? = null,
    @SerializedName("question")
    val question: String? = null,
    @SerializedName("context_used")
    val context_used: List<String>? = null
)
