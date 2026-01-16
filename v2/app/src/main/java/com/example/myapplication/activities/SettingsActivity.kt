package com.example.myapplication.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var saveBtn: Button
    private lateinit var statusText: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ipInput = findViewById(R.id.serverIpInput)
        portInput = findViewById(R.id.serverPortInput)
        saveBtn = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)

        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Load current values
        val currentIp = prefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2"
        val currentPort = prefs.getString("server_port", "3000") ?: "3000"

        ipInput.setText(currentIp)
        portInput.setText(currentPort)

        saveBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            val port = portInput.text.toString().trim()

            if (ip.isEmpty()) {
                Toast.makeText(this, "IP address cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (port.isEmpty()) {
                Toast.makeText(this, "Port cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate port is a number
            try {
                port.toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Port must be a valid number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save to SharedPreferences
            prefs.edit().apply {
                putString("server_ip", ip)
                putString("server_port", port)
                commit()
            }

            // Update RetrofitClient with new settings
            com.example.myapplication.api.RetrofitClient.updateBaseUrl()

            statusText.text = "Settings saved! Restarting app..."
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()

            // Restart the app after a short delay
            Thread {
                Thread.sleep(500)
                restartApp()
            }.start()
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // Kill the current process
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
