package com.yemreak.snakegame

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.media.AudioManager
import android.media.SoundPool
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.Toast
import java.io.IOException
import java.util.*





class SnakeEngine(context: Context, size: Point) : SurfaceView(context), Runnable {
    // Pixel konumları
    private var screenX = size.x
    private var screenY = size.y
    private var blockSize: Int

    // Blok genişliği & uzunluğu
    private val blockNumX = 40
    private val blockNumY: Int

    // Ses Efektleri
    private var soundPool: SoundPool
    private var eatFeed = -1
    private var snakeCrash = -1
    private var bgMusic = -1

    // Çizilecek objelerin tanımlanması
    private var surfaceHolder = holder
    private var paint = Paint()

    // Eğer 200 skor olursa ödül verilecek
    private var snakeXs = IntArray(200)
    private var snakeYs = IntArray(200)

    @Volatile
    private var isPLaying = false

    private lateinit var thread: Thread

    private val defaultSnakeLength = 5
    private var snakeLength = defaultSnakeLength

    private var score = 0

    private var nextFrameTime: Long = 0

    private var feedX: Int = 0
    private var feedY: Int = 0

    // Baş'ın yönü
    enum class Heading {
        UP, RIGHT, DOWN, LEFT
    }

    private var heading = Heading.RIGHT

    private lateinit var canvas: Canvas

    // Saniyede 10 kere yeniler
    private val FPS = 10

    private val milisPerSecond = 1000

    init { // Constructor
        blockSize = screenX / blockNumX
        blockNumY = screenY / blockSize

        // Ses efektleri eklenmesi
        soundPool = SoundPool(10, AudioManager.STREAM_MUSIC, 0)
        try {
            val assetManager = context.assets
            var descriptor = assetManager.openFd("eating.ogg") // Res'in yanına Assets klasörü açıyoruz ve içine atıyoruz.
            eatFeed = soundPool.load(descriptor, 0)

            descriptor = assetManager.openFd("crash.ogg")
            snakeCrash = soundPool.load(descriptor, 0)

            descriptor = assetManager.openFd("bg_music.ogg")
            bgMusic = soundPool.load(descriptor, 0)

        } catch (e: IOException) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
        }

        // Oyunu başlatma
        newGame()
    }

    override fun run() {
        while (isPLaying) {
            // Belli bir saniyede güncelleme
            if (updateRequired()) {
                update()
                draw()
            }
        }
    }

    fun pause() {
        isPLaying = false
        try {
            thread.join()
        } catch (e: InterruptedException) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun resume() {
        isPLaying = true

        thread = Thread(this)
        thread.start()
    }

    /**
     * Oyunu başlatma
     */
    private fun newGame() {
        soundPool.play(bgMusic, 1f, 1f, 1, 0, 1f)
        // Yılanın parçasını oluşturuyoruz
        snakeLength = defaultSnakeLength
        snakeXs[0] = blockNumX / 2
        snakeYs[0] = blockNumY / 2

        // Yemleri oluşşturmak
        spawnFeed()

        // Skor sıfırlama
        score = 0

        // Yeni çerçeve zamanını kuruyoruz, bu sayade güncelleme tetikleniyor
        nextFrameTime = System.currentTimeMillis()
    }

    /**
     * Yem oluşturma
     */
    private fun spawnFeed() {
        val random = Random()
        feedX = random.nextInt(blockNumX - 1) + 1
        feedY = random.nextInt(blockNumY - 1) + 1
    }

    /**
     * Yem yeme metodu
     */
    private fun eatFeed() {
        snakeLength++
        score++

        soundPool.play(eatFeed, 1f, 1f, 0, 0, 1f)
        spawnFeed()
    }

    private fun moveSnake() {
        // Vücudunu hareket ettiriyoruz
        for (i in snakeLength downTo 1) {
            snakeXs[i] = snakeXs[i - 1]
            snakeYs[i] = snakeYs[i - 1]

            // Baş'ı dahil etmiyoruz, çünkü o uçtadır.
        }

        when (heading) {
            Heading.UP -> snakeYs[0]--
            Heading.RIGHT -> snakeXs[0]++
            Heading.DOWN -> snakeYs[0]++
            Heading.LEFT -> snakeXs[0]--
        }
    }

    private fun detectDeath(): Boolean {
        var dead = false

        // Kenarlara çarpma durumu
        if (snakeXs[0] == -1) dead = true
        if (snakeXs[0] >= blockNumX) dead = true
        if (snakeYs[0] == -1) dead = true
        if (snakeYs[0] >= blockNumY) dead = true

        // Çarpışma kontrolü
        for (i in snakeLength - 1 downTo 1) {
            if (i > 4 && snakeXs[0] == snakeXs[i] && snakeYs[0] == snakeYs[i]) {
                dead = true
            }
        }

        return dead
    }

    private fun update() {
        // Eğer yem yediysek
        if (snakeXs[0] == feedX && snakeYs[0] == feedY)
            eatFeed()

        moveSnake()

        if (detectDeath()) {
            // Ses çalma
            soundPool.play(snakeCrash, 1f, 1f, 0, 0, 1f)

            //  Baştan başlatıyoruz.
            newGame()
        }
    }

    private fun draw() {
        // Tuval = canvas
        // Get a lock on the canvas
        if (surfaceHolder.surface.isValid) {
            canvas = surfaceHolder.lockCanvas()

            // Ekranı mavi yapıyoruz
            canvas.drawColor(Color.BLUE)
            // Yılanı beyaz yapmak için renk ayarlıyoruz
            paint.color = Color.WHITE
            canvas.drawText("Score:$score", 10f, 70f, paint)

            // Yılanın kutularını çizme
            for (i in 0 until snakeLength) {
                canvas.drawRect(
                        (snakeXs[i] * blockSize).toFloat(),
                        (snakeYs[i] * blockSize).toFloat(),
                        (snakeXs[i] * blockSize + blockSize).toFloat(),
                        (snakeYs[i] * blockSize + blockSize).toFloat(),
                        paint
                )
            }
            // Yem rengi için kırmızıya alıyoruz.
            paint.color = Color.RED

            canvas.drawRect(
                    (feedX * blockSize).toFloat(),
                    (feedY * blockSize).toFloat(),
                    (feedX * blockSize + blockSize).toFloat(),
                    (feedY * blockSize + blockSize).toFloat(),
                    paint
            )

            // Tuvalı kilitliyoruz ve bu Frame için grafikleri gösteriyoruz.
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    private fun updateRequired() : Boolean {
        // Sonraki güncelleme zamanına geldiysek, güncelleme olmalı
        if (nextFrameTime <= System.currentTimeMillis()) {
            nextFrameTime = System.currentTimeMillis() + milisPerSecond / FPS
            return true
        }

        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    public override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_UP -> heading = if (event.x >= screenX / 2) {
                when (heading) {
                    Heading.UP -> Heading.RIGHT
                    Heading.RIGHT -> Heading.DOWN
                    Heading.DOWN -> Heading.LEFT
                    Heading.LEFT -> Heading.UP
                }
            } else {
                when (heading) {
                    Heading.UP -> Heading.LEFT
                    Heading.LEFT -> Heading.DOWN
                    Heading.DOWN -> Heading.RIGHT
                    Heading.RIGHT -> Heading.UP
                }
            }
        }
        return true
    }
}