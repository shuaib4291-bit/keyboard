package com.example.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
        "English", "123", enN, enS, r("1 2 3 4 5 6 7 8 9 0")
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
        r("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০")
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
        r("١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩ ٠")
    )
)

private val SYM = listOf(
    r(". , ? ! ' \" - _ : ;"),
    r("( ) @ # % & + = / *"),
    r("< > [ ] { } $ € ৳ ~")
)

class KeyboardService : InputMethodService() {

    private lateinit var root: LinearLayout
    private lateinit var bar: LinearLayout
    private lateinit var keysBox: LinearLayout
    private var lang = "en"
    private var shift = false
    private var num = false
    private val handler = Handler(Looper.getMainLooper())
    private var repeater: Runnable? = null

    // সোলায়মান লিপি ফন্ট: app/src/main/assets/fonts/SolaimanLipi.ttf
    private val bnFont: Typeface? by lazy {
        try {
            Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
        } catch (e: Exception) {
            null
        }
    }

    private val prefs by lazy { getSharedPreferences("kb", MODE_PRIVATE) }
    private val dicts = HashMap<String, List<String>>()
    private val learned = HashMap<String, MutableSet<String>>()

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val screenH = resources.displayMetrics.heightPixels
        val keysH = maxOf((screenH * 0.20f).toInt(), dp(170))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keysH + dp(40)
            )
        }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
            )
        }
        keysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(bar)
        root.addView(keysBox)
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shift = false
        num = false

        // পাসওয়ার্ড/ইমেইল/নম্বরের ঘরে নিজে থেকে ইংরেজি
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        val textPass = cls == InputType.TYPE_CLASS_TEXT && (
                v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                v == InputType.TYPE_TEXT_VARIATION_URI)
        val numPass = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE

        if (textPass || numPass) lang = "en"
        if (numPass) num = true

        if (::root.isInitialized) {
            render()
            updateSuggestions()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        updateSuggestions()
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
        keysBox.addView(rw)
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
        size: Float = 19f,
        repeat: Boolean = false,
        action: () -> Unit
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.textSize = size
        if (lang == "bn") bnFont?.let { t.typeface = it }
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
        updateSuggestions()
    }

    private fun render() {
        keysBox.removeAllViews()
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

        // নিচের সারি: ১২৩ | , | ভাষা বাটন | space | . | ↵ | ✓
        val dot = if (lang == "bn") "।" else "."
        addRow(
            listOf(
                key(if (num) "ABC" else d.lbl, 1.4f, fn = true) {
                    num = !num
                    render()
                },
                key(",") { type(",") },
                key("🌐 " + d.name, 2.6f, fn = true, active = true, size = 16f) { nextLang() },
                key("space", 2.4f, size = 14f) { type(" ") },
                key(dot) { type(dot) },
                key("↵", 1.2f, fn = true) { enter() },
                key("✓", 1.2f, fn = true, size = 22f) { confirm() }
            )
        )
    }

    // ---------- শব্দ সাজেশন ----------

    private fun suggestionsAllowed(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val v = t and InputType.TYPE_MASK_VARIATION
        return v != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
                v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                v != InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun isWordChar(c: Char): Boolean {
        if (c.isLetter()) return true
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() ||
                t == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        return before.takeLastWhile { isWordChar(it) }
    }

    private fun dict(code: String): List<String> = dicts.getOrPut(code) {
        try {
            assets.open("words_$code.txt").bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun learnedSet(code: String): MutableSet<String> = learned.getOrPut(code) {
        (prefs.getStringSet("learned_$code", emptySet()) ?: emptySet()).toMutableSet()
    }

    private fun learn(w: String) {
        if (w.length < 2 || !suggestionsAllowed()) return
        val set = learnedSet(lang)
        if (set.size > 3000) return
        if (set.add(w)) prefs.edit().putStringSet("learned_$lang", HashSet(set)).apply()
    }

    private fun suggest(prefix: String): List<String> {
        val out = LinkedHashSet<String>()
        for (w in learnedSet(lang)) {
            if (out.size >= 3) break
            if (w.length > prefix.length && w.startsWith(prefix, true)) out.add(w)
        }
        for (w in dict(lang)) {
            if (out.size >= 3) break
            if (w.length > prefix.length && w.startsWith(prefix, true)) out.add(w)
        }
        return out.toList()
    }

    private fun updateSuggestions() {
        if (!::bar.isInitialized) return
        bar.removeAllViews()
        if (!suggestionsAllowed()) return
        val w = currentWord()
        if (w.isEmpty()) return
        suggest(w).forEach { s ->
            val shown = if (w[0].isUpperCase()) s.replaceFirstChar { it.uppercase() } else s
            val t = TextView(this)
            t.text = shown
            t.gravity = Gravity.CENTER
            t.textSize = 18f
            if (lang == "bn") bnFont?.let { t.typeface = it }
            t.maxLines = 1
            t.setTextColor(TXT)
            t.background = bg(KEY2)
            t.layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ).apply { setMargins(dp(2), 0, dp(2), dp(2)) }
            t.setOnClickListener { pick(w, shown) }
            bar.addView(t)
        }
    }

    private fun pick(w: String, s: String) {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        learn(s)
        updateSuggestions()
    }

    // ---------- ইনপুট ----------

    private fun isPasswordField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun type(s: String) {
        val ic = currentInputConnection ?: return
        if (s.isNotEmpty() && !isWordChar(s[0])) learn(currentWord())
        if (s.length == 1 && s[0] in '0'..'9' && isPasswordField()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_0 + (s[0] - '0'))
        } else {
            ic.commitText(s, 1)
        }
        updateSuggestions()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (isPasswordField()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            val sel = ic.getSelectedText(0)
            if (sel.isNullOrEmpty()) ic.deleteSurroundingText(1, 0)
            else ic.commitText("", 1)
        }
        updateSuggestions()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        learn(currentWord())
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnter = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun confirm() {
        val ic = currentInputConnection ?: return
        learn(currentWord())
        val t = currentInputEditorInfo?.inputType ?: 0
        val multiLine = (t and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else if (!multiLine) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        requestHideSelf(0)
    }
}