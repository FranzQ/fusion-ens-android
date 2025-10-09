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
        // Check for standard ENS format: name.eth
        if (text.endsWith(".eth") && text.length > 4) {
            return true
        }
        
        // Check for multi-chain format: name.eth:chain
        if (text.contains(".eth:") && text.length > 8) {
            return true
        }
        
        // Check for shortcut format: name:chain (will auto-insert .eth)
        if (text.contains(":") && !text.contains(".eth") && text.length > 3) {
            return true
        }
        
        return false
    }
    
    fun getENSNameFromText(text: String): String? {
        return when {
            text.endsWith(".eth") -> text
            text.contains(".eth:") -> text
            text.contains(":") && !text.contains(".eth") -> {
                // Convert shortcut format to full format
                val parts = text.split(":")
                if (parts.size == 2) {
                    "${parts[0]}.eth:${parts[1]}"
                } else null
            }
            else -> null
        }
    }
    
    fun getSupportedChains(): List<String> {
        return listOf(
            "btc", "eth", "sol", "doge", "xrp", "ltc", "ada", 
            "dot", "avax", "matic", "base", "arb", "op", "bsc"
        )
    }
}
