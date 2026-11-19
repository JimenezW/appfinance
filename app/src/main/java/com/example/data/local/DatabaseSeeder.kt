package com.example.data.local

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
    // 1. Transaction Types
    dao.insertTypes(
      listOf(
        TransactionTypeEntity(id = TransactionTypes.GASTO, name = "Gasto", sortOrder = 0),
        TransactionTypeEntity(id = TransactionTypes.INGRESO, name = "Ingreso", sortOrder = 1),
      )
    )

    // 2. Classifications & 3. Dependent Details (exact seed data from specification document)
    val expenseCatalog: List<Pair<String, List<String>>> =
      listOf(
        "Bebé" to
          listOf(
            "Leche",
            "Pañal",
            "Pediatra",
            "Medicamento",
            "Ropa",
            "Zapato",
            "Carriola / Accesorios",
            "Andadera",
            "Parto / Hospitalario",
            "Devolución",
            "Otro",
          ),
        "Alimentación" to listOf("Despensa", "Comida", "Desayuno", "Cena", "Gas", "Otro"),
        "Servicios" to
          listOf("Internet", "Recargas celular", "Streaming Netflix", "Suscripciones IA", "Otro"),
        "Vivienda" to
          listOf("Renta / Depósito", "Accesorios / Hogar", "Despensa / Insumos", "Mantenimiento / Otro"),
        "Transporte" to listOf("Gasolina", "Pasaje", "Taxi", "Pochi", "Mantenimiento / Otro"),
        "Salud" to listOf("Medicamento", "Consultas médicas", "Insumos médicos", "Otro"),
        "Gasto hormiga" to listOf("Comida / Botanas", "Bebidas / Antojos", "Otro"),
        "Salidas" to
          listOf("Ropa / Calzado", "Accesorios", "Restaurantes / Comida fuera", "Entretenimiento", "Otro"),
        "Extras" to listOf("Cuidado personal", "Fondo de ahorro", "Otro"),
        "Entretenimiento" to listOf("Servicios digitales / Cloud", "Salidas recreativas", "Otro"),
        "Otro" to
          listOf(
            "Trámites / Gobierno",
            "Reparaciones extraordinarias",
            "Comisiones bancarias",
            "Compras online",
            "Devolución",
            "Otro",
          ),
      )

    val incomeCatalog: List<Pair<String, List<String>>> =
      listOf(
        "Nómina" to listOf("Anticipo", "Nómina", "PTU", "Prima Vacacional"),
        "Préstamo" to listOf("Cobro / Devolución de préstamo", "Préstamo recibido"),
        "Otro" to
          listOf(
            "Vales de despensa",
            "Aguinaldo",
            "Rendimientos / FinTech",
            "Reintegros / Devoluciones",
          ),
      )

    val classificationIdMap = mutableMapOf<String, Long>()
    val detailIdMap = mutableMapOf<String, Long>()

    expenseCatalog.forEachIndexed { idx, (className, details) ->
      val classId =
        dao.insertClassification(
          ClassificationEntity(
            typeId = TransactionTypes.GASTO,
            name = className,
            sortOrder = idx,
            isActive = true,
          )
        )
      classificationIdMap["GASTO:$className"] = classId
      details.forEachIndexed { dIdx, detailName ->
        val dId =
          dao.insertDetail(
            DetailItemEntity(
              classificationId = classId,
              name = detailName,
              sortOrder = dIdx,
              isActive = true,
            )
          )
        detailIdMap["GASTO:$className:$detailName"] = dId
      }
    }

    incomeCatalog.forEachIndexed { idx, (className, details) ->
      val classId =
        dao.insertClassification(
          ClassificationEntity(
            typeId = TransactionTypes.INGRESO,
            name = className,
            sortOrder = idx,
            isActive = true,
          )
        )
      classificationIdMap["INGRESO:$className"] = classId
      details.forEachIndexed { dIdx, detailName ->
        val dId =
          dao.insertDetail(
            DetailItemEntity(
              classificationId = classId,
              name = detailName,
              sortOrder = dIdx,
              isActive = true,
            )
          )
        detailIdMap["INGRESO:$className:$detailName"] = dId
      }
    }

    // 4. Accounts / Payment Methods (initial balance $0.00, editable anytime)
    val bbvaId =
      dao.insertAccount(
        AccountEntity(
          name = "Tarjeta de Débito (BBVA)",
          shortLabel = "BBVA Débito",
          balanceCents = 0L,
          accountKind = "DEBIT",
          sortOrder = 0,
        )
      )
    dao.insertAccount(
      AccountEntity(
        name = "Efectivo",
        shortLabel = "Efectivo",
        balanceCents = 0L,
        accountKind = "CASH",
        sortOrder = 1,
      )
    )
    val valesId =
      dao.insertAccount(
        AccountEntity(
          name = "Tarjeta de Vales de Despensa",
          shortLabel = "Vales",
          balanceCents = 0L,
          accountKind = "VALES",
          sortOrder = 2,
        )
      )
    dao.insertAccount(
      AccountEntity(
        name = "Tarjeta de Crédito",
        shortLabel = "Crédito",
        balanceCents = 0L,
        accountKind = "CREDIT",
        sortOrder = 3,
      )
    )
    dao.insertAccount(
      AccountEntity(
        name = "Cuenta Inversión / FinTech (Doopla)",
        shortLabel = "Doopla",
        balanceCents = 0L,
        accountKind = "FINTECH",
        sortOrder = 4,
      )
    )

    // 5. Frequent Merchants
    val merchants =
      listOf(
        "Chedraui",
        "Bodega Aurrera",
        "Soriana",
        "Liverpool",
        "Coppel",
        "Farmacias Guadalajara",
        "Farmacias Iza",
        "Netflix",
        "Google IA",
        "Google Cloud",
        "MercadoLibre",
        "BBVA",
        "GOB ESTADO",
      )
    dao.insertMerchants(
      merchants.mapIndexed { idx, name ->
        MerchantEntity(name = name, sortOrder = idx, isActive = true)
      }
    )

    // 6. Scheduled Rules
    val internetClass = classificationIdMap["GASTO:Servicios"] ?: 1L
    val internetDetail = detailIdMap["GASTO:Servicios:Internet"] ?: 1L
    val netflixDetail = detailIdMap["GASTO:Servicios:Streaming Netflix"] ?: 1L
    val nominaClass = classificationIdMap["INGRESO:Nómina"] ?: 1L
    val anticipoDetail = detailIdMap["INGRESO:Nómina:Anticipo"] ?: 1L
    val nominaDetail = detailIdMap["INGRESO:Nómina:Nómina"] ?: 1L
    val otroIngClass = classificationIdMap["INGRESO:Otro"] ?: 1L
    val valesDetail = detailIdMap["INGRESO:Otro:Vales de despensa"] ?: 1L

    dao.insertScheduledRules(
      listOf(
        ScheduledRuleEntity(
          title = "Netflix",
          typeId = TransactionTypes.GASTO,
          classificationId = internetClass,
          detailId = netflixDetail,
          accountId = bbvaId,
          merchantName = "Netflix",
          amountCents = 26900L,
          dayOfMonth = 1,
          isEndOfMonth = false,
        ),
        ScheduledRuleEntity(
          title = "Internet",
          typeId = TransactionTypes.GASTO,
          classificationId = internetClass,
          detailId = internetDetail,
          accountId = bbvaId,
          merchantName = null,
          amountCents = 34000L,
          dayOfMonth = 14,
          isEndOfMonth = false,
        ),
        ScheduledRuleEntity(
          title = "Anticipo Nómina",
          typeId = TransactionTypes.INGRESO,
          classificationId = nominaClass,
          detailId = anticipoDetail,
          accountId = bbvaId,
          merchantName = "Empleador",
          amountCents = 660000L,
          dayOfMonth = 15,
          isEndOfMonth = false,
        ),
        ScheduledRuleEntity(
          title = "Vales de despensa",
          typeId = TransactionTypes.INGRESO,
          classificationId = otroIngClass,
          detailId = valesDetail,
          accountId = valesId,
          merchantName = "Empleador",
          amountCents = 68940L,
          dayOfMonth = 31,
          isEndOfMonth = true,
        ),
        ScheduledRuleEntity(
          title = "Nómina Fin de Mes",
          typeId = TransactionTypes.INGRESO,
          classificationId = nominaClass,
          detailId = nominaDetail,
          accountId = bbvaId,
          merchantName = "Empleador",
          amountCents = 1620000L,
          dayOfMonth = 31,
          isEndOfMonth = true,
        ),
      )
    )
  }
}
