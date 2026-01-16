package com.example.myapplication.datasource

import com.example.myapplication.api.LampApi
import com.example.myapplication.api.PowerRequest
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.ScheduleFilterRequest
import com.example.myapplication.api.ScheduleRequest
import com.example.myapplication.api.StateRequest
import com.example.myapplication.models.Lamp
import com.example.myapplication.models.Reading
import com.example.myapplication.models.Schedule
import com.example.myapplication.models.UsageStats
import org.json.JSONObject
import retrofit2.HttpException

class LampRepository {
    private val api: LampApi = RetrofitClient.getLampApi()

    private fun toSchedule(schedule: com.example.myapplication.api.ScheduleResponse): Schedule {
        return Schedule(
            id = schedule.id,
            espId = schedule.esp_id,
            timeHm = schedule.time_hm,
            action = schedule.action,
            days = schedule.days,
            enabled = schedule.enabled == 1,
            createdAt = schedule.created_at
        )
    }

    private fun parseErrorMessage(e: HttpException): String? {
        val body = e.response()?.errorBody()?.string() ?: return null
        return try {
            val message = JSONObject(body).optString("error")
            message.ifBlank { null }
        } catch (ex: Exception) {
            null
        }
    }

    suspend fun getLamps(): List<Lamp> {
        return try {
            android.util.Log.d("LampRepository", "Calling api.getDevices()")
            val devices = api.getDevices()
            android.util.Log.d("LampRepository", "Got ${devices.size} devices from API")
            devices.mapIndexed { index, device ->
                Lamp(
                    id = index,
                    espId = device.esp_id,
                    name = device.name ?: "Device ${device.esp_id}",
                    isOn = device.state == 1,
                    lastSeen = device.last_seen,
                    power = device.watth
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LampRepository", "Error fetching devices", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getLamp(espId: String): Lamp? {
        return try {
            val devices = api.getDevices()
            val device = devices.find { it.esp_id == espId }
            device?.let {
                Lamp(
                    id = devices.indexOf(device),
                    espId = it.esp_id,
                    name = it.name ?: "Device ${it.esp_id}",
                    isOn = it.state == 1,
                    lastSeen = it.last_seen,
                    power = it.watth
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun toggleLamp(espId: String): Boolean {
        return try {
            val device = api.getDevices().find { it.esp_id == espId }
            if (device != null) {
                val newState = if (device.state == 1) 0 else 1
                val response = api.toggleRelay(espId, StateRequest(newState))
                response.ok == true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun setPower(espId: String, watts: Float): Boolean {
        return try {
            val response = api.setPower(espId, PowerRequest(watts))
            response.ok == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getReadings(espId: String): List<Reading> {
        return try {
            api.getReadings(espId).map { reading ->
                Reading(
                    id = reading.id,
                    espId = reading.esp_id,
                    timestamp = reading.timestamp,
                    powerW = reading.power_w
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getUsageStats(espId: String): UsageStats {
        return try {
            val readings = getReadings(espId)
            
            if (readings.isEmpty()) {
                return UsageStats(daily = 0f, weekly = 0f, monthly = 0f, yearly = 0f)
            }

            val now = System.currentTimeMillis() / 1000
            val oneDayAgo = now - (24 * 60 * 60)
            val sevenDaysAgo = now - (7 * 24 * 60 * 60)
            val thirtyDaysAgo = now - (30 * 24 * 60 * 60)
            val oneYearAgo = now - (365 * 24 * 60 * 60)

            val dailyReadings = readings.filter { it.timestamp >= oneDayAgo }
            val weeklyReadings = readings.filter { it.timestamp >= sevenDaysAgo }
            val monthlyReadings = readings.filter { it.timestamp >= thirtyDaysAgo }
            val yearlyReadings = readings.filter { it.timestamp >= oneYearAgo }

            val daily = dailyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000 // Convert to kWh
            val weekly = weeklyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000
            val monthly = monthlyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000
            val yearly = yearlyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000

            UsageStats(
                daily = daily,
                weekly = weekly,
                monthly = monthly,
                yearly = yearly
            )
        } catch (e: Exception) {
            e.printStackTrace()
            UsageStats(daily = 0f, weekly = 0f, monthly = 0f, yearly = 0f)
        }
    }

    suspend fun getSchedules(espId: String): List<Schedule> {
        return try {
            api.getSchedules(espId).map { schedule -> toSchedule(schedule) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createSchedule(
        espId: String,
        timeHm: String,
        action: String,
        days: String = "daily"
    ): Pair<Boolean, String?> {
        return try {
            val response = api.createSchedule(
                espId,
                ScheduleRequest(timeHm, action, days)
            )
            if (response.ok == true) {
                Pair(true, null)
            } else {
                Pair(false, response.error ?: "Failed to create schedule")
            }
        } catch (e: HttpException) {
            Pair(false, parseErrorMessage(e) ?: "Failed to create schedule")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Error creating schedule")
        }
    }

    suspend fun updateSchedule(
        scheduleId: Int,
        timeHm: String,
        action: String,
        days: String = "daily"
    ): Pair<Boolean, String?> {
        return try {
            val response = api.updateSchedule(
                scheduleId,
                ScheduleRequest(timeHm, action, days)
            )
            if (response.ok == true) {
                Pair(true, null)
            } else {
                Pair(false, response.error ?: "Failed to update schedule")
            }
        } catch (e: HttpException) {
            Pair(false, parseErrorMessage(e) ?: "Failed to update schedule")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Error updating schedule")
        }
    }

    suspend fun deleteSchedule(scheduleId: Int): Pair<Boolean, String?> {
        return try {
            val response = api.deleteSchedule(scheduleId)
            if (response.ok == true) {
                Pair(true, null)
            } else {
                Pair(false, response.error ?: "Failed to delete schedule")
            }
        } catch (e: HttpException) {
            Pair(false, parseErrorMessage(e) ?: "Failed to delete schedule")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Error deleting schedule")
        }
    }

    suspend fun filterSchedules(
        espId: String,
        action: String?,
        day: Int?,
        timeFrom: String?,
        timeTo: String?
    ): List<Schedule> {
        return try {
            val request = ScheduleFilterRequest(
                timefilterfrom = timeFrom,
                timefilterto = timeTo,
                dayfilter = day?.toString(),
                actionfilter = action
            )
            api.filterSchedules(espId, request).map { schedule -> toSchedule(schedule) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: LampRepository? = null

        fun getInstance(): LampRepository =
            instance ?: synchronized(this) {
                instance ?: LampRepository().also { instance = it }
            }
    }
}
