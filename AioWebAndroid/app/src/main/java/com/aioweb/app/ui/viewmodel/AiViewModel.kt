package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.ChatRequest
import com.aioweb.app.data.api.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiMessage(val fromUser: Boolean, val text: String)

data class AiState(
    val messages: List<AiMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val sessionId: String? = null,

    val imagePrompt: String = "",
    val imageLoading: Boolean = false,
    val imageError: String? = null,
    val generatedImageBase64: String? = null,
)

class AiViewModel(private val sl: ServiceLocator) : ViewModel() {
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()

    fun sendChat(text: String) {
        _state.update {
            it.copy(messages = it.messages + AiMessage(true, text), loading = true, error = null)
        }
        viewModelScope.launch {
            try {
                val provider = sl.settings.aiProvider.first()
                val model = sl.settings.aiModel.first()
                val resp = sl.backend().chat(
                    ChatRequest(
                        message = text,
                        sessionId = _state.value.sessionId,
                        provider = provider,
                        model = model,
                    )
                )
                _state.update {
                    it.copy(
                        messages = it.messages + AiMessage(false, resp.response),
                        loading = false,
                        sessionId = resp.sessionId,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Chat failed: ${e.message}") }
            }
        }
    }

    fun setImagePrompt(s: String) {
        _state.update { it.copy(imagePrompt = s) }
    }

    fun generateImage() {
        val prompt = _state.value.imagePrompt
        if (prompt.isBlank()) return
        _state.update { it.copy(imageLoading = true, imageError = null, generatedImageBase64 = null) }
        viewModelScope.launch {
            try {
                val resp = sl.backend().image(ImageRequest(prompt = prompt))
                val first = resp.images.firstOrNull()
                _state.update {
                    it.copy(
                        imageLoading = false,
                        generatedImageBase64 = first,
                        imageError = if (first == null) "No image returned" else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(imageLoading = false, imageError = "Image gen failed: ${e.message}") }
            }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiViewModel(ServiceLocator.get(context)) as T
            }
        }
    }
}
