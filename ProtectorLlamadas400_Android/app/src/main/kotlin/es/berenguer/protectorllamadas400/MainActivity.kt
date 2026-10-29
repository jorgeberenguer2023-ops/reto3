package es.berenguer.protectorllamadas400

import android.app.role.RoleManager
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.*
import android.app.TimePickerDialog
import android.Manifest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CallLogFilter {
    ALL,
    BLOCKED,
    OUTGOING
}

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun formatScheduleTime(minutesAfterMidnight: Int): String =
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutesAfterMidnight / 60,
            minutesAfterMidnight % 60
        )

    private fun showTimePicker(title: String, preferenceKey: String, button: Button) {
        val currentMinutes = prefs.getInt(
            preferenceKey,
            if (preferenceKey == "blocking_schedule_start") 22 * 60 else 7 * 60
        )
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val totalMinutes = hour * 60 + minute
                prefs.edit().putInt(preferenceKey, totalMinutes).apply()
                val label = if (preferenceKey == "blocking_schedule_start") "Desde" else "Hasta"
                button.text = "$label ${formatScheduleTime(totalMinutes)}"
            },
            currentMinutes / 60,
            currentMinutes % 60,
            true
        ).apply { setTitle(title) }.show()
    }

    private lateinit var statusCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var blockedList: LinearLayout
    private lateinit var blockedToggleButton: Button
    private lateinit var blockedHideButton: Button
    private lateinit var countryPrefixList: LinearLayout
    private lateinit var contentHost: FrameLayout
    private var scheduleExemptButton: Button? = null
    private var protectionPage: ScrollView? = null
    private var currentPage = "home"
    private val assistantConversation = mutableListOf<AssistantMessage>()
    private var assistantMessageList: LinearLayout? = null
    private var assistantInput: EditText? = null
    private var assistantSpeechRecognizer: SpeechRecognizer? = null
    private var assistantTextToSpeech: TextToSpeech? = null
    private var assistantTextToSpeechReady = false
    private var pendingAssistantSpeech: String? = null
    private var assistantListening = false

    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 1002
        private const val EXPORT_LOG_REQUEST = 1003
        private const val BLOCK_CONTACTS_PERMISSION_REQUEST = 1004
        private const val NOTIFICATION_PERMISSION_REQUEST = 1005
        private const val PICK_BLOCKED_CONTACT_REQUEST = 1006
        private const val DEFAULT_PHONE_ROLE_REQUEST = 1007
        private const val IN_CALL_NOTIFICATION_PERMISSION_REQUEST = 1008
        private const val FIRST_RUN_NOTIFICATION_REQUEST = 1010
        private const val ASSISTANT_AUDIO_PERMISSION_REQUEST = 1011
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val primaryColor = Color.parseColor("#5865E8")
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F6FB"))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#29265F"), primaryColor)
            )
            elevation = dp(4).toFloat()
        }
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandText.addView(TextView(this).apply {
            text = "NoMolesten"
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        brandText.addView(TextView(this).apply {
            text = "Tu tranquilidad, bajo control"
            textSize = 12f
            setTextColor(Color.parseColor("#DDDDFB"))
        })
        toolbar.addView(brandText)
        toolbar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_logo)
            contentDescription = "Logotipo de teléfono rojo"
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(8)
            }
        })
        toolbar.addView(TextView(this).apply {
            contentDescription = "Más opciones"
            setTextColor(Color.WHITE)
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            setOnClickListener { showOverflowMenu(this) }
        })
        shell.addView(toolbar)

        contentHost = FrameLayout(this)
        shell.addView(contentHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        setContentView(shell)
        ViewCompat.requestApplyInsets(shell)
        if (prefs.getBoolean("first_contact_import_complete", false)) {
            showCallLog()
        } else {
            showFirstRunImport()
        }
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Inicio")
            menu.add("Protección y listas")
            menu.add("Reglas de bloqueo")
            menu.add("Registro de llamadas")
            menu.add("Asistente de llamadas")
            menu.add("Importar todos los contactos")
            menu.add("Exportar registro a Excel (CSV)")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Inicio" -> showCallLog()
                    "Protección y listas" -> showProtection()
                    "Reglas de bloqueo" -> showAdvancedRules()
                    "Registro de llamadas" -> showCallLog()
                    "Asistente de llamadas" -> showAssistant()
                    "Importar todos los contactos" -> importContacts()
                    "Exportar registro a Excel (CSV)" -> exportCallLog()
                }
                true
            }
            show()
        }
    }

    /**
     * Displays a local, voice-enabled chat for questions about the saved call history.
     */
    private fun showAssistant() {
        stopAssistantListening()
        currentPage = "assistant"

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }
        page.addView(TextView(this).apply {
            text = "🤖 Asistente de llamadas"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#20204A"))
        })
        page.addView(TextView(this).apply {
            text = "Pregunta por tu registro. El asistente solo usa llamadas guardadas: no escucha llamadas ni accede al buzón. El dictado usa el servicio de voz configurado en Android."
            textSize = 13f
            setTextColor(Color.parseColor("#626477"))
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(6), 0, dp(10))
        })

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            "¿Qué llamadas he recibido?",
            "¿Cuáles están bloqueadas?"
        ).forEach { suggestion ->
            suggestions.addView(Button(this).apply {
                text = suggestion
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.parseColor("#4542A3"))
                background = createButtonBackground(Color.parseColor("#E9E9FF"))
                setOnClickListener { sendAssistantQuestion(suggestion) }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = dp(6) }
            })
        }
        page.addView(suggestions)

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
        assistantMessageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        scroll.addView(assistantMessageList)
        page.addView(scroll)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        assistantInput = EditText(this).apply {
            hint = "Escribe una pregunta…"
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createCardBackground(Color.WHITE, Color.parseColor("#DADBE8"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = dp(6) }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendAssistantQuestion(text.toString())
                    text.clear()
                    true
                } else {
                    false
                }
            }
        }
        inputRow.addView(assistantInput)
        inputRow.addView(Button(this).apply {
            text = "🎙"
            contentDescription = "Dictar pregunta"
            isAllCaps = false
            background = createButtonBackground(Color.parseColor("#E9E9FF"))
            setOnClickListener { startAssistantListening() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(6) })
        inputRow.addView(Button(this).apply {
            text = "Enviar"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = createButtonBackground(Color.parseColor("#5865E8"))
            setOnClickListener {
                val question = assistantInput?.text?.toString().orEmpty()
                sendAssistantQuestion(question)
                assistantInput?.text?.clear()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)
        ))
        page.addView(inputRow)

        contentHost.removeAllViews()
        contentHost.addView(page)
        renderAssistantConversation()
    }

    /**
     * Adds a question and a history-grounded answer to the current chat.
     */
    private fun sendAssistantQuestion(question: String) {
        val cleanedQuestion = question.trim()
        if (cleanedQuestion.isEmpty()) {
            toast("Escribe o dicta una pregunta primero.")
            return
        }

        val calls = try {
            CallLogStore.readAll(this)
        } catch (exception: IOException) {
            toast("No se pudo leer el registro de llamadas.")
            return
        }
        assistantConversation.add(
            AssistantMessage(AssistantMessageSender.USER, cleanedQuestion)
        )
        val answer = CallAssistant.reply(cleanedQuestion, calls)
        assistantConversation.add(
            AssistantMessage(AssistantMessageSender.ASSISTANT, answer)
        )
        renderAssistantConversation()
        speakAssistantReply(answer)
    }

    /**
     * Rebuilds the chat bubbles from the in-memory conversation.
     */
    private fun renderAssistantConversation() {
        val messageList = assistantMessageList ?: return
        messageList.removeAllViews()
        if (assistantConversation.isEmpty()) {
            messageList.addView(TextView(this).apply {
                text = "Hola. Puedo resumir las llamadas recientes y decirte cuáles aparecen como bloqueadas o realizadas."
                textSize = 15f
                setTextColor(Color.parseColor("#45465C"))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F0"))
            })
        } else {
            assistantConversation.forEach { message ->
                val isUser = message.sender == AssistantMessageSender.USER
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = if (isUser) Gravity.END else Gravity.START
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                row.addView(TextView(this).apply {
                    text = message.text
                    textSize = 15f
                    setTextColor(
                        if (isUser) Color.WHITE else Color.parseColor("#303044")
                    )
                    maxWidth = dp(300)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    background = createCardBackground(
                        if (isUser) Color.parseColor("#5865E8") else Color.WHITE,
                        if (isUser) Color.TRANSPARENT else Color.parseColor("#E7E8F0")
                    )
                })
                messageList.addView(row)
            }
        }
        messageList.post {
            (messageList.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    /**
     * Requests microphone permission before starting Android's Spanish speech recognizer.
     */
    private fun startAssistantListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                ASSISTANT_AUDIO_PERMISSION_REQUEST
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No hay un servicio de reconocimiento de voz disponible.")
            return
        }
        stopAssistantListening()
        assistantSpeechRecognizer = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        val recognizer = assistantSpeechRecognizer ?: run {
            toast("No se pudo iniciar el reconocimiento de voz.")
            return
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                assistantListening = true
                toast("Te escucho. Habla ahora.")
            }

            override fun onResults(results: Bundle?) {
                assistantListening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { recognizedText ->
                        assistantInput?.setText(recognizedText)
                        assistantInput?.setSelection(recognizedText.length)
                    } ?: toast("No se reconoció ninguna frase.")
                destroyAssistantSpeechRecognizer()
            }

            override fun onError(error: Int) {
                assistantListening = false
                toast(assistantSpeechErrorMessage(error))
                destroyAssistantSpeechRecognizer()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        assistantListening = true
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Haz una pregunta sobre tus llamadas")
            }
        )
    }

    /**
     * Converts the recognizer's technical failure codes into actionable Spanish feedback.
     */
    private fun assistantSpeechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió la frase. Inténtalo de nuevo."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz. Vuelve a intentarlo."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El servicio de voz no está disponible ahora."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Android no concedió permiso para usar el micrófono."
        else -> "Falló el reconocimiento de voz (código $error)."
    }

    /**
     * Creates the device's Spanish text-to-speech engine when the first answer is ready.
     */
    private fun speakAssistantReply(text: String) {
        if (assistantTextToSpeech == null) {
            pendingAssistantSpeech = text
            assistantTextToSpeech = TextToSpeech(this) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    pendingAssistantSpeech = null
                    toast("No se pudo iniciar la lectura en voz alta.")
                    return@TextToSpeech
                }
                val engine = assistantTextToSpeech ?: return@TextToSpeech
                val languageStatus = engine.setLanguage(Locale("es", "ES"))
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                    languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    pendingAssistantSpeech = null
                    toast("No está instalada una voz española en Android.")
                    return@TextToSpeech
                }
                assistantTextToSpeechReady = true
                pendingAssistantSpeech?.let(::speakAssistantText)
                pendingAssistantSpeech = null
            }
            return
        }
        if (assistantTextToSpeechReady) {
            speakAssistantText(text)
        } else {
            pendingAssistantSpeech = text
        }
    }

    /**
     * Speaks an answer after the Android speech engine has initialized.
     */
    private fun speakAssistantText(text: String) {
        val result = assistantTextToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "call_assistant_reply"
        )
        if (result == TextToSpeech.ERROR) {
            toast("Android no pudo reproducir la respuesta.")
        }
    }

    /**
     * Stops and releases the active recognizer so the microphone is not left in use.
     */
    private fun stopAssistantListening() {
        assistantListening = false
        destroyAssistantSpeechRecognizer()
    }

    private fun destroyAssistantSpeechRecognizer() {
        assistantSpeechRecognizer?.destroy()
        assistantSpeechRecognizer = null
    }

    private fun showFirstRunImport() {
        // Contact import unlocks local caller-name lookups and schedule contact selection.
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(24), dp(22), dp(24))
        }
        val welcomeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F3"))
            elevation = dp(2).toFloat()
        }
        welcomeCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_logo)
            contentDescription = "Icono de NoMolesten"
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply {
                bottomMargin = dp(20)
            }
        })
        welcomeCard.addView(TextView(this).apply {
            text = "Bienvenido a NoMolesten"
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#20204A"))
            gravity = Gravity.CENTER
        })
        welcomeCard.addView(TextView(this).apply {
            text = "Empieza importando tus contactos"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#5865E8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        })
        welcomeCard.addView(TextView(this).apply {
            text = "Veremos los nombres guardados cuando te llamen y podrás elegir contactos para excluir del horario de bloqueo. Android no permite importar miembros ni grupos de WhatsApp."
            textSize = 14f
            setTextColor(Color.parseColor("#626477"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(22))
        })
        welcomeCard.addView(Button(this).apply {
            text = "Importar contactos"
            setOnClickListener { importContacts() }
            isAllCaps = false
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createButtonBackground(Color.parseColor("#5865E8"))
            setTextColor(Color.WHITE)
            minHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        page.addView(welcomeCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        contentHost.removeAllViews()
        contentHost.addView(page)
        currentPage = "first_import"
    }

    private fun showProtection() {
        stopAssistantListening()
        val page = protectionPage ?: buildProtectionPage().also { protectionPage = it }
        contentHost.removeAllViews()
        contentHost.addView(page)
        currentPage = "protection"
        refreshStatus()
        refreshLists()
    }

    private fun buildProtectionPage(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(root)

        // App Title
        root.addView(TextView(this).apply {
            text = "Protección"
            textSize = 27f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#20204A"))
        })

        root.addView(TextView(this).apply {
            text = "Controla quién puede llamarte y cuándo."
            textSize = 14f
            setTextColor(Color.parseColor("#686A7C"))
            setPadding(0, dp(4), 0, dp(18))
        })

        // Status Card
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createCardBackground(Color.parseColor("#EEF0FF"), Color.parseColor("#D9DBFF"))
            elevation = dp(2).toFloat()
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(Color.parseColor("#34356D"))
        }
        statusCard.addView(statusText)

        val btnRole = Button(this).apply {
            text = "Configurar Permiso de Llamadas"
            setOnClickListener { requestCallScreeningRole() }
            isAllCaps = false
            background = createButtonBackground(Color.parseColor("#5865E8"))
            setTextColor(Color.WHITE)
            minHeight = dp(48)
        }
        val roleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
        statusCard.addView(btnRole, roleParams)
        statusCard.addView(Button(this).apply {
            text = "Configurar teléfono predeterminado"
            setOnClickListener { requestDefaultPhoneRole() }
            isAllCaps = false
            background = createButtonBackground(Color.parseColor("#35327C"))
            setTextColor(Color.WHITE)
            minHeight = dp(48)
        }, roleParams)
        statusCard.addView(TextView(this).apply {
            text = "Para finalizar y bloquear una llamada ya contestada, NoMolesten debe ser la aplicación de teléfono predeterminada."
            textSize = 12f
            setTextColor(Color.parseColor("#626477"))
            setPadding(0, dp(12), 0, 0)
        })

        root.addView(statusCard)

        // Switch Card / Section
        val switchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F0"))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        switchContainer.addView(Switch(this).apply {
            text = "Bloquear automáticamente prefijos 400"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            isChecked = prefs.getBoolean("block_400", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("block_400", checked).apply()
                toast(if (checked) "Bloqueo de 400 activado" else "Bloqueo de 400 desactivado")
            }
        })
        root.addView(switchContainer)

        val scheduleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createCardBackground(
                Color.parseColor("#F0F0FF"),
                Color.parseColor("#E0E1F5")
            )
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        scheduleContainer.addView(TextView(this).apply {
            text = "Horario de bloqueo"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#292D80"))
        })
        scheduleContainer.addView(TextView(this).apply {
            text = "Las reglas de bloqueo configuradas solo se aplican durante este horario."
            textSize = 14f
            setTextColor(Color.parseColor("#55566D"))
            setPadding(0, 8, 0, 12)
        })

        scheduleContainer.addView(Switch(this).apply {
            text = "Activar horario"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            isChecked = prefs.getBoolean("blocking_schedule_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("blocking_schedule_enabled", checked).apply()
                toast(if (checked) "Horario de bloqueo activado" else "Horario desactivado")
                refreshStatus()
            }
        })

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val startTimeButton = Button(this).apply {
            text = "Desde ${formatScheduleTime(prefs.getInt("blocking_schedule_start", 22 * 60))}"
            setOnClickListener {
                showTimePicker("Hora de inicio", "blocking_schedule_start", this)
            }
            background = createButtonBackground(Color.parseColor("#4542A3"))
            setTextColor(Color.WHITE)
            isAllCaps = false
            minHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = dp(8) }
        }
        val endTimeButton = Button(this).apply {
            text = "Hasta ${formatScheduleTime(prefs.getInt("blocking_schedule_end", 7 * 60))}"
            setOnClickListener {
                showTimePicker("Hora de fin", "blocking_schedule_end", this)
            }
            background = createButtonBackground(Color.parseColor("#4542A3"))
            setTextColor(Color.WHITE)
            isAllCaps = false
            minHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        timeRow.addView(startTimeButton)
        timeRow.addView(endTimeButton)
        scheduleContainer.addView(timeRow)

        scheduleContainer.addView(TextView(this).apply {
            text = "Días"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 12, 0, 4)
        })

        val dayLabels = listOf(
            2 to "Lunes",
            3 to "Martes",
            4 to "Miércoles",
            5 to "Jueves",
            6 to "Viernes",
            7 to "Sábado",
            1 to "Domingo"
        )
        val selectedScheduleDays = getScheduleDays()
        for ((calendarDay, label) in dayLabels) {
            scheduleContainer.addView(CheckBox(this).apply {
                text = label
                setTextColor(Color.parseColor("#333333"))
                isChecked = calendarDay.toString() in selectedScheduleDays
                setOnCheckedChangeListener { _, checked ->
                    val days = getScheduleDays()
                    if (checked) days.add(calendarDay.toString())
                    else days.remove(calendarDay.toString())
                    saveSet("blocking_schedule_days", days)
                }
            })
        }
        scheduleExemptButton = Button(this).apply {
            text = "Elegir contactos exentos (${getSet("blocking_schedule_exempt_numbers").size})"
            setOnClickListener { showScheduleExemptPicker(this) }
        }
        scheduleContainer.addView(scheduleExemptButton)
        scheduleContainer.addView(Button(this).apply {
            text = "Añadir número exento"
            setOnClickListener { showAddScheduleExemptNumberDialog() }
        })
        root.addView(scheduleContainer)

        val countryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createCardBackground(Color.WHITE, Color.parseColor("#E0E0E0"))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        countryContainer.addView(TextView(this).apply {
            text = "Países permitidos"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        })

        countryContainer.addView(TextView(this).apply {
            text = "Si añades prefijos, solo se permitirán llamadas con uno de ellos. Ejemplo: +34 para España. Los números ocultos o sin prefijo internacional identificable se bloquearán."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 8, 0, 12)
        })

        val countryPrefixOptions = PhoneRules.countryPrefixOptions()
        val countryPrefixSelector = AutoCompleteTextView(this).apply {
            setAdapter(ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_dropdown_item_1line,
                countryPrefixOptions.map { it.label }
            ))
            threshold = 0
            background = createCardBackground(
                Color.parseColor("#F1F3F5"),
                Color.parseColor("#CED4DA")
            )
            val defaultPrefix = countryPrefixOptions.firstOrNull { it.prefix == "+34" }
                ?: countryPrefixOptions.firstOrNull()
            if (defaultPrefix != null) setText(defaultPrefix.label, false)
            setOnClickListener { showDropDown() }
        }
        countryContainer.addView(countryPrefixSelector)

        countryContainer.addView(Button(this).apply {
            text = "Permitir prefijo"
            setOnClickListener {
                val selected = countryPrefixOptions.firstOrNull {
                    it.label == countryPrefixSelector.text.toString()
                }
                if (selected == null) {
                    toast("Elige un país o prefijo de la lista.")
                    return@setOnClickListener
                }

                val prefixes = getSet("allowed_country_prefixes")
                if (!prefixes.add(selected.prefix)) {
                    toast("El prefijo ${selected.prefix} ya está permitido.")
                    return@setOnClickListener
                }

                saveSet("allowed_country_prefixes", prefixes)
                refreshLists()
                toast("Prefijo ${selected.prefix} añadido.")
            }
            background = createButtonBackground(Color.parseColor("#5865E8"))
            setTextColor(Color.WHITE)
            isAllCaps = false
            minHeight = dp(46)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        })

        countryPrefixList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        countryContainer.addView(countryPrefixList)
        root.addView(countryContainer)

        // Add Number Section
        val addContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createCardBackground(Color.WHITE, Color.parseColor("#E0E0E0"))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        addContainer.addView(TextView(this).apply {
            text = "Bloquear Nuevo Número"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 12)
        })

        val input = EditText(this).apply {
            hint = "Ej.: 600123456 o +34600123456"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(24, 24, 24, 24)
            background = createCardBackground(Color.parseColor("#F1F3F5"), Color.parseColor("#CED4DA"))
        }
        addContainer.addView(input)

        val btnBlock = Button(this).apply {
            text = "Bloquear Número"
            setOnClickListener {
                val raw = input.text.toString()
                val n = PhoneRules.normalize(raw)
                val matchingKeys = PhoneRules.matchingKeys(raw)
                if (n.length == 9) {
                    val blocked = getSet("blocked_numbers")
                    if (blocked.contains(n)) {
                        toast("Este número ya está bloqueado.")
                        return@setOnClickListener
                    }
                    blocked.add(n)
                    val allowed = getSet("allowed_numbers")
                    allowed.removeAll(matchingKeys)
                    saveSet("blocked_numbers", blocked)
                    saveSet("allowed_numbers", allowed)
                    input.text.clear()
                    refreshLists()
                    toast("Número bloqueado con éxito.")
                } else {
                    toast("Por favor, introduce un número español válido de 9 dígitos.")
                }
            }
            background = createButtonBackground(Color.parseColor("#4542A3"))
            setTextColor(Color.WHITE)
            isAllCaps = false
            minHeight = dp(46)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        addContainer.addView(btnBlock)
        root.addView(addContainer)

        blockedToggleButton = Button(this).apply {
            setOnClickListener {
                if (prefs.getBoolean("blocked_numbers_hidden_until_import", false)) {
                    toast("Importa los contactos para volver a mostrar esta lista.")
                } else {
                    prefs.edit()
                        .putBoolean(
                            "blocked_numbers_expanded",
                            !prefs.getBoolean("blocked_numbers_expanded", false)
                        )
                        .apply()
                    refreshLists()
                }
            }
        }
        root.addView(blockedToggleButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })
        blockedHideButton = Button(this).apply {
            text = "No mostrar"
            setOnClickListener {
                prefs.edit()
                    .putBoolean("blocked_numbers_hidden_until_import", true)
                    .putBoolean("blocked_numbers_expanded", false)
                    .apply()
                refreshLists()
            }
        }
        root.addView(blockedHideButton)

        blockedList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(blockedList)

        // Clear All Button
        val btnClearAll = Button(this).apply {
            text = "Vaciar Todas las Listas"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("¿Vaciar listas?")
                    .setMessage(
                        "Se eliminarán todos los números bloqueados, la lista blanca y los prefijos de países permitidos."
                    )
                    .setPositiveButton("Vaciar") { _, _ ->
                        saveSet("blocked_numbers", mutableSetOf())
                        saveSet("allowed_numbers", mutableSetOf())
                        saveSet("allowed_country_prefixes", mutableSetOf())
                        prefs.edit()
                            .putBoolean("blocked_numbers_hidden_until_import", false)
                            .putBoolean("blocked_numbers_expanded", false)
                            .apply()
                        refreshLists()
                        toast("Listas vaciadas.")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            setBackgroundColor(Color.parseColor("#616161"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        root.addView(btnClearAll)

        refreshStatus()
        refreshLists()
        return scroll
    }

    private fun showAdvancedRules() {
        stopAssistantListening()
        // Create the channel before sending the user to Android's per-channel settings.
        CallAlertNotification.ensureChannel(this)
        val page = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
        }
        page.addView(root)
        root.addView(TextView(this).apply {
            text = "Reglas de bloqueo"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            setPadding(0, 0, 0, 20)
        })

        val contactsCard = createRuleCard(root, "Contactos")
        val blockExceptContacts = Switch(this).apply {
            text = "Bloquear todos excepto mis contactos"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            isChecked = prefs.getBoolean("block_except_contacts", false)
            setOnCheckedChangeListener { button, checked ->
                if (!checked) {
                    prefs.edit().putBoolean("block_except_contacts", false).apply()
                } else if (checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    importAllContacts(enableBlockExceptContacts = true)
                } else {
                    button.isChecked = false
                    requestPermissions(
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        BLOCK_CONTACTS_PERMISSION_REQUEST
                    )
                }
            }
        }
        contactsCard.addView(blockExceptContacts)
        contactsCard.addView(TextView(this).apply {
            text = "Al activarlo se actualizará la lista permitida con los números de la agenda."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        })
        contactsCard.addView(Button(this).apply {
            text = "Bloquear un contacto concreto"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                    PICK_BLOCKED_CONTACT_REQUEST
                )
            }
        })

        val privacyCard = createRuleCard(root, "Números privados")
        privacyCard.addView(Switch(this).apply {
            text = "Bloquear números ocultos o privados"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            isChecked = prefs.getBoolean("block_private_numbers", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("block_private_numbers", checked).apply()
            }
        })
        privacyCard.addView(TextView(this).apply {
            text = "Android no siempre entrega el número cuando el llamante lo oculta."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        })

        val prefixCard = createRuleCard(root, "Bloquear números por prefijo")
        val prefixInput = EditText(this).apply {
            hint = "Prefijo, por ejemplo 900 o +3491"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            background = createCardBackground(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#CED4DA")
            )
            setPadding(20, 16, 20, 16)
        }
        prefixCard.addView(prefixInput)
        val prefixList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var refreshPrefixList: () -> Unit
        refreshPrefixList = {
            prefixList.removeAllViews()
            getSet("blocked_prefixes").toList().sorted().forEach { prefix ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(this).apply {
                    text = prefix
                    textSize = 16f
                    setTextColor(Color.parseColor("#333333"))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                row.addView(Button(this).apply {
                    text = "Quitar"
                    setOnClickListener {
                        val prefixes = getSet("blocked_prefixes")
                        prefixes.remove(prefix)
                        saveSet("blocked_prefixes", prefixes)
                        refreshPrefixList()
                    }
                })
                prefixList.addView(row)
            }
            if (prefixList.childCount == 0) {
                prefixList.addView(TextView(this).apply {
                    text = "No hay prefijos bloqueados manualmente."
                    setTextColor(Color.parseColor("#777777"))
                    setPadding(0, 12, 0, 0)
                })
            }
        }
        prefixCard.addView(Button(this).apply {
            text = "Añadir prefijo"
            setOnClickListener {
                val prefix = PhoneRules.normalizeBlockedPrefix(prefixInput.text.toString())
                if (prefix.isEmpty()) {
                    toast("Introduce un prefijo válido.")
                    return@setOnClickListener
                }
                val prefixes = getSet("blocked_prefixes")
                if (!prefixes.add(prefix)) {
                    toast("Ese prefijo ya está bloqueado.")
                    return@setOnClickListener
                }
                saveSet("blocked_prefixes", prefixes)
                prefixInput.text.clear()
                refreshPrefixList()
            }
            setBackgroundColor(Color.parseColor("#5865E8"))
            setTextColor(Color.WHITE)
        })
        prefixCard.addView(prefixList)
        refreshPrefixList()

        val alertsCard = createRuleCard(root, "Aviso durante la llamada")
        alertsCard.addView(Switch(this).apply {
            text = "Mostrar notificación emergente con opción de bloquear"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            isChecked = prefs.getBoolean("call_alert_enabled", true)
            setOnCheckedChangeListener { button, checked ->
                if (checked && android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    prefs.edit().putBoolean("call_alert_enabled", false).apply()
                    button.isChecked = false
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST
                    )
                } else {
                    prefs.edit().putBoolean("call_alert_enabled", checked).apply()
                }
            }
        })
        alertsCard.addView(TextView(this).apply {
            text = "Para que aparezca sobre otras aplicaciones, concede notificaciones, activa las notificaciones emergentes del canal y permite la pantalla completa si Android lo solicita."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        })
        alertsCard.addView(Button(this).apply {
            text = "Configurar notificación emergente"
            setOnClickListener { openAlertChannelSettings() }
        })
        alertsCard.addView(Button(this).apply {
            text = "Permitir aviso a pantalla completa"
            setOnClickListener { openAlertSettings() }
        })

        contentHost.removeAllViews()
        contentHost.addView(page)
        currentPage = "advanced"
    }

    private fun createRuleCard(root: LinearLayout, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = createCardBackground(
                Color.parseColor("#F0EFFC"),
                Color.parseColor("#DDD9F6")
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#292D80"))
            setPadding(0, 0, 0, 12)
        })
        root.addView(card)
        return card
    }

    private fun createCardBackground(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(bgColor)
            if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
        }
    }

    private fun createButtonBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun getSet(key: String): MutableSet<String> {
        return (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
    }

    private fun saveSet(key: String, value: MutableSet<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    private fun getScheduleDays(): MutableSet<String> {
        return if (prefs.contains("blocking_schedule_days")) {
            getSet("blocking_schedule_days")
        } else {
            mutableSetOf("1", "2", "3", "4", "5", "6", "7")
        }
    }

    private fun showScheduleExemptPicker(button: Button) {
        // Include custom numbers already exempted so they can also be deselected.
        val selected = getSet("blocking_schedule_exempt_numbers")
        val contactOptions = ContactDirectory.scheduleOptions(this)
        val contactNumbers = contactOptions.mapTo(mutableSetOf()) { it.first }
        val options = contactOptions + selected
            .filterNot { it in contactNumbers }
            .map { it to it }
        if (options.isEmpty()) {
            toast("Importa primero los contactos de la agenda.")
            return
        }
        val labels = options.map { (number, name) -> "$name — $number" }.toTypedArray()
        val checked = options.map { (number, _) -> number in selected }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Contactos exentos del horario")
            .setMultiChoiceItems(labels, checked) { _, index, isChecked ->
                val number = options[index].first
                if (isChecked) selected.add(number) else selected.remove(number)
            }
            .setPositiveButton("Guardar") { _, _ ->
                saveSet("blocking_schedule_exempt_numbers", selected)
                button.text = "Elegir contactos exentos (${selected.size})"
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddScheduleExemptNumberDialog() {
        val input = EditText(this).apply {
            hint = "Ej.: 600123456 o +34600123456"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(this)
            .setTitle("Añadir número exento")
            .setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val rawNumber = input.text.toString().trim()
                val keys = PhoneRules.matchingKeys(rawNumber)
                if (keys.isEmpty()) {
                    toast("Introduce un número válido.")
                    return@setPositiveButton
                }
                val selected = getSet("blocking_schedule_exempt_numbers")
                selected.add(PhoneRules.preferredMatchKey(rawNumber).ifBlank { keys.first() })
                saveSet("blocking_schedule_exempt_numbers", selected)
                scheduleExemptButton?.text =
                    "Elegir contactos exentos (${selected.size})"
                toast("Número añadido a las excepciones del horario.")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openAlertSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
            }
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        try {
            startActivity(intent)
        } catch (exception: android.content.ActivityNotFoundException) {
            toast("No se pudieron abrir los ajustes de notificaciones de Android.")
        }
    }

    private fun openAlertChannelSettings() {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CallAlertNotification.CHANNEL_ID)
                }
            )
        } catch (exception: android.content.ActivityNotFoundException) {
            toast("No se pudieron abrir los ajustes del aviso de llamada.")
        }
    }

    private fun importContacts() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_CONTACTS),
                CONTACTS_PERMISSION_REQUEST
            )
            return
        }
        importAllContacts()
    }

    private fun importAllContacts(enableBlockExceptContacts: Boolean = false) {
        Thread {
            try {
                val allowed = getSet("allowed_numbers")
                val contacts = linkedMapOf<String, String>()
                // Imported contacts are caller-ID data unless the all-contacts rule is enabled.
                val keepContactsAllowed = enableBlockExceptContacts ||
                    prefs.getBoolean("block_except_contacts", false)
                var imported = 0
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                ) ?: throw IOException("Android no pudo leer la agenda.")

                cursor.use {
                    val numberColumn = it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    val nameColumn = it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                    while (it.moveToNext()) {
                        val rawNumber = it.getString(numberColumn) ?: continue
                        val matchKey = PhoneRules.preferredMatchKey(rawNumber)
                        if (matchKey.isEmpty()) continue
                        val name = it.getString(nameColumn).orEmpty()
                        val matchingKeys = PhoneRules.matchingKeys(rawNumber)
                        contacts[rawNumber] = name
                        allowed.removeAll(matchingKeys)
                        if (keepContactsAllowed) {
                            if (allowed.addAll(matchingKeys)) imported++
                        }
                    }
                }

                ContactDirectory.save(this, contacts)
                prefs.edit()
                    .putStringSet("allowed_numbers", allowed)
                    .putBoolean(
                        "block_except_contacts",
                        enableBlockExceptContacts || prefs.getBoolean(
                            "block_except_contacts",
                            false
                        )
                    )
                    .putBoolean("blocked_numbers_hidden_until_import", false)
                    .putBoolean("first_contact_import_complete", true)
                    .apply()

                runOnUiThread {
                    refreshListsIfVisible()
                    if (keepContactsAllowed) {
                        toast("Se importaron ${contacts.size} contactos y se añadieron $imported números permitidos.")
                    } else {
                        toast("Se importaron ${contacts.size} contactos y se guardaron sus nombres. No se importan grupos de WhatsApp.")
                    }
                    if (currentPage == "first_import") {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissions(
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                FIRST_RUN_NOTIFICATION_REQUEST
                            )
                        } else {
                            showCallLog()
                        }
                    }
                }
            } catch (exception: SecurityException) {
                runOnUiThread {
                    toast("Android no permitió acceder a tus contactos.")
                }
            } catch (exception: IOException) {
                runOnUiThread {
                    toast(exception.message ?: "No se pudieron importar los contactos.")
                }
            }
        }.start()
    }

    private fun refreshListsIfVisible() {
        if (::statusText.isInitialized && protectionPage?.parent != null) {
            refreshLists()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                importAllContacts()
            } else {
                toast("No se importaron contactos porque no se concedió el permiso.")
            }
        } else if (requestCode == BLOCK_CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                importAllContacts(enableBlockExceptContacts = true)
            } else {
                toast("No se activó la regla porque se denegó el acceso a contactos.")
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("call_alert_enabled", granted).apply()
            if (!granted) {
                toast("No se activó el aviso porque se denegó el permiso de notificaciones.")
            }
            if (currentPage == "advanced") showAdvancedRules()
        } else if (requestCode == IN_CALL_NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                toast("Sin notificaciones, Android no podrá mostrar los controles de llamadas.")
            }
            refreshStatus()
        } else if (requestCode == FIRST_RUN_NOTIFICATION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("call_alert_enabled", granted).apply()
            if (!granted) {
                toast("Puedes activar los avisos emergentes más tarde desde Reglas de bloqueo.")
            }
            showCallLog()
        } else if (requestCode == ASSISTANT_AUDIO_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startAssistantListening()
            } else {
                toast("No se concedió permiso de micrófono para dictar preguntas.")
            }
        }
    }

    private fun showCallLog(filter: CallLogFilter = CallLogFilter.ALL) {
        stopAssistantListening()
        val allEntries = try {
            CallLogStore.readAll(this)
        } catch (exception: IOException) {
            toast("No se pudo leer el registro de llamadas.")
            return
        }
        val entries = when (filter) {
            CallLogFilter.ALL -> allEntries
            CallLogFilter.BLOCKED -> allEntries.filter { it.blocked }
            CallLogFilter.OUTGOING -> allEntries.filter {
                it.direction == CallDirection.OUTGOING
            }
        }

        val scroll = ScrollView(this).apply {
            clipToPadding = false
        }
        val logPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        logPage.addView(TextView(this).apply {
            text = "Registro de llamadas"
            textSize = 27f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#20204A"))
        })
        logPage.addView(TextView(this).apply {
            text = "Revisa las llamadas recibidas, bloqueadas y realizadas."
            textSize = 14f
            setTextColor(Color.parseColor("#686A7C"))
            setPadding(0, dp(4), 0, dp(14))
        })
        val tabsContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val tabs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabsContent)
        }
        val blockedCount = allEntries.count { it.blocked }
        val outgoingCount = allEntries.count { it.direction == CallDirection.OUTGOING }
        val filters = listOf(
            Triple(CallLogFilter.ALL, "Todas", allEntries.size),
            Triple(CallLogFilter.BLOCKED, "Bloqueadas", blockedCount),
            Triple(CallLogFilter.OUTGOING, "Realizadas", outgoingCount)
        )
        filters.forEach { (tabFilter, label, count) ->
            val selected = filter == tabFilter
            tabsContent.addView(Button(this).apply {
                text = "$label · $count"
                setOnClickListener { showCallLog(tabFilter) }
                background = createButtonBackground(
                    if (selected) Color.parseColor("#5865E8")
                    else Color.WHITE
                )
                setTextColor(
                    if (selected) Color.WHITE
                    else Color.parseColor("#4542A3")
                )
                isAllCaps = false
                textSize = 13f
                minHeight = dp(42)
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            })
        }
        logPage.addView(tabs)
        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F0"))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(12)
            }
        }
        summaryCard.addView(TextView(this).apply {
            text = "${entries.size} llamadas en esta vista"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#30315F"))
        })
        summaryCard.addView(TextView(this).apply {
            text = "$blockedCount bloqueadas   ·   $outgoingCount realizadas"
            textSize = 13f
            setTextColor(Color.parseColor("#686A7C"))
            setPadding(0, dp(4), 0, 0)
        })
        logPage.addView(summaryCard)
        logPage.addView(Button(this).apply {
            text = "Preguntar al asistente"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = createButtonBackground(Color.parseColor("#4542A3"))
            minHeight = dp(44)
            setOnClickListener { showAssistant() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        })
        if (filter == CallLogFilter.BLOCKED) {
            val voicemailCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = createCardBackground(
                    Color.parseColor("#F0F0FF"),
                    Color.parseColor("#DEDEFA")
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
            }
            voicemailCard.addView(TextView(this).apply {
                text = "Buzón de voz del operador"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#30315F"))
            })
            voicemailCard.addView(TextView(this).apply {
                text = "Las llamadas bloqueadas aparecen aquí como llamadas. Si tu operador las envía al buzón, abre Teléfono para escuchar o borrar los mensajes. NoMolesten no puede leer ni eliminar los audios del operador."
                textSize = 13f
                setTextColor(Color.parseColor("#626477"))
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(0, dp(6), 0, dp(10))
            })
            voicemailCard.addView(Button(this).apply {
                text = "Abrir aplicación Teléfono"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = createButtonBackground(Color.parseColor("#4542A3"))
                minHeight = dp(44)
                setOnClickListener { openCarrierPhoneApp() }
            })
            voicemailCard.addView(Button(this).apply {
                text = "Gestionar números bloqueados"
                isAllCaps = false
                setTextColor(Color.parseColor("#4542A3"))
                background = createButtonBackground(Color.parseColor("#E3E3FF"))
                minHeight = dp(44)
                setOnClickListener { showProtection() }
            })
            logPage.addView(voicemailCard)
        }
        logPage.addView(Button(this).apply {
            text = "Exportar registro (CSV)"
            setOnClickListener { exportCallLog() }
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#4542A3"))
            background = createButtonBackground(Color.parseColor("#E9E9FF"))
            minHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        })

        if (entries.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(30), dp(24), dp(30))
                background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F0"))
            }
            emptyCard.addView(TextView(this).apply {
                text = "☎"
                textSize = 32f
                setTextColor(Color.parseColor("#9697BC"))
                gravity = Gravity.CENTER
            })
            emptyCard.addView(TextView(this).apply {
                text = when (filter) {
                    CallLogFilter.ALL -> "Todavía no hay llamadas"
                    CallLogFilter.BLOCKED -> "No hay llamadas bloqueadas"
                    CallLogFilter.OUTGOING -> "Todavía no hay llamadas realizadas"
                }
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#45465C"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            logPage.addView(emptyCard)
        } else {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
            for (entry in entries.asReversed().take(300)) {
                val result = when {
                    entry.direction == CallDirection.OUTGOING -> "Realizada"
                    entry.blocked -> "Bloqueada"
                    else -> "Recibida"
                }
                val resultColor = if (entry.blocked) {
                    Color.parseColor("#C03945")
                } else {
                    Color.parseColor("#338565")
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    background = createCardBackground(Color.WHITE, Color.parseColor("#E7E8F0"))
                    elevation = dp(1).toFloat()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(9) }
                }
                row.addView(TextView(this).apply {
                    text = if (entry.blocked) "×" else "☎"
                    textSize = 19f
                    gravity = Gravity.CENTER
                    setTextColor(resultColor)
                    background = createCardBackground(
                        if (entry.blocked) Color.parseColor("#FFF0F1")
                        else Color.parseColor("#EAF6F0"),
                        Color.TRANSPARENT
                    )
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                        marginEnd = dp(12)
                    }
                })
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                details.addView(TextView(this).apply {
                    text = entry.contactName?.let { "$it\n${entry.number}" } ?: entry.number
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#292A42"))
                })
                details.addView(TextView(this).apply {
                    text = "${dateFormat.format(Date(entry.timestamp))}  ·  $result"
                    textSize = 12f
                    setTextColor(resultColor)
                    setPadding(0, dp(4), 0, 0)
                })
                row.addView(details)
                logPage.addView(row)
            }
        }

        scroll.addView(logPage)
        contentHost.removeAllViews()
        contentHost.addView(scroll)
        currentPage = "log"
    }

    private fun openCarrierPhoneApp() {
        val dialIntent = Intent(Intent.ACTION_DIAL)
        val phoneApp = packageManager
            .queryIntentActivities(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName != packageName }
            ?.activityInfo

        if (phoneApp == null) {
            toast("No se encontró otra aplicación Teléfono. Abre el buzón desde la app de tu operador.")
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(phoneApp.packageName)
            ?: dialIntent.setClassName(phoneApp.packageName, phoneApp.name)
        try {
            startActivity(launchIntent)
        } catch (exception: ActivityNotFoundException) {
            toast("No se pudo abrir la aplicación Teléfono.")
        }
    }

    private fun exportCallLog() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "registro_llamadas.csv")
            },
            EXPORT_LOG_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DEFAULT_PHONE_ROLE_REQUEST) {
            if (isDefaultPhoneRoleHeld()) {
                requestInCallNotifications()
            } else {
                toast("No se activó el bloqueo de llamadas ya contestadas.")
            }
            refreshStatus()
            return
        }
        if (resultCode != RESULT_OK) return
        if (requestCode == PICK_BLOCKED_CONTACT_REQUEST) {
            val uri = data?.data ?: return
            try {
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val cursor = contentResolver.query(uri, projection, null, null, null)
                    ?: throw IOException("Android no pudo leer el número del contacto.")
                val rawNumber = cursor.use {
                    if (!it.moveToFirst()) null
                    else it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    )
                }
                if (rawNumber.isNullOrBlank()) {
                    toast("El contacto seleccionado no tiene un número.")
                    return
                }
                val blocked = getSet("blocked_numbers")
                blocked.addAll(PhoneRules.matchingKeys(rawNumber))
                val allowed = getSet("allowed_numbers")
                allowed.removeAll(PhoneRules.matchingKeys(rawNumber))
                prefs.edit()
                    .putStringSet("blocked_numbers", blocked)
                    .putStringSet("allowed_numbers", allowed)
                    .apply()
                toast("Contacto añadido a la lista de bloqueados.")
                refreshListsIfVisible()
            } catch (exception: SecurityException) {
                toast("Android no permitió leer el número del contacto.")
            } catch (exception: IOException) {
                toast(exception.message ?: "No se pudo bloquear el contacto.")
            }
            return
        }
        if (requestCode != EXPORT_LOG_REQUEST) return
        val uri = data?.data ?: run {
            toast("No se seleccionó un archivo para exportar.")
            return
        }

        try {
            writeCallLogCsv(uri)
            toast("Registro exportado correctamente.")
        } catch (exception: IOException) {
            toast("No se pudo guardar el archivo exportado.")
        }
    }

    private fun writeCallLogCsv(uri: Uri) {
        val entries = CallLogStore.readAll(this)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val output = contentResolver.openOutputStream(uri)
            ?: throw IOException("Android no pudo abrir el archivo seleccionado.")

        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFFNombre;Número;Fecha y hora;Tipo;Resultado\r\n")
            for (entry in entries) {
                val type = if (entry.direction == CallDirection.OUTGOING) {
                    "Realizada"
                } else {
                    "Recibida"
                }
                val result = if (entry.blocked) "Bloqueada" else "Permitida"
                writer.write(
                    "${csvCell(entry.contactName.orEmpty())};" +
                        "${csvCell(entry.number)};" +
                        "${csvCell(dateFormat.format(Date(entry.timestamp)))};" +
                        "${csvCell(type)};" +
                        "${csvCell(result)}\r\n"
                )
            }
        }
    }

    private fun csvCell(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun refreshLists() {
        blockedList.removeAllViews()
        countryPrefixList.removeAllViews()

        val blocked = getSet("blocked_numbers").toList().sorted()
        val countryPrefixes = getSet("allowed_country_prefixes").toList().sorted()
        val blockedHidden = prefs.getBoolean("blocked_numbers_hidden_until_import", false)
        val blockedExpanded = prefs.getBoolean("blocked_numbers_expanded", false) && !blockedHidden
        val prefixOptions = PhoneRules.countryPrefixOptions().associateBy { it.prefix }

        blockedToggleButton.text = when {
            blockedHidden -> "Números bloqueados ocultos hasta importar contactos"
            blockedExpanded -> "Ocultar números bloqueados (${blocked.size})"
            else -> "Números bloqueados (${blocked.size})"
        }
        blockedToggleButton.isEnabled = !blockedHidden
        blockedHideButton.visibility = if (blockedHidden) View.GONE else View.VISIBLE
        blockedList.visibility = if (blockedExpanded) View.VISIBLE else View.GONE

        if (countryPrefixes.isEmpty()) {
            countryPrefixList.addView(TextView(this).apply {
                text = "Sin filtro por país: se permiten llamadas de cualquier prefijo."
                setTextColor(Color.parseColor("#888888"))
                setPadding(8, 16, 8, 8)
            })
        }

        for (prefix in countryPrefixes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 4)
            }

            row.addView(TextView(this).apply {
                val countryNames = prefixOptions[prefix]
                    ?.label
                    ?.substringAfter(" — ")
                    ?: "Prefijo internacional"
                text = "$prefix — $countryNames"
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            row.addView(Button(this).apply {
                text = "Quitar"
                textSize = 12f
                setOnClickListener {
                    val prefixes = getSet("allowed_country_prefixes")
                    prefixes.remove(prefix)
                    saveSet("allowed_country_prefixes", prefixes)
                    refreshLists()
                    toast("Prefijo $prefix eliminado.")
                }
                setBackgroundColor(Color.parseColor("#D32F2F"))
                setTextColor(Color.WHITE)
            })
            countryPrefixList.addView(row)
        }

        if (blockedExpanded && blocked.isEmpty()) {
            blockedList.addView(TextView(this).apply {
                text = "No hay números bloqueados manualmente."
                setTextColor(Color.parseColor("#888888"))
                setPadding(8, 8, 8, 8)
            })
        }

        if (blockedExpanded) for (number in blocked) {
            val formatted = PhoneRules.formatNumber(number)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 16, 24, 16)
                gravity = Gravity.CENTER_VERTICAL
                background = createCardBackground(
                    Color.parseColor("#F0EFFC"),
                    Color.parseColor("#DDD9F6")
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
            }

            row.addView(TextView(this).apply {
                text = formatted
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            val btnUnblock = Button(this).apply {
                text = "Desbloquear"
                textSize = 12f
                setOnClickListener {
                    val b = getSet("blocked_numbers")
                    b.remove(number)
                    val a = getSet("allowed_numbers")
                    a.add(number)
                    saveSet("blocked_numbers", b)
                    saveSet("allowed_numbers", a)
                    refreshLists()
                    toast("Número $formatted pasado a lista blanca")
                }
                setBackgroundColor(Color.parseColor("#5865E8"))
                setTextColor(Color.WHITE)
            }
            row.addView(btnUnblock)
            blockedList.addView(row)
        }
    }

    private fun refreshStatus() {
        val isActive = isCallScreeningRoleHeld()
        val isDefaultPhone = isDefaultPhoneRoleHeld()
        val callNotificationsAllowed = android.os.Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val scheduleEnabled = prefs.getBoolean("blocking_schedule_enabled", false)
        val scheduleStatus = if (scheduleEnabled) {
            val start = formatScheduleTime(prefs.getInt("blocking_schedule_start", 22 * 60))
            val end = formatScheduleTime(prefs.getInt("blocking_schedule_end", 7 * 60))
            "Horario activo: $start–$end, según los días seleccionados."
        } else {
            "Sin horario: las reglas se aplican en todo momento."
        }
        if (isActive) {
            statusCard.background = createCardBackground(Color.parseColor("#E8F5E9"), Color.parseColor("#A5D6A7"))
            statusText.text = "✓ FILTRADO DE LLAMADAS ACTIVO\nAndroid está filtrando llamadas entrantes antes de que contesten.\n"
                .plus(phoneRoleStatus(isDefaultPhone, callNotificationsAllowed))
                .plus("\n$scheduleStatus")
            statusText.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            statusCard.background = createCardBackground(Color.parseColor("#FFF3E0"), Color.parseColor("#FFE0B2"))
            statusText.text = "⚠ FILTRADO DE LLAMADAS INACTIVO\nEs necesario conceder el permiso de filtrado de llamadas en este dispositivo.\n"
                .plus(phoneRoleStatus(isDefaultPhone, callNotificationsAllowed))
                .plus("\n$scheduleStatus")
            statusText.setTextColor(Color.parseColor("#E65100"))
        }
    }

    private fun phoneRoleStatus(
        isDefaultPhone: Boolean,
        callNotificationsAllowed: Boolean
    ): String =
        if (isDefaultPhone) {
            if (callNotificationsAllowed) {
                "Teléfono predeterminado: activo. Puedes bloquear y finalizar llamadas en curso."
            } else {
                "Teléfono predeterminado activo, pero faltan notificaciones para mostrar los controles de llamada."
            }
        } else {
            "Teléfono predeterminado: inactivo. El bloqueo solo se aplicará a futuras llamadas."
        }

    private fun isCallScreeningRoleHeld(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return getSystemService(RoleManager::class.java)
            .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun isDefaultPhoneRoleHeld(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return getSystemService(RoleManager::class.java)
            .isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun requestCallScreeningRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                    1001
                )
            } else {
                toast("Este dispositivo no soporta el rol de filtrado de llamadas.")
            }
        } else {
            toast("Se requiere Android 10 o superior.")
        }
    }

    private fun requestDefaultPhoneRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                requestInCallNotifications()
            } else if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    DEFAULT_PHONE_ROLE_REQUEST
                )
            } else {
                toast("Este dispositivo no permite cambiar la aplicación de teléfono.")
            }
        } else {
            toast("Se requiere Android 10 o superior.")
        }
    }

    private fun requestInCallNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                IN_CALL_NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        stopAssistantListening()
        assistantTextToSpeech?.stop()
        assistantTextToSpeech?.shutdown()
        assistantTextToSpeech = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            refreshStatus()
            refreshLists()
        }
    }
}
