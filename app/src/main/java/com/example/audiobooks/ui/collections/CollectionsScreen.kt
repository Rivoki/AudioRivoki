package com.example.audiobooks.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.audiobooks.data.db.model.CollectionWithBooks
import com.example.audiobooks.ui.components.AppActionButton
import androidx.compose.ui.Alignment

@Composable
fun CollectionsScreen(
    collections: List<CollectionWithBooks>,
    selectedCollectionId: Long?,
    isDarkMode: Boolean,
    onSelectCollection: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onToggleTheme: () -> Unit
) {
    var newCollectionName by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newCollectionName,
                onValueChange = { newCollectionName = it },
                label = { Text("Название новой папки") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppActionButton(text = "Добавить папку", onClick = {
                    onCreateCollection(newCollectionName)
                    newCollectionName = ""
                })
                AppActionButton(
                    text = if (isDarkMode) "☀️" else "🌙",
                    onClick = onToggleTheme
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = renameValue,
                onValueChange = { renameValue = it },
                label = { Text("Переименовать выбранную папку") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppActionButton(
                    text = "Переименовать",
                    enabled = selectedCollectionId != null,
                    onClick = {
                        selectedCollectionId?.let { onRenameCollection(it, renameValue) }
                        renameValue = ""
                    }
                )
                AppActionButton(
                    text = "Удалить",
                    enabled = selectedCollectionId != null,
                    onClick = {
                        showDeleteDialog = true
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            collections.forEach { item ->
                val isSelected = item.collection.id == selectedCollectionId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) {
                                if (isDarkMode) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectCollection(item.collection.id) }
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.collection.name,
                        style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${item.books.size} книг",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Вы точно хотите удалить папку с книгой?") },
            confirmButton = {
                AppActionButton(
                    text = "Да",
                    onClick = {
                        selectedCollectionId?.let { onDeleteCollection(it) }
                        showDeleteDialog = false
                    }
                )
            },
            dismissButton = {
                AppActionButton(
                    text = "Нет",
                    onClick = { showDeleteDialog = false }
                )
            }
        )
    }
}
