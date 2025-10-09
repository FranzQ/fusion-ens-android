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
            println("ENSResolver: Attempting to resolve ENS name: $name")
            val response = apiService.resolveENS(name)
            println("ENSResolver: API response for $name: $response")
            
            // Handle Fusion API response format (like iOS)
            if (response.success == true && response.data?.address != null) {
                println("ENSResolver: Found address in Fusion API format: ${response.data?.address}")
                return@withContext response.data?.address
            }
            
            // Fallback to direct address field (for backward compatibility)
            if (response.address != null) {
                println("ENSResolver: Found address in direct format: ${response.address}")
                return@withContext response.address
            }
            
            println("ENSResolver: No address found in response")
            null
        } catch (e: Exception) {
            println("ENSResolver: Error resolving $name: ${e.message}")
            e.printStackTrace()
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
                return isValidENSName(namePart) && isValidChain(chainPart)
            }
        }
        
        // Check for shortcut format: name:chain (will auto-insert .eth)
        if (trimmedText.contains(":") && !trimmedText.contains(".eth") && trimmedText.length > 3) {
            val parts = trimmedText.split(":")
            if (parts.size == 2) {
                val namePart = parts[0]
                val chainPart = parts[1]
                return isValidENSName(namePart) && isValidChain(chainPart)
            }
        }
        
        return false
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
        val ensPatterns = listOf(
            // Standard ENS: name.eth (supports subdomains and Unicode)
            Regex("\\b([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth\\b"),
            // Multi-chain: name.eth:chain (supports subdomains and Unicode)
            Regex("\\b([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?)\\.eth:([a-zA-Z0-9]+)\\b"),
            // Shortcut: name:chain (supports subdomains and Unicode)
            Regex("\\b([a-zA-Z0-9\\p{L}\\p{N}\\p{M}]([a-zA-Z0-9\\p{L}\\p{N}\\p{M}-.]*[a-zA-Z0-9\\p{L}\\p{N}\\p{M}])?):([a-zA-Z0-9]+)\\b")
        )
        
        for (pattern in ensPatterns) {
            val match = pattern.find(trimmedText)
            if (match != null) {
                val fullMatch = match.value
                return when {
                    fullMatch.endsWith(".eth") -> fullMatch
                    fullMatch.contains(".eth:") -> fullMatch
                    fullMatch.contains(":") && !fullMatch.contains(".eth") -> {
                        // Convert shortcut format to full format
                        val parts = fullMatch.split(":")
                        if (parts.size == 2 && isValidChain(parts[1])) {
                            "${parts[0]}.eth:${parts[1]}"
                        } else null
                    }
                    else -> null
                }
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
}
