package com.example

import com.example.data.security.CanonicalRequestSigner
import com.example.data.security.DeviceAuthContext
import com.example.data.security.DeviceSignatureInterceptor
import com.example.data.security.DeviceSigner
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSecurityTest {

  private class InMemoryEcdsaP256Signer : DeviceSigner {
    private val keyPair =
      KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
      }.generateKeyPair()

    override fun hasKeyPair(): Boolean = true

    override fun getOrCreatePublicKeySpkiBase64(): String =
      Base64.getEncoder().encodeToString(keyPair.public.encoded)

    override fun signCanonicalMessage(canonicalMessage: String): String {
      val sig = Signature.getInstance("SHA256withECDSA")
      sig.initSign(keyPair.private)
      sig.update(canonicalMessage.toByteArray(Charsets.UTF_8))
      return Base64.getEncoder().encodeToString(sig.sign())
    }
  }

  @Test
  fun sha256Hex_emptyBodyMatchesGuideConstant() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      CanonicalRequestSigner.sha256Hex(ByteArray(0)),
    )
  }

  @Test
  fun buildCanonicalMessage_matchesExactBackFinanceAppFormat() {
    val canonical =
      CanonicalRequestSigner.buildCanonicalMessage(
        method = "get",
        pathAndQuery = "/api/v1/transactions?fiscalPeriodKey=2026-11",
        deviceId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        timestampMillis = 1795106531000L,
        nonce = "a1b2c3d4-e5f6-4890-abcd-ef1234567890",
        bodySha256 = CanonicalRequestSigner.EMPTY_BODY_SHA256,
      )
    assertEquals(
      "GET|/api/v1/transactions?fiscalPeriodKey=2026-11|f47ac10b-58cc-4372-a567-0e02b2c3d479|1795106531000|a1b2c3d4-e5f6-4890-abcd-ef1234567890|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      canonical,
    )
  }

  @Test
  fun deviceSignatureInterceptor_signsRequestAndVerifiesWithSpkiPublicKey() {
    val signer = InMemoryEcdsaP256Signer()
    val deviceId = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    val fixedNow = 1795106531000L
    val fixedNonce = "a1b2c3d4-e5f6-4890-abcd-ef1234567890"
    var isEnrolled = false

    var capturedRequest: Request? = null
    val client =
      OkHttpClient.Builder()
        .addInterceptor(
          DeviceSignatureInterceptor(
            signer = signer,
            authContextProvider = {
              DeviceAuthContext(
                deviceId = deviceId,
                deviceLabel = "Pixel 9",
                enrollmentToken = "enroll-secret-123",
                isEnrolled = isEnrolled,
              )
            },
            clockMillis = { fixedNow },
            nonceProvider = { fixedNonce },
          )
        )
        .addInterceptor { chain ->
          capturedRequest = chain.request()
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"success":true}""".toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    val jsonBody = """{"schemaVersion":"1.0","exportType":"wifi_sync_batch"}"""
    val postReq =
      Request.Builder()
        .url("https://api.example.com/api/v1/sync/batch")
        .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()

    client.newCall(postReq).execute().close()

    val signed = capturedRequest!!
    assertEquals(deviceId, signed.header(DeviceSignatureInterceptor.HEADER_DEVICE_ID))
    assertEquals(fixedNow.toString(), signed.header(DeviceSignatureInterceptor.HEADER_TIMESTAMP))
    assertEquals(fixedNonce, signed.header(DeviceSignatureInterceptor.HEADER_NONCE))
    assertEquals("enroll-secret-123", signed.header(DeviceSignatureInterceptor.HEADER_ENROLLMENT_TOKEN))
    assertEquals("Pixel 9", signed.header(DeviceSignatureInterceptor.HEADER_DEVICE_LABEL))
    assertNotNull(signed.header(DeviceSignatureInterceptor.HEADER_REQUEST_ID))

    val spkiBase64 = signed.header(DeviceSignatureInterceptor.HEADER_DEVICE_PUBLIC_KEY)!!
    val sigBase64 = signed.header(DeviceSignatureInterceptor.HEADER_SIGNATURE)!!

    // Verify ECDSA P-256 DER signature using exported X.509 SPKI public key (exactly as backend does)
    val pubKey =
      KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)))
    val expectedCanonical =
      CanonicalRequestSigner.buildCanonicalMessage(
        method = "POST",
        pathAndQuery = "/api/v1/sync/batch",
        deviceId = deviceId,
        timestampMillis = fixedNow,
        nonce = fixedNonce,
        bodySha256 = CanonicalRequestSigner.sha256Hex(jsonBody.toByteArray(Charsets.UTF_8)),
      )
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(pubKey)
    verifier.update(expectedCanonical.toByteArray(Charsets.UTF_8))
    assertTrue(verifier.verify(Base64.getDecoder().decode(sigBase64)))

    // Once enrolled, TOFU headers must no longer be attached
    isEnrolled = true
    val getReq =
      Request.Builder()
        .url("https://api.example.com/api/v1/sync/changes?since=0")
        .get()
        .build()
    client.newCall(getReq).execute().close()

    val signedEnrolled = capturedRequest!!
    assertNull(signedEnrolled.header(DeviceSignatureInterceptor.HEADER_DEVICE_PUBLIC_KEY))
    assertNull(signedEnrolled.header(DeviceSignatureInterceptor.HEADER_ENROLLMENT_TOKEN))
    assertNotNull(signedEnrolled.header(DeviceSignatureInterceptor.HEADER_SIGNATURE))
  }
}
