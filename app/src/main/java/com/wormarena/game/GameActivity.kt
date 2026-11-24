package com.wormarena.game

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME)?.takeIf { it.isNotBlank() } ?: "Jugador"
        gameView = GameView(this, playerName)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    companion object {
        const val EXTRA_PLAYER_NAME = "extra_player_name"
    }
}
