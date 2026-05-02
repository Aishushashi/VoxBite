package com.voxbite.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceInputManager(
    private val context: Context,
    private val onListeningStart: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val TAG = "VoxBite"

    // Supported languages — English (India), Hindi, Kannada
    // IETF language tags used by Android SpeechRecognizer
    private val supportedLanguages = listOf("en-IN", "hi-IN", "kn-IN")

    fun startListening() {
        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStart()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    Log.d(TAG, "STT result: $spokenText")
                    onResult(spokenText)
                } else {
                    onError("Could not hear anything. Please try again.")
                }
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO             -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT            -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                    SpeechRecognizer.ERROR_NETWORK           -> "Network error — check internet"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT   -> "Network timeout — check internet"
                    SpeechRecognizer.ERROR_NO_MATCH          -> "Could not understand. Try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY   -> "Recognizer busy. Try again."
                    SpeechRecognizer.ERROR_SERVER            -> "Server error. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT    -> "No speech detected. Tap mic and try again."
                    else                                     -> "Unknown error code: $error"
                }
                Log.e(TAG, "STT error: $message (code=$error)")
                onError(message)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Build the recognizer intent with multilingual support
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            // Primary model — enhanced for Indian accents
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // Primary language — English India
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")

            // Extra languages — Hindi and Kannada
            // Android will auto-detect which language is being spoken
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "kn-IN"))

            // Prefer offline recognition if available (faster, works without internet)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            // Get only the top result
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        Log.d(TAG, "Starting STT — languages: ${supportedLanguages.joinToString(", ")}")
        speechRecognizer?.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}