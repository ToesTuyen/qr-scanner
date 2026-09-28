package com.ivistatect.qrscanner.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivistatect.qrscanner.data.HistoryEntity
import com.ivistatect.qrscanner.data.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository) : ViewModel() {

    val scanned: StateFlow<List<HistoryEntity>> = repo.scanned().asState()
    val created: StateFlow<List<HistoryEntity>> = repo.created().asState()
    val favorites: StateFlow<List<HistoryEntity>> = repo.favorites().asState()

    fun delete(id: Long) = viewModelScope.launch { repo.deleteByIds(listOf(id)) }

    fun deleteAll(ids: List<Long>) = viewModelScope.launch { repo.deleteByIds(ids) }

    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.asState(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
