package com.example.myapplication.testing.mockcontext

import android.content.Context
import android.util.Log
import com.example.myapplication.CharacterProfiles
import com.example.myapplication.context.UserPrefsContextProvider

/**
 * Mock UserPrefsContextProvider for testing
 */
object MockUserPrefsContextProvider {
    private const val TAG = "MockUserPrefsContextProvider"
    
    private var mockProfile: CharacterProfiles.CharacterProfile? = null
    private var mockCharacterId: String? = null
    
    /**
     * Set mock character profile
     */
    fun setMockProfile(profile: CharacterProfiles.CharacterProfile) {
        mockProfile = profile
        Log.d(TAG, "Set mock character profile: ${profile.name}")
    }
    
    /**
     * Set mock character ID (will use CharacterProfiles to get profile)
     */
    fun setMockCharacterId(characterId: String) {
        mockCharacterId = characterId
        Log.d(TAG, "Set mock character ID: $characterId")
    }
    
    /**
     * Get character profile (mock or real)
     */
    fun getCharacterProfile(context: Context): CharacterProfiles.CharacterProfile {
        return mockProfile ?: (mockCharacterId?.let { CharacterProfiles.getProfile(it) } 
            ?: CharacterProfiles.getProfile("ralsei"))
    }
    
    /**
     * Get current mock data for display
     */
    fun getCurrentMockData(): MockPrefsData {
        return MockPrefsData(
            characterId = mockCharacterId ?: "ralsei",
            profile = mockProfile ?: CharacterProfiles.getProfile(mockCharacterId ?: "ralsei")
        )
    }
    
    /**
     * Reset to default state
     */
    fun reset() {
        mockProfile = null
        mockCharacterId = null
    }
    
    data class MockPrefsData(
        val characterId: String,
        val profile: CharacterProfiles.CharacterProfile
    )
}

