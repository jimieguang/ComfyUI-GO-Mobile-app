package com.example.myapplication.presentation.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LoadedState {
    enum class Source { STORAGE, HISTORY }

    var source: Source? by mutableStateOf(null)
        private set

    var storageId: String? by mutableStateOf(null)
        private set

    var historyId: String? by mutableStateOf(null)
        private set

    fun markStorage(id: String) {
        source = Source.STORAGE
        storageId = id
        historyId = null
    }

    fun markHistory(id: String) {
        source = Source.HISTORY
        historyId = id
        storageId = null
    }

    fun clear() {
        source = null
        storageId = null
        historyId = null
    }
}


