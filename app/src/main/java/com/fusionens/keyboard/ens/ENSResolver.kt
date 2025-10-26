package com.fusionens.keyboard.ens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class ENSResponse(
    val success: Boolean?,
    val data: ENSData?,
    val address: String?,
    val error: String?
)

data class ENSData(
    val address: String?
)

interface ENSApiService {
    @GET("resolve/{domainName}")
    suspend fun resolveENS(
        @Path("domainName") domainName: String,
        @Query("network") network: String = "mainnet",
        @Query("source") source: String = "android"
    ): ENSResponse
}

class ENSResolver {
    private val apiService: ENSApiService
    
    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.fusionens.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ENSApiService::class.java)
    }
    
    suspend fun resolveENSName(name: String): String? = withContext(Dispatchers.IO) {
        try {
            // Check if this is a text record (like onshow.eth:x, onshow.eth:url)
            if (isTextRecord(name)) {
                // For text records, use the same API as regular ENS resolution
                // The Fusion API handles text records directly
                val response = apiService.resolveENS(name)
                
                // Handle Fusion API response format for text records
                if (response.success == true && response.data?.address != null) {
                    val textValue = response.data?.address
                    if (textValue != null) {
                        val parts = name.split(":")
                        if (parts.size == 2) {
                            val recordType = parts[1]
                            // Only convert certain text records to URLs, others return raw text
                            if (shouldConvertToURL(recordType)) {
                                val url = convertTextRecordToURL(recordType, textValue)
                                return@withContext url
                            } else {
                                // Return raw text for name, bio, description, etc.
                                return@withContext textValue
                            }
                        }
                    }
                }
                
                // Fallback to direct address field (for backward compatibility)
                if (response.address != null) {
                    val textValue = response.address
                    if (textValue != null) {
                        val parts = name.split(":")
                        if (parts.size == 2) {
                            val recordType = parts[1]
                            // Only convert certain text records to URLs, others return raw text
                            if (shouldConvertToURL(recordType)) {
                                val url = convertTextRecordToURL(recordType, textValue)
                                return@withContext url
                            } else {
                                // Return raw text for name, bio, description, etc.
                                return@withContext textValue
                            }
                        }
                    }
                }
                
                return@withContext null
            }
            
            // Check if this is a multi-chain ENS name (like onshow.eth:btc)
            val multiChainName = if (name.contains(":") && !name.contains(".eth:")) {
                // Convert shortcut format (onshow:btc) to full format (onshow.eth:btc)
                val parts = name.split(":")
                if (parts.size == 2 && isValidChain(parts[1])) {
                    "${parts[0]}.eth:${parts[1]}"
                } else {
                    name
                }
            } else {
                name
            }
            
            val response = apiService.resolveENS(multiChainName)
            
            // Handle Fusion API response format (like iOS)
            if (response.success == true && response.data?.address != null) {
                return@withContext response.data?.address
            }
            
            // Fallback to direct address field (for backward compatibility)
            if (response.address != null) {
                return@withContext response.address
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun resolveENSTextRecord(name: String, recordType: String = "name"): String? = withContext(Dispatchers.IO) {
        try {
            val fullName = if (name.endsWith(".$recordType")) name else "$name.$recordType"
            val response = apiService.resolveENS(fullName)
            response.address
        } catch (e: Exception) {
            null
        }
    }
    
    fun isValidENS(text: String): Boolean {
        // Remove leading/trailing whitespace
        val trimmedText = text.trim()
        
        // Check for standard ENS format: name.eth
        if (trimmedText.endsWith(".eth") && trimmedText.length > 4) {
            val namePart = trimmedText.substring(0, trimmedText.length - 4)
            // Validate ENS name part (alphanumeric, hyphens, no spaces, no special chars)
            return isValidENSName(namePart)
        }
        
        // Check for multi-chain format: name.eth:chain
        if (trimmedText.contains(".eth:") && trimmedText.length > 8) {
            val parts = trimmedText.split(".eth:")
            if (parts.size == 2) {
                val namePart = parts[0]
                val chainPart = parts[1]
                return isValidENSName(namePart) && (isValidChain(chainPart) || isValidTextRecord(chainPart))
            }
        }
        
        // Check for shortcut format: name:chain (will auto-insert .eth)
        if (trimmedText.contains(":") && !trimmedText.contains(".eth") && trimmedText.length > 3) {
            val parts = trimmedText.split(":")
            if (parts.size == 2) {
                val namePart = parts[0]
                val chainPart = parts[1]
                return isValidENSName(namePart) && (isValidChain(chainPart) || isValidTextRecord(chainPart))
            }
        }
        
        return false
    }
    
    private fun isValidTextRecord(recordType: String): Boolean {
        return getSupportedTextRecords().contains(recordType.lowercase())
    }
    
    private fun isValidENSName(name: String): Boolean {
        // ENS names must be 1-63 characters, can contain subdomains and Unicode
        if (name.isEmpty() || name.length > 63) return false
        
        // Split by dots to handle subdomains
        val parts = name.split(".")
        
        // Each part must be valid
        for (part in parts) {
            if (part.isEmpty()) return false
            
            // Check for valid characters: alphanumeric, hyphens, Unicode (but not spaces)
            // Allow Unicode characters but exclude spaces and other problematic characters
            val ensNameRegex = Regex("^[a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?$")
            if (!ensNameRegex.matches(part)) return false
        }
        
        return true
    }
    
    private fun isValidChain(chain: String): Boolean {
        // Check if chain is in supported chains list
        return getSupportedChains().contains(chain.lowercase())
    }
    
    fun getENSNameFromText(text: String): String? {
        val trimmedText = text.trim()
        
        // Try to find ENS name in the text using regex patterns
        // Order matters: more specific patterns first
        val ensPatterns = listOf(
            // Multi-chain: name.eth:chain (supports subdomains and Unicode) - CHECK FIRST
            Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth:([a-zA-Z0-9]+)"),
            // Shortcut: name:chain (supports subdomains and Unicode) - CHECK SECOND  
            Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?):([a-zA-Z0-9]+)"),
            // Standard ENS: name.eth (supports subdomains and Unicode) - CHECK LAST
            Regex("([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth")
        )
        
        for ((index, pattern) in ensPatterns.withIndex()) {
            val match = pattern.find(trimmedText)
            if (match != null) {
                val fullMatch = match.value
                val result = when (index) {
                    0 -> {
                        // Multi-chain: name.eth:chain - return as is
                        fullMatch
                    }
                    1 -> {
                        // Shortcut: name:chain - convert to full format
                        val parts = fullMatch.split(":")
                        if (parts.size == 2 && (isValidChain(parts[1]) || isValidTextRecord(parts[1]))) {
                            "${parts[0]}.eth:${parts[1]}"
                        } else null
                    }
                    2 -> {
                        // Standard ENS: name.eth - return as is
                        fullMatch
                    }
                    else -> null
                }
                return result
            }
        }
        
        return null
    }
    
    fun getSupportedChains(): List<String> {
        return listOf(
            "btc", "eth", "sol", "doge", "xrp", "ltc", "ada", 
            "dot", "avax", "matic", "base", "arb", "op", "bsc"
        )
    }
    
    fun getSupportedTextRecords(): List<String> {
        return listOf("x", "url", "github", "name", "bio", "description")
    }
    
    fun isTextRecord(ensName: String): Boolean {
        val trimmedName = ensName.trim()
        return if (trimmedName.contains(":")) {
            val parts = trimmedName.split(":")
            if (parts.size == 2) {
                val recordType = parts[1]
                getSupportedTextRecords().contains(recordType.lowercase())
            } else {
                false
            }
        } else {
            false
        }
    }
    
    private fun shouldConvertToURL(recordType: String): Boolean {
        return when (recordType.lowercase()) {
            "x", "url", "github" -> true
            "name", "bio", "description" -> false
            else -> false
        }
    }
    
    fun convertTextRecordToURL(recordType: String, value: String): String {
        return when (recordType.lowercase()) {
            "x" -> {
                // Twitter/X handle
                if (value.startsWith("@")) {
                    "https://x.com/${value.substring(1)}"
                } else {
                    "https://x.com/$value"
                }
            }
            "url" -> {
                // Direct URL
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    value
                } else {
                    "https://$value"
                }
            }
            "github" -> {
                // GitHub profile
                if (value.startsWith("@")) {
                    "https://github.com/${value.substring(1)}"
                } else {
                    "https://github.com/$value"
                }
            }
            "name" -> {
                // Name - search
                val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
                "https://google.com/search?q=$encodedValue"
            }
            "bio", "description" -> {
                // Bio - search
                val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
                "https://google.com/search?q=$encodedValue"
            }
            else -> {
                // Default to search
                val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
                "https://google.com/search?q=$encodedValue"
            }
        }
    }
}
