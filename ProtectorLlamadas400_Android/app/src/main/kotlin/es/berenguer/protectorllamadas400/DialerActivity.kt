package es.berenguer.protectorllamadas400

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DialerActivity : AppCompatActivity() {

    private lateinit var numberField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        numberField.setText(intent.data?.schemeSpecificPart.orEmpty())
        numberField.setSelection(numberField.text.length)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "Teléfono"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })
        numberField = EditText(this).apply {
            hint = "Introduce un número"
            textSize = 24f
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            gravity = Gravity.CENTER
            setSingleLine(true)
            contentDescription = "Número de teléfono"
        }
        root.addView(numberField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(24)
            bottomMargin = dp(16)
        })

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        keys.chunked(3).forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { key ->
                row.addView(Button(this).apply {
                    text = key
                    textSize = 20f
                    setOnClickListener { insertKey(key) }
                }, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(Button(this).apply {
            text = "Borrar"
            setOnClickListener { deleteLastCharacter() }
        })
        root.addView(Button(this).apply {
            text = "Llamar"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(46, 125, 50))
            setOnClickListener { placeCall() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Volver al protector"
            setOnClickListener {
                startActivity(Intent(this@DialerActivity, MainActivity::class.java))
            }
        }, buttonParams())
        setContentView(root)
    }

    private fun insertKey(key: String) {
        val start = numberField.selectionStart.coerceAtLeast(0)
        val end = numberField.selectionEnd.coerceAtLeast(0)
        numberField.text.replace(minOf(start, end), maxOf(start, end), key)
    }

    private fun deleteLastCharacter() {
        val start = numberField.selectionStart.coerceAtLeast(0)
        val end = numberField.selectionEnd.coerceAtLeast(0)
        if (start != end) {
            numberField.text.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            numberField.text.delete(start - 1, start)
        }
    }

    private fun placeCall() {
        val number = numberField.text.toString().trim()
        if (number.none { it.isDigit() }) {
            Toast.makeText(this, "Introduce un número de teléfono.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            getSystemService(TelecomManager::class.java).placeCall(
                Uri.fromParts("tel", number, null),
                Bundle()
            )
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .putExtra("wait_for_outgoing_call", true)
            )
        } catch (exception: SecurityException) {
            Log.e("DialerActivity", "No se pudo iniciar la llamada.", exception)
            Toast.makeText(this, "No se pudo iniciar la llamada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(8)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
