package com.jiqu.lite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiqu.lite.data.ParsedMedia
import com.jiqu.lite.data.parseMediaUrl
import kotlinx.coroutines.launch

data class ParseUiState(
    val sourceUrl: String = "",
    val parsedSourceUrl: String? = null,
    val parsedMedia: ParsedMedia? = null,
    val parsing: Boolean = false,
    val errorMessage: String? = null,
    val successRevision: Long = 0,
    val historyPending: Boolean = false
)

class ParseViewModel : ViewModel() {
    var uiState by mutableStateOf(ParseUiState())
        private set

    fun updateSourceUrl(sourceUrl: String) {
        if (uiState.parsing) return
        uiState = uiState.copy(
            sourceUrl = sourceUrl,
            parsedSourceUrl = null,
            parsedMedia = null,
            errorMessage = null
        )
    }

    fun parse(sourceUrl: String) {
        if (uiState.parsing) return
        uiState = uiState.copy(
            sourceUrl = sourceUrl,
            parsedSourceUrl = null,
            parsedMedia = null,
            parsing = true,
            errorMessage = null,
            historyPending = false
        )
        viewModelScope.launch {
            parseMediaUrl(sourceUrl).fold(
                onSuccess = { media ->
                    uiState = uiState.copy(
                        parsedSourceUrl = sourceUrl,
                        parsedMedia = media,
                        parsing = false,
                        successRevision = uiState.successRevision + 1,
                        historyPending = true
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        parsing = false,
                        errorMessage = error.message ?: "解析失败，请稍后重试"
                    )
                }
            )
        }
    }

    fun markHistoryRecorded(successRevision: Long) {
        if (uiState.successRevision == successRevision && uiState.historyPending) {
            uiState = uiState.copy(historyPending = false)
        }
    }
}
