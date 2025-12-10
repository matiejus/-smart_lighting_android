package com.example.myapplication.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.ui.EnergyChart
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.TimePickerDialog
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.models.Schedule
import com.example.myapplication.activities.ScheduleAdapter
import com.example.myapplication.viewmodels.LampDetailViewModel
import kotlinx.coroutines.launch

class LampDetailActivity : AppCompatActivity() {

    private var espId = ""
    private lateinit var viewModel: LampDetailViewModel

    private lateinit var name: TextView
    private lateinit var status: TextView
    private lateinit var toggleBtn: Button
    private lateinit var chart: EnergyChart
    private lateinit var schedulesRecycler: RecyclerView
    private lateinit var addScheduleButton: Button
    private lateinit var scheduleAdapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lamp_detail)

        espId = intent.getStringExtra("lamp_esp_id") ?: ""
        
        if (espId.isEmpty()) {
            Toast.makeText(this, "Device ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        name = findViewById(R.id.detailLampName)
        status = findViewById(R.id.detailLampStatus)
        toggleBtn = findViewById(R.id.toggleButton)
        chart = findViewById(R.id.readingsChart)

        viewModel = ViewModelProvider(this).get(LampDetailViewModel::class.java)
        viewModel.loadLamp(espId)

        // Observe lamp data
        lifecycleScope.launch {
            viewModel.lamp.collect { lamp ->
                if (lamp != null) {
                    name.text = lamp.name
                    status.text = if (lamp.isOn) "ON" else "OFF"
                }
            }
        }

        // Observe readings and update chart
        lifecycleScope.launch {
            viewModel.readings.collect { readings ->
                if (readings.isNotEmpty()) {
                    chart.setData(readings.takeLast(10)) // Show last 10 readings
                }
            }
        }

        // Schedules UI
        schedulesRecycler = findViewById(R.id.schedulesRecycler)
        addScheduleButton = findViewById(R.id.addScheduleButton)

        scheduleAdapter = ScheduleAdapter(emptyList()) { schedule ->
            // Delete schedule
            viewModel.deleteSchedule(espId, schedule.id)
        }

        schedulesRecycler.layoutManager = LinearLayoutManager(this)
        schedulesRecycler.adapter = scheduleAdapter

        // Observe errors
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null) {
                    Toast.makeText(this@LampDetailActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        toggleBtn.setOnClickListener {
            viewModel.lamp.value?.let { lamp ->
                viewModel.toggleLamp(lamp)
            }
        }

        // Observe schedules and update list
        lifecycleScope.launch {
            viewModel.schedules.collect { schedules ->
                scheduleAdapter.updateSchedules(schedules)
            }
        }

        addScheduleButton.setOnClickListener {
            // Show time picker then details dialog
            val now = java.util.Calendar.getInstance()
            val tp = TimePickerDialog(this, { _, hourOfDay, minute ->
                val timeHm = String.format("%02d:%02d", hourOfDay, minute)

                // After time chosen, ask for action and days
                val actions = arrayOf("on", "off")
                var selectedAction = "on"
                val actionDialog = AlertDialog.Builder(this)
                    .setTitle("Select action")
                    .setSingleChoiceItems(actions, 0) { _, which -> selectedAction = actions[which] }
                    .setPositiveButton("Next") { d, _ ->
                        d.dismiss()
                        // Ask for days (simple input)
                        val input = EditText(this)
                        input.hint = "daily or 0,1,2 (comma-separated)"
                        input.setText("daily")
                        AlertDialog.Builder(this)
                            .setTitle("Days")
                            .setView(input)
                            .setPositiveButton("Create") { _, _ ->
                                val days = input.text.toString().ifBlank { "daily" }
                                viewModel.createSchedule(espId, timeHm, selectedAction, days)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("Cancel", null)
                actionDialog.show()

            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true)

            tp.show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLamp(espId)
    }
}
