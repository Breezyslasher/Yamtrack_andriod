package com.yamtrack.app.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.data.api.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt dependency injection module.
 * 
 * Wires up:
 *  - Gson for JSON parsing
 *  - OkHttp client with auth + logging interceptors
 *  - Retrofit instance pointing at the configured base URL
 *  - YamtrackApi instance
 *  - Shared providers for the current token & base URL
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = InMemoryTokenProvider()

    @Provides
    @Singleton
    fun provideBaseUrlProvider(): BaseUrlProvider = 
        InMemoryBaseUrlProvider(BuildConfig.DEFAULT_SERVER_URL)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: TokenProvider): AuthInterceptor =
        AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun provideBaseUrlInterceptor(baseUrlProvider: BaseUrlProvider): BaseUrlInterceptor =
        BaseUrlInterceptor(baseUrlProvider)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor
    ): OkHttpClient {
        // 15 MB on-disk HTTP cache. Responses are revalidated normally;
        // when the device is offline we serve stale GETs (up to a week)
        // so list/detail screens aren't blank without a connection.
        val cache = Cache(File(context.cacheDir, "http_cache"), 15L * 1024 * 1024)
        val offlineFallback = okhttp3.Interceptor { chain ->
            var request = chain.request()
            if (request.method == "GET") {
                val online = runCatching {
                    val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                    cm?.activeNetwork != null
                }.getOrDefault(true)
                if (!online) {
                    request = request.newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=604800")
                        .build()
                }
            }
            chain.proceed(request)
        }
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Never print the bearer token to logcat, even in debug.
            redactHeader("Authorization")
            redactHeader("X-API-Key")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(offlineFallback)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        // Base URL is required by Retrofit but our BaseUrlInterceptor
        // overrides the scheme/host/port on every request, so this
        // is effectively just a placeholder.
        .baseUrl(BuildConfig.DEFAULT_SERVER_URL.let {
            if (it.endsWith("/")) it else "$it/"
        })
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideYamtrackApi(retrofit: Retrofit): YamtrackApi =
        retrofit.create(YamtrackApi::class.java)
}
