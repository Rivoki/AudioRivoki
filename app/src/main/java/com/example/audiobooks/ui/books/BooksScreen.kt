package com.example.audiobooks.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audiobooks.data.db.model.BookWithTracks
import com.example.audiobooks.ui.BookNameFormatter
import com.example.audiobooks.ui.components.AppActionButton

@Composable
fun BooksScreen(
    books: List<BookWithTracks>,
    selectedBookId: Long?,
    canImport: Boolean,
    onImportClick: () -> Unit,
    onBookClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Книги", style = MaterialTheme.typography.titleLarge)
            AppActionButton(text = "Импорт .zip/.rar", enabled = canImport, onClick = onImportClick)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            books.forEach { book ->
                val selected = selectedBookId == book.book.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookClick(book.book.id) }
                        .padding(8.dp)
                ) {
                    Text(
                        text = BookNameFormatter.toCyrillicAnalog(book.book.title),
                        style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${book.tracks.size} трек(ов)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
