package yassine.app.smart_note.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.realtime.Realtime
import yassine.app.smart_note.utils.Constants

object SupabaseManager {

    private var client: SupaqueClient? = null

    fun getInstance(): SupabaseClient {
        if (client == null) {
            client = createSupabaseClient(
                supabaseUrl = Constants.SUPABASE_URL,
                supabaseKey = Constants.SUPABASE_ANON_KEY
            ) {
                install(Postgrest)
                install(Auth)
                install(Realtime)
                // Le client HTTP par défaut est automatiquement configuré
            }
        }
        return client!!
    }
}