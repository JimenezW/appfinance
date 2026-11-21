# Arquitectura Futura: Firma de JSON e Identificación por Dispositivo (Sin Login)

> [!NOTE]
> **Documento histórico:** La especificación oficial vigente para la integración con `backFinanceApp v0.4.0` (mensaje canónico con `METHOD|PATH_AND_QUERY`, `X-Enrollment-Token` y catálogo completo de APIs) se encuentra en [`02-guia-integracion-app-movil-seguridad-y-apis.md`](./02-guia-integracion-app-movil-seguridad-y-apis.md).

> **Propósito:** Documento de referencia técnica para implementar en el futuro la identificación criptográfica por dispositivo, de modo que múltiples usuarios/dispositivos puedan sincronizar y consultar su propia información en un backend REST **sin sistema de login (usuario/contraseña)**.

---

## 1. Principio de Diseño: Identidad Criptográfica vs. IDs Planos

* **Qué evitar (IMEI, MAC o `ANDROID_ID` plano):**
  * El acceso a IMEI y dirección MAC está restringido en Android moderno y entra en conflicto con políticas de privacidad de Google Play.
  * Enviar únicamente un ID plano (como `ANDROID_ID` o un `UUID` sin firma) **no garantiza identidad**: cualquiera que conozca o intercepte ese ID podría suplantar al dispositivo y leer o sobrescribir su información.
* **Qué usar (`deviceId` + Par de llaves ECDSA en Android Keystore):**
  1. Al instalar/iniciar la app por primera vez, se genera un `deviceId` único (`UUID.randomUUID()`).
  2. Se genera un par de llaves asimétricas **`EC` (`secp256r1` / P-256)** con algoritmo de firma **`SHA256withECDSA`** almacenado en **`AndroidKeyStore`**.
  3. La **llave privada** nunca abandona el hardware seguro del dispositivo (TEE / StrongBox); la app solo solicita firmar bytes.
  4. La **llave pública** (codificada en X.509 / Base64) se comparte con el backend junto con el `deviceId`.

---

## 2. Estrategias para Firmar el JSON

Al firmar JSON, cualquier cambio de espacios, saltos de línea u orden de llaves altera el hash. Por ello existen dos patrones recomendados según el canal:

### Opción A: Firma en Cabeceras HTTP (Recomendada para Sincronización Wi-Fi `POST`)

El cliente firma exactamente los bytes UTF-8 enviados en el cuerpo HTTP, evitando problemas de re-serialización en el servidor:

1. Serializar el lote JSON una sola vez a `rawBodyBytes` (UTF-8).
2. Calcular el digest: `bodySha256 = SHA256(rawBodyBytes)` (hex o Base64).
3. Construir el mensaje canónico anti-replay:
   ```text
   {deviceId}|{timestampMillis}|{nonce}|{bodySha256}
   ```
4. Firmar ese mensaje con la llave privada del `AndroidKeyStore` (`SHA256withECDSA`) -> `signatureBase64`.
5. Enviar el JSON como body y adjuntar los metadatos de firma en los headers HTTP:
   * `X-Device-Id`: `f47ac10b-58cc-4372-a567-0e02b2c3d479`
   * `X-Device-Public-Key`: `<Base64 SPKI>`
   * `X-Timestamp`: `1795106531000`
   * `X-Nonce`: `<UUID único por petición>`
   * `X-Signature`: `<Base64 ECDSA>`

**Ventaja:** El backend lee el raw body recibido (`req.rawBody`), verifica el `SHA-256` y valida la firma sin importar cómo el parser JSON reordene las propiedades.

### Opción B: Firma Embebida en el Archivo `.json` (Para Exportación Manual)

Si se desea que los archivos `.json` exportados manualmente también incluyan firma verificable fuera de HTTP:

```json
{
  "schemaVersion": "1.1",
  "exportType": "captured_transactions",
  "deviceAuth": {
    "deviceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "deviceLabel": "Android",
    "algorithm": "ECDSA_P256_SHA256",
    "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
    "timestampMillis": 1795106531000,
    "nonce": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "signature": "MEUCIQDx..."
  },
  "payload": {
    "transactions": []
  }
}
```

> Para verificar una firma embebida sobre `payload`, tanto el cliente como el servidor deben canonicalizar el objeto `payload` (por ejemplo siguiendo **RFC 8785 - JSON Canonicalization Scheme / JCS**, ordenando llaves alfabéticamente y sin espacios extra) antes de calcular `SHA-256` y verificar la firma ECDSA.

---

## 3. Flujo en el Backend: TOFU (*Trust On First Use*) y Aislamiento

### Tabla de dispositivos en el servidor

```sql
CREATE TABLE devices (
  device_id     VARCHAR(64) PRIMARY KEY,
  public_key    TEXT NOT NULL,
  device_label  VARCHAR(120),
  created_at    TIMESTAMP NOT NULL,
  last_seen_at  TIMESTAMP NOT NULL
);
```

### Reglas de verificación en cada endpoint (`POST /sync`, `GET /movements`, `GET /admin`)

1. **Primer contacto (Registro automático sin login):**
   * Si `deviceId` **no existe** en la tabla `devices`:
     * El servidor valida la firma usando la `publicKey` enviada en la petición.
     * Si la firma es válida, inserta `(deviceId, publicKey, deviceLabel)` en `devices` y procesa la carga.
2. **Peticiones posteriores (Autenticación estricta):**
   * Si `deviceId` **ya existe** en `devices`:
     * El servidor **ignora cualquier `publicKey` nueva** en la petición y valida la firma **únicamente contra la `publicKey` almacenada en base de datos**.
     * Valida que `timestampMillis` esté dentro de una ventana permitida (ej. ±5 minutos) y que `nonce` no haya sido usado previamente (protección contra *replay attacks*).
3. **Aislamiento de datos por dispositivo:**
   * Todas las tablas de negocio (`transactions`, `accounts`, `classifications`, `details`, `merchants`, `scheduled_rules`) deben incluir la columna `device_id` indexada.
   * Todas las consultas y escrituras filtran por `WHERE device_id = :authenticatedDeviceId`.

---

## 4. Consideración Importante: Cambio o Pérdida de Teléfono

Dado que la llave privada vive en `AndroidKeyStore`, si el usuario desinstala la app o cambia de teléfono se generará un nuevo `deviceId` y un nuevo par de llaves.

Para soportar recuperación o múltiples dispositivos a futuro sin crear login tradicional, se puede añadir opcionalmente:
* Un **código de vinculación / QR temporal** generado desde el dispositivo original que autorice en el backend asociar una segunda `publicKey` al mismo `account_group_id`.

---

## 5. Integración con la Base Actual de "Mis Finanzas"

La arquitectura actual ya deja listos los puntos de extensión:
* **`SyncSettingsEntity`**: ya persiste `deviceLabel`; bastará con agregar `deviceId` generado la primera vez.
* **`ApiJsonBuilder`**: centraliza los esquemas JSON (`admin_configuration`, `captured_transactions` y `wifi_sync_batch`) con `schemaVersion`.
* **`OkHttpSyncTransport`**: centraliza las cabeceras HTTP del `POST`, donde se podrá inyectar un `DeviceSigner` que firme el payload con `AndroidKeyStore`.
