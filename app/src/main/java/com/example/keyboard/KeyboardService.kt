private fun render() {
    keysBox.removeAllViews()
    val d = LANGS[lang]!!

    if (num) {
        // ── সংখ্যা মোড: বড় সংখ্যা-প্যাড ──
        addRow(d.digits.map { c -> key(c, size = 18f) { type(c) } })
        SYM.forEachIndexed { i, rw ->
            val ks = rw.map { c -> key(c) { type(c) } }.toMutableList<View>()
            if (i == 2) ks.add(key("⌫", 1.5f, fn = true, repeat = true) { backspace() })
            addRow(ks)
        }
        // নিচের সারি
        addRow(
            listOf(
                key("ABC", 1.5f, fn = true) { num = false; render() },
                key(",") { type(",") },
                key("🎤", 1.5f, fn = true, size = 12f) { toggleVoice() },
                key("🌐 " + d.name, 2.0f, fn = true, size = 8f) { nextLang() },
                key("space", 2.0f, size = 10f) { type(" ") },
                key(".") { type(".") },
                key("↵", 1.5f, fn = true) { enter() },
                key("✓", 1.5f, fn = true, size = 16f) { confirm() }
            )
        )
        return
    }

    // ── সাধারণ মোড: প্রথম সারিতেই সংখ্যা ──
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
                    if (shift) { shift = false; render() }
                }
            }
        })
    }

    // নিচের সারি
    val dot = if (lang == "bn") "।" else "."
    addRow(
        listOf(
            key(d.lbl, 1.5f, fn = true) { num = true; render() },   // ১২৩ / 123
            key(",") { type(",") },
            key(if (listening) "⏹" else "🎤", 1.5f, fn = true, active = listening, size = 12f) {
                toggleVoice()
            },
            key("🌐 " + d.name, 2.0f, fn = true, active = !listening, size = 8f) { nextLang() },
            key("space", 2.0f, size = 10f) { type(" ") },
            key(dot) { type(dot) },
            key("↵", 1.5f, fn = true) { enter() },
            key("✓", 1.5f, fn = true, size = 16f) { confirm() }
        )
    )
}