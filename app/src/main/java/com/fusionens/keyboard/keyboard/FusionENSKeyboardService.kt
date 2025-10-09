package com.fusionens.keyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.view.animation.AlphaAnimation
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.GestureDetector
import com.fusionens.keyboard.ens.ENSResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.pow
import android.content.SharedPreferences

class FusionENSKeyboardService : InputMethodService() {
    
    private var inputConnection: InputConnection? = null
    private var currentText = ""
    private val ensResolver = ENSResolver()
    private var isShiftPressed = false
    private var isNumbersMode = false
    private var isSymbolsMode = false
    private var lastSpacePressTime = 0L
    private var lastENSResolutionTime = 0L
    private var lastResolvedText = ""
    
    // Enhanced features
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var gestureDetector: GestureDetector? = null
    private var isGestureMode = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gesturePath = mutableListOf<Pair<Float, Float>>()
    
    // SharedPreferences for saving resolved ENS names
    private lateinit var prefs: SharedPreferences
    
    // Popular ENS names (like iOS keyboard)
    private val popularENS = listOf("vitalik.eth", "ethereum.eth", "uniswap.eth", "opensea.eth", "ens.eth")
    
    // Keyboard layouts
    private val letterKeys = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )
    
    private val numberKeys = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@"),
        listOf(".", ",", "?", "!", "'", "\"", "=", "+", "#")
    )
    
    private val symbolKeys = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥"),
        listOf(".", ",", "?", "!", "'", "\"", "=", "+", "#")
    )
    
    override fun onCreateInputView(): View {
        // Initialize enhanced features
        initializeEnhancedFeatures()
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences("fusion_ens_keyboard", Context.MODE_PRIVATE)
        
        // Sync current text with input field
        syncCurrentText()
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#2C2C2C")) // Gboard-like background
        
        // Set layout to fill available space
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // Create the keyboard (includes suggestion bar in top row)
        createKeyboard(layout)
        
        return layout
    }
    
    private fun initializeEnhancedFeatures() {
        // Initialize vibrator for haptic feedback
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Initialize tone generator for key sounds
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
        
        // Initialize gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gestureStartX = e.x
                gestureStartY = e.y
                gesturePath.clear()
                gesturePath.add(Pair(e.x, e.y))
                return true
            }
            
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                gesturePath.add(Pair(e2.x, e2.y))
                return true
            }
        })
    }
    
    private fun createKeyboard(layout: LinearLayout) {
        // Suggestion bar as the very top row (flush with keyboard top)
        val suggestionBar = createSuggestionBar()
        layout.addView(suggestionBar)
        
        
        if (isNumbersMode || isSymbolsMode) {
            // Numbers/Symbols layout
            val numbersRow1 = createNumbersRow1()
            layout.addView(numbersRow1)
            
            val numbersRow2 = createNumbersRow2()
            layout.addView(numbersRow2)
            
            val numbersRow3 = createNumbersRow3()
            layout.addView(numbersRow3)
        } else {
            // Letter layout
            // Q-P row with superscript numbers and backspace
            val qpRow = createQPRow()
            layout.addView(qpRow)
            
            // A-L row with tab and enter
            val alRow = createALRow()
            layout.addView(alRow)
            
            // Z-M row with shift keys
            val zmRow = createZMRow()
            layout.addView(zmRow)
        }
        
        // Bottom row with numbers toggle, space, and navigation
        val bottomRow = createBottomRow()
        layout.addView(bottomRow)
    }
    
    
    private fun createWordSuggestionsRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 4, 8, 4)
        row.setBackgroundColor(Color.parseColor("#2C2C2C"))
        
        // Common word suggestions (like Gboard)
        val suggestions = listOf("the", "and", "you", "that", "was")
        
        suggestions.forEach { suggestion ->
            val button = createSuggestionChip(suggestion)
            button.setOnClickListener { 
                inputConnection?.commitText("$suggestion ", 1)
                currentText += "$suggestion "
            }
            row.addView(button)
        }
        
        return row
    }
    
    
    private fun createSuggestionChip(text: String, isENS: Boolean = false): TextView {
        val button = TextView(this)
        button.text = text
        button.setTextColor(Color.parseColor("#0080BC")) // Blue text color for ENS suggestions
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.setPadding(16, 16, 16, 16) // Increased padding for taller suggestion bar

        // No background color - just text
        button.background = null

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 0) // No margins, we'll add separators
        button.layoutParams = params

        return button
    }
    
    private fun createSuggestionButton(text: String): TextView {
        val button = TextView(this)
        button.text = text
        button.setTextColor(Color.parseColor("#FFFFFF"))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.setPadding(12, 8, 12, 8)
        
        // Gboard-like suggestion button styling
        val drawable = GradientDrawable()
        drawable.setColor(Color.parseColor("#3A3A3A"))
        drawable.cornerRadius = 16f
        button.background = drawable
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(4, 0, 4, 0)
        button.layoutParams = params
        
        return button
    }
    
    private fun createSuggestionBar(): LinearLayout {
        // Create a container for the scrollable suggestion bar
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(Color.parseColor("#2C2C2C"))
        
        // Create horizontal scroll view
        val scrollView = HorizontalScrollView(this)
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        // Create the horizontal row inside scroll view
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 12, 8, 12) // Increased vertical padding for taller bar
        
        // Get filtered suggestions based on current text
        val suggestions = getFilteredSuggestions()
        
        if (suggestions.isNotEmpty()) {
            suggestions.forEachIndexed { index, ensName ->
                val button = createSuggestionChip(ensName, true) // true for ENS styling
                button.setOnClickListener { 
                    inputConnection?.commitText(ensName, 1)
                    currentText = ensName
                    // Refresh suggestions after selection
                    refreshSuggestionBar()
                }
                row.addView(button)
                
                // Add separator (|) between suggestions, but not after the last one
                if (index < suggestions.size - 1) {
                    val separator = TextView(this)
                    separator.text = "|"
                    separator.setTextColor(Color.parseColor("#666666"))
                    separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    separator.setPadding(8, 8, 8, 8)
                    row.addView(separator)
                }
            }
        } else {
            // Show empty bar when no suggestions
            val emptyText = TextView(this)
            emptyText.text = "No suggestions"
            emptyText.setTextColor(Color.parseColor("#666666"))
            emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            emptyText.setPadding(16, 16, 16, 16)
            row.addView(emptyText)
        }
        
        // Add row to scroll view and scroll view to container
        scrollView.addView(row)
        container.addView(scrollView)
        
        return container
    }
    
    private fun getFilteredSuggestions(): List<String> {
        val allSuggestions = mutableListOf<String>()
        
        // Add popular ENS names
        allSuggestions.addAll(popularENS)
        
        // Add saved ENS names (last 10)
        val savedENS = getSavedENS()
        allSuggestions.addAll(savedENS)
        
        // If user is typing, filter suggestions
        if (currentText.isNotEmpty()) {
            return allSuggestions.filter { ensName ->
                ensName.lowercase().contains(currentText.lowercase())
            }.distinct().take(10)
        }
        
        // If not typing, show last 10 suggestions
        return allSuggestions.distinct().take(10)
    }
    
    private fun refreshSuggestionBar() {
        // Sync currentText with actual input field content
        syncCurrentText()
        // Recreate the keyboard to update suggestions
        recreateKeyboard()
    }
    
    private fun syncCurrentText() {
        // Get the current text from the input field
        val currentInputText = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        currentText = currentInputText
    }
    
    private fun createQPRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 2, 8, 2) // Reduced vertical padding to eliminate gap
        
        // Q-P keys only (no backspace like Gboard)
        letterKeys[0].forEach { key ->
            val button = createKeyButtonWithSuperscript(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            row.addView(button)
        }
        
        return row
    }
    
    private fun createALRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 2, 8, 2) // Reduced vertical padding to eliminate gap
        
        // A-L keys only (no tab key like Gboard)
        letterKeys[1].forEachIndexed { index, key ->
            val button = createKeyButton(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            
            // Add left margin to first key to offset the row
            if (index == 0) {
                val params = button.layoutParams as LinearLayout.LayoutParams
                params.leftMargin = 24 // Offset the 'a' key to the right
                button.layoutParams = params
            }
            
            // Add right margin to last key to extend the row further right
            if (index == letterKeys[1].size - 1) {
                val params = button.layoutParams as LinearLayout.LayoutParams
                params.rightMargin = 24 // Extend the 'l' key to the right of 'p'
                button.layoutParams = params
            }
            
            row.addView(button)
        }
        
        return row
    }
    
    private fun createZMRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 2, 8, 2) // Reduced vertical padding to eliminate gap
        
        // Left shift key
        val leftShiftButton = createKeyButton("⇧", Color.parseColor("#3A3A3A"), 1.5f)
        leftShiftButton.setOnClickListener { 
            isShiftPressed = !isShiftPressed
            recreateKeyboard()
        }
        row.addView(leftShiftButton)
        
        // Z-M keys (offset to the right of A-L row)
        letterKeys[2].forEachIndexed { index, key ->
            val button = createKeyButton(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            
            // Add left margin to first key to offset the row further
            if (index == 0) {
                val params = button.layoutParams as LinearLayout.LayoutParams
                params.leftMargin = 16 // Offset the 'z' key to the right of 'a'
                button.layoutParams = params
            }
            
            row.addView(button)
        }
        
        // Backspace key (like Gboard)
        val backspaceButton = createKeyButton("⌫", Color.parseColor("#3A3A3A"), 1.5f)
        backspaceButton.setOnClickListener { 
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            // Update current text and refresh suggestions
            if (currentText.isNotEmpty()) {
                currentText = currentText.dropLast(1)
                refreshSuggestionBar()
            }
        }
        row.addView(backspaceButton)
        
        return row
    }
    
    private fun createBottomRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 2, 8, 2) // Reduced vertical padding to eliminate gap
        
        // Numbers/Symbols toggle (left) - smaller font to prevent line break
        val toggleText = if (isNumbersMode || isSymbolsMode) "ABC" else "?123"
        val toggleButton = createKeyButton(toggleText, Color.parseColor("#3A3A3A"), 1.2f)
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Smaller font for ?123
        toggleButton.setOnClickListener { 
            isNumbersMode = !isNumbersMode
            isSymbolsMode = false
            recreateKeyboard()
        }
        row.addView(toggleButton)
        
        // .eth button (replacing emoji) with long-press menu
        val ethButton = createKeyButton(".eth", Color.parseColor("#0080BC"), 1f)
        ethButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Smaller font for .eth
        ethButton.setOnClickListener { 
            inputConnection?.commitText(".eth", 1)
            currentText += ".eth"
        }
        ethButton.setOnLongClickListener {
            showETHSubdomainMenu(ethButton)
            true
        }
        row.addView(ethButton)
        
        // Globe icon for keyboard switching
        val globeButton = createKeyButton("🌐", Color.parseColor("#3A3A3A"), 1f)
        globeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Smaller font for globe
        globeButton.setOnClickListener { 
            switchToNextInputMethod()
        }
        row.addView(globeButton)
        
        // Space bar (wider like Gboard)
        val spaceButton = createKeyButton("English", Color.parseColor("#4A4A4A"), 4f)
        spaceButton.setOnClickListener { 
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSpacePressTime < 500) {
                // Double tap space for period
                inputConnection?.commitText(". ", 1)
                currentText += ". "
            } else {
                onKeyPress(" ")
            }
            lastSpacePressTime = currentTime
        }
        row.addView(spaceButton)
        
        // Period key (like Gboard)
        val periodButton = createKeyButton(".", Color.parseColor("#4A4A4A"), 1f)
        periodButton.setOnClickListener { onKeyPress(".") }
        row.addView(periodButton)
        
        // Enter key (right side)
        val enterButton = createKeyButton("⏎", Color.parseColor("#3A3A3A"), 1.2f)
        enterButton.setOnClickListener { 
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
        row.addView(enterButton)
        
        return row
    }
    
    private fun createNumbersRow1(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 4, 8, 4)
        
        // Numbers 1-0
        numberKeys[0].forEach { key ->
            val button = createKeyButton(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            row.addView(button)
        }
        
        return row
    }
    
    private fun createNumbersRow2(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 4, 8, 4)
        
        // Symbols row
        numberKeys[1].forEach { key ->
            val button = createKeyButton(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            row.addView(button)
        }
        
        return row
    }
    
    private fun createNumbersRow3(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 4, 8, 4)
        
        // More symbols
        numberKeys[2].forEach { key ->
            val button = createKeyButton(key, Color.parseColor("#4A4A4A"))
            button.setOnClickListener { onKeyPress(key) }
            row.addView(button)
        }
        
        return row
    }
    
    
    private fun createKeyButton(text: String, backgroundColor: Int, weight: Float = 1f): Button {
        val button = Button(this)
        button.text = if (isShiftPressed && text.length == 1 && !isNumbersMode) text.uppercase() else text.lowercase()
        button.setTextColor(Color.parseColor("#FFFFFF"))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) // Larger text
        button.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Create Gboard-like rounded corners with proper styling
        val drawable = GradientDrawable()
        drawable.setColor(backgroundColor)
        drawable.cornerRadius = 8f // Perfect Gboard radius
        drawable.setStroke(0, Color.TRANSPARENT)
        button.background = drawable
        
        // Layout params with perfect Gboard spacing
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        params.setMargins(2, 4, 2, 4) // Tighter horizontal margins for better alignment
        params.height = 140 // Better height for key visibility
        button.layoutParams = params
        
        // Add elevation for depth
        button.elevation = 2f
        
        // Add enhanced touch feedback
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Haptic feedback
                    triggerHapticFeedback()
                    
                    // Key press animation
                    animateKeyPress(button)
                    
                    // Key sound
                    playKeySound()
                }
                MotionEvent.ACTION_UP -> {
                    // Key release animation
                    animateKeyRelease(button)
                }
            }
            false // Don't consume the event
        }
        
        return button
    }
    
    private fun createKeyButtonWithSuperscript(text: String, backgroundColor: Int, weight: Float = 1f): Button {
        val button = Button(this)

        // Simple, clean text - NO superscripts or extra characters
        val displayText = if (isShiftPressed && text.length == 1 && !isNumbersMode) text.uppercase() else text.lowercase()

        button.text = displayText
        button.setTextColor(Color.parseColor("#FFFFFF"))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) // Larger text
        button.typeface = android.graphics.Typeface.DEFAULT_BOLD

        // Create Gboard-like rounded corners with proper styling
        val drawable = GradientDrawable()
        drawable.setColor(backgroundColor)
        drawable.cornerRadius = 8f
        drawable.setStroke(0, Color.TRANSPARENT)
        button.background = drawable

        // Layout params with perfect Gboard spacing
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        params.setMargins(2, 4, 2, 4) // Tighter horizontal margins for better alignment
        params.height = 140 // Better height for key visibility
        button.layoutParams = params

        // Add elevation for depth
        button.elevation = 2f

        // Add enhanced touch feedback
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Haptic feedback
                    triggerHapticFeedback()

                    // Key press animation
                    animateKeyPress(button)

                    // Key sound
                    playKeySound()
                }
                MotionEvent.ACTION_UP -> {
                    // Key release animation
                    animateKeyRelease(button)
                }
            }
            false // Don't consume the event
        }

        return button
    }
    
    private fun createENSKeyButton(text: String, backgroundColor: Int, weight: Float = 1f): Button {
        val button = Button(this)
        
        // Add superscript numbers for Q-P row (like Gboard)
        val displayText = if (text.length == 1 && letterKeys[0].contains(text.lowercase())) {
            val index = letterKeys[0].indexOf(text.lowercase())
            if (index != -1) {
                val number = (index + 1) % 10
                val superscript = when (number) {
                    1 -> "¹"
                    2 -> "²"
                    3 -> "³"
                    4 -> "⁴"
                    5 -> "⁵"
                    6 -> "⁶"
                    7 -> "⁷"
                    8 -> "⁸"
                    9 -> "⁹"
                    0 -> "⁰"
                    else -> number.toString()
                }
                "${text.uppercase()}$superscript"
            } else {
                text.uppercase()
            }
        } else {
            if (isShiftPressed && text.length == 1 && !isNumbersMode) text.uppercase() else text
        }
        
        button.text = displayText
        button.setTextColor(Color.parseColor("#FFFFFF"))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Slightly larger for better readability
        button.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Create Gboard-like rounded corners with proper styling
        val drawable = GradientDrawable()
        drawable.setColor(backgroundColor)
        drawable.cornerRadius = 12f
        drawable.setStroke(0, Color.TRANSPARENT)
        button.background = drawable
        
        // Layout params with proper sizing and spacing
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        params.setMargins(3, 3, 3, 3)
        params.height = 110
        button.layoutParams = params
        
        // Add elevation for depth
        button.elevation = 3f
        
        return button
    }
    
    private fun recreateKeyboard() {
        // Recreate the entire keyboard view
        val newView = onCreateInputView()
        setInputView(newView)
    }
    
    private fun onKeyPress(key: String) {
        val textToInsert = if (isShiftPressed && key.length == 1 && !isNumbersMode) {
            key.uppercase()
        } else {
            key
        }
        
        inputConnection?.commitText(textToInsert, 1)
        currentText += textToInsert
        
        // Refresh suggestions when typing
        refreshSuggestionBar()
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputConnection = currentInputConnection
        
        // Sync current text with input field
        syncCurrentText()
        
        // Start monitoring for text selection changes
        startSelectionMonitoring()
    }
    
    private fun startSelectionMonitoring() {
        // Use a timer to periodically check for selected text
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val selectionChecker = object : Runnable {
            override fun run() {
                checkForSelectedText()
                // Check every 500ms
                handler.postDelayed(this, 500)
            }
        }
        handler.post(selectionChecker)
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        inputConnection = null
    }
    
    private fun checkForSelectedText() {
        // Check if there's selected text and if it's an ENS name
        val selectedText = getSelectedText()
        println("Selection check: selectedText = '$selectedText'")
        if (selectedText != null && selectedText != lastResolvedText) {
            println("Selection check: isValidENS = ${ensResolver.isValidENS(selectedText)}")
            if (ensResolver.isValidENS(selectedText)) {
                println("Selection check: Resolving ENS name: $selectedText")
                resolveSelectedENS(selectedText)
            }
        }
    }
    
    private fun getSelectedText(): String? {
        return try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                val selectedText = inputConnection.getSelectedText(0)
                println("getSelectedText: inputConnection exists, selectedText = '$selectedText'")
                selectedText?.toString()
            } else {
                println("getSelectedText: inputConnection is null")
                null
            }
        } catch (e: Exception) {
            println("getSelectedText: Exception = ${e.message}")
            null
        }
    }
    
    private fun resolveSelectedENS(selectedText: String) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce to prevent multiple resolutions
        if (currentTime - lastENSResolutionTime < 1000) {
            return
        }
        
        lastENSResolutionTime = currentTime
        lastResolvedText = selectedText // Track what we're resolving
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val address = ensResolver.resolveENSName(selectedText.trim())
                if (address != null) {
                    println("ENS Resolution Success: $selectedText -> $address")
                    
                    // Replace the selected text with the resolved address
                    withContext(Dispatchers.Main) {
                        replaceSelectedText(address)
                        saveENSName(selectedText.trim())
                        showENSResolutionFeedback(selectedText.trim(), address)
                        
                        // Clear the resolved text after a delay to allow new selections
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            lastResolvedText = ""
                        }, 2000)
                    }
                } else {
                    println("ENS Resolution Error: $selectedText not found")
                    withContext(Dispatchers.Main) {
                        showENSResolutionError(selectedText.trim())
                        // Clear the resolved text even on error
                        lastResolvedText = ""
                    }
                }
            } catch (e: Exception) {
                println("ENS Resolution error: ${e.message}")
                withContext(Dispatchers.Main) {
                    showENSResolutionError(selectedText.trim())
                    // Clear the resolved text even on error
                    lastResolvedText = ""
                }
            }
        }
    }
    
    private fun replaceSelectedText(newText: String) {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Delete the selected text and insert the new text
                inputConnection.commitText(newText, 1)
            }
        } catch (e: Exception) {
            println("Error replacing selected text: ${e.message}")
        }
    }
    
    private fun showENSResolutionFeedback(ensName: String, address: String) {
        // For now, just log the resolution - we can add visual feedback later
        println("ENS Resolution Success: $ensName -> $address")
        // TODO: Add visual feedback in suggestion bar
    }
    
    private fun showENSResolutionError(ensName: String) {
        // For now, just log the error - we can add visual feedback later
        println("ENS Resolution Error: $ensName not found")
        // TODO: Add visual feedback in suggestion bar
    }
    
    private fun showETHSubdomainMenu(anchorButton: Button) {
        // Create a popup window with popular ENS subdomains
        val popupView = LinearLayout(this)
        popupView.orientation = LinearLayout.HORIZONTAL
        popupView.setPadding(16, 12, 16, 12)
        popupView.setBackgroundColor(Color.parseColor("#2C2C2C"))
        
        // Popular ENS subdomains (like iOS)
        val subdomains = listOf(
            ".base.eth", ".uni.eth", ".dao.eth", ".ens.eth"
        )
        
        subdomains.forEach { subdomain ->
            val button = createSubdomainButton(subdomain)
            button.setOnClickListener {
                inputConnection?.commitText(subdomain, 1)
                currentText += subdomain
                // Dismiss popup (it will auto-dismiss)
            }
            popupView.addView(button)
        }
        
        // Create and show popup window
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Position popup above the .eth button
        popupWindow.showAsDropDown(anchorButton, 0, -anchorButton.height - 20)
        
        // Auto-dismiss after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (popupWindow.isShowing) {
                popupWindow.dismiss()
            }
        }, 3000)
    }
    
    private fun createSubdomainButton(text: String): Button {
        val button = Button(this)
        button.text = text
        button.setTextColor(Color.parseColor("#FFFFFF"))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.setPadding(12, 8, 12, 8)
        
        // Blue background like other ENS elements
        val drawable = GradientDrawable()
        drawable.setColor(Color.parseColor("#0080BC"))
        drawable.cornerRadius = 8f
        button.background = drawable
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(4, 0, 4, 0)
        button.layoutParams = params
        
        return button
    }
    
    private fun saveENSName(ensName: String) {
        val savedENS = getSavedENS().toMutableSet()
        savedENS.add(ensName)
        // Keep only last 10 resolved ENS names
        val limitedENS = savedENS.take(10)
        prefs.edit().putStringSet("saved_ens", limitedENS.toSet()).apply()
    }
    
    private fun getSavedENS(): List<String> {
        return prefs.getStringSet("saved_ens", emptySet())?.toList() ?: emptyList()
    }
    
    private fun switchToNextInputMethod() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e("FusionENS", "Failed to switch input method", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
    }
    
    // Enhanced feedback methods
    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
    
    private fun playKeySound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
        } catch (e: Exception) {
            // Ignore sound errors
        }
    }
    
    private fun animateKeyPress(button: Button) {
        val scaleAnimation = ScaleAnimation(
            1.0f, 0.95f, // Scale X
            1.0f, 0.95f, // Scale Y
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f, // Pivot X
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f  // Pivot Y
        )
        scaleAnimation.duration = 100
        scaleAnimation.fillAfter = true
        button.startAnimation(scaleAnimation)
        
        // Change background color for press effect (preserve .eth button color)
        val drawable = button.background as GradientDrawable
        if (button.text == ".eth") {
            drawable.setColor(Color.parseColor("#0066A0")) // Darker blue when pressed
        } else {
            drawable.setColor(Color.parseColor("#5A5A5A")) // Lighter when pressed
        }
    }
    
    private fun animateKeyRelease(button: Button) {
        val scaleAnimation = ScaleAnimation(
            0.95f, 1.0f, // Scale X
            0.95f, 1.0f, // Scale Y
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f, // Pivot X
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f  // Pivot Y
        )
        scaleAnimation.duration = 100
        scaleAnimation.fillAfter = true
        button.startAnimation(scaleAnimation)
        
        // Restore original background color (preserve .eth button color)
        val drawable = button.background as GradientDrawable
        if (button.text == ".eth") {
            drawable.setColor(Color.parseColor("#0080BC")) // Original blue color
        } else {
            drawable.setColor(Color.parseColor("#4A4A4A")) // Original gray color
        }
    }
    
    // Gesture typing support
    private fun processGestureTyping() {
        if (gesturePath.size < 3) return
        
        // Simple gesture recognition - find closest keys along the path
        val gestureText = StringBuilder()
        val keyPositions = getKeyPositions()
        
        for (point in gesturePath) {
            val closestKey = findClosestKey(point, keyPositions)
            if (closestKey != null && !gestureText.contains(closestKey)) {
                gestureText.append(closestKey)
            }
        }
        
        if (gestureText.isNotEmpty()) {
            inputConnection?.commitText(gestureText.toString(), 1)
            currentText += gestureText.toString()
        }
    }
    
    private fun getKeyPositions(): Map<String, Pair<Float, Float>> {
        // This would need to be implemented based on actual key positions
        // For now, return empty map
        return emptyMap()
    }
    
    private fun findClosestKey(point: Pair<Float, Float>, keyPositions: Map<String, Pair<Float, Float>>): String? {
        // Find the closest key to the given point
        var closestKey: String? = null
        var minDistance = Float.MAX_VALUE
        
        for ((key, position) in keyPositions) {
            val dx = point.first - position.first
            val dy = point.second - position.second
            val distance = sqrt(dx * dx + dy * dy)
            if (distance < minDistance) {
                minDistance = distance
                closestKey = key
            }
        }
        
        return closestKey
    }
}

