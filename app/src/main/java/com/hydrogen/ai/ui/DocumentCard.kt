package com.hydrogen.ai.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hydrogen.ai.model.PdfMarkdown
import com.hydrogen.ai.model.ReplyPart
import com.hydrogen.ai.ui.theme.OrbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface DocumentState {
    data object Working : DocumentState
    data class Ready(val file: File, val preview: ImageBitmap, val pages: Int) : DocumentState
    data object Failed : DocumentState
}

/**
 * A document the model wrote (a worksheet with its answers, notes, a letter) shown as a PDF: a first-page preview,
 * the page count, and Open, Share and Save. "Show text" reads it right in the chat.
 */
@Composable
fun DocumentCard(document: ReplyPart.Document, streaming: Boolean, showTextToggle: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val orbit = OrbitTheme.colors
    val writing = streaming && !document.complete
    var state by remember(document.title, document.markdown) { mutableStateOf<DocumentState>(DocumentState.Working) }
    var showText by rememberSaveable { mutableStateOf(false) }
    if (!writing) {
        LaunchedEffect(document.title, document.markdown) {
            state = withContext(Dispatchers.IO) {
                runCatching {
                    val file = PdfExport.writeToCache(context, document.title, document.markdown)
                    val preview = PdfExport.preview(file, widthPx = 300)
                    DocumentState.Ready(file, preview.bitmap.asImageBitmap(), preview.pages)
                }.getOrElse { DocumentState.Failed }
            }
        }
    }
    val ready = state as? DocumentState.Ready
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = ready?.file ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: error("No output")
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "Saved" else "Couldn't save the PDF", Toast.LENGTH_SHORT).show()
        }
    }

    val subtitle = when {
        writing -> "Writing the document…"
        state is DocumentState.Failed -> "Couldn't make the PDF. Show text to read it here."
        ready != null -> "PDF · ${ready.pages} ${if (ready.pages == 1) "page" else "pages"}"
        else -> "Making the PDF…"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = orbit.surface,
        border = BorderStroke(1.dp, orbit.line),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 56.dp, height = 79.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.dp, orbit.line, RoundedCornerShape(6.dp))
                ) {
                    when {
                        ready != null -> Image(
                            bitmap = ready.preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter
                        )
                        writing || state is DocumentState.Working -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = orbit.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = orbit.muted)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = orbit.muted)
                    if (!document.complete && !streaming) {
                        Text(
                            text = "The reply stopped early, so the end may be missing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = orbit.muted
                        )
                    }
                }
            }
            if (ready != null) {
                Spacer(Modifier.height(10.dp))
                val compact = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { openPdf(context, ready.file) },
                        contentPadding = compact,
                        modifier = Modifier.semantics { contentDescription = "Open ${document.title}" }
                    ) { Text("Open") }
                    OutlinedButton(onClick = { sharePdf(context, ready.file) }, contentPadding = compact) { Text("Share") }
                    OutlinedButton(onClick = { saveLauncher.launch(ready.file.name) }, contentPadding = compact) { Text("Save") }
                }
            }
            if (showTextToggle && !writing) {
                TextButton(onClick = { showText = !showText }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (showText) "Hide text" else "Show text")
                }
                if (showText) {
                    MarkdownMessage(content = document.markdown, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/**
 * Under a reply to a message that asked for a PDF when the model answered without making one: one tap turns the
 * whole reply into a document.
 */
@Composable
fun MakePdfOffer(content: String) {
    var made by rememberSaveable { mutableStateOf(false) }
    if (made) {
        Spacer(Modifier.height(8.dp))
        DocumentCard(
            document = ReplyPart.Document(PdfMarkdown.titleOf(content), content, complete = true),
            streaming = false,
            showTextToggle = false
        )
    } else {
        TextButton(onClick = { made = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Make a PDF")
        }
    }
}

private fun pdfUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun openPdf(context: Context, file: File) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(pdfUri(context, file), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(view) }.onFailure {
        Toast.makeText(context, "No app here opens PDFs. Use Share or Save instead.", Toast.LENGTH_LONG).show()
    }
}

private fun sharePdf(context: Context, file: File) {
    val uri = pdfUri(context, file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    runCatching { context.startActivity(Intent.createChooser(send, "Share PDF")) }.onFailure {
        Toast.makeText(context, "No app can share this PDF", Toast.LENGTH_SHORT).show()
    }
}
