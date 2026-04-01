package com.anivault.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object NetworkUtils {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(url: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!continuation.isCompleted) {
                        val body = response.body?.string()
                        continuation.resume(body)
                    }
                }
            })
        }
    }

    suspend fun fetchJson(url: String): String? {
        return fetch(url)
    }

    suspend fun downloadFile(url: String, destination: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (!continuation.isCompleted) {
                        continuation.resume(false)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val inputStream = response.body?.byteStream() ?: run {
                            continuation.resume(false)
                            return
                        }

                        java.io.File(destination).outputStream().use { output ->
                            inputStream.copyTo(output)
                        }

                        if (!continuation.isCompleted) {
                            continuation.resume(true)
                        }
                    } catch (e: Exception) {
                        if (!continuation.isCompleted) {
                            continuation.resume(false)
                        }
                    }
                }
            })
        }
    }

    suspend fun getImage(url: String): okhttp3.Response? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }
}
