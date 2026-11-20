package com.yamtrack.app.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds the Bearer authentication token
 * to every API request.
 * 
 * The token is managed by TokenProvider so it can be updated dynamically
 * when the user logs in/out or switches servers.
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider.getToken()
        
        val requestBuilder = original.newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        return chain.proceed(requestBuilder.build())
    }
}

/**
 * Provides the current authentication token.
 * Implementations can fetch from DataStore, memory cache, etc.
 */
interface TokenProvider {
    fun getToken(): String?
    fun setToken(token: String?)
}

/**
 * Simple in-memory token provider - token is loaded on app start
 * from DataStore and kept in memory for quick access.
 */
class InMemoryTokenProvider : TokenProvider {
    @Volatile
    private var token: String? = null
    
    override fun getToken(): String? = token
    
    override fun setToken(token: String?) {
        this.token = token
    }
}

/**
 * Dynamic base URL provider - lets us change the server URL at runtime
 * without having to recreate the Retrofit instance.
 */
class BaseUrlInterceptor(
    private val baseUrlProvider: BaseUrlProvider
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val baseUrl = baseUrlProvider.getBaseUrl()
        
        if (baseUrl.isNullOrBlank()) {
            return chain.proceed(original)
        }
        
        // Replace the host in the URL using OkHttp 4.x Kotlin API.
        val newUrl = baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return chain.proceed(original)
        
        val originalUrl = original.url
        val newRequestUrl = originalUrl.newBuilder()
            .scheme(newUrl.scheme)
            .host(newUrl.host)
            .port(newUrl.port)
            .build()
        
        val newRequest = original.newBuilder()
            .url(newRequestUrl)
            .build()
        
        return chain.proceed(newRequest)
    }
}

interface BaseUrlProvider {
    fun getBaseUrl(): String?
    fun setBaseUrl(url: String)
}

class InMemoryBaseUrlProvider(initialUrl: String) : BaseUrlProvider {
    @Volatile
    private var baseUrl: String = initialUrl
    
    override fun getBaseUrl(): String? = baseUrl
    
    override fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }
}
