package com.fusionens.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*

class SettingsActivity : Activity() {
    
    private lateinit var prefs: android.content.SharedPreferences
    
    // UI Components
    private lateinit var hapticToggle: Switch
    private lateinit var soundToggle: Switch
    private lateinit var soundVolumeSeekBar: SeekBar
    private lateinit var browserActionSpinner: Spinner
    private lateinit var soundVolumeText: TextView
    private lateinit var autoResolveToggle: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences("fusion_ens_keyboard", Context.MODE_PRIVATE)
        
        // Initialize UI components
        initializeViews()
        loadSettings()
        setupListeners()
    }
    
    private fun initializeViews() {
        hapticToggle = findViewById(R.id.hapticToggle)
        soundToggle = findViewById(R.id.soundToggle)
        soundVolumeSeekBar = findViewById(R.id.soundVolumeSeekBar)
        browserActionSpinner = findViewById(R.id.browserActionSpinner)
        soundVolumeText = findViewById(R.id.soundVolumeText)
        autoResolveToggle = findViewById(R.id.autoResolveToggle)
        
        // Setup browser action spinner
        val browserActions = arrayOf(
            "Etherscan",
            "Website", 
            "GitHub",
            "X (Twitter)"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, browserActions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        browserActionSpinner.adapter = adapter
    }
    
    private fun loadSettings() {
        // Load haptic feedback setting
        hapticToggle.isChecked = prefs.getBoolean("haptic_feedback_enabled", true)
        
        // Load sound setting
        soundToggle.isChecked = prefs.getBoolean("keypress_sound_enabled", false)
        
        // Load sound volume (0-100)
        val volume = prefs.getInt("sound_volume", 50)
        soundVolumeSeekBar.progress = volume
        soundVolumeText.text = "Volume: $volume%"
        
        // Load browser action setting
        val browserAction = prefs.getString("default_browser_action", "etherscan") ?: "etherscan"
        val actionIndex = when (browserAction) {
            "etherscan" -> 0
            "url" -> 1
            "github" -> 2
            "x" -> 3
            else -> 0
        }
        browserActionSpinner.setSelection(actionIndex)
        
        // Load auto-resolve setting (disabled by default)
        autoResolveToggle.isChecked = prefs.getBoolean("auto_resolve_enabled", false)
        
        // Update sound volume visibility
        updateSoundVolumeVisibility()
    }
    
    private fun setupListeners() {
        hapticToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("haptic_feedback_enabled", isChecked).apply()
        }
        
        soundToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keypress_sound_enabled", isChecked).apply()
            updateSoundVolumeVisibility()
        }
        
        soundVolumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    soundVolumeText.text = "Volume: $progress%"
                    prefs.edit().putInt("sound_volume", progress).apply()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        autoResolveToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_resolve_enabled", isChecked).apply()
        }
        
        browserActionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val action = when (position) {
                    0 -> "etherscan"
                    1 -> "url"
                    2 -> "github"
                    3 -> "x"
                    else -> "etherscan"
                }
                prefs.edit().putString("default_browser_action", action).apply()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun updateSoundVolumeVisibility() {
        val isSoundEnabled = soundToggle.isChecked
        soundVolumeSeekBar.isEnabled = isSoundEnabled
        soundVolumeText.alpha = if (isSoundEnabled) 1.0f else 0.5f
    }
}
