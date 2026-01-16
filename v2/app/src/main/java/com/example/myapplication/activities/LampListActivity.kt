package com.example.myapplication.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.viewmodels.LampListViewModel
import kotlinx.coroutines.launch

class LampListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: LampAdapter
    private lateinit var viewModel: LampListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lamp_list)

        // Set up Toolbar as ActionBar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize RetrofitClient with SharedPreferences
        RetrofitClient.init(this)

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_lamp_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
