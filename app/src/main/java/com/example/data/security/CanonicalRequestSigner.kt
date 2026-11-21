package com.example.data.security

import java.security.MessageDigest

/**
 * Pure canonicalization and SHA-256 helper matching backFinanceApp v0.4.0 DeviceSignatureAuth:
 * {METHOD}|{PATH_AND_QUERY}|{deviceId}|{timestampMillis}|{nonce}|{bodySha256}
 */
object CanonicalRequestSigner {
  const val EMPTY_BODY_SHA256 =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

  fun sha256Hex(rawBytes: ByteArray): String {
    if (rawBytes.isEmpty()) return EMPTY_BODY_SHA256
    val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
      sb.append(HEX_CHARS[(b.toInt() shr 4) and 0x0F])
      sb.append(HEX_CHARS[b.toInt() and 0x0F])
    }
    return sb.toString()
  }

  fun buildCanonicalMessage(
    method: String,
    pathAndQuery: String,
    deviceId: String,
    timestampMillis: Long,
    nonce: String,
    bodySha256: String,
  ): String {
    return "${method.uppercase()}|$pathAndQuery|$deviceId|$timestampMillis|$nonce|$bodySha256"
  }

  private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
