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

    private val PKG_OLA = "com.olacabs.customer"
    private val PKG_SWIGGY = "in.swiggy.android"
    private val PKG_ZOMATO = "com.application.zomato"

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

        // DEBUG — remove these 3 lines after confirming Toast says "Ola visible: true"
        val isOlaVisible = packageManager.getLaunchIntentForPackage(PKG_OLA) != null
        Log.d(TAG, "Ola visible to app: $isOlaVisible")
        Toast.makeText(this, "Ola visible: $isOlaVisible", Toast.LENGTH_SHORT).show()

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

                if (intent.action == "error") {
                    // ✅ FIXED: was intent.errorMessage — now uses confirmationMessage
                    val errorMsg = intent.confirmationMessage.ifBlank { "Unknown error" }
                    Log.e(TAG, "IntentParser error: $errorMsg")
                    Toast.makeText(this@MainActivity, "ERROR: $errorMsg", Toast.LENGTH_LONG).show()
                    updateStatus("Error: $errorMsg")
                    speak("Something went wrong. Please try again.")
                    resetMicButton()
                    return@launch
                }

                if (intent.action == "start_over") {
                    // ✅ speaks in detected language
                    speak(intent.confirmationMessage.ifBlank { "Okay, starting over. What would you like to do?" }, intent.detectedLanguage)
                    updateStatus("Ready! What would you like to do?\nTry: Order biryani from Swiggy")
                    resetMicButton()
                    return@launch
                }

                val correctionPrefix = if (intent.isCorrection) "Got it, changing that!\n" else ""

                Log.d(TAG, "Intent: action=${intent.action} app=${intent.app} items=${intent.items} destination=${intent.destination} budget=${intent.budget} correction=${intent.isCorrection} lang=${intent.detectedLanguage}")

                when (intent.action) {
                    "order", "order_food" -> handleFoodOrder(intent, correctionPrefix)
                    "book_cab", "navigate" -> handleCabBooking(intent, correctionPrefix)
                    "open" -> handleOpenApp(intent)
                    else -> {
                        speak("I didn't understand. Try: Order biryani from Swiggy")
                        updateStatus("Try saying:\n\"Order biryani from Swiggy\"\n\"Order something light under ₹150\"\n\"Book a cab to Koramangala\"")
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

    private fun handleFoodOrder(intent: com.voxbite.app.model.UserIntent, correctionPrefix: String) {
        if (intent.items.isNotEmpty()) {
            val query = intent.items.joinToString("+") { it.name.replace(" ", "+") }
            val itemsList = intent.items.joinToString(" and ") { it.name }
            val budgetText = if (intent.budget != null) " under ₹${intent.budget}" else ""

            // ✅ speaks confirmationMessage in user's language
            speak(intent.confirmationMessage.ifBlank {
                if (intent.isCorrection)
                    "Sure! Changed to $itemsList$budgetText. Opening Swiggy now."
                else
                    "Got it! Opening Swiggy for $itemsList$budgetText."
            }, intent.detectedLanguage)

            updateStatus("${correctionPrefix}Opening Swiggy for:\n$itemsList$budgetText\n\nTap Add to Cart in Swiggy")
            openUrl("https://www.swiggy.com/search?query=$query")

        } else if (intent.category != null || intent.budget != null) {
            val category = intent.category ?: "food"
            val budgetText = if (intent.budget != null) " under ₹${intent.budget}" else ""
            val query = category.replace(" ", "+")

            // ✅ speaks confirmationMessage in user's language
            speak(intent.confirmationMessage.ifBlank {
                if (intent.isCorrection)
                    "Sure! Changed to $category$budgetText. Opening Swiggy."
                else
                    "Opening Swiggy for $category$budgetText."
            }, intent.detectedLanguage)

            updateStatus("${correctionPrefix}Opening Swiggy for:\n$category$budgetText\n\nBrowse and pick what you like!")
            openUrl("https://www.swiggy.com/search?query=$query")

        } else {
            speak(intent.confirmationMessage.ifBlank { "What would you like to order?" }, intent.detectedLanguage)
            updateStatus("What would you like to order?\nTry: Order biryani from Swiggy\nOr: Order something light under ₹150")
        }
        resetMicButton()
    }

    private fun handleCabBooking(intent: com.voxbite.app.model.UserIntent, correctionPrefix: String) {
        val destination = intent.destination

        if (destination.isNullOrBlank()) {
            speak(intent.confirmationMessage.ifBlank { "Where would you like to go?" }, intent.detectedLanguage)
            updateStatus("Where to?\nTry: Book a cab to Koramangala\nOr: Book a cab to airport")
            resetMicButton()
            return
        }

        // ✅ speaks confirmationMessage in user's language
        speak(intent.confirmationMessage.ifBlank {
            if (intent.isCorrection)
                "Sure! Changed destination to $destination. Opening Ola."
            else
                "Opening Ola for $destination."
        }, intent.detectedLanguage)

        updateStatus("${correctionPrefix}Opening Ola...\nDestination: $destination")

        val olaLaunchIntent = packageManager.getLaunchIntentForPackage(PKG_OLA)

        if (olaLaunchIntent != null) {
            Log.d(TAG, "Ola found via getLaunchIntentForPackage — launching")
            olaLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(olaLaunchIntent)
            updateStatus("Ola opened!\nEnter destination: $destination")

        } else {
            Log.d(TAG, "getLaunchIntentForPackage returned null — trying fallback")

            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(PKG_OLA)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolvedActivities = packageManager.queryIntentActivities(fallbackIntent, 0)

            if (resolvedActivities.isNotEmpty()) {
                Log.d(TAG, "Ola found via fallback — launching")
                startActivity(fallbackIntent)
                updateStatus("Ola opened!\nEnter destination: $destination")

            } else {
                Log.d(TAG, "Ola not found — opening Play Store")
                speak("Ola app is not installed. Opening Play Store to install it.")
                updateStatus("Ola not installed.\nOpening Play Store...")

                try {
                    val marketIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$PKG_OLA")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(marketIntent)
                } catch (e: Exception) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$PKG_OLA")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(webIntent)
                }
            }
        }

        resetMicButton()
    }

    private fun handleOpenApp(intent: com.voxbite.app.model.UserIntent) {
        val appName = intent.app.replaceFirstChar { it.uppercase() }
        val pkg = getPackageNameForApp(intent.app)

        // ✅ speaks confirmationMessage in user's language
        speak(intent.confirmationMessage.ifBlank { "Opening $appName." }, intent.detectedLanguage)
        updateStatus("Opening $appName...")

        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            speak("$appName is not installed.")
            updateStatus("$appName is not installed on this device.")
        }
        resetMicButton()
    }

    private fun openUrl(url: String) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(webIntent)
    }

    private fun getPackageNameForApp(app: String): String {
        return when (app.lowercase()) {
            "swiggy" -> PKG_SWIGGY
            "ola"    -> PKG_OLA
            "zomato" -> PKG_ZOMATO
            else     -> app
        }
    }

    // ✅ Single speak() function — handles all 3 languages
    private fun speak(text: String, languageCode: String = "en-IN") {
        val locale = when (languageCode) {
            "hi-IN" -> Locale("hi", "IN")
            "kn-IN" -> Locale("kn", "IN")
            else    -> Locale("en", "IN")
        }
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language $languageCode not supported, falling back to English")
            tts.setLanguage(Locale("en", "IN"))
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voxbite_tts")
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