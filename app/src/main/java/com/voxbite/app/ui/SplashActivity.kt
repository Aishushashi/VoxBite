package com.voxbite.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.voxbite.app.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // True fullscreen — hide status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val ivLogo      = findViewById<ImageView>(R.id.iv_logo)
        val tvAppName   = findViewById<TextView>(R.id.tv_app_name)
        val tvTagline   = findViewById<TextView>(R.id.tv_tagline)
        val tvLanguages = findViewById<TextView>(R.id.tv_languages)

        // Step 1: Logo pops in (scale + fade) at 0ms
        ivLogo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(0)
            .start()

        // Step 2: App name fades in at 400ms
        tvAppName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(400)
            .start()

        // Step 3: Tagline fades in at 700ms
        tvTagline.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(700)
            .start()

        // Step 4: Language pills fade in at 1000ms
        tvLanguages.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(1000)
            .start()

        // Step 5: Go to MainActivity after 2500ms
        ivLogo.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            // Smooth transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2500)
    }
}