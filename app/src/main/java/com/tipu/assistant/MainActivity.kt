
package com.tipu.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 40, 30, 40)
            setBackgroundColor(0xFF080D1C.toInt())
        }

        val title = TextView(this).apply {
            text = "TIPU"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(0xFF80E5FF.toInt())
        }

        status = TextView(this).apply {
            text = "नमस्ते! मैं TIPU हूँ।"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(10, 40, 10, 40)
        }

        val listen = Button(this).apply {
            text = "🎙 बोलने के लिए दबाएँ"
            setOnClickListener { startListening() }
        }

        val stop = Button(this).apply {
            text = "Listening बंद करें"
            setOnClickListener {
                speech?.cancel()
                status.text = "Listening बंद है"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(listen)
        layout.addView(stop)
        setContentView(layout)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech Recognition उपलब्ध नहीं है")
            return
        }

        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "TIPU सुन रहा है..."
            }

            override fun onResults(results: Bundle?) {
                val command = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull().orEmpty()
                handleCommand(command)
            }

            override fun onError(error: Int) {
                status.text = "फिर से बोलने की कोशिश करें।"
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        speech?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        val c = command.lowercase(Locale.ROOT)

        when {
            c.contains("कॉल") || c.contains("call") -> {
                val name = c.replace("कॉल", "")
                    .replace("call", "")
                    .replace("करो", "")
                    .replace("लगाओ", "")
                    .trim()

                if (name.isBlank()) speak("किसे कॉल करना है?")
                else findContact(name)
            }

            c.contains("whatsapp") || c.contains("व्हाट्सएप") ->
                openApp("com.whatsapp")

            c.contains("instagram") || c.contains("इंस्टाग्राम") ->
                openApp("com.instagram.android")

            c.contains("youtube") || c.contains("यूट्यूब") ->
                openApp("com.google.android.youtube")

            c.contains("समय") || c.contains("time") ->
                speak("अभी समय " + java.text.SimpleDateFormat(
                    "hh:mm a", Locale("hi", "IN")
                ).format(java.util.Date()))

            else -> speak("मैंने सुना: $command")
        }
    }

    private fun findContact(name: String) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 20)
            speak("कॉन्टैक्ट की अनुमति दें और फिर दोबारा बोलें।")
            return
        }

        var number: String? = null

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(0) ?: ""
                if (contactName.contains(name, ignoreCase = true)) {
                    number = cursor.getString(1)
                    break
                }
            }
        }

        if (number != null) {
            speak("कॉन्टैक्ट मिल गया। डायलर खोल रहा हूँ।")
            startActivity(Intent(Intent.ACTION_DIAL,
                Uri.parse("tel:$number")))
        } else {
            speak("$name नाम का कॉन्टैक्ट नहीं मिला।")
        }
    }

    private fun openApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            startActivity(launch)
            speak("ऐप खोल रहा हूँ।")
        } else {
            speak("यह ऐप इंस्टॉल नहीं है।")
        }
    }

    private fun speak(message: String) {
        status.text = message
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tipu_reply")
    }

    override fun onDestroy() {
        speech?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
