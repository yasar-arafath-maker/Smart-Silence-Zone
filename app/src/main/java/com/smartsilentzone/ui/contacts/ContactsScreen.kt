package com.smartsilentzone.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartsilentzone.data.db.ContactEntity
import com.smartsilentzone.ui.components.GlassmorphicCard
import com.smartsilentzone.ui.theme.NeonCyan

@Composable
fun ContactsScreen(
    vm: ContactsViewModel = hiltViewModel()
) {
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Explanatory VIP DND Card
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = NeonCyan
                )
                Column {
                    Text(
                        "DND Priority Whitelist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Contacts in this list are prioritized. If repeated calls occur or an emergency arises during silent lectures, they can reach you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = NeonCyan
                        )
                        Text(
                            "No VIP Contacts Configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Add important contacts (like Parents or College Proctor) who can bypass DND during emergencies.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add VIP Contact")
                        }

                        OutlinedButton(
                            onClick = { vm.seedFamilyPresets() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Seed Emergency Presets")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onToggle = { vm.toggleContact(contact) },
                        onDelete = { vm.deleteContact(contact.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Contact")
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { entity ->
                vm.upsertContact(entity)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ContactCard(
    contact: ContactEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    AssistChip(
                        onClick = {},
                        label = { Text(contact.relationship, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = contact.enabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onSave: (ContactEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add VIP Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Relationship Category", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Family", "Professor", "Emergency").forEach { rel ->
                        FilterChip(
                            selected = relationship == rel,
                            onClick = { relationship = rel },
                            label = { Text(rel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onSave(ContactEntity(name = name.trim(), phoneNumber = phone.trim(), relationship = relationship))
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
