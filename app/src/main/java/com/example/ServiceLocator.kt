package com.example

import android.content.Context
import com.example.data.api.OpenRouterApi
import com.example.data.api.TelegramApi
import com.example.data.local.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.data.repository.KeyRotator
import com.example.data.repository.TelegramRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ServiceLocator {
    private var isInitialized = false
    
    lateinit var database: AppDatabase
        private set
    lateinit var openRouterApi: OpenRouterApi
        private set
    lateinit var telegramApi: TelegramApi
        private set
    lateinit var keyRotator: KeyRotator
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var telegramRepository: TelegramRepository
        private set

    fun init(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext

        // 1. Initialize DB
        database = AppDatabase.getDatabase(appContext)

        // 2. Setup Moshi
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        // 3. Setup HTTP client
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // 4. Setup Retrofit
        val openRouterRetrofit = Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val telegramRetrofit = Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        openRouterApi = openRouterRetrofit.create(OpenRouterApi::class.java)
        telegramApi = telegramRetrofit.create(TelegramApi::class.java)

        // 5. Providers & Repositories
        keyRotator = KeyRotator(
            apiKeyDao = database.apiKeyDao(),
            logDao = database.logDao()
        )
        
        chatRepository = ChatRepository(
            chatDao = database.chatDao(),
            openRouterApi = openRouterApi,
            keyRotator = keyRotator
        )

        telegramRepository = TelegramRepository(
            botDao = database.botDao(),
            logDao = database.logDao(),
            telegramApi = telegramApi
        )

        isInitialized = true
    }
}
