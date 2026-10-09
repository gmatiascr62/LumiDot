package com.lumidot.app

import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class DotActivity : Activity() {
    private var pulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        val size = (resources.displayMetrics.density * 15).toInt()
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getSharedPreferences("lumidot", MODE_PRIVATE).getInt("color", Color.GREEN))
            }
        }
        root.addView(dot, FrameLayout.LayoutParams(size, size,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (resources.displayMetrics.density * 55).toInt()
        })
        setContentView(root)
        pulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.15f, 1f).apply {
            duration = 1700
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }
}
