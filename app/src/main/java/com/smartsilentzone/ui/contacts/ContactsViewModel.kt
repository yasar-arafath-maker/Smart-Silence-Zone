package com.smartsilentzone.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsilentzone.data.db.ContactDao
import com.smartsilentzone.data.db.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactDao: ContactDao
) : ViewModel() {

    val contacts: StateFlow<List<ContactEntity>> = contactDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun upsertContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactDao.upsert(contact)
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            contactDao.delete(id)
        }
    }

    fun toggleContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactDao.upsert(contact.copy(enabled = !contact.enabled))
        }
    }

    fun seedFamilyPresets() {
        viewModelScope.launch {
            val presets = listOf(
                ContactEntity(name = "Mom / Home", phoneNumber = "+91 98765 43210", relationship = "Family"),
                ContactEntity(name = "Dad", phoneNumber = "+91 98765 43211", relationship = "Family"),
                ContactEntity(name = "HOD / Faculty Advisor", phoneNumber = "+91 98765 43212", relationship = "Professor"),
                ContactEntity(name = "Campus Security / Emergency", phoneNumber = "+91 98765 00000", relationship = "Emergency")
            )
            presets.forEach { contactDao.upsert(it) }
        }
    }
}
