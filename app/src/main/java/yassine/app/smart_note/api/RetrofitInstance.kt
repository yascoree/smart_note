package yassine.app.smart_note.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import yassine.app.smart_note.utils.Constants
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private val loggingInterceptor = HttpLoggingInterceptor().apply { // Logs FastAPI requests
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(Constants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()

    fun createApi(context: Context): SmartNotesApi {
        val sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
        val configuredUrl = sharedPreferences.getString(Constants.KEY_BACKEND_BASE_URL, null)
        val baseUrl = if (configuredUrl.isNullOrBlank()) {
            Constants.getBaseUrl()
        } else {
            Constants.normalizeBaseUrl(configuredUrl)
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SmartNotesApi::class.java)
    }
}