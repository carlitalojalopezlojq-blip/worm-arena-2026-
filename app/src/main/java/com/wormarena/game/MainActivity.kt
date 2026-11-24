package com.wormarena.game

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF101522.toInt())
        }

        val title = TextView(this).apply {
            text = "WORM ARENA – 2026\nAlpha 0.1 (Offline)"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 64)
        }

        val playButton = Button(this).apply {
            text = "JUGAR (OFFLINE)"
            textSize = 18f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GameActivity::class.java))
            }
        }

        layout.addView(title)
        layout.addView(playButton)
        setContentView(layout)
    }
}
