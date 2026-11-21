package com.example.data.local

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Deterministic RFC 4122 UUIDv5 (SHA-1) generator used for offline seed catalogs
 * and migrating legacy Room v3 numeric IDs to stable UUIDs.
 */
object UuidV5 {
  // Dedicated namespace UUID for Mis Finanzas legacy/offline deterministic IDs
  val NAMESPACE_MIS_FINANZAS: UUID = UUID.fromString("9f8b6c4a-2d1e-4b3a-8c5d-7e9f0a1b2c3d")

  fun fromName(name: String, namespace: UUID = NAMESPACE_MIS_FINANZAS): String {
    val nsBytes =
      ByteBuffer.allocate(16)
        .putLong(namespace.mostSignificantBits)
        .putLong(namespace.leastSignificantBits)
        .array()
    val nameBytes = name.toByteArray(Charsets.UTF_8)

    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(nsBytes)
    digest.update(nameBytes)
    val hash = digest.digest()

    // Set version to 5 (bits 4..7 of byte 6)
    hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
    // Set variant to RFC 4122 (bits 6..7 of byte 8)
    hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()

    val bb = ByteBuffer.wrap(hash, 0, 16)
    val msb = bb.long
    val lsb = bb.long
    return UUID(msb, lsb).toString()
  }
}
