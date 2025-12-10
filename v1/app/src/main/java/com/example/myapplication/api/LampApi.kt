package com.example.myapplication.api

import retrofit2.http.*

data class DeviceResponse(
    val esp_id: String,
    val name: String,
    val last_seen: Long,
    val state: Int,
    val watth: Float?
)

data class ReadingResponse(
    val id: Int,
    val esp_id: String,
    val timestamp: Long,
    val power_w: Float
)

data class ScheduleResponse(
    val id: Int,
    val esp_id: String,
    val time_hm: String,
    val action: String,
    val days: String,
    val enabled: Int,
    val created_at: Long
)

data class StateRequest(
    val state: Int
)

data class PowerRequest(
    val wh: Float
)

data class ScheduleRequest(
    val time_hm: String,
    val action: String,
    val days: String = "daily"
)

data class ApiResponse(
    val ok: Boolean? = null,
    val state: Int? = null,
    val wh: Float? = null,
    val error: String? = null
)

interface LampApi {
    // Device endpoints
    @GET("/api/devices")
    suspend fun getDevices(): List<DeviceResponse>

    @GET("/api/devices/{esp_id}/readings")
    suspend fun getReadings(@Path("esp_id") espId: String): List<ReadingResponse>

    @POST("/api/devices/{esp_id}/relay")
    suspend fun toggleRelay(
        @Path("esp_id") espId: String,
        @Body request: StateRequest
    ): ApiResponse

    @POST("/api/devices/{esp_id}/wh")
    suspend fun setPower(
        @Path("esp_id") espId: String,
        @Body request: PowerRequest
    ): ApiResponse

    // Schedule endpoints
    @GET("/api/devices/{esp_id}/schedules")
    suspend fun getSchedules(@Path("esp_id") espId: String): List<ScheduleResponse>

    @POST("/api/devices/{esp_id}/schedules")
    suspend fun createSchedule(
        @Path("esp_id") espId: String,
        @Body request: ScheduleRequest
    ): ApiResponse

    @DELETE("/api/devices/{esp_id}/schedules/{id}")
    suspend fun deleteSchedule(
        @Path("esp_id") espId: String,
        @Path("id") id: Int
    ): ApiResponse

    // ESP endpoints
    @GET("/api/esp/{esp_id}/state")
    suspend fun getDeviceState(@Path("esp_id") espId: String): ApiResponse
}
