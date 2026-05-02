package com.voxbite.app.ui

import com.voxbite.app.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    private val TAG = "VoxBite"

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
                updateStatus("Listening... Speak now!")
                fabMic.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, android.R.color.holo_red_light)
                    )
            },
            onResult = { spokenText ->
                updateStatus("You said:\n\"$spokenText\"\n\nProcessing...")
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
                val intent = intentParser.parseIntent(speech)

                // Show real error as Toast for debugging
                if (intent.action == "error") {
                    val errorMsg = intent.errorMessage ?: "Unknown error"
                    Log.e(TAG, "IntentParser error: $errorMsg")
                    Toast.makeText(this@MainActivity, "ERROR: $errorMsg", Toast.LENGTH_LONG).show()
                    updateStatus("Error: $errorMsg")
                    speak("Something went wrong. Please try again.")
                    resetMicButton()
                    return@launch
                }

                Log.d(TAG, "Intent: action=${intent.action} app=${intent.app} items=${intent.items}")

                val appName = intent.app.replaceFirstChar { it.uppercase() }

                when (intent.action) {
                    "order", "order_food" -> {
                        if (intent.items.isNotEmpty()) {
                            val itemName = intent.items[0].name
                            val query = intent.items.joinToString("+") {
                                it.name.replace(" ", "+")
                            }

                            // Build spoken confirmation with all items
                            val itemsList = intent.items.joinToString(" and ") { it.name }
                            val budgetText = if (intent.budget != null) " under ₹${intent.budget}" else ""
                            speak("Got it! Opening Swiggy for $itemsList$budgetText.")
                            updateStatus("Opening Swiggy for:\n$itemsList$budgetText\n\nTap Add to Cart in Swiggy")

                            val swiggyUri = Uri.parse("https://www.swiggy.com/search?query=$query")
                            val swiggyIntent = Intent(Intent.ACTION_VIEW, swiggyUri)
                            swiggyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(swiggyIntent)

                        } else if (intent.category != null || intent.budget != null) {
                            // User said "something light under ₹150" — no specific item
                            val category = intent.category ?: "food"
                            val budgetText = if (intent.budget != null) " under ₹${intent.budget}" else ""
                            val query = category.replace(" ", "+")

                            speak("Opening Swiggy for $category$budgetText.")
                            updateStatus("Opening Swiggy for:\n$category$budgetText\n\nBrowse and pick what you like!")

                            val swiggyUri = Uri.parse("https://www.swiggy.com/search?query=$query")
                            val swiggyIntent = Intent(Intent.ACTION_VIEW, swiggyUri)
                            swiggyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(swiggyIntent)

                        } else {
                            speak("What would you like to order?")
                            updateStatus("What would you like to order?\nTry: Order biryani from Swiggy\nOr: Order something light under ₹150")
                        }
                        resetMicButton()
                    }

                    "book_cab", "navigate" -> {
                        val destination = intent.destination ?: ""
                        if (destination.isNotEmpty()) {
                            speak("Opening Ola for $destination.")
                            updateStatus("Opening Ola...\nDestination: $destination")

                            // Open Ola via deep link
                            val olaUri = Uri.parse("https://book.olacabs.com/?drop=$destination")
                            val olaIntent = Intent(Intent.ACTION_VIEW, olaUri)
                            olaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(olaIntent)

                        } else {
                            speak("Where would you like to go?")
                            updateStatus("Where to?\nTry: Book a cab to Koramangala")
                        }
                        resetMicButton()
                    }

                    "open" -> {
                        speak("Opening $appName.")
                        updateStatus("Opening $appName...")
                        val launchIntent = packageManager.getLaunchIntentForPackage(
                            getPackageName(intent.app)
                        )
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            speak("$appName is not installed.")
                            updateStatus("$appName is not installed on this device.")
                        }
                        resetMicButton()
                    }

                    else -> {
                        speak("I didn't understand. Try: Order biryani from Swiggy")
                        updateStatus("Try saying:\n\"Order biryani from Swiggy\"\n\"Book a cab to airport\"")
                        resetMicButton()
                    }
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown exception"
                Log.e(TAG, "processVoiceInput crashed: $errorMsg", e)
                Toast.makeText(this@MainActivity, "DEBUG: $errorMsg", Toast.LENGTH_LONG).show()
                updateStatus("Error: $errorMsg")
                speak("Something went wrong. Please try again.")
                resetMicButton()
            }
        }
    }

    private fun getPackageName(app: String): String {
        return when (app.lowercase()) {
            "swiggy" -> "in.swiggy.android"
            "ola" -> "com.olacabs.customer"
            "zomato" -> "com.application.zomato"
            else -> app
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
            speak("VoxBite ready. Tap mic and tell me what to do.")
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