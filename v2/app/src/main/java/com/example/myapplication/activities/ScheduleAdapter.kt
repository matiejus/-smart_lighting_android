package com.example.myapplication.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.models.Schedule

class ScheduleAdapter(
    private var schedules: List<Schedule>,
    private val onEdit: (Schedule) -> Unit,
    private val onDelete: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.scheduleText)
        val editBtn: Button = view.findViewById(R.id.editScheduleButton)
        val deleteBtn: Button = view.findViewById(R.id.deleteScheduleButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val s = schedules[position]
        
        val dayNames = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        
        // Convert days string to human-readable format
        val displayDays = when (val d = s.days) {
            is String -> {
                val trimmed = d.trim()
                when {
                    trimmed.equals("daily", ignoreCase = true) -> "Daily"
                    trimmed.isEmpty() -> "Daily"
                    else -> {
                        // Parse comma-separated numbers: "0,1,5" -> "SUN, MON, FRI"
                        trimmed.split(',')
                            .mapNotNull { dayStr ->
                                dayStr.trim().toIntOrNull()?.let { dayNum ->
                                    if (dayNum in 0..6) dayNames[dayNum] else null
                                }
                            }
                            .joinToString(", ")
                    }
                }
            }
            else -> d?.toString() ?: "Daily"
        }

        holder.text.text = "${s.timeHm} ${s.action.uppercase()} ($displayDays)"
        holder.editBtn.setOnClickListener { onEdit(s) }
        holder.deleteBtn.setOnClickListener { onDelete(s) }
    }

    override fun getItemCount() = schedules.size

    fun updateSchedules(newSchedules: List<Schedule>) {
        schedules = newSchedules
        notifyDataSetChanged()
    }

    fun getSchedules(): List<Schedule> = schedules
}
