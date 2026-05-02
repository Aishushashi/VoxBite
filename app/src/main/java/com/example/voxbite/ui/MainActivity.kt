package com.voxbite.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.voxbite.app.R
import com.voxbite.app.agent.IntentParser
import com.voxbite.app.automation.VoxBiteAccessibilityService
import com.voxbite.app.voice.VoiceInputManager
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibilityWarning: TextView
    private lateinit var fabMic: FloatingActionButton

    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var intentParser: IntentParser
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvAccessibilityWarning = findViewById(R.id.tv_accessibility_warning)
        fabMic = findViewById(R.id.fab_mic)

        intentParser = IntentParser()
        tts = TextToSpeech(this, this)

        setupVoiceManager()
        checkPermissions()

        fabMic.setOnClickListener {
            if (!VoxBiteAccessibilityService.isConnected) {
                speak("Please enable VoxBite in Accessibility Settings first")
                openAccessibilitySettings()
                return@setOnClickListener
            }
            voiceInputManager.startListening()
        }

        tvAccessibilityWarning.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun setupVoiceManager() {
        voiceInputManager = VoiceInputManager(
            context = this,
            onListeningStart = {
                updateStatus("🎤 Listening...")
                fabMic.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, android.R.color.holo_red_light)
                    )
            },
            onResult = { spokenText ->
                updateStatus("You said:\n\"$spokenText\"\n\nProcessing...")
                fabMic.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, android.R.color.holo_orange_light)
                    )
                processVoiceInput(spokenText)
            },
            onError = { error ->
                updateStatus(error)
                resetMicButton()
            }
        )
    }

    private fun processVoiceInput(speech: String) {
        lifecycleScope.launch {
            try {
                val intent = intentParser.parse(speech)
                updateStatus("Got it! Opening ${intent.app
                    .replaceFirstChar { it.uppercase() }}...")

                speak(
                    if (intent.items.isNotEmpty())
                        "Got it! Ordering ${intent.items
                            .joinToString(" and ") { it.name }}"
                    else "Processing your request"
                )

                val service = VoxBiteAccessibilityService.instance

                when (intent.action) {
                    "order_food", "search_product" -> {
                        if (intent.items.isNotEmpty()) {
                            service?.openSwiggyAndSearch(intent.items[0]) {
                                service.clickAddButton {
                                    speak("Done! ${intent.items[0].name} added to your cart.")
                                    updateStatus("✅ Added to cart!")
                                    resetMicButton()
                                }
                            }
                        }
                    }
                    "book_cab" -> {
                        service?.openOlaForDestination(
                            intent.destination ?: ""
                        ) {
                            speak("Opened Ola. Please confirm your pickup.")
                            updateStatus("✅ Ola opened with destination!")
                            resetMicButton()
                        }
                    }
                    else -> {
                        speak("I didn't understand. Try saying: Order Maggi from Swiggy")
                        updateStatus("Try: \"Order Maggi from Swiggy\"")
                        resetMicButton()
                    }
                }
            } catch (e: Exception) {
                speak("Something went wrong. Please try again.")
                updateStatus("Error occurred. Try again.")
                resetMicButton()
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateStatus(text: String) {
        runOnUiThread { tvStatus.text = text }
    }

    private fun resetMicButton() {
        runOnUiThread {
            fabMic.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                )
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
        if (!VoxBiteAccessibilityService.isConnected) {
            tvAccessibilityWarning.visibility = View.VISIBLE
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
            speak("VoxBite is ready. Tap the mic and tell me what to do.")
        }
    }

    override fun onResume() {
        super.onResume()
        if (VoxBiteAccessibilityService.isConnected) {
            tvAccessibilityWarning.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.stopListening()
        tts.shutdown()
    }
}