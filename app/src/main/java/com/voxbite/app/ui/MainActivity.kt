package com.voxbite.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.voxbite.app.R
import com.voxbite.app.agent.IntentParser
import com.voxbite.app.agent.RecommendationEngine
import com.voxbite.app.automation.VoxBiteAccessibilityService
import com.voxbite.app.emergency.EmergencyManager
import com.voxbite.app.model.UserIntent
import com.voxbite.app.voice.VoiceInputManager
import kotlinx.coroutines.launch
import java.util.Locale

// ─── Translation helper ───────────────────────────────────────────────────────

object Translator {
    fun ready(lang: String) = when (lang) {
        "hi-IN" -> "तैयार हूँ! क्या करना है?\nकहें: बिरयानी ऑर्डर करो"
        "kn-IN" -> "ಸಿದ್ಧವಾಗಿದ್ದೇನೆ! ಏನು ಮಾಡಬೇಕು?\nಹೇಳಿ: ಬಿರಿಯಾನಿ ಆರ್ಡರ್ ಮಾಡಿ"
        else    -> "Ready! What would you like to do?\nTry: Order biryani from Swiggy"
    }
    fun listening(lang: String) = when (lang) {
        "hi-IN" -> "🎙️ सुन रहा हूँ... बोलिए!"
        "kn-IN" -> "🎙️ ಕೇಳುತ್ತಿದ್ದೇನೆ... ಮಾತನಾಡಿ!"
        else    -> "🎙️ Listening... Speak now!"
    }
    fun processing(lang: String, speech: String) = when (lang) {
        "hi-IN" -> "आपने कहा:\n\"$speech\"\n\nसमझ रहा हूँ..."
        "kn-IN" -> "ನೀವು ಹೇಳಿದ್ದು:\n\"$speech\"\n\nಅರ್ಥ ಮಾಡಿಕೊಳ್ಳುತ್ತಿದ್ದೇನೆ..."
        else    -> "You said:\n\"$speech\"\n\nProcessing..."
    }
    fun searchingFood(lang: String, item: String) = when (lang) {
        "hi-IN" -> "🍽️ स्विगी पर $item खोज रहा हूँ..."
        "kn-IN" -> "🍽️ ಸ್ವಿಗ್ಗಿಯಲ್ಲಿ $item ಹುಡುಕುತ್ತಿದ್ದೇನೆ..."
        else    -> "🍽️ Finding $item on Swiggy..."
    }
    fun bookingCab(lang: String, dest: String) = when (lang) {
        "hi-IN" -> "🚗 $dest के लिए ओला खोल रहा हूँ..."
        "kn-IN" -> "🚗 $dest ಗೆ ಓಲಾ ತೆರೆಯುತ್ತಿದ್ದೇನೆ..."
        else    -> "🚗 Opening Ola → $dest..."
    }
    fun chainingMessage(lang: String, item: String, dest: String) = when (lang) {
        "hi-IN" -> "🔗 $item ऑर्डर करने के बाद $dest के लिए कैब बुक करूँगा..."
        "kn-IN" -> "🔗 $item ಆರ್ಡರ್ ಮಾಡಿದ ನಂತರ $dest ಗೆ ಕ್ಯಾಬ್ ಬುಕ್ ಮಾಡುತ್ತೇನೆ..."
        else    -> "🔗 Ordering $item, then booking cab to $dest..."
    }
    fun noItem(lang: String) = when (lang) {
        "hi-IN" -> "क्या ऑर्डर करना है?"
        "kn-IN" -> "ಏನು ಆರ್ಡರ್ ಮಾಡಬೇಕು?"
        else    -> "What would you like to order?"
    }
    fun noDest(lang: String) = when (lang) {
        "hi-IN" -> "कहाँ जाना है?"
        "kn-IN" -> "ಎಲ್ಲಿಗೆ ಹೋಗಬೇಕು?"
        else    -> "Where do you want to go?"
    }
    fun notInstalled(lang: String, app: String) = when (lang) {
        "hi-IN" -> "$app इंस्टॉल नहीं है।"
        "kn-IN" -> "$app ಇನ್ಸ್ಟಾಲ್ ಆಗಿಲ್ಲ."
        else    -> "$app is not installed."
    }
    fun notUnderstood(lang: String) = when (lang) {
        "hi-IN" -> "समझ नहीं आया। कहें: बिरयानी ऑर्डर करो"
        "kn-IN" -> "ಅರ್ಥವಾಗಲಿಲ್ಲ. ಹೇಳಿ: ಬಿರಿಯಾನಿ ಆರ್ಡರ್ ಮಾಡಿ"
        else    -> "I didn't understand. Try: Order biryani from Swiggy"
    }
    fun somethingWrong(lang: String) = when (lang) {
        "hi-IN" -> "कुछ गड़बड़ हो गई। फिर से कोशिश करें।"
        "kn-IN" -> "ಏನೋ ತಪ್ಪಾಯಿತು. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ."
        else    -> "Something went wrong. Please try again."
    }
    fun sosConfirm(lang: String) = when (lang) {
        "hi-IN" -> "ठीक है, आपकी लोकेशन आपके संपर्कों को भेज रहा हूँ। अस्पताल खोज रहा हूँ।"
        "kn-IN" -> "ಸರಿ, ನಿಮ್ಮ ಸ್ಥಳವನ್ನು ಕಳುಹಿಸುತ್ತಿದ್ದೇನೆ. ಆಸ್ಪತ್ರೆ ಹುಡುಕುತ್ತಿದ್ದೇನೆ."
        else    -> "Okay, sending your location to emergency contacts and finding nearest hospital."
    }
    fun sosNoContacts(lang: String) = when (lang) {
        "hi-IN" -> "कोई आपातकालीन संपर्क नहीं है। पहले सेटअप करें।"
        "kn-IN" -> "ತುರ್ತು ಸಂಪರ್ಕಗಳು ಇಲ್ಲ. ಮೊದಲು ಸೆಟಪ್ ಮಾಡಿ."
        else    -> "No emergency contacts set up. Please set them up first."
    }
}

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibilityWarning: TextView
    private lateinit var fabMic: FloatingActionButton
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var intentParser: IntentParser
    private lateinit var tts: TextToSpeech
    private lateinit var emergencyManager: EmergencyManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val recommender = RecommendationEngine()

    private var lastDetectedLang = "en-IN"
    private var ttsReady = false

    private var pendingChainDestination: String? = null
    private var pendingChainLang: String = "en-IN"

    companion object {
        const val PKG_OLA        = "com.olacabs.customer"
        const val PKG_SWIGGY     = "in.swiggy.android"
        const val PKG_ZOMATO     = "com.application.zomato"
        const val TAG            = "VoxBite"
        const val CHAIN_DELAY_MS = 5000L
        const val PERMISSION_REQUEST_CODE = 101
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus               = findViewById(R.id.tv_status)
        tvAccessibilityWarning = findViewById(R.id.tv_accessibility_warning)
        fabMic                 = findViewById(R.id.fab_mic)

        intentParser        = IntentParser()
        tts                 = TextToSpeech(this, this)
        emergencyManager    = EmergencyManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupVoiceManager()
        checkPermissions()
        requestRuntimePermissions()

        val olaVisible = packageManager.getLaunchIntentForPackage(PKG_OLA) != null
        Log.d(TAG, "Ola visible: $olaVisible")
        // TEMP: open setup screen to add contacts
        startActivity(Intent(this, EmergencySetupActivity::class.java))

        fabMic.setOnClickListener {
            if (!VoxBiteAccessibilityService.isConnected) {
                speak("Please enable VoxBite in Accessibility Settings first")
                openAccessibilitySettings()
                return@setOnClickListener
            }
            voiceInputManager.startListening()
        }

        tvAccessibilityWarning.setOnClickListener { openAccessibilitySettings() }
    }

    override fun onResume() {
        super.onResume()
        if (VoxBiteAccessibilityService.isConnected) {
            tvAccessibilityWarning.visibility = View.GONE
        }
        pendingChainDestination?.let { dest ->
            pendingChainDestination = null
            Handler(Looper.getMainLooper()).postDelayed({
                launchOla(dest, pendingChainLang)
            }, 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.stopListening()
        tts.shutdown()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
            ttsReady = true
            speakWelcomeWithRecommendation()
        }
    }

    // ─── Welcome + Smart Recommendation ──────────────────────────────────────

    private fun speakWelcomeWithRecommendation() {
        val greeting       = recommender.getGreeting(lastDetectedLang)
        val recommendation = recommender.getSpokenRecommendation(lastDetectedLang)
        speak(greeting)
        Handler(Looper.getMainLooper()).postDelayed({
            if (recommendation.isNotBlank()) speak(recommendation)
        }, 2500L)
    }

    // ─── Voice Manager ────────────────────────────────────────────────────────

    private fun setupVoiceManager() {
        voiceInputManager = VoiceInputManager(
            context = this,
            onListeningStart = {
                updateStatus(Translator.listening(lastDetectedLang))
                fabMic.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, android.R.color.holo_red_light)
                    )
            },
            onResult = { spokenText ->
                updateStatus(Translator.processing(lastDetectedLang, spokenText))
                processVoiceInput(spokenText)
            },
            onError = { error ->
                updateStatus(error)
                resetMicButton()
            }
        )
    }

    // ─── Voice Processing Pipeline ────────────────────────────────────────────

    private fun processVoiceInput(speech: String) {
        lifecycleScope.launch {
            try {
                val intent = intentParser.parseIntent(speech)
                lastDetectedLang = intent.detectedLanguage

                if (intent.action == "error") {
                    val msg = intent.confirmationMessage.ifBlank {
                        Translator.somethingWrong(intent.detectedLanguage)
                    }
                    Log.e(TAG, "Error: $msg")
                    Toast.makeText(this@MainActivity, "ERROR: $msg", Toast.LENGTH_LONG).show()
                    updateStatus("⚠️ $msg")
                    speak(msg, intent.detectedLanguage)
                    resetMicButton()
                    return@launch
                }

                if (intent.action == "start_over") {
                    val msg = intent.confirmationMessage.ifBlank {
                        Translator.ready(intent.detectedLanguage)
                    }
                    speak(msg, intent.detectedLanguage)
                    updateStatus(Translator.ready(intent.detectedLanguage))
                    Handler(Looper.getMainLooper()).postDelayed({
                        val rec = recommender.getSpokenRecommendation(intent.detectedLanguage)
                        if (rec.isNotBlank()) speak(rec, intent.detectedLanguage)
                    }, 2000L)
                    resetMicButton()
                    return@launch
                }

                Log.d(TAG, "Intent: action=${intent.action} items=${intent.items} " +
                        "dest=${intent.destination} lang=${intent.detectedLanguage}")

                when (intent.action) {
                    "order", "order_food"  -> handleFoodOrder(intent)
                    "book_cab", "navigate" -> handleCabBooking(intent)
                    "open"                 -> handleOpenApp(intent)
                    "chain"                -> handleChainedCommand(intent)
                    "emergency"            -> handleEmergency(intent.detectedLanguage)
                    else -> {
                        val msg = Translator.notUnderstood(intent.detectedLanguage)
                        speak(msg, intent.detectedLanguage)
                        updateStatus(msg)
                        Handler(Looper.getMainLooper()).postDelayed({
                            val rec = recommender.getSpokenRecommendation(intent.detectedLanguage)
                            if (rec.isNotBlank()) speak(rec, intent.detectedLanguage)
                        }, 2500L)
                        resetMicButton()
                    }
                }

            } catch (e: Exception) {
                val msg = e.message ?: "Unknown exception"
                Log.e(TAG, "Crash: $msg", e)
                Toast.makeText(this@MainActivity, "DEBUG: $msg", Toast.LENGTH_LONG).show()
                updateStatus("Error: $msg")
                speak(Translator.somethingWrong(lastDetectedLang), lastDetectedLang)
                resetMicButton()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  EMERGENCY / SOS
    // ═══════════════════════════════════════════════════════════════

    private fun handleEmergency(lang: String) {
        val contacts = emergencyManager.getContacts()

        if (contacts.isEmpty()) {
            val msg = Translator.sosNoContacts(lang)
            speak(msg, lang)
            updateStatus("⚠️ $msg")
            // Open setup screen so user can add contacts immediately
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, EmergencySetupActivity::class.java))
            }, 2000L)
            resetMicButton()
            return
        }

        val confirmMsg = Translator.sosConfirm(lang)
        speak(confirmMsg, lang)
        updateStatus("🆘 SOS activated — sending location...")
        Log.d(TAG, "SOS triggered in language: $lang")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    emergencyManager.sendSosMessages(location)
                    emergencyManager.openNearestHospital(location)
                    Log.d(TAG, "SOS sent with location: ${location?.latitude}, ${location?.longitude}")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Location fetch failed: ${it.message}")
                    emergencyManager.sendSosMessages(null)
                    emergencyManager.openNearestHospital(null)
                }
        } else {
            // No location permission — still send SMS, just without coordinates
            emergencyManager.sendSosMessages(null)
            emergencyManager.openNearestHospital(null)
        }

        resetMicButton()
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHAINED COMMAND
    // ═══════════════════════════════════════════════════════════════

    private fun handleChainedCommand(intent: UserIntent) {
        val lang  = intent.detectedLanguage
        val chain = intent.chainData

        if (chain == null) {
            val msg = Translator.notUnderstood(lang)
            speak(msg, lang); updateStatus(msg); resetMicButton(); return
        }
        if (chain.foodItems.isEmpty()) {
            val msg = Translator.noItem(lang)
            speak(msg, lang); updateStatus(msg); resetMicButton(); return
        }
        if (chain.destination.isBlank()) {
            val msg = Translator.noDest(lang)
            speak(msg, lang); updateStatus(msg); resetMicButton(); return
        }

        val firstItem = chain.foodItems[0].name
        recommender.recordCommand(firstItem)

        val confirmMsg = intent.confirmationMessage.ifBlank {
            Translator.chainingMessage(lang, firstItem, chain.destination)
        }
        speak(confirmMsg, lang)
        updateStatus(Translator.chainingMessage(lang, firstItem, chain.destination))

        VoxBiteAccessibilityService.pendingSwiggyItem = firstItem
        VoxBiteAccessibilityService.swiggyState =
            VoxBiteAccessibilityService.SwiggyState.SEARCHING

        val swiggyIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.swiggy.com/search?query=${Uri.encode(firstItem)}")
        ).apply {
            setPackage(PKG_SWIGGY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(swiggyIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Swiggy not installed: ${e.message}")
            speak(Translator.notInstalled(lang, "Swiggy"), lang)
            resetMicButton(); return
        }

        pendingChainDestination = chain.destination
        pendingChainLang        = lang

        Handler(Looper.getMainLooper()).postDelayed({
            pendingChainDestination?.let { dest ->
                pendingChainDestination = null
                launchOla(dest, lang)
            }
        }, CHAIN_DELAY_MS)

        resetMicButton()
    }

    // ─── Shared Ola launcher ──────────────────────────────────────────────────

    private fun launchOla(destination: String, lang: String) {
        val olaInstalled = packageManager.getLaunchIntentForPackage(PKG_OLA) != null
        if (!olaInstalled) {
            speak(Translator.notInstalled(lang, "Ola"), lang)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG_OLA")))
            return
        }

        VoxBiteAccessibilityService.pendingOlaDestination = destination
        VoxBiteAccessibilityService.olaState =
            VoxBiteAccessibilityService.OlaState.APP_OPEN

        updateStatus(Translator.bookingCab(lang, destination))

        val deepIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("olacabs://book?drop=${Uri.encode(destination)}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        if (deepIntent.resolveActivity(packageManager) != null) {
            startActivity(deepIntent)
        } else {
            val launchIntent = packageManager.getLaunchIntentForPackage(PKG_OLA)!!
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(launchIntent)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SWIGGY
    // ═══════════════════════════════════════════════════════════════

    private fun handleFoodOrder(intent: UserIntent) {
        val lang  = intent.detectedLanguage
        val items = intent.items

        if (items.isEmpty()) {
            val msg = Translator.noItem(lang)
            speak(msg, lang); updateStatus(msg); resetMicButton(); return
        }

        val firstItem = items[0].name
        recommender.recordCommand(firstItem)

        VoxBiteAccessibilityService.pendingSwiggyItem = firstItem
        VoxBiteAccessibilityService.swiggyState =
            VoxBiteAccessibilityService.SwiggyState.SEARCHING

        val confirmMsg = intent.confirmationMessage.ifBlank {
            Translator.searchingFood(lang, firstItem)
        }
        speak(confirmMsg, lang)
        updateStatus(Translator.searchingFood(lang, firstItem))

        val swiggyIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.swiggy.com/search?query=${Uri.encode(firstItem)}")
        ).apply {
            setPackage(PKG_SWIGGY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(swiggyIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Swiggy not installed: ${e.message}")
            speak(Translator.notInstalled(lang, "Swiggy"), lang)
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG_SWIGGY"))
            )
        }
        resetMicButton()
    }

    // ═══════════════════════════════════════════════════════════════
    //  OLA
    // ═══════════════════════════════════════════════════════════════

    private fun handleCabBooking(intent: UserIntent) {
        val lang        = intent.detectedLanguage
        val destination = intent.destination.orEmpty().trim()

        if (destination.isBlank()) {
            val msg = Translator.noDest(lang)
            speak(msg, lang); updateStatus(msg); resetMicButton(); return
        }

        val confirmMsg = intent.confirmationMessage.ifBlank {
            Translator.bookingCab(lang, destination)
        }
        speak(confirmMsg, lang)
        launchOla(destination, lang)
        resetMicButton()
    }

    // ═══════════════════════════════════════════════════════════════
    //  OPEN APP
    // ═══════════════════════════════════════════════════════════════

    private fun handleOpenApp(intent: UserIntent) {
        val lang    = intent.detectedLanguage
        val appName = intent.app.replaceFirstChar { it.uppercase() }
        val pkg     = when (intent.app.lowercase()) {
            "swiggy" -> PKG_SWIGGY
            "ola"    -> PKG_OLA
            "zomato" -> PKG_ZOMATO
            else     -> intent.app
        }

        val msg = intent.confirmationMessage.ifBlank { "Opening $appName." }
        speak(msg, lang)
        updateStatus("Opening $appName...")

        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            speak(Translator.notInstalled(lang, appName), lang)
        }
        resetMicButton()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun speak(text: String, languageCode: String = "en-IN") {
        if (!ttsReady) return
        val locale = when (languageCode) {
            "hi-IN" -> Locale("hi", "IN")
            "kn-IN" -> Locale("kn", "IN")
            else    -> Locale("en", "IN")
        }
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale("en", "IN")
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "voxbite_tts")
    }

    private fun updateStatus(text: String) = runOnUiThread { tvStatus.text = text }

    private fun resetMicButton() = runOnUiThread {
        fabMic.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            )
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
        if (!VoxBiteAccessibilityService.isConnected) {
            tvAccessibilityWarning.visibility = View.VISIBLE
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.SEND_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}