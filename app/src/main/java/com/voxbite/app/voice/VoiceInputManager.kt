package com.voxbite.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
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
    private val TAG = "VoxBiteVoice"
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        setupRecognizer()
    }

    private fun setupRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                onListeningStart()
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    Log.d(TAG, "Result: $spoken")
                    onResult(spoken)
                } else {
                    onError("Could not understand. Please try again.")
                }
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH        -> "No speech detected. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT  -> "Listening timed out. Try again."
                    SpeechRecognizer.ERROR_NETWORK         -> "Network error. Check connection."
                    SpeechRecognizer.ERROR_AUDIO           -> "Audio error. Check microphone."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Wait a moment."
                    else                                   -> "Speech error ($error). Try again."
                }
                Log.e(TAG, "STT error $error: $msg")
                onError(msg)
            }

            override fun onBeginningOfSpeech()               { Log.d(TAG, "Speaking started") }
            override fun onEndOfSpeech()                     { Log.d(TAG, "Speaking ended") }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partial: Bundle?)  {}
            override fun onRmsChanged(rmsdB: Float)          {}
            override fun onEvent(type: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")

            // EXTRA_ADDITIONAL_LANGUAGES requires API 33+ — guard it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    "android.speech.extra.ADDITIONAL_LANGUAGES",
                    arrayOf("hi-IN", "kn-IN")
                )
            }

            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
        Log.d(TAG, "Started listening")
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "Stopped listening")
    }
}