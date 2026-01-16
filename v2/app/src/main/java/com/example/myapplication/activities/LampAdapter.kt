package com.example.myapplication.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.models.Lamp

class LampAdapter(
    private var lamps: List<Lamp>,
    private val onClick: (Lamp) -> Unit
) : RecyclerView.Adapter<LampAdapter.LampViewHolder>() {

    class LampViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.lampName)
        val status: TextView = view.findViewById(R.id.lampStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LampViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lamp, parent, false)
        return LampViewHolder(view)
    }

    override fun onBindViewHolder(holder: LampViewHolder, position: Int) {
        val lamp = lamps[position]
        holder.name.text = lamp.name
        holder.status.text = if (lamp.isOn) "ON" else "OFF"

        holder.itemView.setOnClickListener {
            onClick(lamp)
        }
    }

    override fun getItemCount() = lamps.size

    fun updateLamps(newLamps: List<Lamp>) {
        lamps = newLamps
        notifyDataSetChanged()
    }
}
