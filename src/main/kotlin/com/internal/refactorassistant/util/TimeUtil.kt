package com.internal.refactorassistant.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object TimeUtil {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun nowIso(): String = OffsetDateTime.now(ZoneId.systemDefault()).format(formatter)

    fun newSessionId(): String = UUID.randomUUID().toString()
}
