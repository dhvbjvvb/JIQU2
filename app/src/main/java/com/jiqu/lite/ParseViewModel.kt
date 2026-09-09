package com.jiqu.lite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiqu.lite.data.ParsedMedia
import com.jiqu.lite.data.ParsePhase
import com.jiqu.lite.data.parseMediaUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParseUiState(
    val sourceUrl: String = "",
    val parsedSourceUrl: String? = null,
    val parsedMedia: ParsedMedia? = null,
    val parsing: Boolean = false,
    val parsePhase: ParsePhase? = null,
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
            parsePhase = null,
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
            parsePhase = ParsePhase.CONNECTING,
            errorMessage = null,
            historyPending = false
        )
        viewModelScope.launch {
            parseMediaUrl(sourceUrl) { phase ->
                withContext(Dispatchers.Main.immediate) {
                    uiState = uiState.copy(parsePhase = phase)
                }
            }.fold(
                onSuccess = { media ->
                    uiState = uiState.copy(
                        parsedSourceUrl = sourceUrl,
                        parsedMedia = media,
                        parsing = false,
                        parsePhase = null,
                        successRevision = uiState.successRevision + 1,
                        historyPending = true
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        parsing = false,
                        parsePhase = null,
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
