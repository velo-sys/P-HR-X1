package de.y2k.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MainActivity : Activity() {
    private lateinit var deck: DeckView
    private var controller: ListenableFuture<MediaController>? = null

    private val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
        deck = DeckView(this)
        setContentView(deck)
        immersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) Engine.rescan() else requestPermissions(arrayOf(perm), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) Engine.rescan() else deck.openWin("noperm")
    }

    override fun onStart() {
        super.onStart()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        deck.onShow()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }; controller = null
        deck.onHide()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }

    @Suppress("DEPRECATION")
    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { it.hide(WindowInsets.Type.systemBars()); it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private var keepOn: Boolean? = null
    fun keepScreenOn(on: Boolean) {
        if (keepOn == on) return
        keepOn = on
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun quitApp() {
        Engine.quit()
        stopService(Intent(this, PlaybackService::class.java))
        finishAndRemoveTask()
    }

    /** Gerätetasten -> Aktionen */
    private fun map(code: Int): String? {
        val swap = Engine.opts["swapab"] == 1
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> if (swap) "b" else "a"
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL -> if (swap) "a" else "b"
            KeyEvent.KEYCODE_BUTTON_X -> "x"; KeyEvent.KEYCODE_BUTTON_Y -> "y"
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_S -> "start"
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_Q -> "select"
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> "l1"; KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> "r1"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "a"
            KeyEvent.KEYCODE_VOLUME_UP -> if (Engine.opts["vosd"] == 1) "vup" else null
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (Engine.opts["vosd"] == 1) "vdn" else null
            else -> null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        android.util.Log.w("Y2K-KEY", "down ${KeyEvent.keyCodeToString(keyCode)} ($keyCode) scan=${event.scanCode} src=${event.source} dev=${event.device?.name}")
        if (deck.keyTest(keyCode, event)) return true
        val k = map(keyCode) ?: return super.onKeyDown(keyCode, event)
        deck.press(k, event.repeatCount > 0)
        return true
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val hx = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X); val hy = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
        if (hx != 0f || hy != 0f) android.util.Log.w("Y2K-KEY", "hat x=$hx y=$hy")
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val k = map(keyCode) ?: return super.onKeyUp(keyCode, event)
        deck.release(k); return true
    }
}
