package com.smartsilentzone.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsilentzone.data.db.TimetableDao
import com.smartsilentzone.data.db.TimetableEntity
import com.smartsilentzone.domain.Zone
import com.smartsilentzone.domain.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableDao: TimetableDao,
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private val _selectedDay = MutableStateFlow("Monday")
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    val availableZones: StateFlow<List<Zone>> = zoneRepository.zones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedulesForDay: StateFlow<List<TimetableEntity>> = _selectedDay
        .flatMapLatest { day -> timetableDao.observeByDay(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun upsertSchedule(schedule: TimetableEntity) {
        viewModelScope.launch {
            timetableDao.upsert(schedule)
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            timetableDao.delete(id)
        }
    }

    fun toggleEnabled(schedule: TimetableEntity) {
        viewModelScope.launch {
            timetableDao.upsert(schedule.copy(enabled = !schedule.enabled))
        }
    }

    fun seedSampleTimetable(zones: List<Zone>) {
        viewModelScope.launch {
            val sampleClassroom = zones.firstOrNull { it.type.name == "CLASSROOM" } ?: zones.firstOrNull()
            val sampleLibrary = zones.firstOrNull { it.type.name == "LIBRARY" } ?: zones.firstOrNull()

            val samples = listOf(
                TimetableEntity(
                    subjectName = "Operating Systems Lecture",
                    dayOfWeek = "Monday",
                    startTime = "09:00 AM",
                    endTime = "10:30 AM",
                    zoneId = sampleClassroom?.id ?: "class_1",
                    zoneName = sampleClassroom?.name ?: "Lecture Complex",
                    soundMode = "SILENT"
                ),
                TimetableEntity(
                    subjectName = "Library Research Study",
                    dayOfWeek = "Monday",
                    startTime = "11:00 AM",
                    endTime = "01:00 PM",
                    zoneId = sampleLibrary?.id ?: "lib_1",
                    zoneName = sampleLibrary?.name ?: "Central Library",
                    soundMode = "SILENT"
                ),
                TimetableEntity(
                    subjectName = "Computer Networks Seminar",
                    dayOfWeek = "Tuesday",
                    startTime = "10:00 AM",
                    endTime = "11:30 AM",
                    zoneId = sampleClassroom?.id ?: "class_1",
                    zoneName = sampleClassroom?.name ?: "Lecture Complex",
                    soundMode = "SILENT"
                ),
                TimetableEntity(
                    subjectName = "Data Structures Lab",
                    dayOfWeek = "Wednesday",
                    startTime = "02:00 PM",
                    endTime = "04:30 PM",
                    zoneId = sampleClassroom?.id ?: "class_1",
                    zoneName = sampleClassroom?.name ?: "Lecture Complex",
                    soundMode = "VIBRATE"
                )
            )

            samples.forEach { timetableDao.upsert(it) }
        }
    }
}
