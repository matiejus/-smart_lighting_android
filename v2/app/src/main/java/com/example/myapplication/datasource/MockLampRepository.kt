//package com.example.myapplication.datasource
//
//import com.example.myapplication.models.Lamp
//import com.example.myapplication.models.UsageStats
//
//object MockLampRepository {
//
//    private val lamps = mutableListOf(
//        Lamp(1, "Living Room", true),
//        Lamp(2, "Kitchen", false),
//        Lamp(3, "Bedroom", false)
//    )
//
//    fun getLamps(): List<Lamp> = lamps
//
//    fun getLamp(id: Int): Lamp? =
//        lamps.find { it.id == id }
//
//    fun toggleLamp(id: Int) {
//        val lamp = lamps.find { it.id == id }
//        lamp?.isOn = !(lamp.isOn)
//    }
//
//    fun getUsageStats(id: Int): UsageStats {
//        return UsageStats(
//            daily = 1.4f,
//            weekly = 9.2f,
//            monthly = 37.8f
//        )
//    }
//}
