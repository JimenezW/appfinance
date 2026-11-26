package com.example.data.sync

import com.example.data.security.AndroidKeyStoreDeviceSigner
import com.example.data.security.DeviceAuthContext
import com.example.data.security.DeviceSignatureInterceptor
import com.example.data.security.DeviceSigner
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed interface SyncTransportResult {
  data class Success(val httpCode: Int, val responseBody: String) : SyncTransportResult

  data class Failure(
    val httpCode: Int? = null,
    val errorCode: String? = null,
    val message: String,
  ) : SyncTransportResult
}

interface SyncTransport {
  suspend fun postJson(
    endpointUrl: String,
    jsonPayload: String,
    authContext: DeviceAuthContext,
    idempotencyKey: String? = null,
  ): SyncTransportResult

  suspend fun getJson(
    endpointUrl: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult

  suspend fun putJson(
    endpointUrl: String,
    jsonPayload: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult =
    SyncTransportResult.Failure(message = "PUT no implementado en este transporte")

  suspend fun deleteJson(
    endpointUrl: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult =
    SyncTransportResult.Failure(message = "DELETE no implementado en este transporte")
}

object SyncUrlHelper {
  fun normalizeBaseUrl(rawUrl: String): String {
    var trimmed = rawUrl.trim().trimEnd('/')
    val suffixes = listOf("/api/v1/sync/batch", "/api/v1/sync/changes", "/api/v1")
    for (suffix in suffixes) {
      if (trimmed.endsWith(suffix, ignoreCase = true)) {
        trimmed = trimmed.removeSuffix(suffix).trimEnd('/')
      }
    }
    return trimmed
  }

  fun syncBatchUrl(rawUrl: String): String = "${normalizeBaseUrl(rawUrl)}/api/v1/sync/batch"

  fun syncChangesUrl(rawUrl: String, sinceMillis: Long): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/sync/changes?since=$sinceMillis"

  fun adminConfigUrl(rawUrl: String): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/catalogs/admin-config"

  fun budgetPeriodUrl(rawUrl: String, fiscalPeriodKey: String): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/budgets/$fiscalPeriodKey"

  fun budgetCloneUrl(rawUrl: String, fiscalPeriodKey: String): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/budgets/$fiscalPeriodKey/clone"

  fun budgetSuggestionsUrl(
    rawUrl: String,
    referencePeriodKey: String,
    lookbackPeriods: Int = 3,
  ): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/budgets/suggestions?lookbackPeriods=$lookbackPeriods&referencePeriodKey=$referencePeriodKey"

  fun budgetHistoryUrl(
    rawUrl: String,
    endPeriodKey: String,
    months: Int = 6,
  ): String =
    "${normalizeBaseUrl(rawUrl)}/api/v1/budgets/history?months=$months&endPeriodKey=$endPeriodKey"
}

class OkHttpSyncTransport(
  private val signer: DeviceSigner = AndroidKeyStoreDeviceSigner(),
  private val baseClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(25, TimeUnit.SECONDS)
      .build(),
) : SyncTransport {

  private fun clientFor(authContext: DeviceAuthContext): OkHttpClient {
    return baseClient
      .newBuilder()
      .addInterceptor(
        DeviceSignatureInterceptor(
          signer = signer,
          authContextProvider = { authContext },
        )
      )
      .build()
  }

  override suspend fun postJson(
    endpointUrl: String,
    jsonPayload: String,
    authContext: DeviceAuthContext,
    idempotencyKey: String?,
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

      if (!idempotencyKey.isNullOrBlank()) {
        requestBuilder.header(DeviceSignatureInterceptor.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
      }

      executeSigned(clientFor(authContext), requestBuilder.build())
    }

  override suspend fun getJson(
    endpointUrl: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult =
    withContext(Dispatchers.IO) {
      val trimmedUrl = endpointUrl.trim()
      if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return@withContext SyncTransportResult.Failure(
          message = "La URL del API debe iniciar con http:// o https://"
        )
      }

      val request =
        Request.Builder()
          .url(trimmedUrl)
          .get()
          .header("Accept", "application/json")
          .header("X-Client-App", "MisFinanzas-Android")
          .header("X-Schema-Version", ApiJsonBuilder.SCHEMA_VERSION)
          .build()

      executeSigned(clientFor(authContext), request)
    }

  override suspend fun putJson(
    endpointUrl: String,
    jsonPayload: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult =
    withContext(Dispatchers.IO) {
      val trimmedUrl = endpointUrl.trim()
      if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return@withContext SyncTransportResult.Failure(
          message = "La URL del API debe iniciar con http:// o https://"
        )
      }

      val mediaType = "application/json; charset=utf-8".toMediaType()
      val request =
        Request.Builder()
          .url(trimmedUrl)
          .put(jsonPayload.toRequestBody(mediaType))
          .header("Accept", "application/json")
          .header("Content-Type", "application/json; charset=utf-8")
          .header("X-Client-App", "MisFinanzas-Android")
          .header("X-Schema-Version", ApiJsonBuilder.SCHEMA_VERSION)
          .build()

      executeSigned(clientFor(authContext), request)
    }

  override suspend fun deleteJson(
    endpointUrl: String,
    authContext: DeviceAuthContext,
  ): SyncTransportResult =
    withContext(Dispatchers.IO) {
      val trimmedUrl = endpointUrl.trim()
      if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return@withContext SyncTransportResult.Failure(
          message = "La URL del API debe iniciar con http:// o https://"
        )
      }

      val request =
        Request.Builder()
          .url(trimmedUrl)
          .delete()
          .header("Accept", "application/json")
          .header("X-Client-App", "MisFinanzas-Android")
          .header("X-Schema-Version", ApiJsonBuilder.SCHEMA_VERSION)
          .build()

      executeSigned(clientFor(authContext), request)
    }

  private fun executeSigned(client: OkHttpClient, request: Request): SyncTransportResult {
    return try {
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (response.isSuccessful) {
          SyncTransportResult.Success(httpCode = response.code, responseBody = body)
        } else {
          val parsedError = parseBackendError(body)
          SyncTransportResult.Failure(
            httpCode = response.code,
            errorCode = parsedError?.first,
            message =
              parsedError?.second
                ?: "HTTP ${response.code}: ${response.message.ifBlank { "Error del servidor" }}",
          )
        }
      }
    } catch (e: IOException) {
      SyncTransportResult.Failure(
        message = "Sin respuesta del API (${e.message ?: "Error de red"})"
      )
    } catch (e: IllegalArgumentException) {
      SyncTransportResult.Failure(message = "URL del API inválida: ${e.message}")
    } catch (e: Exception) {
      SyncTransportResult.Failure(message = "Error de firma/transporte: ${e.message ?: "desconocido"}")
    }
  }

  private fun parseBackendError(body: String): Pair<String?, String?>? {
    if (body.isBlank()) return null
    return runCatching {
        val root = JSONObject(body)
        val err = root.optJSONObject("error") ?: return@runCatching null
        val code = err.optString("code").takeIf { it.isNotBlank() }
        val msg = err.optString("message").takeIf { it.isNotBlank() }
        val detailsArr = err.optJSONArray("details")
        val detailsSummary =
          if (detailsArr != null && detailsArr.length() > 0) {
            buildList {
                val limit = minOf(detailsArr.length(), 4)
                for (i in 0 until limit) {
                  val item = detailsArr.opt(i)
                  when (item) {
                    is JSONObject -> {
                      val path = item.optString("path").ifBlank { item.optString("field") }
                      val dMsg = item.optString("message").ifBlank { item.toString() }
                      add(if (path.isNotBlank()) "$path: $dMsg" else dMsg)
                    }
                    else -> add(item.toString())
                  }
                }
              }
              .joinToString("; ")
          } else null
        val fullMsg =
          when {
            msg != null && !detailsSummary.isNullOrBlank() -> "$msg ($detailsSummary)"
            msg != null -> msg
            !detailsSummary.isNullOrBlank() -> detailsSummary
            else -> null
          }
        code to fullMsg
      }
      .getOrNull()
  }
}
