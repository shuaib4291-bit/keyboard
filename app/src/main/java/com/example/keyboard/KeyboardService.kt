package com.example.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

private val BG = Color.parseColor("#2A2A3C")
private val KEY = Color.parseColor("#565A73")
private val KEY2 = Color.parseColor("#434660")
private val PRESS = Color.parseColor("#7A7FA3")
private val ACC = Color.parseColor("#8AB4F8")
private val TXT = Color.parseColor("#C9D1FF")
private val TXT_ACC = Color.parseColor("#10192E")

private fun r(s: String) = s.split(" ")

private class Lang(
    val name: String,
    val lbl: String,
    val n: List<List<String>>,
    val s: List<List<String>>,
    val digits: List<String>
)

private val enN = listOf(
    r("q w e r t y u i o p"),
    r("a s d f g h j k l"),
    r("⇧ z x c v b n m ⌫")
)
private val enS = enN.map { row ->
    row.map { if (it.length == 1 && it[0].isLetter()) it.uppercase() else it }
}

private val LANGS = linkedMapOf(
    "en" to Lang(
        "English", "123", enN, enS, r("0 1 2 3 4 5 6 7 8 9")
    ),
    "bn" to Lang(
        "বাংলা", "১২৩",
        listOf(
            r("ক খ গ ঘ ঙ চ ছ জ ঝ ঞ"),
            r("ট ঠ ড ঢ ণ ত থ দ ধ ন"),
            r("⇧ প ফ ব ভ ম য র ল ⌫")
        ),
        listOf(
            r("অ আ ই ঈ উ ঊ ঋ এ ঐ ও"),
            r("ঔ া ি ী ু ূ ৃ ে ৈ ো"),
            r("⇧ ৌ শ ষ স হ ং ঃ ঁ ⌫"),
            r("ড় ঢ় য় ৎ ্ ।")
        ),
        r("০ ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯")
    ),
    "ar" to Lang(
        "العربية", "١٢٣",
        listOf(
            r("ض ص ث ق ف غ ع ه خ ح"),
            r("ش س ي ب ل ا ت ن م"),
            r("⇧ ئ ء ؤ ر لا ى ة ⌫")
        ),
        listOf(
            r("ج د ذ ط ظ ك و ز ، ؟"),
            r("َ ُ ِ ً ٌ ٍ ّ ْ ـ"),
            r("⇧ أ إ آ پ چ گ ژ ⌫")
        ),
        r("٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩")
    )
)

private val SYM = listOf(
    r(". , ? ! ' \" - _ : ;"),
    r("( ) @ # % & + = / *"),
    r("< > [ ] { } $ € ৳ ~")
)

class KeyboardService : InputMethodService() {

    private lateinit var root: LinearLayout
    private var lang = "en"
    private var shift = false
    private var num = false
    private val handler = Handler(Looper.getMainLooper())
    private var repeater: Runnable? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val screenH = resources.displayMetrics.heightPixels
        val h = maxOf((screenH * 0.18f).toInt(), dp(150))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(4), dp(3), dp(4), dp(4))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        }
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shift = false
        num = false
        if (::root.isInitialized) render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        super.onFinishInputView(finishingInput)
    }

    // ---------- UI ----------

    private fun shape(c: Int) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(9).toFloat()
    }

    private fun bg(c: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(PRESS))
        addState(intArrayOf(), shape(c))
    }

    private fun row(w: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, w
        ).apply { setMargins(0, dp(2), 0, dp(2)) }
    }

    private fun addRow(keys: List<View>) {
        val rw = row(1f)
        keys.forEach { rw.addView(it) }
        root.addView(rw)
    }

    private fun label(c: String): String {
        if (c.length == 1) {
            val t = Character.getType(c[0])
            if (t == Character.NON_SPACING_MARK.toInt() ||
                t == Character.COMBINING_SPACING_MARK.toInt()
            ) return "◌$c"
        }
        return c
    }

    private fun key(
        text: String,
        weight: Float = 1f,
        fn: Boolean = false,
        active: Boolean = false,
        size: Float = 18f,
        repeat: Boolean = false,
        action: () -> Unit
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.textSize = size
        t.setTextColor(if (active) TXT_ACC else TXT)
        t.background = bg(if (active) ACC else if (fn) KEY2 else KEY)
        t.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, weight
        ).apply { setMargins(dp(2), 0, dp(2), 0) }
        t.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    if (repeat) {
                        val rn = object : Runnable {
                            override fun run() {
                                action()
                                handler.postDelayed(this, 60)
                            }
                        }
                        repeater = rn
                        handler.postDelayed(rn, 400)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                }
            }
            true
        }
        return t
    }

    private fun stopRepeat() {
        repeater?.let { handler.removeCallbacks(it) }
        repeater = null
    }

    private fun nextLang() {
        val codes = LANGS.keys.toList()
        lang = codes[(codes.indexOf(lang) + 1) % codes.size]
        shift = false
        render()
    }

    private fun render() {
        root.removeAllViews()
        val d = LANGS[lang]!!

        if (num) {
            addRow(d.digits.map { c -> key(c) { type(c) } })
            SYM.forEachIndexed { i, rw ->
                val ks = rw.map { c -> key(c) { type(c) } }.toMutableList<View>()
                if (i == 2) ks.add(key("⌫", 1.4f, fn = true, repeat = true) { backspace() })
                addRow(ks)
            }
        } else {
            (if (shift) d.s else d.n).forEach { rw ->
                addRow(rw.map { c ->
                    when (c) {
                        "⇧" -> key("⇧", 1.4f, fn = true, active = shift) {
                            shift = !shift
                            render()
                        }
                        "⌫" -> key("⌫", 1.4f, fn = true, repeat = true) { backspace() }
                        else -> key(label(c)) {
                            type(c)
                            if (shift) {
                                shift = false
                                render()
                            }
                        }
                    }
                })
            }
        }

        // নিচের সারি: ১২৩ | ভাষা বাটন (বড়, নীল) | space | . | enter
        val dot = if (lang == "bn") "।" else "."
        addRow(
            listOf(
                key(if (num) "ABC" else d.lbl, 1.4f, fn = true) {
                    num = !num
                    render()
                },
                key(",") { type(",") },
                key("🌐 " + d.name, 2.6f, fn = true, active = true, size = 18f) { nextLang() },
                key("space", 3f, size = 16f) { type(" ") },
                key(dot) { type(dot) },
                key("↵", 1.4f, fn = true) { enter() }
            )
        )
    }

    // ---------- ইনপুট ----------

    private fun type(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) ic.deleteSurroundingText(1, 0)
        else ic.commitText("", 1)
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnter = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}