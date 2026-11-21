# Guía de Integración App Móvil Android ↔ `backFinanceApp` (Seguridad y Referencia de APIs)

> **Propósito:** Insumo técnico oficial para actualizar la aplicación Android *Mis Finanzas* (Kotlin + Compose + Room + OkHttp) y conectarla de forma segura con `backFinanceApp` (`v0.4.0`).  
> **Swagger UI (desarrollo):** `http://<host>:3003/docswg`  
> **OpenAPI 3.1 JSON:** `http://<host>:3003/api/v1/openapi.json`

---

## 1. Cambios de Modelo Requeridos en la App Móvil (`v1.1` → `v1.2`)

1. **Identificadores UUID en todas las entidades:**
   - Todos los `id`, `typeId`, `classificationId`, `detailId`, `accountId` son **`String` (UUID)** en lugar de `Int`/`Long`.
   - En los catálogos iniciales (`mis_finanzas_admin_config.json`), el backend genera **UUIDv5 deterministas**. Para registros nuevos creados offline en el teléfono, la app debe generar **UUIDv4** (`UUID.randomUUID().toString()`).
2. **`TransactionType`:**
   - Ahora es una entidad con `id` (UUID), `code` (`"GASTO"` | `"INGRESO"`) y `name` (`"Gasto"` | `"Ingreso"`).
3. **Bandera `default: Boolean`:**
   - `true`: elementos del catálogo base compartidos al instalar la app por primera vez.
   - `false`: elementos creados por el usuario desde el dispositivo.
4. **Precisión monetaria estricta:**
   - Todos los montos y saldos se envían y reciben en **centavos enteros** (`Long` en Kotlin: `amountCents`, `balanceCents`, `monthlyBudgetCents`). Los campos `*Mxn` o `balance` son solo de presentación.
5. **Periodo Fiscal (corte día 15):**
   - Días `1–14` → mes en curso (`YYYY-MM`).
   - Días `15–31` → mes siguiente (`YYYY-MM`, avanzando año en diciembre).
6. **Aislamiento multiusuario:**
   - Las transacciones pertenecen al `deviceId` autenticado. Cada dispositivo solo consulta, edita y elimina sus propias transacciones.

---

## 2. Seguridad en la Conexión App ↔ Backend (Sin Login)

La app **no usa usuario ni contraseña**. La seguridad combina:
1. **Protección local del dispositivo:** `BiometricPrompt` (huella / PIN / FaceID) vinculado a la llave criptográfica en `AndroidKeyStore` (`setUserAuthenticationRequired(true)`).
2. **Identidad criptográfica por dispositivo (ECDSA P-256):**
   - Al iniciar por primera vez:
     - Generar `deviceId = UUID.randomUUID().toString()` y persistirlo en `SyncSettingsEntity`.
     - Generar un par de llaves asimétricas en `AndroidKeyStore`: algoritmo `EC`, curva `secp256r1` (`prime256v1`), firma `SHA256withECDSA`.
     - Exportar la llave pública en formato **X.509 SPKI DER codificado en Base64** (sin saltos de línea).
     - La llave privada nunca sale del hardware seguro (TEE / StrongBox).

### 2.1. Algoritmo de Firma por Petición (Interceptor OkHttp)

En **cada** petición HTTP a `/api/v1/*` (cuando `DEVICE_AUTH_ENABLED=true` en el servidor):

