package yassine.app.smart_note.api

import retrofit2.http.*
import yassine.app.smart_note.models.AskRequest
import yassine.app.smart_note.models.AskResponse

interface SmartNotesApi {

    @POST("ask")
    suspend fun sendMessage(
        @Body request: AskRequest
    ): AskResponse

    @GET("health")
    suspend fun checkHealth(): retrofit2.Response<Map<String, Any>>
}