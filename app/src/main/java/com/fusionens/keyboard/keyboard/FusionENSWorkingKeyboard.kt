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
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.fusionens.keyboard.ens.ENSResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple, working Fusion ENS Keyboard
 */
class FusionENSWorkingKeyboard : InputMethodService() {
    
    private var inputConnection: InputConnection? = null
    private var currentText = ""
    private val ensResolver = ENSResolver()
    private var isShiftPressed = false
    private var isNumbersMode = false
    private var lastSpacePressTime = 0L
    
    // Enhanced features
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    
    // Proper QWERTY layouts
    private val qwertyLayout = arrayOf(
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("z", "x", "c", "v", "b", "n", "m")
    )
    
    private val numberLayout = arrayOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("-", "/", ":", ";", "(", ")", "$", "&", "@"),
        arrayOf(".", ",", "?", "!", "'", "\"", "=", "+", "#")
    )
    
    override fun onCreateInputView(): View {
        initializeEnhancedFeatures()
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"))
        layout.setPadding(4, 4, 4, 4)
        
        // Add word suggestions row
        val suggestionsRow = createWordSuggestionsRow()
        layout.addView(suggestionsRow)
        
        // Create the keyboard
        createKeyboard(layout)
        
        return layout
    }
    
    private fun initializeEnhancedFeatures() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    }
    
    private fun createWordSuggestionsRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 4, 8, 4)
        row.setBackgroundColor(Color.parseColor("#1E1E1E"))
        
        // Common word suggestions
        val suggestions = listOf("the", "and", "you", "that", "was")
        
        suggestions.forEach { suggestion ->
            val button = TextView(this)
            button.text = suggestion
            button.setTextColor(Color.WHITE)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            button.setPadding(12, 8, 12, 8)
            
            // Rounded background
            val drawable = GradientDrawable()
            drawable.setColor(Color.parseColor("#616161"))
            drawable.cornerRadius = 16f
            button.background = drawable
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(4, 0, 4, 0)
            button.layoutParams = params
            
            button.setOnClickListener {
                inputConnection?.commitText("$suggestion ", 1)
                currentText += "$suggestion "
            }
            
            row.addView(button)
        }
        
        return row
    }
    
    private fun createKeyboard(layout: LinearLayout) {
        val currentLayout = if (isNumbersMode) numberLayout else qwertyLayout
        
        // Q-P row (or 1-0 row)
        val qpRow = createKeyRow(currentLayout[0], true)
        layout.addView(qpRow)
        
        // A-L row (or symbols row)
        val alRow = createKeyRow(currentLayout[1], false)
        layout.addView(alRow)
        
        // Z-M row (or bottom symbols row)
        val zmRow = createKeyRow(currentLayout[2], false)
        layout.addView(zmRow)
        
        // Bottom row with space and function keys
        val bottomRow = createBottomRow()
        layout.addView(bottomRow)
    }
    
    private fun createKeyRow(keys: Array<String>, isTopRow: Boolean): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(2, 2, 2, 2)
        
        // Add shift key for non-top rows
        if (!isTopRow && keys.size < 10) {
            val shiftButton = createFunctionKey("⇧", 1.2f)
            shiftButton.setOnClickListener {
                isShiftPressed = !isShiftPressed
                recreateKeyboard()
            }
            row.addView(shiftButton)
        }
        
        // Add letter keys in correct order
        keys.forEach { key ->
            val button = if (isTopRow) {
                createKeyWithSuperscript(key)
            } else {
                createLetterKey(key)
            }
            button.setOnClickListener {
                onKeyPress(key)
            }
            row.addView(button)
        }
        
        // Add backspace for top row
        if (isTopRow) {
            val backspaceButton = createFunctionKey("⌫", 1.2f)
            backspaceButton.setOnClickListener {
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            row.addView(backspaceButton)
        }
        
        return row
    }
    
    private fun createBottomRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(4, 4, 4, 4)
        
        // Numbers/Symbols toggle
        val toggleButton = createFunctionKey("?123", 1.2f)
        toggleButton.setOnClickListener {
            isNumbersMode = !isNumbersMode
            recreateKeyboard()
        }
        row.addView(toggleButton)
        
        // Globe icon for keyboard switching
        val globeButton = createFunctionKey("🌐", 1f)
        globeButton.setOnClickListener {
            switchToNextInputMethod()
        }
        row.addView(globeButton)
        
        // Space bar (wider)
        val spaceButton = createSpaceKey()
        row.addView(spaceButton)
        
        // Enter key
        val enterButton = createFunctionKey("⏎", 1.2f)
        enterButton.setOnClickListener {
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
        row.addView(enterButton)
        
        return row
    }
    
    private fun createLetterKey(text: String): Button {
        return createKey(text, Color.parseColor("#424242"), 1f)
    }
    
    private fun createKeyWithSuperscript(text: String): Button {
        val displayText = if (text.length == 1 && qwertyLayout[0].contains(text.lowercase())) {
            val index = qwertyLayout[0].indexOf(text.lowercase())
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
        
        return createKey(displayText, Color.parseColor("#424242"), 1f)
    }
    
    private fun createFunctionKey(text: String, weight: Float): Button {
        return createKey(text, Color.parseColor("#616161"), weight)
    }
    
    private fun createSpaceKey(): Button {
        val button = createKey("English", Color.parseColor("#424242"), 4f)
        button.setOnClickListener {
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
        return button
    }
    
    private fun createKey(text: String, backgroundColor: Int, weight: Float): Button {
        val button = Button(this)
        button.text = text
        button.setTextColor(Color.WHITE)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        button.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Professional rounded background
        val drawable = GradientDrawable()
        drawable.setColor(backgroundColor)
        drawable.cornerRadius = 8f
        button.background = drawable
        
        // Layout params with proper sizing
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        params.setMargins(2, 2, 2, 2)
        params.height = 80
        button.layoutParams = params
        
        // Add elevation for depth
        button.elevation = 2f
        
        // Touch feedback
        button.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    triggerHapticFeedback()
                    playKeySound()
                    animateKeyPress(button)
                }
                android.view.MotionEvent.ACTION_UP -> {
                    animateKeyRelease(button)
                }
            }
            false
        }
        
        return button
    }
    
    private fun recreateKeyboard() {
        val currentView = window?.currentFocus
        if (currentView != null && currentView is LinearLayout) {
            val layout = currentView
            layout.removeAllViews()
            createKeyboard(layout)
        }
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
            1.0f, 0.95f,
            1.0f, 0.95f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 100
        scaleAnimation.fillAfter = true
        button.startAnimation(scaleAnimation)
    }
    
    private fun animateKeyRelease(button: Button) {
        val scaleAnimation = ScaleAnimation(
            0.95f, 1.0f,
            0.95f, 1.0f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 100
        scaleAnimation.fillAfter = true
        button.startAnimation(scaleAnimation)
    }
    
    private fun onKeyPress(key: String) {
        inputConnection?.commitText(key, 1)
        currentText += key
        checkForENSResolution(currentText)
    }
    
    private fun checkForENSResolution(text: String) {
        if (ensResolver.isValidENS(text)) {
            CoroutineScope(Dispatchers.IO).launch {
                val address = ensResolver.resolveENSName(text)
                if (address != null) {
                    println("ENS Resolved: $text -> $address")
                }
            }
        }
    }
    
    private fun switchToNextInputMethod() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            // Ignore errors
        }
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputConnection = currentInputConnection
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        inputConnection = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
    }
}
