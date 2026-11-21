package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

interface DeviceSigner {
  fun hasKeyPair(): Boolean

  fun getOrCreatePublicKeySpkiBase64(): String

  fun signCanonicalMessage(canonicalMessage: String): String
}

/**
 * Hardware-backed ECDSA P-256 (secp256r1 / prime256v1) device identity stored in AndroidKeyStore.
 * Private keys never leave TEE / StrongBox; only the X.509 SPKI DER public key (Base64) is exported.
 */
class AndroidKeyStoreDeviceSigner(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : DeviceSigner {

  private fun loadKeyStore(): KeyStore =
    KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  override fun hasKeyPair(): Boolean =
    runCatching { loadKeyStore().containsAlias(keyAlias) }.getOrDefault(false)

  private fun ensureKeyPair(): KeyStore.PrivateKeyEntry {
    val ks = loadKeyStore()
    if (!ks.containsAlias(keyAlias)) {
      generateKeyPair()
      ks.load(null)
    }
    return ks.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
  }

  private fun generateKeyPair() {
    val spec =
      KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .build()

    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
    kpg.initialize(spec)
    kpg.generateKeyPair()
  }

  override fun getOrCreatePublicKeySpkiBase64(): String {
    val entry = ensureKeyPair()
    val spkiDerBytes = entry.certificate.publicKey.encoded
    return Base64.encodeToString(spkiDerBytes, Base64.NO_WRAP)
  }

  override fun signCanonicalMessage(canonicalMessage: String): String {
    val entry = ensureKeyPair()
    val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
    signature.initSign(entry.privateKey)
    signature.update(canonicalMessage.toByteArray(Charsets.UTF_8))
    val derSignature = signature.sign()
    return Base64.encodeToString(derSignature, Base64.NO_WRAP)
  }

  companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_KEY_ALIAS = "mis_finanzas_device_ec_p256"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
  }
}
