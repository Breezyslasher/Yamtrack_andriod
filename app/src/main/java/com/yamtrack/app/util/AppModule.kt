package com.yamtrack.app.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.data.api.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor
    ): OkHttpClient {
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
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
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
