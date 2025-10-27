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
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.view.animation.AlphaAnimation
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
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
    
    // Popup windows
    private lateinit var btcPopup: PopupWindow
    
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
    
    // Theme support
    private var isDarkMode = true
    
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
        // Initialize SharedPreferences first
        prefs = getSharedPreferences("fusion_ens_keyboard", Context.MODE_PRIVATE)
        
        // Detect system theme
        detectSystemTheme()
        
        // Initialize enhanced features
        initializeEnhancedFeatures()
        
        // Sync current text with input field
        syncCurrentText()
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(getBackgroundColor()) // Dynamic background based on theme
        
        // Set layout to fill available space
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // Create the keyboard (includes suggestion bar in top row)
        createKeyboard(layout)
        
        return layout
    }
    
    private fun detectSystemTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun getBackgroundColor(): Int {
        return if (isDarkMode) Color.parseColor("#2C2C2C") else Color.parseColor("#F5F5F5")
    }
    
    private fun getKeyColor(): Int {
        return if (isDarkMode) Color.parseColor("#4A4A4A") else Color.parseColor("#E0E0E0")
    }
    
    private fun getFunctionKeyColor(): Int {
        return if (isDarkMode) Color.parseColor("#3A3A3A") else Color.parseColor("#D0D0D0")
    }
    
    private fun getTextColor(): Int {
        return if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
    }
    
    private fun getSecondaryTextColor(): Int {
        return if (isDarkMode) Color.parseColor("#666666") else Color.parseColor("#666666")
    }
    
    private fun initializeEnhancedFeatures() {
        // Initialize vibrator for haptic feedback
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Initialize tone generator for key sounds based on settings
        val soundEnabled = prefs.getBoolean("keypress_sound_enabled", false)
        if (soundEnabled) {
            val volume = prefs.getInt("sound_volume", 50)
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, volume)
        }
        
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
        row.setBackgroundColor(getBackgroundColor())
        
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
        button.setTextColor(Color.parseColor("#0080BC")) // ENS blue color for suggestions
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
        button.setTextColor(getTextColor())
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
        container.setBackgroundColor(getBackgroundColor())
        
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
                    // Replace current text with the selected suggestion
                    replaceCurrentTextWithSuggestion(ensName)
                    currentText = ensName
                    // Refresh suggestions after selection
                    refreshSuggestionBar()
                }
                row.addView(button)
                
                // Add separator (|) between suggestions, but not after the last one
                if (index < suggestions.size - 1) {
                    val separator = TextView(this)
                    separator.text = "|"
                    separator.setTextColor(getSecondaryTextColor())
                    separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    separator.setPadding(8, 8, 8, 8)
                    row.addView(separator)
                }
            }
        } else {
            // Show empty bar when no suggestions - use space to maintain proper height
            val emptyText = TextView(this)
            emptyText.text = " "
            emptyText.setTextColor(getSecondaryTextColor())
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
        
        // If user is typing, filter suggestions based on the last word
        if (currentText.isNotEmpty()) {
            // Get the last word being typed (after the last space)
            val words = currentText.trim().split("\\s+".toRegex())
            val lastWord = if (words.isNotEmpty()) words.last() else ""
            
            // Only filter if the last word is not empty and not just spaces
            if (lastWord.isNotEmpty()) {
                return allSuggestions.filter { ensName ->
                    ensName.lowercase().contains(lastWord.lowercase())
                }.distinct().take(10)
            }
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
    
    private fun resetSuggestionsToDefault() {
        // Clear currentText for filtering purposes to show default suggestions
        // but keep the actual input field content intact
        val originalText = currentText
        currentText = ""
        // Recreate the keyboard to show default suggestions
        recreateKeyboard()
        // Restore the original text for future operations
        currentText = originalText
    }
    
    private fun syncCurrentText() {
        // Get the current text from the input field
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val textAfterCursor = inputConnection?.getTextAfterCursor(1000, 0)?.toString() ?: ""
        currentText = textBeforeCursor + textAfterCursor
    }
    
    private fun findENSNameInText(text: String): String? {
        // Look for ENS names in the text (ending with .eth)
        val ensPattern = Regex("\\b\\w+\\.eth\\b")
        val matches = ensPattern.findAll(text)
        return matches.lastOrNull()?.value
    }
    
    private fun findPartialTextInInput(text: String): String? {
        // Find the last word or partial word that the user is typing
        // This helps identify what to replace when selecting suggestions
        val words = text.trim().split("\\s+".toRegex())
        if (words.isNotEmpty()) {
            val lastWord = words.last()
            // If the last word is not empty and doesn't end with a space, it's partial
            if (lastWord.isNotEmpty() && !text.endsWith(" ")) {
                return lastWord
            }
        }
        return null
    }
    
    private fun checkForENSResolution(text: String) {
        if (ensResolver.isValidENS(text)) {
            CoroutineScope(Dispatchers.IO).launch {
                val address = ensResolver.resolveENSName(text)
                if (address != null) {
                    // Replace the ENS name with the resolved address
                    withContext(Dispatchers.Main) {
                        replaceCurrentTextInInputField(address)
                        currentText = address
                    }
                }
            }
        }
    }
    
    private fun replaceCurrentTextWithSuggestion(suggestion: String) {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Get the current text before cursor
                val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                
                // Find the partial text that matches the current input
                val partialText = findPartialTextInInput(textBeforeCursor)
                if (partialText != null) {
                    // Find the position of the partial text in the input
                    val partialStartIndex = textBeforeCursor.lastIndexOf(partialText)
                    if (partialStartIndex != -1) {
                        // Calculate how many characters to delete from the cursor position
                        val deleteCount = textBeforeCursor.length - partialStartIndex
                        
                        // Delete the partial text using deleteSurroundingText
                        inputConnection.deleteSurroundingText(deleteCount, 0)
                        
                        // Insert the new suggestion
                        inputConnection.commitText(suggestion, 1)
                        
                        // Update currentText to reflect the change
                        currentText = textBeforeCursor.substring(0, partialStartIndex) + suggestion + textAfterCursor
                    } else {
                        // Fallback: replace all text before cursor
                        inputConnection.deleteSurroundingText(textBeforeCursor.length, 0)
                        inputConnection.commitText(suggestion, 1)
                        currentText = suggestion + textAfterCursor
                    }
                } else {
                    // Fallback: replace all text before cursor
                    inputConnection.deleteSurroundingText(textBeforeCursor.length, 0)
                    inputConnection.commitText(suggestion, 1)
                    currentText = suggestion + textAfterCursor
                }
            }
        } catch (e: Exception) {
        }
    }
    
    private fun resolveCurrentTextAsENS() {
        try {
            // Get the complete text from the input field (not just currentText)
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                val beforeText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val fullText = beforeText + afterText
                
                
                // Use the same smart extraction logic as extractInputFromAddressBar
                val trimmedText = fullText.trim()
                
                // Smart extraction: look for ENS patterns in the text
                val ensPatterns = listOf(
                    // Multi-chain ENS with .eth (e.g., "ses.eth:x", "vitalik.eth:btc", "jesse.base.eth:btc")
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth:([a-zA-Z0-9]+)"),
                    // Multi-chain ENS shortcut (e.g., "ses:x", "vitalik:btc", "jesse.base:btc") 
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?):([a-zA-Z0-9]+)"),
                    // Standard ENS (e.g., "vitalik.eth", "ses.eth", "jesse.base.eth")
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth")
                )
                
                // Find the first ENS pattern in the text
                for (pattern in ensPatterns) {
                    val match = pattern.find(trimmedText)
                    if (match != null) {
                        val ensName = match.value
                        
                        // For spacebar long press, always just replace text (don't navigate)
                        // This is different from Enter key which should navigate in browser context
                        resolveENSAndReplace(ensName)
                        return
                    }
                }
                
                // If no ENS pattern found, try the last word (for backward compatibility)
                val words = trimmedText.split("\\s+".toRegex())
                val lastWord = if (words.isNotEmpty()) words.last() else trimmedText
                
                if (ensResolver.isValidENS(lastWord)) {
                    val ensName = ensResolver.getENSNameFromText(lastWord)
                    if (ensName != null) {
                        // For spacebar long press, always just replace text (don't navigate)
                        resolveENSAndReplace(ensName)
                    }
                } else {
                }
            } else {
            }
        } catch (e: Exception) {
            // Log error and prevent crash
        }
    }
    
    private fun isInBrowserContext(): Boolean {
        try {
            // Get the package name of the app that's using the keyboard
            val currentPackageName = currentInputEditorInfo?.packageName?.lowercase()
            
            val browserPackages = listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "org.mozilla.fenix",
                "com.microsoft.emmx",
                "com.opera.browser",
                "com.opera.browser.beta",
                "com.brave.browser",
                "com.duckduckgo.mobile.android",
                "com.vivaldi.browser",
                "com.samsung.android.app.sbrowser",
                "com.sec.android.app.sbrowser"
            )

            // Check if current package is a browser
            val isBrowser = currentPackageName?.let { packageName ->
                browserPackages.any { packageName.contains(it) }
            } ?: false
            
            if (isBrowser) {
                return true
            }
            
            // Check if the current input field is likely an address bar
            // Note: We can't easily detect address bar input type from InputConnection
            // So we rely mainly on package name detection for browser context
        } catch (e: Exception) {
        }

        return false
    }
    
    private fun handleBrowserENSResolution(ensName: String) {
        try {
            val defaultAction = prefs.getString("default_browser_action", "etherscan") ?: "etherscan"
        
        CoroutineScope(Dispatchers.Main).launch {
            when (defaultAction) {
                "etherscan" -> {
                    // Resolve to address and open Etherscan
                    val resolvedAddress = ensResolver.resolveENSName(ensName)
                    if (resolvedAddress != null) {
                        openEtherscan(resolvedAddress)
                        saveENSName(ensName)
                    }
                }
                "url" -> {
                    // Try to resolve URL text record
                    val url = ensResolver.resolveENSTextRecord(ensName, "url")
                    if (url != null) {
                        openURL(url)
                        saveENSName(ensName)
                    } else {
                        // Fallback to Etherscan
                        val resolvedAddress = ensResolver.resolveENSName(ensName)
                        if (resolvedAddress != null) {
                            openEtherscan(resolvedAddress)
                            saveENSName(ensName)
                        }
                    }
                }
                "github" -> {
                    // Try to resolve GitHub text record
                    val github = ensResolver.resolveENSTextRecord(ensName, "github")
                    if (github != null) {
                        openGitHub(github)
                        saveENSName(ensName)
                    } else {
                        // Fallback to Etherscan
                        val resolvedAddress = ensResolver.resolveENSName(ensName)
                        if (resolvedAddress != null) {
                            openEtherscan(resolvedAddress)
                            saveENSName(ensName)
                        }
                    }
                }
                "x" -> {
                    // Try to resolve X/Twitter text record
                    val twitter = ensResolver.resolveENSTextRecord(ensName, "x")
                    if (twitter != null) {
                        openTwitter(twitter)
                        saveENSName(ensName)
                    } else {
                        // Fallback to Etherscan
                        val resolvedAddress = ensResolver.resolveENSName(ensName)
                        if (resolvedAddress != null) {
                            openEtherscan(resolvedAddress)
                            saveENSName(ensName)
                        }
                    }
                }
            }
        }
        } catch (e: Exception) {
            // Log error and prevent crash
        }
    }
    
    private fun resolveENSAndReplace(ensName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val resolvedValue = ensResolver.resolveENSName(ensName)
            if (resolvedValue != null) {
                // Check if we're in browser context
                if (isInBrowserContext()) {
                    // In browser context - check if this is a text record (URL) or an address
                    if (resolvedValue.startsWith("http://") || resolvedValue.startsWith("https://")) {
                        // This is a text record resolved to a URL - open it
                        openURL(resolvedValue)
                        saveENSName(ensName)
                    } else {
                        // This is an address - replace only the ENS name
                        replaceENSNameInText(ensName, resolvedValue)
                        saveENSName(ensName)
                    }
                } else {
                    // Not in browser context - always replace only the ENS name (never open URLs)
                    // This matches iOS behavior: text records resolve to addresses in regular text fields
                    replaceENSNameInText(ensName, resolvedValue)
                    saveENSName(ensName)
                }
                
                // Show feedback
                triggerHapticFeedback()
            } else {
            }
        }
    }
    
    private fun replaceENSNameInText(ensName: String, resolvedValue: String) {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Get the complete text from the input field
                val beforeText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val fullText = beforeText + afterText
                
                
                // Find the ENS name in the complete text
                val ensNameIndex = fullText.indexOf(ensName)
                if (ensNameIndex != -1) {
                    // Calculate the position relative to cursor
                    val cursorPosition = beforeText.length
                    val ensNameStart = ensNameIndex
                    val ensNameEnd = ensNameIndex + ensName.length
                    
                    
                    // Move cursor to the start of the ENS name
                    val moveToStart = ensNameStart - cursorPosition
                    if (moveToStart != 0) {
                        inputConnection.setSelection(ensNameStart, ensNameStart)
                    }
                    
                    // Select the ENS name
                    inputConnection.setSelection(ensNameStart, ensNameEnd)
                    
                    // Replace the selected text with the resolved value
                    inputConnection.commitText(resolvedValue, 1)
                    
                    // Update current text
                    val newText = fullText.substring(0, ensNameStart) + resolvedValue + fullText.substring(ensNameEnd)
                    currentText = newText
                } else {
                    // Fallback: replace all text before cursor
                    inputConnection.deleteSurroundingText(beforeText.length, 0)
                    inputConnection.commitText(resolvedValue, 1)
                    currentText = resolvedValue + afterText
                }
            }
        } catch (e: Exception) {
        }
    }
    
    private fun replaceCurrentTextInInputField(newText: String) {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Get the current text before cursor
                val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                
                // Find the ENS name in the text before cursor
                val ensName = findENSNameInText(textBeforeCursor)
                if (ensName != null) {
                    // Find the position of the ENS name in the text
                    val ensStartIndex = textBeforeCursor.lastIndexOf(ensName)
                    if (ensStartIndex != -1) {
                        // Calculate how many characters to delete from the cursor position
                        val deleteCount = textBeforeCursor.length - ensStartIndex
                        
                        // Delete the ENS name using deleteSurroundingText
                        inputConnection.deleteSurroundingText(deleteCount, 0)
                        
                        // Insert the new text
                        inputConnection.commitText(newText, 1)
                        
                        // Update currentText to reflect the change
                        currentText = textBeforeCursor.substring(0, ensStartIndex) + newText + textAfterCursor
                    } else {
                        // Fallback: replace all text before cursor
                        inputConnection.deleteSurroundingText(textBeforeCursor.length, 0)
                        inputConnection.commitText(newText, 1)
                        currentText = newText + textAfterCursor
                    }
                } else {
                    // Fallback: replace all text before cursor
                    inputConnection.deleteSurroundingText(textBeforeCursor.length, 0)
                    inputConnection.commitText(newText, 1)
                    currentText = newText + textAfterCursor
                }
            }
        } catch (e: Exception) {
        }
    }
    
    private fun openEtherscan(address: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://etherscan.io/address/$address"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }
    
    private fun openURL(url: String) {
        try {
            val finalUrl = if (url.startsWith("http")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(finalUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }
    
    private fun openGitHub(username: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/$username"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }
    
    private fun openTwitter(username: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://x.com/$username"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }
    
    private fun createQPRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(8, 2, 8, 2) // Reduced vertical padding to eliminate gap
        
        // Q-P keys only (no backspace like Gboard)
        letterKeys[0].forEach { key ->
            val button = createKeyButtonWithSuperscript(key, getKeyColor())
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
            val button = createKeyButton(key, getKeyColor())
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
        val leftShiftButton = createKeyButton("⇧", getFunctionKeyColor(), 1.5f)
        leftShiftButton.setOnClickListener { 
            isShiftPressed = !isShiftPressed
            recreateKeyboard()
        }
        row.addView(leftShiftButton)
        
        // Z-M keys (offset to the right of A-L row)
        letterKeys[2].forEachIndexed { index, key ->
            val button = createKeyButton(key, getKeyColor())
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
        val backspaceButton = createKeyButton("⌫", getFunctionKeyColor(), 1.5f)
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
        val toggleButton = createKeyButton(toggleText, getFunctionKeyColor(), 1.2f)
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) // Smaller font for ?123
        toggleButton.setOnClickListener { 
            isNumbersMode = !isNumbersMode
            isSymbolsMode = false
            recreateKeyboard()
        }
        row.addView(toggleButton)
        
        // .eth button (replacing emoji) with long-press menu
        val ethButton = createKeyButton(".eth", Color.parseColor("#0080BC"), 1f)
        ethButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Larger font for better readability
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
        val globeButton = createKeyButton("🌐", getFunctionKeyColor(), 1f)
        globeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Larger font for better readability
        globeButton.setOnClickListener { 
            switchToNextInputMethod()
        }
        row.addView(globeButton)
        
        // Space bar (wider like Gboard)
        val spaceButton = createKeyButton("", getKeyColor(), 4f)
        spaceButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Consistent font size with other bottom row keys
        spaceButton.setOnClickListener { 
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSpacePressTime < 500) {
                // Double tap space for period
                inputConnection?.commitText(". ", 1)
                currentText += ". "
                // Reset suggestions after period
                resetSuggestionsToDefault()
            } else {
                // Single space press
                inputConnection?.commitText(" ", 1)
                currentText += " "
                // Refresh suggestions to show new word suggestions
                refreshSuggestionBar()
            }
            lastSpacePressTime = currentTime
        }
        
        // Long press spacebar to resolve ENS
        spaceButton.setOnLongClickListener {
            resolveCurrentTextAsENS()
            true // Consume the long press event
        }
        
        // Add proper touch handling for spacebar to fix stuck pressed state
        spaceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Haptic feedback
                    triggerHapticFeedback()
                    
                    // Key press animation
                    animateKeyPress(spaceButton)
                    
                    // Key sound
                    playKeySound()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Key release animation
                    animateKeyRelease(spaceButton)
                }
            }
            false // Don't consume the event - let click listeners work
        }
        row.addView(spaceButton)

        // :btc key (crypto ticker) - between space and period
        val btcButton = createKeyButton(":btc", Color.parseColor("#FF9500"), 1f) // Orange color like iOS
        btcButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Larger font for better readability
        // Ensure the button maintains its orange color after press
        btcButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Slightly darker orange on press
                    btcButton.setBackgroundColor(Color.parseColor("#E6850E"))
                    playKeySound()
                    triggerHapticFeedback()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Restore original orange color
                    btcButton.setBackgroundColor(Color.parseColor("#FF9500"))
                }
            }
            false // Don't consume the event
        }
        btcButton.setOnClickListener {
            inputConnection?.commitText(":btc", 1)
            currentText += ":btc"
        }
        btcButton.setOnLongClickListener {
            showCryptoTickerMenu(it as Button)
            true
        }
        row.addView(btcButton)

        // Period key (like Gboard)
        val periodButton = createKeyButton(".", getKeyColor(), 1f)
        periodButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Larger font for better readability
        periodButton.setOnClickListener { onKeyPress(".") }
        row.addView(periodButton)
        
        // Enter key (right side)
        val enterButton = createKeyButton("⏎", getFunctionKeyColor(), 1.2f)
        enterButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Larger font for better readability
        enterButton.setOnClickListener { 
            handleEnterKeyPress()
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
            val button = createKeyButton(key, getKeyColor())
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
            val button = createKeyButton(key, getKeyColor())
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
            val button = createKeyButton(key, getKeyColor())
            button.setOnClickListener { onKeyPress(key) }
            row.addView(button)
        }
        
        return row
    }
    
    
    private fun createKeyButton(text: String, backgroundColor: Int, weight: Float = 1f): Button {
        val button = Button(this)
        button.text = if (isShiftPressed && text.length == 1 && !isNumbersMode) text.uppercase() else text.lowercase()
        button.setTextColor(getTextColor())
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
        button.setTextColor(getTextColor())
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
        button.setTextColor(getTextColor())
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
        
        // Check if auto-resolve is enabled
        if (prefs.getBoolean("auto_resolve_enabled", false)) {
            checkForENSResolution(currentText)
        }
        
        // Refresh suggestions when typing
        refreshSuggestionBar()
    }
    
    private fun handleEnterKeyPress() {
        // Check if we're in a browser context
        val isBrowser = isInBrowserContext()
        
        if (isBrowser) {
            // In browser context - try to resolve ENS before triggering enter
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Get the FULL text from the input field
                val beforeText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val fullText = beforeText + afterText
                val trimmedFullText = fullText.trim()
                
                
                // Check if the FULL input is ONLY an ENS name (not part of a sentence)
                val isOnlyENS = trimmedFullText.isNotEmpty() && 
                               ensResolver.isValidENS(trimmedFullText) && 
                               !trimmedFullText.contains(" ") // No spaces = only ENS name
                
                if (isOnlyENS) {
                    // Show loading state on enter button
                    updateEnterButtonToLoading()
                    
                    // Resolve ENS and then trigger enter
                    resolveENSForEnterKey(trimmedFullText)
                } else {
                    // Not only an ENS name, proceed with normal enter
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                }
            } else {
                // No input connection, proceed with normal enter
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            }
        } else {
            // Not in browser context - proceed with normal enter
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
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
        if (selectedText != null && selectedText != lastResolvedText) {
            if (ensResolver.isValidENS(selectedText)) {
                resolveSelectedENS(selectedText)
            }
        }
    }
    
    private fun getSelectedText(): String? {
        return try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                val selectedText = inputConnection.getSelectedText(0)
                selectedText?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
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
        
        val ensName = selectedText.trim()
        
        // For highlighting/selection, always just replace text (don't navigate)
        // This is different from Enter key which should navigate in browser context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolvedValue = ensResolver.resolveENSName(ensName)
                if (resolvedValue != null) {
                    
                    // Always replace text (never open URLs) for highlighting/selection
                    // This matches iOS behavior: text records resolve to addresses in regular text fields
                    withContext(Dispatchers.Main) {
                        replaceSelectedText(resolvedValue)
                        saveENSName(ensName)
                        showENSResolutionFeedback(ensName, resolvedValue)
                        
                        // Clear the resolved text after a delay to allow new selections
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            lastResolvedText = ""
                        }, 2000)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showENSResolutionError(ensName)
                        // Clear the resolved text even on error
                        lastResolvedText = ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showENSResolutionError(ensName)
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
        }
    }
    
    private fun showENSResolutionFeedback(ensName: String, address: String) {
        // For now, just log the resolution - we can add visual feedback later
        // TODO: Add visual feedback in suggestion bar
    }
    
    private fun showENSResolutionError(ensName: String) {
        // For now, just log the error - we can add visual feedback later
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
        button.setTextColor(getTextColor())
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
    
    private fun showCryptoTickerMenu(anchorButton: Button) {
        // Add haptic feedback
        triggerHapticFeedback()
        
        // Create a container for the popup
        val containerView = LinearLayout(this)
        containerView.orientation = LinearLayout.VERTICAL
        containerView.setPadding(8, 8, 8, 8)
        containerView.setBackgroundColor(Color.parseColor("#2C2C2C"))
        
        // Create a horizontal scroll view for the crypto ticker options
        val scrollView = HorizontalScrollView(this)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        // Create the horizontal layout for buttons
        val popupView = LinearLayout(this)
        popupView.orientation = LinearLayout.HORIZONTAL
        popupView.setPadding(8, 8, 8, 8)
        
        // Crypto ticker options (like iOS)
        val cryptoTickers = listOf(
            ":btc", ":sol", ":doge", ":xrp", ":ltc", ":ada", ":dot",
            ":url", ":x", ":github", ":name", ":bio"
        )
        
        cryptoTickers.forEach { ticker ->
            val button = createCryptoTickerButton(ticker)
            button.setOnClickListener {
                inputConnection?.commitText(ticker, 1)
                currentText += ticker
                // Dismiss popup
                if (::btcPopup.isInitialized) {
                    btcPopup.dismiss()
                }
            }
            popupView.addView(button)
        }
        
        // Add the horizontal layout to scroll view
        scrollView.addView(popupView)
        containerView.addView(scrollView)
        
        // Create and show popup window
        btcPopup = PopupWindow(
            containerView,
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Position popup above the :btc button
        btcPopup.showAsDropDown(anchorButton, 0, -anchorButton.height - 20)
        
        // Auto-dismiss after 5 seconds (longer since it's scrollable)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (btcPopup.isShowing) {
                btcPopup.dismiss()
            }
        }, 5000)
    }
    
    private fun createCryptoTickerButton(text: String): Button {
        val button = Button(this)
        button.text = text
        button.setTextColor(getTextColor())
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.setPadding(12, 8, 12, 8)
        
        // Orange background like iOS :btc key
        val drawable = GradientDrawable()
        drawable.setColor(Color.parseColor("#FF9500"))
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
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
    }
    
    // Enhanced feedback methods
    private fun triggerHapticFeedback() {
        val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
        if (!hapticEnabled) return
        
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
        val soundEnabled = prefs.getBoolean("keypress_sound_enabled", false)
        if (!soundEnabled || toneGenerator == null) return
        
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
            // Use a darker version of the current theme's key color
            val pressedColor = if (isDarkMode) Color.parseColor("#5A5A5A") else Color.parseColor("#C0C0C0")
            drawable.setColor(pressedColor)
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
            drawable.setColor(getKeyColor()) // Use dynamic color based on theme
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
    
    private fun extractInputFromAddressBar(): String {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Get text before and after cursor without selecting anything
                val beforeText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val fullText = beforeText + afterText
                
                
                val trimmedText = fullText.trim()
                
                // Smart extraction: look for ENS patterns in the text
                // This handles cases like "hi there ses.eth:x" -> extracts "ses.eth:x"
                val ensPatterns = listOf(
                    // Multi-chain ENS with .eth (e.g., "ses.eth:x", "vitalik.eth:btc", "jesse.base.eth:btc")
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth:([a-zA-Z0-9]+)"),
                    // Multi-chain ENS shortcut (e.g., "ses:x", "vitalik:btc", "jesse.base:btc") 
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?):([a-zA-Z0-9]+)"),
                    // Standard ENS (e.g., "vitalik.eth", "ses.eth", "jesse.base.eth")
                    Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth")
                )
                
                // Find the first ENS pattern in the text
                for (pattern in ensPatterns) {
                    val match = pattern.find(trimmedText)
                    if (match != null) {
                        val ensName = match.value
                        return ensName
                    }
                }
                
                // If no ENS pattern found, return the last word (for backward compatibility)
                val words = trimmedText.split("\\s+".toRegex())
                val result = if (words.isNotEmpty()) words.last() else trimmedText
                return result
            }
        } catch (e: Exception) {
        }
        return currentText
    }
    
    private fun updateEnterButtonToLoading() {
        // Find the enter button and update its text to show loading
        // This is a simplified version - in a real implementation you'd need to track the button reference
        // For now, we'll just proceed with the resolution
    }
    
    private fun resolveENSForEnterKey(inputText: String) {
        
        // Check if this is a text record (like ses.eth:x) or a standard ENS name (like ses.eth)
        if (inputText.contains(":")) {
            // This is a text record - resolve directly and navigate to URL
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val resolvedValue = ensResolver.resolveENSName(inputText)
                    if (resolvedValue != null) {
                        // Clear the address bar and insert the resolved URL
                        clearAddressBarAndInsertURL(resolvedValue)
                        
                        // Trigger the enter key to navigate
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        }, 200) // Small delay to ensure URL is inserted
                    } else {
                        // If no resolution, proceed with normal enter
                        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    }
                } catch (e: Exception) {
                    // If error, proceed with normal enter
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                }
            }
        } else {
            // This is a standard ENS name - use browser action setting
            handleBrowserENSResolution(inputText)
        }
    }
    
    private fun clearAddressBarAndInsertURL(resolvedURL: String) {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                val beforeText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val totalLength = beforeText.length + afterText.length
                
                // Delete all text in the address bar (with safety limit)
                val maxDeletions = minOf(totalLength, 1000) // Safety limit
                inputConnection.deleteSurroundingText(maxDeletions, 0)
                
                // Insert the resolved URL
                inputConnection.commitText(resolvedURL, 1)
                currentText = resolvedURL
            }
        } catch (e: Exception) {
            // Fallback: just insert the URL
            inputConnection?.commitText(resolvedURL, 1)
            currentText = resolvedURL
        }
    }
}

