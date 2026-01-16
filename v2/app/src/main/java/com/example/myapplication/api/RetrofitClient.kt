package com.example.myapplication.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private lateinit var sharedPrefs: SharedPreferences
    private var retrofit: Retrofit? = null

    init {
        android.util.Log.d("RetrofitClient", "RetrofitClient object initialized")
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun getCurrentBaseUrl(): String {
        val savedIp = sharedPrefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2"
        val savedPort = sharedPrefs.getString("server_port", "3000") ?: "3000"
        return "http://$savedIp:$savedPort/"
    }

    private fun buildRetrofit(): Retrofit {
        val baseUrl = getCurrentBaseUrl()
        android.util.Log.d("RetrofitClient", "Building Retrofit with BASE_URL: $baseUrl")
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun getLampApi(): LampApi {
        if (retrofit == null) {
            retrofit = buildRetrofit()
        }
        return retrofit!!.create(LampApi::class.java)
    }

    /**
     * Initialize RetrofitClient with context to read server IP from SharedPreferences.
     * Call this once in your Application class or MainActivity.onCreate().
     */
    fun init(context: Context) {
        sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val baseUrl = getCurrentBaseUrl()
        android.util.Log.d("RetrofitClient", "init() called with BASE_URL: $baseUrl")
    }

    /**
     * Call this after changing settings to rebuild Retrofit with new IP/port.
     */
    fun updateBaseUrl() {
        val baseUrl = getCurrentBaseUrl()
        android.util.Log.d("RetrofitClient", "updateBaseUrl() called, new BASE_URL: $baseUrl")
        // Force rebuild of Retrofit instance
        retrofit = null
    }

    /**
     * For physical devices, update the server IP in Settings.
     * For emulator, use 10.0.2.2 to access host localhost.
     */
}

