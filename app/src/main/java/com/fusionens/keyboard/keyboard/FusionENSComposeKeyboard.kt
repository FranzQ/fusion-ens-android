package com.fusionens.keyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionens.keyboard.ens.ENSResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Professional FlorisBoard-inspired keyboard using Jetpack Compose
 */
class FusionENSComposeKeyboard : InputMethodService() {
    
    private var inputConnection: InputConnection? = null
    private var currentText = ""
    private val ensResolver = ENSResolver()
    private var isShiftPressed = false
    private var isNumbersMode = false
    private var lastSpacePressTime = 0L
    private lateinit var prefs: SharedPreferences
    
    // Enhanced features
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("fusion_ens_keyboard", Context.MODE_PRIVATE)
    }
    
    // FlorisBoard-inspired theme
    private val keyboardTheme = ComposeKeyboardTheme()
    
    // Professional key layouts
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
        
        return ComposeView(this).apply {
            setContent {
                MaterialTheme(
                    colorScheme = keyboardTheme.colorScheme,
                    typography = keyboardTheme.typography
                ) {
                    ProfessionalKeyboard(
                        onKeyPress = ::onKeyPress,
                        onBackspace = ::onBackspace,
                        onEnter = ::onEnter,
                        onSpace = ::onSpace,
                        onShift = ::onShift,
                        onNumbersToggle = ::onNumbersToggle,
                        onKeyboardSwitch = ::switchToNextInputMethod,
                        isShiftPressed = isShiftPressed,
                        isNumbersMode = isNumbersMode,
                        currentLayout = if (isNumbersMode) numberLayout else qwertyLayout
                    )
                }
            }
        }
    }
    
    private fun initializeEnhancedFeatures() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    }
    
    @Composable
    private fun ProfessionalKeyboard(
        onKeyPress: (String) -> Unit,
        onBackspace: () -> Unit,
        onEnter: () -> Unit,
        onSpace: () -> Unit,
        onShift: () -> Unit,
        onNumbersToggle: () -> Unit,
        onKeyboardSwitch: () -> Unit,
        isShiftPressed: Boolean,
        isNumbersMode: Boolean,
        currentLayout: Array<Array<String>>
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(keyboardTheme.backgroundColor)
                .padding(4.dp)
        ) {
            // Word suggestions row (FlorisBoard style)
            WordSuggestionsRow(
                suggestions = listOf("the", "and", "you", "that", "was"),
                onSuggestionClick = onKeyPress
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Top row with ENS indicator
            TopRow(
                onKeyboardSwitch = onKeyboardSwitch
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Main keyboard rows
            KeyboardRows(
                layout = currentLayout,
                isTopRow = true,
                onKeyPress = onKeyPress,
                onBackspace = onBackspace,
                isShiftPressed = isShiftPressed
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            KeyboardRows(
                layout = currentLayout,
                isTopRow = false,
                onKeyPress = onKeyPress,
                onBackspace = null,
                isShiftPressed = isShiftPressed,
                onShift = onShift
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Bottom row
            BottomRow(
                onNumbersToggle = onNumbersToggle,
                onSpace = onSpace,
                onEnter = onEnter,
                onKeyboardSwitch = onKeyboardSwitch
            )
        }
    }
    
    @Composable
    private fun WordSuggestionsRow(
        suggestions: List<String>,
        onSuggestionClick: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestionClick("$suggestion ") },
                    label = { 
                        Text(
                            text = suggestion,
                            fontSize = 14.sp,
                            color = keyboardTheme.textColor
                        ) 
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(keyboardTheme.suggestionBackgroundColor)
                )
            }
        }
    }
    
    @Composable
    private fun TopRow(
        onKeyboardSwitch: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ENS indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔗",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ENS",
                    fontSize = 14.sp,
                    color = keyboardTheme.ensColor,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Globe button
            KeyboardKey(
                text = "🌐",
                onClick = onKeyboardSwitch,
                modifier = Modifier.size(40.dp),
                backgroundColor = keyboardTheme.functionKeyColor
            )
        }
    }
    
    @Composable
    private fun KeyboardRows(
        layout: Array<Array<String>>,
        isTopRow: Boolean,
        onKeyPress: (String) -> Unit,
        onBackspace: (() -> Unit)?,
        isShiftPressed: Boolean,
        onShift: (() -> Unit)? = null
    ) {
        val rowIndex = if (isTopRow) 0 else if (layout.size > 1) 1 else 0
        val keys = layout[rowIndex]
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Shift key for non-top rows
            if (!isTopRow && onShift != null) {
                KeyboardKey(
                    text = if (isShiftPressed) "⇧" else "⇧",
                    onClick = onShift,
                    modifier = Modifier.weight(1.2f),
                    backgroundColor = if (isShiftPressed) keyboardTheme.pressedKeyColor else keyboardTheme.functionKeyColor
                )
            }
            
            // Letter keys
            keys.forEach { key ->
                val displayText = if (isTopRow && !isNumbersMode) {
                    // Add superscript numbers for Q-P row
                    val index = qwertyLayout[0].indexOf(key.lowercase())
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
                        "${key.uppercase()}$superscript"
                    } else {
                        if (isShiftPressed) key.uppercase() else key
                    }
                } else {
                    if (isShiftPressed && key.length == 1 && !isNumbersMode) key.uppercase() else key
                }
                
                KeyboardKey(
                    text = displayText,
                    onClick = { onKeyPress(key) },
                    modifier = Modifier.weight(1f),
                    backgroundColor = keyboardTheme.keyColor
                )
            }
            
            // Backspace for top row
            if (isTopRow && onBackspace != null) {
                KeyboardKey(
                    text = "⌫",
                    onClick = onBackspace,
                    modifier = Modifier.weight(1.2f),
                    backgroundColor = keyboardTheme.functionKeyColor
                )
            }
        }
    }
    
    @Composable
    private fun BottomRow(
        onNumbersToggle: () -> Unit,
        onSpace: () -> Unit,
        onEnter: () -> Unit,
        onKeyboardSwitch: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Numbers toggle
            KeyboardKey(
                text = "?123",
                onClick = onNumbersToggle,
                modifier = Modifier.weight(1f),
                backgroundColor = keyboardTheme.functionKeyColor
            )
            
            // Emoji button
            KeyboardKey(
                text = "😊",
                onClick = { /* TODO: Emoji picker */ },
                modifier = Modifier.weight(1f),
                backgroundColor = keyboardTheme.functionKeyColor
            )
            
            // Globe button
            KeyboardKey(
                text = "🌐",
                onClick = onKeyboardSwitch,
                modifier = Modifier.weight(0.8f),
                backgroundColor = keyboardTheme.functionKeyColor
            )
            
            // Space bar (wider)
            KeyboardKey(
                text = "English",
                onClick = onSpace,
                modifier = Modifier.weight(4f),
                backgroundColor = keyboardTheme.spaceKeyColor
            )
            
            // Left arrow
            KeyboardKey(
                text = "◀",
                onClick = { 
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                },
                modifier = Modifier.weight(1f),
                backgroundColor = keyboardTheme.functionKeyColor
            )
            
            // Right arrow
            KeyboardKey(
                text = "▶",
                onClick = { 
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                },
                modifier = Modifier.weight(1f),
                backgroundColor = keyboardTheme.functionKeyColor
            )
            
            // Enter key
            KeyboardKey(
                text = "⏎",
                onClick = onEnter,
                modifier = Modifier.weight(1.2f),
                backgroundColor = keyboardTheme.enterKeyColor
            )
        }
    }
    
    @Composable
    private fun KeyboardKey(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        backgroundColor: Color = keyboardTheme.keyColor
    ) {
        var isPressed by remember { mutableStateOf(false) }
        
        Box(
            modifier = modifier
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isPressed) keyboardTheme.pressedKeyColor else backgroundColor
                )
                .shadow(
                    elevation = if (isPressed) 2.dp else 4.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onClick()
                    triggerHapticFeedback()
                    playKeySound()
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isPressed = true
                        },
                        onDragEnd = { 
                            isPressed = false
                        }
                    ) { _, _ -> }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = keyboardTheme.textColor,
                textAlign = TextAlign.Center
            )
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
    
    private fun onKeyPress(key: String) {
        // Check if this is a suggestion selection (contains a space at the end)
        if (key.endsWith(" ")) {
            // This is a suggestion selection, replace current text
            replaceCurrentTextWithSuggestion(key.trim())
        } else {
            // Regular key press, just append
            inputConnection?.commitText(key, 1)
            currentText += key
        }
        
        // Check if auto-resolve is enabled
        if (prefs.getBoolean("auto_resolve_enabled", false)) {
            checkForENSResolution(currentText)
        }
    }
    
    private fun onBackspace() {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }
    
    private fun onEnter() {
        handleEnterKeyPress()
    }
    
    private fun onSpace() {
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
    
    private fun onShift() {
        isShiftPressed = !isShiftPressed
        // Recreate the keyboard view
        setInputView(onCreateInputView())
    }
    
    private fun onNumbersToggle() {
        isNumbersMode = !isNumbersMode
        // Recreate the keyboard view
        setInputView(onCreateInputView())
    }
    
    private fun checkForENSResolution(text: String) {
        if (ensResolver.isValidENS(text)) {
            CoroutineScope(Dispatchers.IO).launch {
                val address = ensResolver.resolveENSName(text)
                if (address != null) {
                    println("ENS Resolved: $text -> $address")
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
            val conn = inputConnection
            if (conn != null) {
                // Get the current text before cursor
                val textBeforeCursor = conn.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val textAfterCursor = conn.getTextAfterCursor(1000, 0)?.toString() ?: ""
                
                // Find the partial text that matches the current input
                val partialText = findPartialTextInInput(textBeforeCursor)
                if (partialText != null) {
                    // Find the position of the partial text in the input
                    val partialStartIndex = textBeforeCursor.lastIndexOf(partialText)
                    if (partialStartIndex != -1) {
                        // Calculate how many characters to delete from the cursor position
                        val deleteCount = textBeforeCursor.length - partialStartIndex
                        
                        // Delete the partial text using deleteSurroundingText
                        conn.deleteSurroundingText(deleteCount, 0)
                        
                        // Insert the new suggestion
                        conn.commitText(suggestion, 1)
                        
                        // Update currentText to reflect the change
                        currentText = textBeforeCursor.substring(0, partialStartIndex) + suggestion + textAfterCursor
                    } else {
                        // Fallback: replace all text before cursor
                        conn.deleteSurroundingText(textBeforeCursor.length, 0)
                        conn.commitText(suggestion, 1)
                        currentText = suggestion + textAfterCursor
                    }
                } else {
                    // Fallback: replace all text before cursor
                    conn.deleteSurroundingText(textBeforeCursor.length, 0)
                    conn.commitText(suggestion, 1)
                    currentText = suggestion + textAfterCursor
                }
            }
        } catch (e: Exception) {
            println("Error replacing text with suggestion: ${e.message}")
        }
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
    
    private fun replaceCurrentTextInInputField(newText: String) {
        try {
            val conn = inputConnection
            if (conn != null) {
                // Get the current text before cursor
                val textBeforeCursor = conn.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val textAfterCursor = conn.getTextAfterCursor(1000, 0)?.toString() ?: ""
                
                // Find the ENS name in the text before cursor
                val ensName = findENSNameInText(textBeforeCursor)
                if (ensName != null) {
                    // Find the position of the ENS name in the text
                    val ensStartIndex = textBeforeCursor.lastIndexOf(ensName)
                    if (ensStartIndex != -1) {
                        // Calculate how many characters to delete from the cursor position
                        val deleteCount = textBeforeCursor.length - ensStartIndex
                        
                        // Delete the ENS name using deleteSurroundingText
                        conn.deleteSurroundingText(deleteCount, 0)
                        
                        // Insert the new text
                        conn.commitText(newText, 1)
                        
                        // Update currentText to reflect the change
                        currentText = textBeforeCursor.substring(0, ensStartIndex) + newText + textAfterCursor
                    } else {
                        // Fallback: replace all text before cursor
                        conn.deleteSurroundingText(textBeforeCursor.length, 0)
                        conn.commitText(newText, 1)
                        currentText = newText + textAfterCursor
                    }
                } else {
                    // Fallback: replace all text before cursor
                    conn.deleteSurroundingText(textBeforeCursor.length, 0)
                    conn.commitText(newText, 1)
                    currentText = newText + textAfterCursor
                }
            }
        } catch (e: Exception) {
            println("Error replacing current text: ${e.message}")
        }
    }
    
    private fun findENSNameInText(text: String): String? {
        // Look for ENS names in the text (ending with .eth)
        val ensPattern = Regex("\\b\\w+\\.eth\\b")
        val matches = ensPattern.findAll(text)
        return matches.lastOrNull()?.value
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
    
    private fun isInBrowserContext(): Boolean {
        try {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Check the package name to see if we're in a browser
                val packageName = packageName.lowercase()
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
                if (browserPackages.any { packageName.contains(it) }) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Error checking browser context
        }

        return false
    }
    
    private fun handleEnterKeyPress() {
        // Check if we're in a browser context
        if (isInBrowserContext()) {
            // In browser context - try to resolve ENS before triggering enter
            val inputText = extractInputFromAddressBar()
            if (inputText.isNotEmpty() && ensResolver.isValidENS(inputText)) {
                // Resolve ENS and then trigger enter
                resolveENSForEnterKey(inputText)
            } else {
                // Not an ENS name, proceed with normal enter
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            }
        } else {
            // Not in browser context - proceed with normal enter
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }
    }
    
    private fun extractInputFromAddressBar(): String {
        try {
            val conn = currentInputConnection
            if (conn != null) {
                val beforeText = conn.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = conn.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val fullText = beforeText + afterText
                
                // Extract the last word/input (similar to iOS implementation)
                val words = fullText.trim().split("\\s+".toRegex())
                return if (words.isNotEmpty()) words.last() else ""
            }
        } catch (e: Exception) {
            // Fallback to current text
        }
        return currentText
    }
    
    private fun resolveENSForEnterKey(inputText: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resolvedValue = ensResolver.resolveENSName(inputText)
                if (resolvedValue != null) {
                    // Clear the address bar and insert the resolved URL
                    clearAddressBarAndInsertURL(resolvedValue)
                    
                    // Trigger the enter key to navigate
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    }, 200) // Small delay to ensure URL is inserted
                } else {
                    // If no resolution, proceed with normal enter
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                }
            } catch (e: Exception) {
                // If error, proceed with normal enter
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            }
        }
    }
    
    private fun clearAddressBarAndInsertURL(resolvedURL: String) {
        try {
            val conn = currentInputConnection
            if (conn != null) {
                val beforeText = conn.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                val afterText = conn.getTextAfterCursor(1000, 0)?.toString() ?: ""
                val totalLength = beforeText.length + afterText.length
                
                // Delete all text in the address bar (with safety limit)
                val maxDeletions = minOf(totalLength, 1000) // Safety limit
                conn.deleteSurroundingText(maxDeletions, 0)
                
                // Insert the resolved URL
                conn.commitText(resolvedURL, 1)
                currentText = resolvedURL
            }
        } catch (e: Exception) {
            // Fallback: just insert the URL
            currentInputConnection?.commitText(resolvedURL, 1)
            currentText = resolvedURL
        }
    }
}

/**
 * Professional FlorisBoard-inspired theme for Compose
 */
class ComposeKeyboardTheme {
    val backgroundColor = Color(0xFF1E1E1E) // Dark background
    val keyColor = Color(0xFF2D2D2D) // Key background
    val functionKeyColor = Color(0xFF3A3A3A) // Function keys
    val spaceKeyColor = Color(0xFF2D2D2D) // Space bar
    val enterKeyColor = Color(0xFF4CAF50) // Enter key (green)
    val pressedKeyColor = Color(0xFF4A4A4A) // Pressed state
    val suggestionBackgroundColor = Color(0xFF3A3A3A) // Suggestions
    val textColor = Color(0xFFFFFFFF) // Text color
    val ensColor = Color(0xFF4CAF50) // ENS green
    
    val colorScheme = darkColorScheme(
        background = backgroundColor,
        surface = keyColor,
        primary = ensColor,
        onBackground = textColor,
        onSurface = textColor
    )
    
    val typography = Typography(
        bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    )
}
