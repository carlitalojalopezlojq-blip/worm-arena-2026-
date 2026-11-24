package com.wormarena.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("worm_arena_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("player_name", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
            setBackgroundColor(0xFF101522.toInt())
        }

        val title = TextView(this).apply {
            text = "WORM ARENA – 2026\nAlpha 0.1 (Offline)"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 32)
        }

        val nameLabel = TextView(this).apply {
            text = "Ingresa tu nombre para iniciar sesión:"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8C4D9.toInt())
            setPadding(0, 0, 0, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Tu apodo (ej. Gian)"
            setText(savedName)
            singleLine = true
            maxWidth = 600
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF7D8AA1.toInt())
        }

        val playButton = Button(this).apply {
            text = "INICIAR SESIÓN Y JUGAR"
            textSize = 18f
            setOnClickListener {
                val rawName = nameInput.text.toString().trim()
                val playerName = if (rawName.isEmpty()) "Jugador" else rawName.take(16)
                prefs.edit().putString("player_name", playerName).apply()

                val intent = Intent(this@MainActivity, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_PLAYER_NAME, playerName)
                }
                startActivity(intent)
            }
        }

        layout.addView(title)
        layout.addView(nameLabel)
        layout.addView(nameInput)
        layout.addView(playButton)
        setContentView(layout)
    }
}
