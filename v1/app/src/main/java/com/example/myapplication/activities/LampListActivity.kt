package com.example.myapplication.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.viewmodels.LampListViewModel
import kotlinx.coroutines.launch

class LampListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: LampAdapter
    private lateinit var viewModel: LampListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lamp_list)

        android.util.Log.d("LampListActivity", "onCreate called")

        recycler = findViewById(R.id.lampRecycler)

        viewModel = ViewModelProvider(this).get(LampListViewModel::class.java)
        android.util.Log.d("LampListActivity", "ViewModel created")

        adapter = LampAdapter(emptyList()) { lamp ->
            val intent = Intent(this, LampDetailActivity::class.java)
            intent.putExtra("lamp_esp_id", lamp.espId)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Observe ViewModel state
        lifecycleScope.launch {
            viewModel.lamps.collect { lamps ->
                android.util.Log.d("LampListActivity", "Lamps updated: ${lamps.size}")
                adapter.updateLamps(lamps)
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null) {
                    Toast.makeText(this@LampListActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLamps()
    }
}
