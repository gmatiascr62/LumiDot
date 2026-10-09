package com.lumidot.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("lumidot", MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 72, 40, 32)
            setBackgroundColor(Color.rgb(14, 17, 25))
        }
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 15, 0, 15)
        }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value
            setOnClickListener { action() }
        }
        layout.addView(label("LumiDot • LED virtual de notificaciones"))
        layout.addView(label("Concedé acceso a notificaciones para habilitar la detección."))
        layout.addView(button("Permitir acceso a notificaciones") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        layout.addView(Switch(this).apply {
            text = "Activar LumiDot"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("enabled", checked).apply()
            }
        })
        layout.addView(label("Color del LED"))
        val colors = listOf("Verde" to Color.GREEN, "Azul" to Color.CYAN,
            "Rojo" to Color.RED, "Blanco" to Color.WHITE)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            colors.map { it.first })
        spinner.setSelection(colors.indexOfFirst {
            it.second == prefs.getInt("color", Color.GREEN)
        }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?,
                position: Int, id: Long) {
                prefs.edit().putInt("color", colors[position].second).apply()
            }
        }
        layout.addView(spinner)
        layout.addView(button("Probar LED") {
            startActivity(Intent(this, DotActivity::class.java))
        })
        layout.addView(label("Versión experimental: Android puede bloquear la visualización automática con la pantalla bloqueada."))
        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
