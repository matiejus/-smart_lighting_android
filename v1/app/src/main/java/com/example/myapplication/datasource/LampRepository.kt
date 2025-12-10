package com.example.myapplication.datasource

import com.example.myapplication.api.LampApi
import com.example.myapplication.api.PowerRequest
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.ScheduleRequest
import com.example.myapplication.api.StateRequest
import com.example.myapplication.models.Lamp
import com.example.myapplication.models.Reading
import com.example.myapplication.models.Schedule
import com.example.myapplication.models.UsageStats

class LampRepository {
    private val api: LampApi = RetrofitClient.getLampApi()

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
                return UsageStats(daily = 0f, weekly = 0f, monthly = 0f)
            }

            val now = System.currentTimeMillis() / 1000
            val oneDayAgo = now - (24 * 60 * 60)
            val sevenDaysAgo = now - (7 * 24 * 60 * 60)
            val thirtyDaysAgo = now - (30 * 24 * 60 * 60)

            val dailyReadings = readings.filter { it.timestamp >= oneDayAgo }
            val weeklyReadings = readings.filter { it.timestamp >= sevenDaysAgo }
            val monthlyReadings = readings.filter { it.timestamp >= thirtyDaysAgo }

            val daily = dailyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000 // Convert to kWh
            val weekly = weeklyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000
            val monthly = monthlyReadings.sumOf { it.powerW.toDouble() }.toFloat() / 1000

            UsageStats(
                daily = daily,
                weekly = weekly,
                monthly = monthly
            )
        } catch (e: Exception) {
            e.printStackTrace()
            UsageStats(daily = 0f, weekly = 0f, monthly = 0f)
        }
    }

    suspend fun getSchedules(espId: String): List<Schedule> {
        return try {
            api.getSchedules(espId).map { schedule ->
                Schedule(
                    id = schedule.id,
                    espId = schedule.esp_id,
                    timeHm = schedule.time_hm,
                    action = schedule.action,
                    days = schedule.days,
                    enabled = schedule.enabled == 1,
                    createdAt = schedule.created_at
                )
            }
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
    ): Boolean {
        return try {
            val response = api.createSchedule(
                espId,
                ScheduleRequest(timeHm, action, days)
            )
            response.ok == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteSchedule(espId: String, scheduleId: Int): Boolean {
        return try {
            val response = api.deleteSchedule(espId, scheduleId)
            response.ok == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
