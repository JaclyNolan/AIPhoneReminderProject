package com.example.myapplication.testing

import android.content.Context
import com.example.myapplication.LLMClient

/**
 * Interface for LLM clients to enable mocking during tests
 * 
 * Allows switching between real API calls and mock responses for testing.
 * Note: Implementations should be blocking. Call from background thread/coroutine.
 */
interface ILLMClient {
    fun callOpenAI(
        context: Context,
        messages: List<LLMClient.Message>,
        model: String = "mistral-medium-latest",
        temperature: Double = 0.9,
        topP: Double = 0.9
    ): LLMClient.LLMResponse?
}

/**
 * Real LLM client adapter (delegates to actual LLMClient)
 */
object RealLLMClient : ILLMClient {
    override fun callOpenAI(
        context: Context,
        messages: List<LLMClient.Message>,
        model: String,
        temperature: Double,
        topP: Double
    ): LLMClient.LLMResponse? {
        return LLMClient.callOpenAI(context, messages, model, temperature, topP)
    }
}

/**
 * Client factory to switch between real and mock
 */
object LLMClientFactory {
    private var useMock = false
    
    fun setMockMode(enabled: Boolean) {
        useMock = enabled
    }
    
    fun isMockMode(): Boolean = useMock
    
    fun getClient(): ILLMClient {
        return if (useMock) MockLLMClient else RealLLMClient
    }
}

