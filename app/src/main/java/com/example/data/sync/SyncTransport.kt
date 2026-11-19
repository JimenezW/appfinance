package com.example.data.sync

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface SyncTransportResult {
  data class Success(val httpCode: Int, val responseBody: String) : SyncTransportResult

  data class Failure(val httpCode: Int? = null, val message: String) : SyncTransportResult
}

interface SyncTransport {
  suspend fun postJson(
    endpointUrl: String,
    authToken: String?,
    jsonPayload: String,
  ): SyncTransportResult
}

class OkHttpSyncTransport(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .build()
) : SyncTransport {

  override suspend fun postJson(
    endpointUrl: String,
    authToken: String?,
    jsonPayload: String,
  ): SyncTransportResult =
    withContext(Dispatchers.IO) {
      val trimmedUrl = endpointUrl.trim()
      if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return@withContext SyncTransportResult.Failure(
          message = "La URL del API debe iniciar con http:// o https://"
        )
      }

      val mediaType = "application/json; charset=utf-8".toMediaType()
      val requestBuilder =
        Request.Builder()
          .url(trimmedUrl)
          .post(jsonPayload.toRequestBody(mediaType))
          .header("Accept", "application/json")
          .header("Content-Type", "application/json; charset=utf-8")
          .header("X-Client-App", "MisFinanzas-Android")
          .header("X-Schema-Version", ApiJsonBuilder.SCHEMA_VERSION)

      val cleanToken = authToken?.trim().orEmpty()
      if (cleanToken.isNotEmpty()) {
        val headerValue =
          if (cleanToken.startsWith("Bearer ", ignoreCase = true)) cleanToken
          else "Bearer $cleanToken"
        requestBuilder.header("Authorization", headerValue)
      }

      try {
        client.newCall(requestBuilder.build()).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (response.isSuccessful) {
            SyncTransportResult.Success(httpCode = response.code, responseBody = body)
          } else {
            SyncTransportResult.Failure(
              httpCode = response.code,
              message = "HTTP ${response.code}: ${response.message.ifBlank { "Error del servidor" }}",
            )
          }
        }
      } catch (e: IOException) {
        SyncTransportResult.Failure(
          message = "Sin respuesta del API (${e.message ?: "Error de red"})"
        )
      } catch (e: IllegalArgumentException) {
        SyncTransportResult.Failure(message = "URL del API inválida: ${e.message}")
      }
    }
}
