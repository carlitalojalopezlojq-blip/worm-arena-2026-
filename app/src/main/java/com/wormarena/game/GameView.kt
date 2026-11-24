package com.wormarena.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class Vec2(var x: Float, var y: Float)

data class FoodParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    val value: Float,
    val color: Int
)

class WormEntity(
    val name: String,
    val isBot: Boolean,
    startX: Float,
    startY: Float,
    val color: Int,
    initialMass: Float = 20f
) {
    val segments: ArrayDeque<Vec2> = ArrayDeque()
    var angle: Float = Random.nextFloat() * (Math.PI * 2).toFloat()
    var mass: Float = initialMass
    var isTurbo: Boolean = false
    var alive: Boolean = true
    private var aiTimer: Float = 0f

    init {
        val count = targetSegmentCount()
        for (i in 0 until count) {
            segments.addLast(Vec2(startX - cos(angle) * i * 8f, startY - sin(angle) * i * 8f))
        }
    }

    fun head(): Vec2 = segments.first()

    fun radius(): Float = 12f + min(26f, mass * 0.08f)

    fun targetSegmentCount(): Int = (12 + (mass / 8f).toInt()).coerceIn(12, 140)

    fun update(dt: Float, mapHalfSize: Float, foods: List<FoodParticle>, others: List<WormEntity>) {
        if (!alive) return

        if (isBot) {
            updateBotAI(dt, mapHalfSize, foods, others)
        }

        val wantsTurbo = isTurbo && mass > 22f
        val speed = if (wantsTurbo) 310f else 185f

        if (wantsTurbo) {
            mass = (mass - 12f * dt).coerceAtLeast(15f)
        }

        val h = head()
        val nx = (h.x + cos(angle) * speed * dt).coerceIn(-mapHalfSize, mapHalfSize)
        val ny = (h.y + sin(angle) * speed * dt).coerceIn(-mapHalfSize, mapHalfSize)

        if (nx <= -mapHalfSize || nx >= mapHalfSize || ny <= -mapHalfSize || ny >= mapHalfSize) {
            alive = false
            return
        }

        segments.addFirst(Vec2(nx, ny))

        val spacing = radius() * 0.55f
        var prev = segments.first()
        val iterator = segments.iterator()
        iterator.next()
        while (iterator.hasNext()) {
            val seg = iterator.next()
            val dx = prev.x - seg.x
            val dy = prev.y - seg.y
            val dist = hypot(dx, dy)
            if (dist > spacing && dist > 0.0001f) {
                val pull = dist - spacing
                seg.x += (dx / dist) * pull
                seg.y += (dy / dist) * pull
            }
            prev = seg
        }

        val desired = targetSegmentCount()
        while (segments.size > desired) {
            segments.removeLast()
        }
    }

    private fun updateBotAI(dt: Float, mapHalfSize: Float, foods: List<FoodParticle>, others: List<WormEntity>) {
        aiTimer -= dt
        val h = head()

        if (absVal(h.x) > mapHalfSize - 250f || absVal(h.y) > mapHalfSize - 250f) {
            val toCenter = atan2(-h.y, -h.x)
            angle = lerpAngle(angle, toCenter, 5f * dt)
            isTurbo = false
            return
        }

        for (other in others) {
            if (other === this || !other.alive) continue
            for (seg in other.segments) {
                val d = hypot(seg.x - h.x, seg.y - h.y)
                if (d < radius() + other.radius() + 65f) {
                    val away = atan2(h.y - seg.y, h.x - seg.x)
                    angle = lerpAngle(angle, away, 7f * dt)
                    isTurbo = d < radius() + other.radius() + 35f
                    return
                }
            }
        }

        if (aiTimer <= 0f) {
            aiTimer = 0.45f
            var nearest: FoodParticle? = null
            var bestDist = 550f
            for (f in foods) {
                val d = hypot(f.x - h.x, f.y - h.y)
                if (d < bestDist) {
                    bestDist = d
                    nearest = f
                }
            }
            val targetAngle = if (nearest != null) {
                atan2(nearest.y - h.y, nearest.x - h.x)
            } else {
                angle + (Random.nextFloat() - 0.5f) * 1.4f
            }
            angle = lerpAngle(angle, targetAngle, 3.5f * dt)
            isTurbo = false
        }
    }

    private fun absVal(v: Float): Float = if (v < 0f) -v else v

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % (Math.PI * 2).toFloat()
        if (diff < -Math.PI) diff += (Math.PI * 2).toFloat()
        if (diff > Math.PI) diff -= (Math.PI * 2).toFloat()
        return a + diff * t.coerceIn(0f, 1f)
    }
}