1. Obtener los bytes exactos UTF-8 del cuerpo enviado (`rawBodyBytes`). Si la petición no tiene cuerpo (`GET`, `DELETE`), usar un arreglo vacío (`ByteArray(0)`).
2. Calcular el hash SHA-256 en hexadecimal minúsculas:
   - `bodySha256 = sha256Hex(rawBodyBytes)`
   - *(Para cuerpo vacío siempre es `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.)*
3. Construir el **mensaje canónico** (exactamente en este orden):
   ```text
   {METHOD}|{PATH_AND_QUERY}|{deviceId}|{timestampMillis}|{nonce}|{bodySha256}
   ```
   **Ejemplo (`GET` con query):**
   ```text
   GET|/api/v1/transactions?fiscalPeriodKey=2026-11|f47ac10b-58cc-4372-a567-0e02b2c3d479|1795106531000|a1b2c3d4-e5f6-4890-abcd-ef1234567890|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
   ```
4. Firmar el mensaje canónico (bytes UTF-8) con `SHA256withECDSA` usando la llave privada del `AndroidKeyStore` y codificar la firma DER resultante en **Base64** (sin saltos de línea).

### 2.2. Cabeceras HTTP

| Cabecera | Cuándo enviarla | Formato / Descripción |
| :--- | :--- | :--- |
| `X-Device-Id` | Siempre | UUIDv4 del dispositivo. |
| `X-Timestamp` | Siempre | Epoch en milisegundos (`System.currentTimeMillis()`). Ventana permitida: **±5 minutos**. |
| `X-Nonce` | Siempre | UUIDv4 único por petición (anti-replay, TTL 10 min en servidor). |
| `X-Signature` | Siempre | Firma ECDSA P-256 en Base64 (DER). |
| `X-Device-Public-Key` | Solo 1er contacto (TOFU) | Llave pública SPKI DER en Base64. En peticiones posteriores el servidor la ignora y usa la almacenada. |
| `X-Enrollment-Token` | Solo 1er contacto (TOFU) | Token de aprovisionamiento configurado en el servidor (`DEVICE_ENROLLMENT_TOKEN`). |
| `X-Device-Label` | Opcional (enrolamiento) | Etiqueta legible del dispositivo (ej. `"Pixel 9"`). |
| `Idempotency-Key` | Opcional en `POST` críticos | UUIDv4 para `POST /api/v1/transactions` y `POST /api/v1/scheduled-rules/:id/confirm`. |
| `X-Request-Id` | Opcional | UUIDv4 de trazabilidad; el servidor lo devuelve en `meta.requestId`. |

> **Nota para desarrollo local:** en `NODE_ENV=development`, `DEVICE_AUTH_ENABLED` está en `false` por defecto. Aun así, si la app envía `X-Device-Id` en desarrollo, el backend lo toma para probar el aislamiento multiusuario. En `production`, `DEVICE_AUTH_ENABLED=true` y `ENFORCE_HTTPS=true` son obligatorios.

### 2.3. Transporte Seguro y Manejo de Errores de Seguridad
- Configurar `network_security_config.xml` en Android con `cleartextTrafficPermitted="false"` en `release`.
- **Reacción esperada en la app ante códigos de error de seguridad:**
  - `401 TIMESTAMP_EXPIRED`: alertar desfase de reloj del dispositivo.
  - `401 REPLAY_DETECTED`: regenerar `X-Nonce` y `X-Timestamp` antes de reintentar.
  - `401 INVALID_SIGNATURE`: revisar canonicalización de `rawBody` y `PATH_AND_QUERY`.
  - `403 DEVICE_REVOKED`: detener sincronización automática y mostrar aviso de dispositivo revocado.
  - `403 INVALID_ENROLLMENT_TOKEN`: solicitar el token de enrolamiento válido.
  - `429 RATE_LIMIT_EXCEEDED`: aplicar *exponential backoff* en la cola *outbox* (límite: 120 req/min).

---

## 3. Convenciones de Respuesta

### Respuesta Exitosa
```json
{
  "success": true,
  "data": { },
  "meta": {
    "timestamp": "2026-11-20T21:45:00.000Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Respuesta de Error
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Descripción legible del error.",
    "details": []
  },
  "meta": {
    "timestamp": "2026-11-20T21:45:00.000Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

---

## 4. Catálogo Completo de APIs para la App Móvil

> Todas las rutas bajo `/api/v1/*` listadas en las secciones **4.1 a 4.6** están protegidas por `DeviceSignatureAuth` (cuando `DEVICE_AUTH_ENABLED=true`).

### 4.1. Sincronización Wi-Fi (`/api/v1/sync`) — *Flujo Principal Offline-First*

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `POST` | `/api/v1/sync/batch` | Envía el lote `wifi_sync_batch` desde la cola *outbox* (`adminChanges`, `capturedTransactions`, `deletedTransactions`). Idempotente y tolerante a fallos parciales. |
| `GET` | `/api/v1/sync/changes?since=<millis>` | Descarga cambios diferenciales ocurridos después de `since` (usar `0` en instalación inicial). |

**Ejemplo `POST /api/v1/sync/batch` (Request):**
```json
{
  "schemaVersion": "1.0",
  "exportType": "wifi_sync_batch",
  "deviceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "sentAtMillis": 1795106531000,
  "adminChanges": {
    "classifications": [],
    "details": [],
    "merchants": [],
    "accounts": [],
    "scheduledRules": [],
    "deletedIds": {
      "classifications": [],
      "details": [],
      "merchants": [],
      "accounts": [],
      "scheduledRules": []
    }
  },
  "capturedTransactions": [
    {
      "id": "cbcabee2-089e-43b1-809b-c1b19ed99f92",
      "operationDate": "19/11/2026",
      "typeId": "<uuid-tipo>",
      "classificationId": "<uuid-clasificacion>",
      "detailId": "<uuid-detalle>",
      "accountId": "<uuid-cuenta>",
      "merchantName": "Netflix",
      "amountCents": 26900,
      "note": "Pago mensual"
    }
  ],
  "deletedTransactions": []
}
```

**Ejemplo `POST /api/v1/sync/batch` (Response `200`):**
```json
{
  "success": true,
  "data": {
    "serverTimestamp": "2026-11-20T21:45:00.000Z",
    "serverTimestampMillis": 1795211100000,
    "syncedTransactionIds": ["cbcabee2-089e-43b1-809b-c1b19ed99f92"],
    "deletedTransactionIds": [],
    "adminApplied": { "classifications": 0, "details": 0, "merchants": 0, "accounts": 0, "scheduledRules": 0, "deleted": 0 },
    "failedItems": []
  },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

---

### 4.2. Catálogos (`/api/v1/catalogs`)

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/catalogs/admin-config` | Descarga completa del contrato `admin_configuration` (tipos, clasificaciones, detalles, cuentas, comercios y reglas con UUIDv5 y `default`). |
| `POST` | `/api/v1/catalogs/classifications` | Crea clasificación (`default: false`). Body: `{ id?, typeId, name, sortOrder?, isActive?, isPinned?, monthlyBudgetCents? }`. |
| `PATCH` | `/api/v1/catalogs/classifications/:id` | Edita clasificación (`name`, `sortOrder`, `isActive`, `isPinned`, `monthlyBudgetCents`). |
| `DELETE` | `/api/v1/catalogs/classifications/:id` | Soft delete de clasificación. |
| `POST` | `/api/v1/catalogs/details` | Crea detalle (`default: false`). Body: `{ id?, classificationId, name, sortOrder?, isActive?, isPinned? }`. |
| `PATCH` | `/api/v1/catalogs/details/:id` | Edita detalle. |
| `DELETE` | `/api/v1/catalogs/details/:id` | Soft delete de detalle. |
| `POST` | `/api/v1/catalogs/merchants` | Crea comercio (`default: false`). Body: `{ id?, name, sortOrder?, isActive?, isPinned? }`. |
| `PATCH` | `/api/v1/catalogs/merchants/:id` | Edita comercio. |
| `DELETE` | `/api/v1/catalogs/merchants/:id` | Soft delete de comercio. |

---

### 4.3. Cuentas (`/api/v1/accounts`)

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/accounts?includeInactive=false` | Lista cuentas con `balanceCents`, `balanceMxn`, `balance` y `totals` por moneda. |
| `POST` | `/api/v1/accounts` | Crea cuenta (`default: false`). Body: `{ id?, name, shortLabel, accountKind, balanceCents?, currency?, sortOrder?, isActive?, isPinned? }`. `accountKind`: `DEBIT \| CASH \| VALES \| CREDIT \| FINTECH`. |
| `PATCH` | `/api/v1/accounts/:id` | Edita cuenta. |
| `DELETE` | `/api/v1/accounts/:id` | Soft delete de cuenta. |

---

### 4.4. Transacciones (`/api/v1/transactions`)

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/transactions` | Lista transacciones del dispositivo. Query opcional: `fiscalPeriodKey`, `typeId`, `classificationId`, `accountId`, `cursor` (UUID), `limit` (`1..200`, default `50`). |
| `POST` | `/api/v1/transactions` | Crea transacción (`default: false`) y actualiza `balanceCents` de la cuenta atómicamente (`INGRESO` suma, `GASTO` resta, permite negativo). Soporta cabecera `Idempotency-Key` (UUIDv4): `201` si es nueva, `200` si fue deduplicada. |
| `PATCH` | `/api/v1/transactions/:id` | Edita transacción y reconcilia atómicamente el saldo (incluso ante cambio de monto, tipo o cuenta). |
| `DELETE` | `/api/v1/transactions/:id` | Soft delete de transacción y reversión atómica del saldo de cuenta. |

---

### 4.5. Reglas Recurrentes (`/api/v1/scheduled-rules`)

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/scheduled-rules?includeInactive=false` | Lista reglas recurrentes. |
| `POST` | `/api/v1/scheduled-rules` | Crea regla (`default: false`). Body: `{ id?, title, typeId, classificationId, detailId, accountId, merchantName?, amountCents, dayOfMonth (1..31), isEndOfMonth?, isActive? }`. |
| `PATCH` | `/api/v1/scheduled-rules/:id` | Edita regla recurrente. |
| `DELETE` | `/api/v1/scheduled-rules/:id` | Soft delete de regla. |
| `POST` | `/api/v1/scheduled-rules/:id/confirm` | Confirma la regla: crea la transacción, actualiza saldo atómicamente, marca `lastConfirmedPeriodKey` y limpia `snoozedUntilEpochDay`. Body opcional: `{ operationDate?, amountCents?, note? }`. Soporta `Idempotency-Key`. |
| `POST` | `/api/v1/scheduled-rules/:id/snooze` | Pospone la regla (+24h por defecto o `{ snoozedUntilEpochDay }`). |

---

### 4.6. Analítica y Balances (`/api/v1/analytics`)

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/summary?fiscalPeriodKey=YYYY-MM` | Resumen del periodo fiscal (actual si se omite): `totalIncomeCents`, `totalExpenseCents`, `netCashFlowCents`, `totalAvailableBalanceCents`, `topExpenseCategory`, `expenseBreakdown`, `incomeBreakdown` y `budgetAlerts` (`WARNING_80` ≥ 80%, `EXCEEDED_100` ≥ 100%). |

---

### 4.7. Endpoints de Sistema y Administración *(No consumidos por la app móvil)*

| Método | Ruta | Auth | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Pública | Vista HTML de estado del servidor. |
| `GET` | `/health` | Pública | Health check JSON. |
| `GET` | `/docswg` | Desarrollo | Interfaz Swagger UI. |
| `GET` | `/api/v1/openapi.json` | Desarrollo | Especificación OpenAPI 3.1 descargable. |
| `POST` | `/api/v1/catalogs/seed` | `X-Admin-Key` | Sembrado inicial idempotente de catálogos. |
| `POST` | `/api/v1/imports/admin-config` | `X-Admin-Key` | Importación de `mis_finanzas_admin_config.json` (JSON o archivo). |
| `POST` | `/api/v1/imports/captured-excel` | `X-Admin-Key` | Importación histórica de hoja `Captura` (`.xlsx`) con log JSON de no coincidentes. |
| `GET` | `/api/v1/devices` | `X-Admin-Key` | Lista dispositivos enrolados. |
| `POST` | `/api/v1/devices/:id/revoke` | `X-Admin-Key` | Revoca un dispositivo perdido o robado (`403 DEVICE_REVOKED`). |
