package com.example.energyflow.data

/**
 * Java 平台 DateTime（java.time） 与 kotlinx.datetime 之间的互转适配器。
 */

fun java.time.LocalDate.toKtLocalDate(): kotlinx.datetime.LocalDate =
    kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

fun java.time.LocalDateTime.toKtLocalDateTime(): kotlinx.datetime.LocalDateTime =
    kotlinx.datetime.LocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)

fun kotlinx.datetime.LocalDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)

fun kotlinx.datetime.LocalDateTime.toJavaLocalDateTime(): java.time.LocalDateTime =
    java.time.LocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second, nanosecond)
