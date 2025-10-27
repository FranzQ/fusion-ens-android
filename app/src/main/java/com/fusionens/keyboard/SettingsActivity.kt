package com.fusionens.keyboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.Preference

class SettingsActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the theme for Material Design
        setTheme(R.style.Theme_FusionENS_Settings)
        
        // Set up action bar with Google-style design
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide title initially
        supportActionBar?.setBackgroundDrawable(null)
        
        // Set the custom layout
        setContentView(R.layout.activity_settings)
        
        // Get reference to title TextView
        titleTextView = findViewById(R.id.title_text)
        
        // Load the settings fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    fun updateTitleVisibility(isScrolled: Boolean) {
        if (isScrolled) {
            // Show title in action bar when scrolled
            supportActionBar?.setDisplayShowTitleEnabled(true)
            supportActionBar?.title = "Fusion ENS Settings"
            titleTextView.visibility = View.GONE
        } else {
            // Hide action bar title and show custom title when at top
            supportActionBar?.setDisplayShowTitleEnabled(false)
            titleTextView.visibility = View.VISIBLE
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        
        private var parentActivity: SettingsActivity? = null
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // Set up preference change listeners
            setupPreferenceListeners()
        }
        
        override fun onAttach(context: android.content.Context) {
            super.onAttach(context)
            parentActivity = context as SettingsActivity
        }
        
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            
            // Add bottom padding to prevent last item cutoff
            val listView = view.findViewById<android.widget.ListView>(android.R.id.list)
            val bottomPadding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 2 // 96dp equivalent
            listView?.setPadding(
                listView.paddingLeft,
                listView.paddingTop,
                listView.paddingRight,
                bottomPadding
            )
            
            // Also add clipToPadding to ensure scrolling works properly
            listView?.clipToPadding = false
            
            // Set up scroll listener for Google-style title behavior
            listView?.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: android.widget.AbsListView?, scrollState: Int) {}
                
                override fun onScroll(view: android.widget.AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                    val isScrolled = firstVisibleItem > 0
                    parentActivity?.updateTitleVisibility(isScrolled)
                }
            })
        }
        
        private fun setupPreferenceListeners() {
            // Handle browser action preference changes
            val browserActionPreference = findPreference<ListPreference>("default_browser_action")
            browserActionPreference?.setOnPreferenceChangeListener { _, newValue ->
                // You can add any custom logic here if needed
                true
            }
            
            // Set summary provider for browser action to show current value
            browserActionPreference?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            
            // Handle clickable preferences
            val privacyPolicyPreference = findPreference<Preference>("privacy_policy")
            privacyPolicyPreference?.setOnPreferenceClickListener {
                // Open Privacy Policy in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fusionens.com/privacy"))
                startActivity(intent)
                true
            }
            
            val termsOfServicePreference = findPreference<Preference>("terms_of_service")
            termsOfServicePreference?.setOnPreferenceClickListener {
                // Open Terms of Service in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fusionens.com/terms"))
                startActivity(intent)
                true
            }
        }
    }
}
