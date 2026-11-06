package com.example.keyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

private val BG = Color.parseColor("#2A2A3C")
private val KEY = Color.parseColor("#565A73")
private val KEY2 = Color.parseColor("#434660")
private val PRESS = Color.parseColor("#7A7FA3")
private val ACC = Color.parseColor("#8AB4F8")
private val TXT = Color.parseColor("#C9D1FF")
private val TXT_ACC = Color.parseColor("#10192E")

private const val HIST_MAX = 3

private fun r(s: String) = s.split(" ")

private class Lang(
    val name: String,
    val lbl: String,
    val voice: String,
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
        "English", "123", "en-US", enN, enS, r("1 2 3 4 5 6 7 8 9 0")
    ),
    "bn" to Lang(
        "বাংলা", "১২৩", "bn-BD",
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
        "العربية", "١٢٣", "ar-SA",
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

    // ভয়েস
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var lastVoice = ""
    private var lastVoiceAt = 0L
    private var voiceLoop = false
    private var silenceTimer: Runnable? = null

    // কপি হিস্টরি
    private val history = ArrayList<String>()
    private var showHistory = false
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        primaryText()?.let { addHistory(it) }
    }

    // ফন্ট
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

    // পরের শব্দের সাজেশন
    private val nextMaps = HashMap<String, HashMap<String, HashMap<String, Int>>>()
    private val dirtyNext = HashSet<String>()
    private val flushRunnable = Runnable { flushNext() }
    
    // ✅ স্পেস চাপার আগের শব্দ মনে রাখার ভেরিয়েবল
    private var lastTypedWord = ""

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onEvaluateFullscreenMode() = false

    override fun onCreate() {
        super.onCreate()
        loadHistory()
        try {
            clipboard().addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
    }

    override fun onCreateInputView(): View {
        val screenH = resources.displayMetrics.heightPixels
        val keysH = maxOf((screenH * 0.13f).toInt(), dp(120))
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keysH + dp(30)
            )
        }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)
            )
        }
        keysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(3), dp(5), dp(5))
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
        listening = false
        showHistory = false
        lastTypedWord = ""
        primaryText()?.let { addHistory(it) }

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
        cancelSilenceTimer()
        voiceLoop = false
        recognizer?.cancel()
        listening = false
        flushNext()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        try {
            clipboard().removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
        cancelSilenceTimer()
        voiceLoop = false
        recognizer?.destroy()
        recognizer = null
        flushNext()
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun shape(c: Int) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(8).toFloat()
    }

    private fun bg(c: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(PRESS))
        addState(intArrayOf(), shape(c))
    }

    private fun row(w: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, w
        ).apply { setMargins(0, dp(1), 0, dp(1)) }
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
        size: Float = 16f,
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
        ).apply { setMargins(dp(1), 0, dp(1), 0) }
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
                        handler.postDelayed(rn, 350)
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
            addRow(d.digits.map { c -> key(c, size = 14f) { type(c) } })
            SYM.forEachIndexed { i, rw ->
                val ks = rw.map { c -> key(c, size = 19f) { type(c) } }.toMutableList<View>()
                if (i == 2) ks.add(key("⌫", 1.5f, fn = true, repeat = true) { backspace() })
                addRow(ks)
            }
            addRow(
                listOf(
                    key("ABC", 1.5f, fn = true) { num = false; render() },
                    key(",") { type(",") },
                    key("🌐 " + d.name, 2.0f, fn = true, size = 8f) { nextLang() },
                    key("space", 2.0f, size = 10f) { type(" ") },
                    key(".") { type(".") },
                    key("↵", 1.5f, fn = true) { enter() },
                    key("✓", 1.5f, fn = true, size = 16f) { confirm() }
                )
            )
            return
        }

        addRow(d.digits.map { c -> key(c, size = 14f) { type(c) } })

        (if (shift) d.s else d.n).forEach { rw ->
            addRow(rw.map { c ->
                when (c) {
                    "⇧" -> key("⇧", 1.3f, fn = true, active = shift) {
                        shift = !shift
                        render()
                    }
                    "⌫" -> key("⌫", 1.5f, fn = true, repeat = true) { backspace() }
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

        val dot = if (lang == "bn") "।" else "."
        addRow(
            listOf(
                key(d.lbl, 1.5f, fn = true) { num = true; render() },
                key(",") { type(",") },
                key("🌐 " + d.name, 2.0f, fn = true, active = !listening, size = 8f) { nextLang() },
                key("space", 2.0f, size = 10f) { type(" ") },
                key(dot) { type(dot) },
                key("↵", 1.5f, fn = true) { enter() },
                key("✓", 1.5f, fn = true, size = 16f) { confirm() }
            )
        )
    }
    // ---------- ভয়েস টাইপিং ----------

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun hasMic() =
        checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun toggleVoice() {
        if (isPasswordField()) return

        if (voiceLoop) {
            stopVoiceLoop()
            return
        }

        if (!hasMic()) {
            toast("মাইক্রোফোনের অনুমতি দিন")
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে ভয়েস টাইপিং সেবা পাওয়া যায়নি")
            return
        }

        voiceLoop = true
        listening = true
        render()
        updateSuggestions()
        startFreshRecognizer()
        scheduleSilenceStop()
    }

    private fun startFreshRecognizer() {
        if (!voiceLoop) return

        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null

        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGS[lang]!!.voice)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { cancelSilenceTimer() }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                if (!voiceLoop) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        handler.postDelayed({ startFreshRecognizer() }, 200)
                        scheduleSilenceStop()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        handler.postDelayed({ startFreshRecognizer() }, 500)
                        scheduleSilenceStop()
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        if (voiceLoop) {
                            handler.postDelayed({ startFreshRecognizer() }, 200)
                            scheduleSilenceStop()
                        }
                    }
                    else -> stopVoiceLoop()
                }
            }

            override fun onResults(results: Bundle?) {
                if (!voiceLoop) return
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                val now = System.currentTimeMillis()
                val dup = text == lastVoice && now - lastVoiceAt < 3000
                if (!text.isNullOrBlank() && !dup) {
                    lastVoice = text
                    lastVoiceAt = now
                    currentInputConnection?.commitText("$text ", 1)
                    updateSuggestions()
                }
                handler.postDelayed({ startFreshRecognizer() }, 150)
                scheduleSilenceStop()
            }
        })

        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            stopVoiceLoop()
        }
    }

    private fun stopVoiceLoop() {
        cancelSilenceTimer()
        voiceLoop = false
        listening = false
        try { recognizer?.stopListening() } catch (e: Exception) {}
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
        render()
        updateSuggestions()
    }

    private fun scheduleSilenceStop() {
        cancelSilenceTimer()
        val r = Runnable {
            if (voiceLoop) {
                toast("১০ সেকেন্ড নীরবতা — ভয়েস বন্ধ")
                stopVoiceLoop()
            }
        }
        silenceTimer = r
        handler.postDelayed(r, 10_000L)
    }

    private fun cancelSilenceTimer() {
        silenceTimer?.let { handler.removeCallbacks(it) }
        silenceTimer = null
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
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: return ""
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

    // ---------- পরের শব্দ শেখা ও সাজেশন ----------

    private fun nextMap(code: String): HashMap<String, HashMap<String, Int>> =
        nextMaps.getOrPut(code) {
            val m = HashMap<String, HashMap<String, Int>>()
            try {
                assets.open("next_$code.txt").bufferedReader().readLines().forEach { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size >= 2) {
                        val inner = m.getOrPut(p[0]) { HashMap() }
                        for (i in 1 until p.size) {
                            inner[p[i]] = (inner[p[i]] ?: 0) + (p.size - i)
                        }
                    }
                }
            } catch (e: Exception) {
            }
            try {
                val o = JSONObject(prefs.getString("nextjson_$code", "{}") ?: "{}")
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val io = o.getJSONObject(k)
                    val inner = m.getOrPut(k) { HashMap() }
                    val ik = io.keys()
                    while (ik.hasNext()) {
                        val n = ik.next()
                        inner[n] = io.getInt(n)
                    }
                }
            } catch (e: Exception) {
            }
            m
        }

    private fun addBigram(prev: String, next: String) {
        val p = prev.lowercase()
        val n = next.lowercase()
        if (p.isEmpty() || n.isEmpty()) return
        val m = nextMap(lang)
        if (!m.containsKey(p) && m.size >= 3000) return
        val inner = m.getOrPut(p) { HashMap() }
        if (!inner.containsKey(n) && inner.size >= 20) {
            val low = inner.minByOrNull { it.value }?.key
            if (low != null) inner.remove(low)
        }
        inner[n] = (inner[n] ?: 0) + 3
        dirtyNext.add(lang)
        handler.removeCallbacks(flushRunnable)
        // ✅ ১ সেকেন্ড পর সেভ হবে, যাতে বারবার ডিস্কে লেখা না হয়
        handler.postDelayed(flushRunnable, 1000)
    }

    private fun flushNext() {
        for (code in dirtyNext) {
            val m = nextMaps[code] ?: continue
            val o = JSONObject()
            for ((p, inner) in m) {
                val io = JSONObject()
                for ((n, c) in inner) io.put(n, c)
                o.put(p, io)
            }
            prefs.edit().putString("nextjson_$code", o.toString()).apply()
        }
        dirtyNext.clear()
    }

    private fun getNextWords(prev: String): List<String> =
        nextMap(lang)[prev.lowercase()]?.entries
            ?.sortedByDescending { it.value }
            ?.take(3)
            ?.map { it.key }
            ?: emptyList()

    private fun updateSuggestions() {
        if (!::bar.isInitialized) return
        bar.removeAllViews()

        if (isPasswordField()) {
            bar.addView(clipChip())
            bar.addView(voiceChip())
            return
        }
        if (showHistory) {
            showHistoryRow()
            return
        }

        val allowed = suggestionsAllowed()
        val w = if (allowed) currentWord() else ""
        var list: List<String> = emptyList()

        if (allowed) {
            if (w.isNotEmpty()) {
                list = suggest(w)
            } else {
                val before = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: ""
                if (before.isNotEmpty() && before.last().isWhitespace()) {
                    val last = before.trimEnd().takeLastWhile { isWordChar(it) }
                    if (last.isNotEmpty()) list = getNextWords(last)
                }
            }
        }

        if (list.isEmpty()) {
            showTools()
            return
        }

        list.forEach { s ->
            val shown = if (w.isNotEmpty() && w[0].isUpperCase())
                s.replaceFirstChar { it.uppercase() } else s
            bar.addView(chip(shown, 14f) { pick(w, shown) })
        }
        bar.addView(clipChip())
        bar.addView(voiceChip())
    }

    // ---------- কপি, পেস্ট ও হিস্টরি ----------

    private fun chip(text: String, size: Float, action: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.textSize = size
        if (lang == "bn") bnFont?.let { t.typeface = it }
        t.isSingleLine = true
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        t.setPadding(dp(3), 0, dp(3), 0)
        t.setTextColor(TXT)
        t.background = bg(KEY2)
        t.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ).apply { setMargins(dp(1), 0, dp(1), dp(1)) }
        t.setOnClickListener { action() }
        return t
    }

    private fun voiceChip(): TextView {
        val t = TextView(this)
        t.text = if (voiceLoop) "⏹" else "🎤"
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.isSingleLine = true
        t.setTextColor(if (voiceLoop) TXT_ACC else TXT)
        t.background = bg(if (voiceLoop) ACC else KEY2)
        t.layoutParams = LinearLayout.LayoutParams(
            dp(42), ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { setMargins(dp(1), 0, dp(1), dp(1)) }
        t.setOnClickListener { toggleVoice() }
        return t
    }

    private fun clipChip(): TextView {
        val t = TextView(this)
        t.text = "📋"
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.isSingleLine = true
        t.setTextColor(TXT)
        t.background = bg(KEY2)
        t.layoutParams = LinearLayout.LayoutParams(
            dp(38), ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { setMargins(dp(1), 0, dp(1), dp(1)) }
        t.setOnClickListener {
            showHistory = true
            updateSuggestions()
        }
        return t
    }

    private fun showTools() {
        bar.addView(chip("সব নির্বাচন", 10f) { doSelectAll() })
        bar.addView(chip("কাট", 12f) { doCopy(true) })
        bar.addView(chip("কপি", 12f) { doCopy(false) })
        bar.addView(chip("পেস্ট", 12f) { doPaste() })
        bar.addView(clipChip())
        bar.addView(voiceChip())
    }

    private fun showHistoryRow() {
        if (history.isEmpty()) {
            bar.addView(chip("কপি করা কিছু নেই", 12f) { })
        } else {
            history.forEach { h ->
                val one = h.replace("\n", " ")
                bar.addView(chip(one, 12f) {
                    currentInputConnection?.commitText(h, 1)
                    showHistory = false
                    updateSuggestions()
                })
            }
        }
        val clear = chip("🗑", 12f) {
            history.clear()
            saveHistory()
            toast("হিস্টরি মুছে গেছে")
            updateSuggestions()
        }
        (clear.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
        bar.addView(clear)
        val close = chip("✕", 12f) {
            showHistory = false
            updateSuggestions()
        }
        (close.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
        bar.addView(close)
        bar.addView(voiceChip())
    }

    private fun clipboard() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun primaryText(): String? = try {
        val c = clipboard().primaryClip
        if (c != null && c.itemCount > 0) c.getItemAt(0).coerceToText(this)?.toString() else null
    } catch (e: Exception) {
        null
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            history.clear()
            for (i in 0 until arr.length()) history.add(arr.getString(i))
        } catch (e: Exception) {
        }
    }

    private fun saveHistory() {
        prefs.edit().putString("history", JSONArray(history).toString()).apply()
    }

    private fun addHistory(t: String) {
        val s = t.trim()
        if (s.isEmpty() || s.length > 2000) return
        history.remove(s)
        history.add(0, s)
        while (history.size > HIST_MAX) history.removeAt(history.size - 1)
        saveHistory()
    }

    private fun doSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    private fun doCopy(cut: Boolean) {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) {
            toast("আগে লেখা নির্বাচন করুন")
            return
        }
        clipboard().setPrimaryClip(ClipData.newPlainText("kb", sel))
        addHistory(sel.toString())
        if (cut) ic.commitText("", 1)
        toast(if (cut) "কাট হয়েছে" else "কপি হয়েছে")
        updateSuggestions()
    }

    private fun doPaste() {
        val ic = currentInputConnection ?: return
        val text = primaryText()
        if (text.isNullOrEmpty()) {
            toast("ক্লিপবোর্ড খালি")
            return
        }
        ic.commitText(text, 1)
        updateSuggestions()
    }

    private fun pick(w: String, s: String) {
        val ic = currentInputConnection ?: return
        if (w.isNotEmpty()) {
            ic.deleteSurroundingText(w.length, 0)
        }
        ic.commitText("$s ", 1)
        learn(s)
        // ✅ সাজেশন থেকে শব্দ নিলে সেটাও জোড়া হিসেবে সেভ হবে
        if (lastTypedWord.isNotEmpty()) {
            addBigram(lastTypedWord, s)
            lastTypedWord = ""
        }
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

    private fun stopVoiceIfActive() {
        if (voiceLoop) stopVoiceLoop()
    }

    private fun type(s: String) {
        stopVoiceIfActive()
        val ic = currentInputConnection ?: return
        showHistory = false

        // ✅ স্পেস দেওয়ার আগেই আগের শব্দটা মনে রাখা
        if (s == " ") {
            val word = currentWord()
            if (word.isNotEmpty()) {
                lastTypedWord = word
            }
        } else if (s.isNotEmpty() && !isWordChar(s[0])) {
            learn(currentWord())
        }

        if (s.length == 1 && s[0] in '0'..'9' && isPasswordField()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_0 + (s[0] - '0'))
        } else {
            ic.commitText(s, 1)
        }

        // ✅ নতুন শব্দ শেষ করে স্পেস দিলে সেটা জোড়া হিসেবে সেভ করা
        if (s == " " && lastTypedWord.isNotEmpty()) {
            val currWord = currentWord()
            if (currWord.isNotEmpty() && currWord != lastTypedWord) {
                addBigram(lastTypedWord, currWord)
                lastTypedWord = "" // কাজ শেষ, রিসেট
            }
        }

        updateSuggestions()
    }

    private fun backspace() {
        stopVoiceIfActive()
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
        stopVoiceIfActive()
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
        stopVoiceIfActive()
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