package com.example.data.security

import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

data class DeviceAuthContext(
  val deviceId: String,
  val deviceLabel: String,
  val enrollmentToken: String,
  val isEnrolled: Boolean,
)

/**
 * OkHttp interceptor that signs every /api/v1/* request with ECDSA P-256 headers
 * per backFinanceApp v0.4.0 DeviceSignatureAuth specification.
 */
class DeviceSignatureInterceptor(
  private val signer: DeviceSigner,
  private val authContextProvider: () -> DeviceAuthContext,
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
  private val nonceProvider: () -> String = { UUID.randomUUID().toString() },
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()
    val path = original.url.encodedPath
    if (!path.contains("/api/v1/")) {
      return chain.proceed(original)
    }

    val ctx = authContextProvider()
    if (ctx.deviceId.isBlank()) {
      return chain.proceed(original)
    }

    val rawBodyBytes =
      original.body?.let { body ->
        val buffer = Buffer()
        body.writeTo(buffer)
        buffer.readByteArray()
      } ?: ByteArray(0)

    val bodySha256 = CanonicalRequestSigner.sha256Hex(rawBodyBytes)
    val encodedQuery = original.url.encodedQuery
    val pathAndQuery = if (encodedQuery.isNullOrEmpty()) path else "$path?$encodedQuery"
    val timestampMillis = clockMillis()
    val nonce = nonceProvider()

    val canonical =
      CanonicalRequestSigner.buildCanonicalMessage(
        method = original.method,
        pathAndQuery = pathAndQuery,
        deviceId = ctx.deviceId,
        timestampMillis = timestampMillis,
        nonce = nonce,
        bodySha256 = bodySha256,
      )

    val signatureBase64 = signer.signCanonicalMessage(canonical)
    val requestBuilder =
      original
        .newBuilder()
        .header(HEADER_DEVICE_ID, ctx.deviceId)
        .header(HEADER_TIMESTAMP, timestampMillis.toString())
        .header(HEADER_NONCE, nonce)
        .header(HEADER_SIGNATURE, signatureBase64)

    if (original.header(HEADER_REQUEST_ID).isNullOrBlank()) {
      requestBuilder.header(HEADER_REQUEST_ID, UUID.randomUUID().toString())
    }

    // TOFU (Trust On First Use) headers sent until the device is confirmed enrolled
    if (!ctx.isEnrolled) {
      val publicKeySpki = signer.getOrCreatePublicKeySpkiBase64()
      requestBuilder.header(HEADER_DEVICE_PUBLIC_KEY, publicKeySpki)
      if (ctx.enrollmentToken.isNotBlank()) {
        requestBuilder.header(HEADER_ENROLLMENT_TOKEN, ctx.enrollmentToken.trim())
      }
      if (ctx.deviceLabel.isNotBlank()) {
        requestBuilder.header(HEADER_DEVICE_LABEL, ctx.deviceLabel.trim())
      }
    }

    return chain.proceed(requestBuilder.build())
  }

  companion object {
    const val HEADER_DEVICE_ID = "X-Device-Id"
    const val HEADER_TIMESTAMP = "X-Timestamp"
    const val HEADER_NONCE = "X-Nonce"
    const val HEADER_SIGNATURE = "X-Signature"
    const val HEADER_DEVICE_PUBLIC_KEY = "X-Device-Public-Key"
    const val HEADER_ENROLLMENT_TOKEN = "X-Enrollment-Token"
    const val HEADER_DEVICE_LABEL = "X-Device-Label"
    const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
    const val HEADER_REQUEST_ID = "X-Request-Id"
  }
}
