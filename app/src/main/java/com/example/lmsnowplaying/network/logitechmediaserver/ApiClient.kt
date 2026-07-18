package com.example.lmsnowplaying.network.logitechmediaserver
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.SettingsRepository
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Rebuilt whenever AppConfig.current changes (e.g. settings edited via
    // the reopened Settings screen), instead of caching forever.
    private var cachedSettings: SettingsRepository.Settings? = null
    private var cachedRetrofit: Retrofit? = null

    val retrofit: Retrofit
        get() {
            val settings = AppConfig.current
            val existing = cachedRetrofit
            if (existing != null && cachedSettings == settings) return existing

            val client = OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(settings.lmsUsername, settings.lmsPassword))
                .build()

            val built = Retrofit.Builder()
                .client(client)
                .baseUrl(settings.lmsUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            cachedSettings = settings
            cachedRetrofit = built
            return built
        }
}

object ApiClient {
    val apiService: ApiService
        get() = RetrofitClient.retrofit.create(ApiService::class.java)
}


class BasicAuthInterceptor(username: String, password: String): Interceptor {
    private var credentials: String = Credentials.basic(username, password)

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var request = chain.request()
        request = request.newBuilder().header("Authorization", credentials).build()
        return chain.proceed(request)
    }
}