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
    private lateinit var usageDailyText: TextView
    private lateinit var usageWeeklyText: TextView
    private lateinit var usageMonthlyText: TextView
    private lateinit var usageYearlyText: TextView
    private lateinit var schedulesRecycler: RecyclerView
    private lateinit var addScheduleButton: Button
    private lateinit var scheduleAdapter: ScheduleAdapter
    
    // Filter vars
    private var filterAction: String? = null
    private var filterDay: Int? = null
    private var filterTimeFrom: String? = null
    private var filterTimeTo: String? = null
    private lateinit var filterStatusText: TextView

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
        usageDailyText = findViewById(R.id.usageDailyText)
        usageWeeklyText = findViewById(R.id.usageWeeklyText)
        usageMonthlyText = findViewById(R.id.usageMonthlyText)
        usageYearlyText = findViewById(R.id.usageYearlyText)

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

        // Observe usage stats and update text
        lifecycleScope.launch {
            viewModel.usageStats.collect { stats ->
                if (stats != null) {
                    usageDailyText.text = "Last 24h: ${formatKwh(stats.daily)} kWh"
                    usageWeeklyText.text = "Last 7d: ${formatKwh(stats.weekly)} kWh"
                    usageMonthlyText.text = "Last 30d: ${formatKwh(stats.monthly)} kWh"
                    usageYearlyText.text = "Last 365d: ${formatKwh(stats.yearly)} kWh"
                }
            }
        }

        // Schedules UI
        schedulesRecycler = findViewById(R.id.schedulesRecycler)
        addScheduleButton = findViewById(R.id.addScheduleButton)
        filterStatusText = findViewById(R.id.filterStatusText)

        scheduleAdapter = ScheduleAdapter(
            emptyList(),
            onEdit = { schedule ->
                // Edit schedule
                showEditScheduleDialog(espId, schedule)
            }
        )

        schedulesRecycler.layoutManager = LinearLayoutManager(this)
        schedulesRecycler.adapter = scheduleAdapter

        // Filter button
        val filterBtn = findViewById<Button>(R.id.filterSchedulesButton)
        val clearFilterBtn = findViewById<Button>(R.id.clearFilterButton)

        filterBtn.setOnClickListener {
            showFilterDialog()
        }

        clearFilterBtn.setOnClickListener {
            filterAction = null
            filterDay = null
            filterTimeFrom = null
            filterTimeTo = null
            filterStatusText.text = ""
            viewModel.reloadSchedules(espId)
        }

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
                        // Ask for days using custom dialog with weekday checkboxes
                        val inflater = layoutInflater
                        val view = inflater.inflate(com.example.myapplication.R.layout.dialog_days, null)

                        val chkSun = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkSun)
                        val chkMon = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkMon)
                        val chkTue = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkTue)
                        val chkWed = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkWed)
                        val chkThu = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkThu)
                        val chkFri = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkFri)
                        val chkSat = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkSat)
                        val chkEvery = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkEvery)

                        // Sync logic: if Every day is checked, check all; if all checked, set Every
                        chkEvery.setOnCheckedChangeListener { _, isChecked ->
                            chkSun.isChecked = isChecked
                            chkMon.isChecked = isChecked
                            chkTue.isChecked = isChecked
                            chkWed.isChecked = isChecked
                            chkThu.isChecked = isChecked
                            chkFri.isChecked = isChecked
                            chkSat.isChecked = isChecked
                        }

                        val anyChangeListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
                            chkEvery.isChecked = chkSun.isChecked && chkMon.isChecked && chkTue.isChecked && chkWed.isChecked && chkThu.isChecked && chkFri.isChecked && chkSat.isChecked
                        }
                        chkSun.setOnCheckedChangeListener(anyChangeListener)
                        chkMon.setOnCheckedChangeListener(anyChangeListener)
                        chkTue.setOnCheckedChangeListener(anyChangeListener)
                        chkWed.setOnCheckedChangeListener(anyChangeListener)
                        chkThu.setOnCheckedChangeListener(anyChangeListener)
                        chkFri.setOnCheckedChangeListener(anyChangeListener)
                        chkSat.setOnCheckedChangeListener(anyChangeListener)

                        AlertDialog.Builder(this)
                            .setTitle("Days")
                            .setView(view)
                            .setPositiveButton("Create") { _, _ ->
                                val selected = mutableListOf<Int>()
                                if (chkSun.isChecked) selected.add(0)
                                if (chkMon.isChecked) selected.add(1)
                                if (chkTue.isChecked) selected.add(2)
                                if (chkWed.isChecked) selected.add(3)
                                if (chkThu.isChecked) selected.add(4)
                                if (chkFri.isChecked) selected.add(5)
                                if (chkSat.isChecked) selected.add(6)

                                val daysValue = if (chkEvery.isChecked || selected.isEmpty()) {
                                    "daily"
                                } else selected.joinToString(",")

                                viewModel.createSchedule(espId, timeHm, selectedAction, daysValue)
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

    private fun formatKwh(value: Float): String {
        return String.format("%.2f", value)
    }

    private fun showFilterDialog() {
        val actions = arrayOf("All", "ON", "OFF")
        val days = arrayOf("All Days", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        
        var selectedAction = if (filterAction == null) 0 else if (filterAction == "on") 1 else 2
        var selectedDay = filterDay?.let { it + 1 } ?: 0

        val filterDialog = AlertDialog.Builder(this)
            .setTitle("Filter Schedules")
            .setSingleChoiceItems(actions, selectedAction) { _, which ->
                selectedAction = which
            }
            .setPositiveButton("Next") { _, _ ->
                val actionDialog = AlertDialog.Builder(this)
                    .setTitle("Select Day")
                    .setSingleChoiceItems(days, selectedDay) { _, which ->
                        selectedDay = which
                    }
                    .setPositiveButton("Apply") { _, _ ->
                        // Apply filter
                        filterAction = when (selectedAction) {
                            0 -> null
                            1 -> "on"
                            else -> "off"
                        }
                        filterDay = if (selectedDay == 0) null else selectedDay - 1

                        // Update filter status text
                        val actionText = when (selectedAction) {
                            0 -> "All"
                            1 -> "ON"
                            else -> "OFF"
                        }
                        val dayText = if (selectedDay == 0) "All" else days[selectedDay]
                        showTimeFilterDialog {
                            val timeText = if (filterTimeFrom.isNullOrBlank() && filterTimeTo.isNullOrBlank()) {
                                "Any time"
                            } else {
                                "${filterTimeFrom ?: "00:00"}-${filterTimeTo ?: "23:59"}"
                            }
                            filterStatusText.text = "Filter: $actionText, $dayText, $timeText"
                            viewModel.filterSchedules(espId, filterAction, filterDay, filterTimeFrom, filterTimeTo)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimeFilterDialog(onApply: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_time_filter, null)
        val fromInput = view.findViewById<EditText>(R.id.timeFromInput)
        val toInput = view.findViewById<EditText>(R.id.timeToInput)

        fromInput.setText(filterTimeFrom ?: "")
        toInput.setText(filterTimeTo ?: "")

        AlertDialog.Builder(this)
            .setTitle("Optional Time Range (HH:MM)")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val fromValue = fromInput.text.toString().trim().ifEmpty { null }
                val toValue = toInput.text.toString().trim().ifEmpty { null }
                filterTimeFrom = fromValue
                filterTimeTo = toValue
                onApply()
            }
            .setNegativeButton("Skip") { _, _ ->
                onApply()
            }
            .show()
    }

    private fun showEditScheduleDialog(espId: String, schedule: Schedule) {
        val now = java.util.Calendar.getInstance()
        val timeParts = schedule.timeHm.split(':')
        val hour = timeParts[0].toIntOrNull() ?: 0
        val minute = timeParts[1].toIntOrNull() ?: 0

        val tp = TimePickerDialog(this, { _, hourOfDay, minute ->
            val timeHm = String.format("%02d:%02d", hourOfDay, minute)

            val actions = arrayOf("on", "off")
            var selectedAction = schedule.action
            val actionIdx = if (selectedAction == "on") 0 else 1

            val actionDialog = AlertDialog.Builder(this)
                .setTitle("Select action")
                .setSingleChoiceItems(actions, actionIdx) { _, which -> selectedAction = actions[which] }
                .setPositiveButton("Next") { d, _ ->
                    d.dismiss()
                    
                    val inflater = layoutInflater
                    val view = inflater.inflate(com.example.myapplication.R.layout.dialog_days, null)

                    val chkSun = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkSun)
                    val chkMon = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkMon)
                    val chkTue = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkTue)
                    val chkWed = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkWed)
                    val chkThu = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkThu)
                    val chkFri = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkFri)
                    val chkSat = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkSat)
                    val chkEvery = view.findViewById<android.widget.CheckBox>(com.example.myapplication.R.id.chkEvery)

                    // Pre-populate with current schedule's days
                    val currentDays = if (schedule.days == "daily") {
                        listOf(0, 1, 2, 3, 4, 5, 6)
                    } else {
                        schedule.days.split(',').mapNotNull { it.trim().toIntOrNull() }
                    }

                    val dayCheckboxes = listOf(chkSun, chkMon, chkTue, chkWed, chkThu, chkFri, chkSat)
                    dayCheckboxes.forEachIndexed { index, checkbox ->
                        checkbox.isChecked = index in currentDays
                    }
                    chkEvery.isChecked = currentDays.size == 7

                    chkEvery.setOnCheckedChangeListener { _, isChecked ->
                        dayCheckboxes.forEach { it.isChecked = isChecked }
                    }

                    val anyChangeListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
                        chkEvery.isChecked = dayCheckboxes.all { it.isChecked }
                    }
                    dayCheckboxes.forEach { it.setOnCheckedChangeListener(anyChangeListener) }

                    AlertDialog.Builder(this)
                        .setTitle("Days")
                        .setView(view)
                        .setPositiveButton("Save") { _, _ ->
                            val selected = mutableListOf<Int>()
                            dayCheckboxes.forEachIndexed { index, checkbox ->
                                if (checkbox.isChecked) selected.add(index)
                            }

                            val daysValue = if (chkEvery.isChecked || selected.isEmpty()) {
                                "daily"
                            } else selected.joinToString(",")

                            viewModel.updateSchedule(espId, schedule.id, timeHm, selectedAction, daysValue)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()

        }, hour, minute, true)

        tp.show()
    }
}
