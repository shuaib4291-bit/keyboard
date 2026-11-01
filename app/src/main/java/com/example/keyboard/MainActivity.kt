package com.example.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = (24 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p * 2, p, p)
            setBackgroundColor(Color.parseColor("#1F1F2E"))
        }

        layout.addView(TextView(this).apply {
            text = "তিন ভাষার কিবোর্ড"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = "English • বাংলা • العربية\n\nপ্রথমে কিবোর্ডটি চালু করুন, তারপর বেছে নিন।"
            textSize = 16f
            setTextColor(Color.parseColor("#AAB0D0"))
            setPadding(0, p / 2, 0, p)
        })

        layout.addView(Button(this).apply {
            text = "১. কিবোর্ড চালু করুন"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })
        layout.addView(Button(this).apply {
            text = "২. কিবোর্ড বেছে নিন"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })

        layout.addView(EditText(this).apply {
            hint = "এখানে টাইপ করে দেখুন..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 20f
        })

        setContentView(layout)
    }
}