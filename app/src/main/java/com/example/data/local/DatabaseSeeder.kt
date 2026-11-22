package com.example.data.local

/**
 * Offline-first initial catalog seeder aligned with the official deterministic UUIDv5
 * identifiers from backFinanceApp (/api/v1/catalogs/admin-config).
 */
object DatabaseSeeder {
  suspend fun seedIfEmpty(dao: FinanceDao) {
    if (dao.countTypes() > 0) return
    seedAll(dao)
  }

  suspend fun resetAndReseed(dao: FinanceDao) {
    dao.clearAllData()
    seedAll(dao)
  }

  private suspend fun seedAll(dao: FinanceDao) {
    // 1. Transaction Types (official UUIDv5)
    dao.insertTypes(
      listOf(
        TransactionTypeEntity(id = "6d404c5b-6d59-5589-a36a-d39fc85137aa", code = "GASTO", name = "Gasto", sortOrder = 0, isDefault = true),
        TransactionTypeEntity(id = "e1135a90-1a21-5d64-84ce-700efa2c8d19", code = "INGRESO", name = "Ingreso", sortOrder = 1, isDefault = true),
      )
    )

    // 2. Classifications (official UUIDv5)
    dao.insertClassifications(
      listOf(
        ClassificationEntity(id = "b8edfaa2-2169-5aea-99d7-7f613689d50b", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Bebé", sortOrder = 0, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "100e34a2-f597-59f1-8d51-e33b7ef136e4", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Alimentación", sortOrder = 1, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Servicios", sortOrder = 2, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "5b55cd4d-63ee-5083-9a25-34e05fc1ba6a", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Vivienda", sortOrder = 3, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "63fbee68-c59e-5099-b65e-900335264f06", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Transporte", sortOrder = 4, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "b4e67fb3-6409-58e0-b9d2-9f2bfa52cce5", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Salud", sortOrder = 5, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "d7b6bc90-45ff-55de-9bff-75e8708e720f", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Gasto hormiga", sortOrder = 6, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "952b9766-e72f-5309-a86e-67ae332e704e", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Salidas", sortOrder = 7, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "bdfffcf2-705c-52ee-9c47-654143927220", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Extras", sortOrder = 8, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "f2c2ecee-31f5-5264-a075-9722318081e6", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Entretenimiento", sortOrder = 9, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", name = "Otro", sortOrder = 10, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "e6163e76-8d7c-59bc-9f6a-5394b5003864", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", name = "Nómina", sortOrder = 0, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "4c5cd38e-f982-5db8-9247-4dbe054052be", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", name = "Préstamo", sortOrder = 1, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
        ClassificationEntity(id = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", name = "Otro", sortOrder = 2, isActive = true, monthlyBudgetCents = null, isPinned = false, isDefault = true),
      )
    )

    // 3. Dependent Details (official UUIDv5)
    dao.insertDetails(
      listOf(
        DetailItemEntity(id = "2821132b-e33a-5f4e-aec3-2fd6e0694d41", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Despensa", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "65d20250-35ad-5d6c-b811-e6d646d58d97", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Comida", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "161701c8-6b2b-57dc-93c2-8650e2c01049", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Desayuno", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "68de1673-e179-52df-88b7-765226818cf9", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Cena", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "6d5cf274-12a5-5359-b63e-dd8bf3725727", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Gas", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "67655b97-a9f8-5233-a60d-d2d9d01abed7", classificationId = "100e34a2-f597-59f1-8d51-e33b7ef136e4", name = "Otro", sortOrder = 5, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b66a67ab-c52b-54b5-bac8-030de5061007", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Trámites / Gobierno", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "d5f6cbfd-ec73-57b6-ad04-aabc3c8c590b", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Reparaciones extraordinarias", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "28ca7ebf-42c2-5caa-8101-518cddbf6bc7", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Comisiones bancarias", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "ee645762-d005-5ddf-9813-48b8c4056465", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Compras online", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b07ada65-8570-5f6b-8035-18befa1808ce", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Devolución", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "d4227e64-28fd-592f-a3ef-a690f145e7f9", classificationId = "36bf19f8-c106-5a13-a8f6-a04a165abaa9", name = "Otro", sortOrder = 5, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "1a7cd695-0d56-51a8-8cdd-9462d072390d", classificationId = "4c5cd38e-f982-5db8-9247-4dbe054052be", name = "Cobro / Devolución de préstamo", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "99bb9c1f-e3b2-5cda-b300-4d668f4ad3d7", classificationId = "4c5cd38e-f982-5db8-9247-4dbe054052be", name = "Préstamo recibido", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "87c7cbaf-639c-52ec-9441-8f4f2b959d35", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", name = "Internet", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "ce17b1dd-d1c1-5cd6-a1fc-ddafa40f1c85", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", name = "Recargas celular", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "c6daab4d-3af7-5a48-8d99-c599682e9f38", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", name = "Streaming Netflix", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "9055cbec-13d3-52ae-8ece-014287ee0f42", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", name = "Suscripciones IA", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "3e9b49ec-a132-5b40-86b9-68ad56c7225b", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", name = "Otro", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "a7fd038f-4a7b-57e7-a40f-c1f59d2bbacc", classificationId = "5b55cd4d-63ee-5083-9a25-34e05fc1ba6a", name = "Renta / Depósito", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "563fba30-5648-536d-a1f5-216e971b4888", classificationId = "5b55cd4d-63ee-5083-9a25-34e05fc1ba6a", name = "Accesorios / Hogar", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b18e7454-7577-5706-bd5c-bad9b01445ba", classificationId = "5b55cd4d-63ee-5083-9a25-34e05fc1ba6a", name = "Despensa / Insumos", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "a70cd497-5805-5770-80ff-10cdeeb216b7", classificationId = "5b55cd4d-63ee-5083-9a25-34e05fc1ba6a", name = "Mantenimiento / Otro", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "68940745-3772-5850-a96a-30016912a22d", classificationId = "63fbee68-c59e-5099-b65e-900335264f06", name = "Gasolina", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b546ddb5-77b5-5ca1-86df-74a665e9f075", classificationId = "63fbee68-c59e-5099-b65e-900335264f06", name = "Pasaje", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "a6d3093b-fd4a-5115-8b06-29d3bfdcf128", classificationId = "63fbee68-c59e-5099-b65e-900335264f06", name = "Taxi", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "70a440ae-533b-5dd0-a25e-8781e1303382", classificationId = "63fbee68-c59e-5099-b65e-900335264f06", name = "Pochi", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "cbf91ed6-54e8-56a2-885c-852865da2dc7", classificationId = "63fbee68-c59e-5099-b65e-900335264f06", name = "Mantenimiento / Otro", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "659e2b56-c2fb-5708-aeb5-484d86d91c73", classificationId = "952b9766-e72f-5309-a86e-67ae332e704e", name = "Ropa / Calzado", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "e259bb95-03e0-59ad-990e-a12c0ce78f7d", classificationId = "952b9766-e72f-5309-a86e-67ae332e704e", name = "Accesorios", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "dd44a649-dca4-5704-a265-531c00a536ab", classificationId = "952b9766-e72f-5309-a86e-67ae332e704e", name = "Restaurantes / Comida fuera", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "e1fc21a1-d361-5899-8c6f-a5735c587510", classificationId = "952b9766-e72f-5309-a86e-67ae332e704e", name = "Entretenimiento", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "33f7074c-d3b3-53d8-a512-c00802699294", classificationId = "952b9766-e72f-5309-a86e-67ae332e704e", name = "Otro", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "9c11e100-8922-59de-b577-e8128a74367f", classificationId = "b4e67fb3-6409-58e0-b9d2-9f2bfa52cce5", name = "Medicamento", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "7c69a495-5ab2-5f3f-8fc6-a636cfae2d7f", classificationId = "b4e67fb3-6409-58e0-b9d2-9f2bfa52cce5", name = "Consultas médicas", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "77cd9c8c-0572-5325-9dc4-7e093efc025c", classificationId = "b4e67fb3-6409-58e0-b9d2-9f2bfa52cce5", name = "Insumos médicos", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "4a549f62-53e9-5858-8b7b-0aed7e9021ed", classificationId = "b4e67fb3-6409-58e0-b9d2-9f2bfa52cce5", name = "Otro", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "7e0cc79d-abe9-5c14-b697-f89e2c277648", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Leche", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "fe047975-14c4-5127-a6b8-062232ab57bd", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Pañal", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "f7099e94-b133-56fc-81a7-178f3238c7f0", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Pediatra", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "4edb4482-3afa-5134-8103-41957f288666", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Medicamento", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "16daa5b4-592e-5d79-ab1d-5c8a0e82b33c", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Ropa", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "03f68641-6135-5144-b71c-b2d0fdd88312", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Zapato", sortOrder = 5, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "5d681949-0dc0-526e-aeb9-ed159a30eebf", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Carriola / Accesorios", sortOrder = 6, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "3462a739-978c-51c0-bed8-3fee42136e34", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Andadera", sortOrder = 7, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "a73ffdf7-88b3-5637-a504-ec83ef467635", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Parto / Hospitalario", sortOrder = 8, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b77ee4c6-ba01-58c4-a61e-64e548800f6c", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Devolución", sortOrder = 9, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "31dc591c-14c6-57ed-9fc3-b948077889c9", classificationId = "b8edfaa2-2169-5aea-99d7-7f613689d50b", name = "Otro", sortOrder = 10, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "5f7f2f5c-283d-5c8b-bbd3-936f3ba0746e", classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", name = "Vales de despensa", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "e8a218bf-9279-54bd-8d39-5d9eda43dd43", classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", name = "Aguinaldo", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "9691e4e3-5161-56fd-84ae-a057e2543374", classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", name = "Rendimientos / FinTech", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "57af565f-e952-5b2d-b748-e910a11dedef", classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", name = "Reintegros / Devoluciones", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = UuidV5.fromName("seed:detail:INGRESO:Otro:Retiro"), classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", name = "Retiro", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "5bcd2862-e55c-59de-beea-82bf9601d2da", classificationId = "bdfffcf2-705c-52ee-9c47-654143927220", name = "Cuidado personal", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "6ac581a4-3e94-55e3-8189-a2cd6fb8eb3e", classificationId = "bdfffcf2-705c-52ee-9c47-654143927220", name = "Fondo de ahorro", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "b675d771-c13b-598c-8ae6-8a886d484d18", classificationId = "bdfffcf2-705c-52ee-9c47-654143927220", name = "Otro", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "a1ee98de-1974-5c4b-a248-ba48c490db0e", classificationId = "d7b6bc90-45ff-55de-9bff-75e8708e720f", name = "Comida / Botanas", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "ac30f8e9-ef80-5bee-baaa-b4c0ea721fd3", classificationId = "d7b6bc90-45ff-55de-9bff-75e8708e720f", name = "Bebidas / Antojos", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "64db9e0d-9176-5040-8141-de610adcefd4", classificationId = "d7b6bc90-45ff-55de-9bff-75e8708e720f", name = "Otro", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "d7e8299a-0187-5856-a82c-ad220d069ab1", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", name = "Anticipo", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "9ab72502-e809-533e-aea0-79d89abcd433", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", name = "Nómina", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "3a857780-7f9c-5c20-adf7-0644bb4743c1", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", name = "PTU", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "4ec10238-faf8-5026-91a4-4efae3de33e2", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", name = "Prima Vacacional", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "5b1bf1af-9805-5833-8330-bd6aa8f249cf", classificationId = "f2c2ecee-31f5-5264-a075-9722318081e6", name = "Servicios digitales / Cloud", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "c4cca899-439f-5348-b174-d279e7637b0b", classificationId = "f2c2ecee-31f5-5264-a075-9722318081e6", name = "Salidas recreativas", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        DetailItemEntity(id = "296ffd97-c11e-5467-839d-6825c96a3723", classificationId = "f2c2ecee-31f5-5264-a075-9722318081e6", name = "Otro", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
      )
    )

    // 4. Accounts / Payment Methods (official UUIDv5, initial offline balance $0.00)
    dao.insertAccounts(
      listOf(
        AccountEntity(id = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554", name = "Tarjeta de Débito (BBVA)", shortLabel = "BBVA Débito", balanceCents = 0L, currency = "MXN", accountKind = "DEBIT", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        AccountEntity(id = "adfa1b53-ff67-5615-9d14-ceed4223db9d", name = "Efectivo", shortLabel = "Efectivo", balanceCents = 0L, currency = "MXN", accountKind = "CASH", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        AccountEntity(id = "bf2bee3c-84c2-5ae2-8413-2ad64a83b109", name = "Tarjeta de Vales de Despensa", shortLabel = "Vales", balanceCents = 0L, currency = "MXN", accountKind = "VALES", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        AccountEntity(id = "f5244485-9daf-5411-962a-7423ab0727d8", name = "Tarjeta de Crédito", shortLabel = "Crédito", balanceCents = 0L, currency = "MXN", accountKind = "CREDIT", sortOrder = 3, isActive = false, isPinned = false, isDefault = true),
        AccountEntity(id = "e3085b29-e742-5c46-9ace-a15ea9dbc004", name = "Cuenta Inversión / FinTech (Doopla)", shortLabel = "Doopla", balanceCents = 0L, currency = "MXN", accountKind = "FINTECH", sortOrder = 4, isActive = false, isPinned = false, isDefault = true),
      )
    )

    // 5. Frequent Merchants (official UUIDv5)
    dao.insertMerchants(
      listOf(
        MerchantEntity(id = "a51e7a9d-f835-5cd8-99da-268dbe62676e", name = "Chedraui", sortOrder = 0, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "0138f9d3-d8a1-5c09-96fc-c27c0c7a504d", name = "Bodega Aurrera", sortOrder = 1, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "3dacc821-dcb1-5686-ba92-6b06127eb7c8", name = "Soriana", sortOrder = 2, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "bbe6a03c-b169-5e5d-b5b4-1802d93bbc31", name = "Liverpool", sortOrder = 3, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "aa1df6ae-0efd-5ae5-9529-ca309133343b", name = "Coppel", sortOrder = 4, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "a0a698c9-b8d5-520e-a39d-9caf87325e3d", name = "Farmacias Guadalajara", sortOrder = 5, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "2aeb36c4-66d6-5e62-823a-c3b2047f0da5", name = "Farmacias Iza", sortOrder = 6, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "38f91a29-fd6e-5a01-a3ea-561b30285ade", name = "Netflix", sortOrder = 7, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "55891a5c-2e86-5585-a24d-95917466fc9a", name = "Google IA", sortOrder = 8, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "78b481a6-620d-5342-b196-c0a9705fd09a", name = "Google Cloud", sortOrder = 9, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "a5e3f69e-df77-5cfc-b595-df55ee136a47", name = "MercadoLibre", sortOrder = 10, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "908c0445-3c52-5d46-8826-7fc238545cc0", name = "BBVA", sortOrder = 11, isActive = true, isPinned = false, isDefault = true),
        MerchantEntity(id = "1b4a6871-ba56-5ca1-93bc-38a698c17a50", name = "GOB ESTADO", sortOrder = 12, isActive = true, isPinned = false, isDefault = true),
      )
    )

    // 6. Scheduled Rules (official UUIDv5)
    dao.insertScheduledRules(
      listOf(
        ScheduledRuleEntity(id = "fcd582ad-7d31-5792-a251-614779ecd9b2", title = "Netflix", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", detailId = "c6daab4d-3af7-5a48-8d99-c599682e9f38", accountId = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554", merchantName = "Netflix", amountCents = 26900L, dayOfMonth = 1, isEndOfMonth = false, isActive = true, isDefault = true),
        ScheduledRuleEntity(id = "30460c9e-4e84-5111-8454-c61a69463f41", title = "Internet - Fast", typeId = "6d404c5b-6d59-5589-a36a-d39fc85137aa", classificationId = "59a34ad0-34e2-5aef-a3c2-060a5aabcafe", detailId = "87c7cbaf-639c-52ec-9441-8f4f2b959d35", accountId = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554", merchantName = null, amountCents = 34000L, dayOfMonth = 10, isEndOfMonth = false, isActive = true, isDefault = true),
        ScheduledRuleEntity(id = "d83b0f9d-b8c1-5d8c-8278-462c8de79aea", title = "Anticipo Nómina", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", detailId = "d7e8299a-0187-5856-a82c-ad220d069ab1", accountId = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554", merchantName = "Empleador", amountCents = 660000L, dayOfMonth = 15, isEndOfMonth = false, isActive = true, isDefault = true),
        ScheduledRuleEntity(id = "0af69158-035e-56fb-91da-3c0aa59e7c73", title = "Nómina Fin de Mes", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", classificationId = "e6163e76-8d7c-59bc-9f6a-5394b5003864", detailId = "9ab72502-e809-533e-aea0-79d89abcd433", accountId = "c0ffd56e-8ffa-59bf-9d4b-3c07acf21554", merchantName = "Empleador", amountCents = 1620000L, dayOfMonth = 31, isEndOfMonth = true, isActive = true, isDefault = true),
        ScheduledRuleEntity(id = "7b14e9d2-94c8-5f4e-80b8-5609ef03013c", title = "Vales de despensa", typeId = "e1135a90-1a21-5d64-84ce-700efa2c8d19", classificationId = "bd1b8947-4dcb-5aa7-8691-a3ddcbb06d7b", detailId = "5f7f2f5c-283d-5c8b-bbd3-936f3ba0746e", accountId = "bf2bee3c-84c2-5ae2-8413-2ad64a83b109", merchantName = "Empleador", amountCents = 68940L, dayOfMonth = 31, isEndOfMonth = true, isActive = true, isDefault = true),
      )
    )
  }
}