class GameView(context: Context, private val playerName: String) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false

    private val mapHalfSize = 2400f
    private val foods = mutableListOf<FoodParticle>()
    private val worms = mutableListOf<WormEntity>()
    private lateinit var player: WormEntity

    private var joystickActive = false
    private var joystickPointerId = -1
    private var joyCenterX = 220f
    private var joyCenterY = 0f
    private var joyKnobX = 220f
    private var joyKnobY = 0f
    private val joyMaxRadius = 110f

    private var turboPointerId = -1
    private var turboBtnX = 0f
    private var turboBtnY = 0f
    private val turboBtnRadius = 85f

    private var nextFoodId = 1
    private var gameOver = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        holder.addCallback(this)
        resetWorld()
    }

    private fun resetWorld() {
        foods.clear()
        worms.clear()
        gameOver = false

        val palette = intArrayOf(
            Color.rgb(255, 99, 132),
            Color.rgb(54, 162, 235),
            Color.rgb(255, 206, 86),
            Color.rgb(75, 192, 192),
            Color.rgb(153, 102, 255),
            Color.rgb(255, 159, 64)
        )

        player = WormEntity(playerName, false, 0f, 0f, Color.rgb(46, 213, 115), 25f)
        worms.add(player)

        for (i in 1..12) {
            val bx = Random.nextFloat() * (mapHalfSize * 1.6f) - (mapHalfSize * 0.8f)
            val by = Random.nextFloat() * (mapHalfSize * 1.6f) - (mapHalfSize * 0.8f)
            worms.add(WormEntity("Bot_$i", true, bx, by, palette[i % palette.size], 20f + Random.nextInt(30)))
        }

        repeat(350) { spawnFood() }
    }

    private fun spawnFood() {
        val colors = intArrayOf(
            Color.rgb(255, 234, 167),
            Color.rgb(129, 236, 236),
            Color.rgb(255, 118, 117),
            Color.rgb(162, 155, 254),
            Color.rgb(85, 239, 196)
        )
        foods.add(
            FoodParticle(
                id = nextFoodId++,
                x = Random.nextFloat() * (mapHalfSize * 1.9f) - (mapHalfSize * 0.95f),
                y = Random.nextFloat() * (mapHalfSize * 1.9f) - (mapHalfSize * 0.95f),
                value = 4f,
                color = colors[Random.nextInt(colors.size)]
            )
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val h = height.toFloat().coerceAtLeast(800f)
        val w = width.toFloat().coerceAtLeast(480f)
        joyCenterX = 210f
        joyCenterY = h - 220f
        joyKnobX = joyCenterX
        joyKnobY = joyCenterY
        turboBtnX = w - 180f
        turboBtnY = h - 220f
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        joyCenterX = 210f
        joyCenterY = height - 220f
        if (!joystickActive) {
            joyKnobX = joyCenterX
            joyKnobY = joyCenterY
        }
        turboBtnX = width - 180f
        turboBtnY = height - 220f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    fun resume() {
        if (running) return
        running = true
        thread = Thread(this).also { it.start() }
    }

    fun pause() {
        running = false
        try {
            thread?.join()
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    override fun run() {
        var lastTime = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
            lastTime = now

            update(dt)
            drawFrame()
        }
    }

    private fun update(dt: Float) {
        if (gameOver) return

        if (joystickActive && player.alive) {
            val dx = joyKnobX - joyCenterX
            val dy = joyKnobY - joyCenterY
            if (hypot(dx, dy) > 12f) {
                player.angle = atan2(dy, dx)
            }
        }
        player.isTurbo = (turboPointerId != -1)

        for (w in worms) {
            w.update(dt, mapHalfSize, foods, worms)
        }

        for (w in worms) {
            if (!w.alive) continue
            val h = w.head()
            val pickupRadius = w.radius() + 18f
            val eatenIterator = foods.iterator()
            while (eatenIterator.hasNext()) {
                val f = eatenIterator.next()
                if (hypot(f.x - h.x, f.y - h.y) <= pickupRadius) {
                    w.mass += f.value
                    eatenIterator.remove()
                }
            }
        }

        while (foods.size < 350) {
            spawnFood()
        }

        for (w in worms) {
            if (!w.alive) continue
            val h = w.head()
            for (other in worms) {
                if (other === w || !other.alive) continue
                for (i in 1 until other.segments.size) {
                    val seg = other.segments[i]
                    if (hypot(h.x - seg.x, h.y - seg.y) < w.radius() * 0.75f + other.radius() * 0.65f) {
                        w.alive = false
                        dropFoodFromWorm(w)
                        break
                    }
                }
                if (!w.alive) break
            }
        }

        for (i in worms.indices) {
            val w = worms[i]
            if (!w.alive && w.isBot) {
                val bx = Random.nextFloat() * (mapHalfSize * 1.5f) - (mapHalfSize * 0.75f)
                val by = Random.nextFloat() * (mapHalfSize * 1.5f) - (mapHalfSize * 0.75f)
                worms[i] = WormEntity(w.name, true, bx, by, w.color, 20f)
            }
        }

        if (!player.alive) {
            gameOver = true
        }
    }

    private fun dropFoodFromWorm(w: WormEntity) {
        val step = 2.coerceAtMost(w.segments.size)
        for (i in 0 until w.segments.size step step) {
            val s = w.segments[i]
            foods.add(FoodParticle(nextFoodId++, s.x, s.y, 6f, w.color))
        }
    }

    private fun drawEyes(canvas: Canvas, w: WormEntity, headRadius: Float) {
        val h = w.head()
        val forwardX = cos(w.angle)
        val forwardY = sin(w.angle)
        val rightX = -forwardY
        val rightY = forwardX

        val eyeForwardOffset = headRadius * 0.35f
        val eyeSideOffset = headRadius * 0.45f
        val eyeRadius = (headRadius * 0.28f).coerceAtLeast(3.5f)
        val pupilRadius = (eyeRadius * 0.55f).coerceAtLeast(2f)

        val sides = floatArrayOf(-1f, 1f)
        for (side in sides) {
            val ex = h.x + forwardX * eyeForwardOffset + rightX * (eyeSideOffset * side)
            val ey = h.y + forwardY * eyeForwardOffset + rightY * (eyeSideOffset * side)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(ex, ey, eyeRadius, paint)

            val px = ex + forwardX * (eyeRadius * 0.25f)
            val py = ey + forwardY * (eyeRadius * 0.25f)
            paint.color = Color.rgb(18, 22, 32)
            canvas.drawCircle(px, py, pupilRadius, paint)
        }
    }

    private fun drawFrame() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.rgb(15, 20, 32))

            val camX = player.head().x - width / 2f
            val camY = player.head().y - height / 2f

            canvas.save()
            canvas.translate(-camX, -camY)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(40, 255, 255, 255)
            val gridStep = 120f
            val startX = ((-mapHalfSize / gridStep).toInt()) * gridStep
            val startY = ((-mapHalfSize / gridStep).toInt()) * gridStep
            var gx = startX
            while (gx <= mapHalfSize) {
                canvas.drawLine(gx, -mapHalfSize, gx, mapHalfSize, paint)
                gx += gridStep
            }
            var gy = startY
            while (gy <= mapHalfSize) {
                canvas.drawLine(-mapHalfSize, gy, mapHalfSize, gy, paint)
                gy += gridStep
            }

            paint.strokeWidth = 10f
            paint.color = Color.rgb(255, 71, 87)
            canvas.drawRect(-mapHalfSize, -mapHalfSize, mapHalfSize, mapHalfSize, paint)

            paint.style = Paint.Style.FILL
            for (f in foods) {
                paint.color = f.color
                canvas.drawCircle(f.x, f.y, 8f, paint)
            }

            for (w in worms) {
                if (!w.alive) continue
                val r = w.radius()
                paint.color = w.color
                val list = w.segments.toList()
                for (i in list.size - 1 downTo 0) {
                    val seg = list[i]
                    val segRadius = if (i == 0) r * 1.1f else r * 0.9f
                    canvas.drawCircle(seg.x, seg.y, segRadius, paint)
                }

                drawEyes(canvas, w, r * 1.1f)

                paint.color = Color.WHITE
                paint.textSize = 26f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(w.name, w.head().x, w.head().y - r * 1.1f - 14f, paint)
            }

            canvas.restore()

            drawControls(canvas)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 36f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${player.name} | Masa: ${player.mass.toInt()}", 36f, 70f, paint)

            if (gameOver) {
                paint.color = Color.argb(180, 0, 0, 0)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.color = Color.WHITE
                paint.textSize = 54f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("¡ELIMINADO! Toca para reintentar", width / 2f, height / 2f, paint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawControls(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(joyCenterX, joyCenterY, joyMaxRadius, paint)

        paint.color = Color.argb(170, 46, 213, 115)
        canvas.drawCircle(joyKnobX, joyKnobY, 44f, paint)

        paint.color = if (turboPointerId != -1) Color.argb(210, 255, 165, 2) else Color.argb(90, 255, 165, 2)
        canvas.drawCircle(turboBtnX, turboBtnY, turboBtnRadius, paint)
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("TURBO", turboBtnX, turboBtnY + 10f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            resetWorld()
            return true
        }

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (hypot(x - turboBtnX, y - turboBtnY) <= turboBtnRadius * 1.2f && turboPointerId == -1) {
                    turboPointerId = pointerId
                } else if (x < width / 2f && !joystickActive) {
                    joystickActive = true
                    joystickPointerId = pointerId
                    updateJoystickKnob(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid == joystickPointerId) {
                        updateJoystickKnob(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == joystickPointerId) {
                    joystickActive = false
                    joystickPointerId = -1
                    joyKnobX = joyCenterX
                    joyKnobY = joyCenterY
                }
                if (pointerId == turboPointerId) {
                    turboPointerId = -1
                }
            }
        }
        return true
    }

    private fun updateJoystickKnob(x: Float, y: Float) {
        val dx = x - joyCenterX
        val dy = y - joyCenterY
        val dist = hypot(dx, dy)
        if (dist <= joyMaxRadius) {
            joyKnobX = x
            joyKnobY = y
        } else if (dist > 0.0001f) {
            joyKnobX = joyCenterX + (dx / dist) * joyMaxRadius
            joyKnobY = joyCenterY + (dy / dist) * joyMaxRadius
        }
    }
}
