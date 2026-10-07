package com.smartsilentzone.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsilentzone.data.db.HistoryDao
import com.smartsilentzone.data.db.HistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao
) : ViewModel() {

    val historyEntries: StateFlow<List<HistoryEntity>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun seedDemoHistory() {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault())
            val now = System.currentTimeMillis()

            val demoList = listOf(
                HistoryEntity(
                    timestamp = now,
                    formattedDateTime = sdf.format(Date(now)),
                    zoneName = "Central Library",
                    eventType = "30s Sustained DWELL Confirmed",
                    previousMode = "NORMAL",
                    newMode = "SILENT"
                ),
                HistoryEntity(
                    timestamp = now - 3600_000,
                    formattedDateTime = sdf.format(Date(now - 3600_000)),
                    zoneName = "Lecture Complex",
                    eventType = "30s Debounced EXIT (GPS Verified)",
                    previousMode = "SILENT",
                    newMode = "NORMAL"
                ),
                HistoryEntity(
                    timestamp = now - 7200_000,
                    formattedDateTime = sdf.format(Date(now - 7200_000)),
                    zoneName = "Lecture Complex",
                    eventType = "30s Sustained DWELL Confirmed",
                    previousMode = "NORMAL",
                    newMode = "SILENT"
                ),
                HistoryEntity(
                    timestamp = now - 18000_000,
                    formattedDateTime = sdf.format(Date(now - 18000_000)),
                    zoneName = "College Campus",
                    eventType = "Campus Boundary Reconciled",
                    previousMode = "SILENT",
                    newMode = "NORMAL"
                )
            )

            demoList.forEach { historyDao.insert(it) }
        }
    }
}
