package com.example.myapplication.models

data class Lamp(
    val id: Int,
    val espId: String,
    val name: String,
    var isOn: Boolean,
    val lastSeen: Long = 0,
    val power: Float? = null
)

data class Reading(
    val id: Int,
    val espId: String,
    val timestamp: Long,
    val powerW: Float
)

data class Schedule(
    val id: Int,
    val espId: String,
    val timeHm: String,
    val action: String,
    val days: String,
    val enabled: Boolean,
    val createdAt: Long
)
