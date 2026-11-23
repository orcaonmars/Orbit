package com.hydrogen.ai.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import com.hydrogen.ai.ui.theme.LocalAppearanceController
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import com.hydrogen.ai.model.QuickAction
import com.hydrogen.ai.model.Persona
import com.hydrogen.ai.model.Personas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.hydrogen.ai.model.Dictation
import com.hydrogen.ai.model.StartGreeting
import com.hydrogen.ai.model.Personal
import com.hydrogen.ai.ui.theme.OrbitTheme
import com.hydrogen.ai.ui.voice.DictationToolbar
import com.hydrogen.ai.ui.voice.InAppSpeech
import com.hydrogen.ai.ui.voice.VoiceChatScreen
import com.hydrogen.ai.ui.voice.rememberInAppSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.hydrogen.ai.model.MessageVersions
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.RowScope
import com.hydrogen.ai.model.Project
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hydrogen.ai.model.AdaptiveLayout
import com.hydrogen.ai.model.ChatListGrouping
import com.hydrogen.ai.model.ContextMeter
import com.hydrogen.ai.model.ContextUsage
import com.hydrogen.ai.model.EditResendPolicy
import java.time.ZoneId
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.hydrogen.ai.model.ModelDisplayName
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SuggestionChip
import com.hydrogen.ai.model.QuickReplies
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import com.hydrogen.ai.BuildConfig
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SkipNext
import com.hydrogen.ai.model.VoiceConversation
import com.hydrogen.ai.model.PromptTemplate
import com.hydrogen.ai.model.ShareAnswer
import com.hydrogen.ai.model.PdfMarkdown
import com.hydrogen.ai.model.ReplyPart
import com.hydrogen.ai.companion.AskPcProposals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.hydrogen.ai.model.ProviderErrorCategory
import com.hydrogen.ai.model.MessageA11y
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrogen.ai.data.AttachmentImportResult
import com.hydrogen.ai.data.ChatBackupCodec
import com.hydrogen.ai.data.LocalAttachmentStorage
import com.hydrogen.ai.data.DocumentImporter
import com.hydrogen.ai.data.DocumentImportResult
import com.hydrogen.ai.data.PromptLibraryStore
import com.hydrogen.ai.data.SavedConnectionStore
import com.hydrogen.ai.model.SavedConnection
import com.hydrogen.ai.model.SavedConnections
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.data.CustomPersonaStore
import com.hydrogen.ai.model.CustomPersonaLibrary
import com.hydrogen.ai.model.PersonaShareCodec
import com.hydrogen.ai.model.SharedPersona
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.foundation.lazy.itemsIndexed
import com.hydrogen.ai.model.CompareModelsPolicy
import androidx.compose.material.icons.outlined.Share
import com.hydrogen.ai.data.computeSampleSize
import com.hydrogen.ai.data.resolveLocalAttachmentFile
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.ConversationListTools
import com.hydrogen.ai.model.ConversationMarkdownExporter
import com.hydrogen.ai.model.SystemPromptPolicy
import com.hydrogen.ai.model.HydrogenDefaults
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.RegeneratePolicy
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.PromptLibrary
import com.hydrogen.ai.model.PrivacyFacts
import com.hydrogen.ai.model.PromptPackCodec
import com.hydrogen.ai.model.ProviderPreset
import com.hydrogen.ai.model.ProviderReadiness
import com.hydrogen.ai.model.SavedPrompt
import com.hydrogen.ai.model.ShareIntentParser
import com.hydrogen.ai.model.VoiceInputPolicy
import com.hydrogen.ai.model.SharedContent
import com.hydrogen.ai.model.VerificationState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sharedContent: SharedContent? = null,
    onSharedContentConsumed: () -> Unit = {},
    quickAction: QuickAction? = null,
    onQuickActionConsumed: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // System Back closes the open chat list instead of leaving the app. With the list
    // closed, Back only sends Hydrogen to the background: finishing the activity would
    // clear the ViewModel and cancel a reply that is still streaming.
    val backContext = LocalContext.current
    val backActivity = remember(backContext) {
        generateSequence(backContext) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    BackHandler(enabled = backActivity != null || drawerState.isOpen) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else if (backActivity?.moveTaskToBack(true) != true) backActivity?.finish()
    }
    var showSettings by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var settingsPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var showCompanionSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showSwitcher by remember { mutableStateOf(false) }
    var renameConnection by remember { mutableStateOf<SavedConnection?>(null) }
    var renameConnectionLabel by remember { mutableStateOf("") }
    var showMemory by remember { mutableStateOf(false) }
    var showPersonal by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    // The chat list's view (all chats, archived, or a project) and the project sheet: "" is a new project, else its id.
    var drawerView by rememberSaveable { mutableStateOf(DrawerView.MAIN) }
    var projectSheet by remember { mutableStateOf<String?>(null) }
    // A chat waiting to go into the project being created from "Move to project".
    var pendingMoveChat by remember { mutableStateOf<String?>(null) }
    // One-off notices from the ViewModel (e.g. "Saved to memory: …"), shown once.
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }
    val connectionStore = remember(context) {
        SavedConnectionStore(File(context.filesDir, "hydrogen_connections.json"))
    }
    var savedConnections by remember { mutableStateOf<List<SavedConnection>>(emptyList()) }
    // Every working server + model is remembered (never its key) so the switcher can jump back to it.
    LaunchedEffect(state.relayConfig, state.hasToken) {
        val current = SavedConnections.from(state.relayConfig, state.hasToken, System.currentTimeMillis())
        savedConnections = withContext(Dispatchers.IO) {
            val loaded = connectionStore.load()
            if (current == null) {
                loaded
            } else {
                SavedConnections.remember(loaded, current).also { updated ->
                    runCatching { connectionStore.save(updated) }
                }
            }
        }
    }
    val attachmentStorage = remember(context) {
        LocalAttachmentStorage(File(context.filesDir, "hydrogen_attachments"))
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    when (val imported = attachmentStorage.importContentUri(context, uri)) {
                        is AttachmentImportResult.Success -> viewModel.addStagedAttachment(imported.item)
                        is AttachmentImportResult.Failure -> viewModel.reportAttachmentImportFailure(imported.reason)
                    }
                }
            }
        }
    }
    // Voice runs inside Orbit with the phone's own recognizer (on-device when the phone can do it). No system
    // popup. Orbit gets text back from the recognizer and never stores audio.
    val speech = rememberInAppSpeech()
    val voiceAvailable = speech.available
    // One speaker for the whole screen: message "Read aloud" buttons and hands-free voice chat share it.
    val speaker = rememberMessageSpeaker()
    var showMicNeeded by remember { mutableStateOf(false) }
    var afterMicGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterMicGranted
        afterMicGranted = null
        if (granted) next?.invoke() else showMicNeeded = true
    }
    val withMic: (() -> Unit) -> Unit = { action ->
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterMicGranted = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Dictation: words appear in the message box as you talk; the check (or 3 s of quiet) keeps them unsent.
    val startDictation: () -> Unit = {
        if (voiceAvailable) withMic {
            speaker.stop()
            speech.start(
                InAppSpeech.Mode.DICTATION,
                onResult = { heard ->
                    if (heard != null) {
                        viewModel.updateDraft(VoiceInputPolicy.mergeIntoDraft(viewModel.uiState.value.draftText, heard))
                    } else {
                        Toast.makeText(context, Dictation.NO_SPEECH_MESSAGE, Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            )
        }
    }
    // Voice chat: listen → send → read the reply aloud → listen again, until the user ends it.
    var voiceChat by remember { mutableStateOf(false) }
    var voiceAwaitFrom by remember { mutableStateOf<Int?>(null) }
    var voiceMuted by remember { mutableStateOf(false) }
    var voiceCaption by remember { mutableStateOf<String?>(null) }
    var voiceQuietTurns by remember { mutableIntStateOf(0) }
    val endVoiceChat: (String?) -> Unit = { reason ->
        if (voiceChat) {
            voiceChat = false
            voiceAwaitFrom = null
            voiceMuted = false
            voiceCaption = null
            voiceQuietTurns = 0
            if (speech.mode == InAppSpeech.Mode.TURN) speech.cancel()
            speaker.stop()
            reason?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
    // "Camera": the phone's camera app saves into one temporary cache file that we hand it through
    // FileProvider; the photo is then copied into Hydrogen's attachment folder like a picked image
    // and the temporary file is deleted. Hydrogen never opens the camera itself (no CAMERA permission).
    val cameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val path = pendingCameraPath ?: return@rememberLauncherForActivityResult
        pendingCameraPath = null
        val photo = File(path)
        scope.launch {
            if (saved && photo.length() > 0L) {
                val name = "photo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
                when (val imported = attachmentStorage.importFromStream(
                    displayName = name,
                    mimeType = "image/jpeg",
                    reportedSizeBytes = photo.length(),
                    openInput = { photo.inputStream() }
                )) {
                    is AttachmentImportResult.Success -> viewModel.addStagedAttachment(imported.item)
                    is AttachmentImportResult.Failure -> viewModel.reportAttachmentImportFailure(imported.reason)
                }
            }
            withContext(Dispatchers.IO) { photo.delete() }
        }
    }
    val takePhoto: () -> Unit = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        // Leftovers from a capture the system abandoned (process death mid-camera) are removed here.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraPath = file.absolutePath
        try {
            cameraLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCameraPath = null
            Toast.makeText(context, "No camera app found.", Toast.LENGTH_SHORT).show()
        }
    }
    // One voice turn: hear one thing, then send it. Two quiet turns in a row stop listening until the orb is
    // tapped, so the mic isn't left open in an empty room.
    fun listenTurn() {
        if (!voiceChat || voiceMuted || !speech.inForeground) return
        speech.start(
            InAppSpeech.Mode.TURN,
            onResult = { heard ->
                when {
                    !voiceChat -> Unit
                    heard == null -> {
                        voiceQuietTurns++
                        if (voiceQuietTurns < 2) listenTurn()
                    }
                    VoiceConversation.isStopPhrase(heard) -> endVoiceChat(null)
                    else -> {
                        voiceQuietTurns = 0
                        voiceCaption = heard
                        val before = viewModel.uiState.value.activeConversation?.messages?.size ?: 0
                        viewModel.updateDraft(heard)
                        viewModel.sendMessage()
                        val after = viewModel.uiState.value.activeConversation?.messages?.size ?: 0
                        if (after > before) voiceAwaitFrom = before else endVoiceChat("Couldn't send that, so voice chat ended.")
                    }
                }
            },
            onFailure = { message ->
                // Stay in voice chat with the mic idle; the caption says what went wrong and the orb retries.
                if (voiceChat) voiceCaption = message
            }
        )
    }
    val startVoiceChat: () -> Unit = {
        if (voiceAvailable) withMic {
            speaker.stop()
            speech.cancel()
            voiceAwaitFrom = null
            voiceCaption = null
            voiceQuietTurns = 0
            voiceMuted = false
            voiceChat = true
            listenTurn()
        }
    }
    // When a reply has been read out in full, listen for the next thing to say.
    SideEffect {
        speaker.onFinished = { _, finished ->
            if (voiceChat && voiceAwaitFrom == null) {
                if (finished) listenTurn() else endVoiceChat("Couldn't read the reply aloud, so voice chat ended.")
            }
        }
    }
    // The caption keeps Orbit's last spoken sentence after it stops talking.
    LaunchedEffect(speaker.caption) {
        val spoken = speaker.caption ?: return@LaunchedEffect
        if (voiceChat) voiceCaption = spoken
    }
    val voiceChatMessages = state.activeConversation?.messages.orEmpty()
    LaunchedEffect(voiceAwaitFrom, voiceChatMessages, state.isSending) {
        val from = voiceAwaitFrom ?: return@LaunchedEffect
        when (val next = VoiceConversation.afterSend(voiceChatMessages, state.isSending, from)) {
            VoiceConversation.Next.Wait -> Unit
            is VoiceConversation.Next.Speak -> {
                voiceAwaitFrom = null
                if (!speaker.speak(next.message.id, PdfMarkdown.plainText(AskPcProposals.displayText(next.message.content)))) listenTurn()
            }
            is VoiceConversation.Next.End -> endVoiceChat(next.reason)
        }
    }
    // Switching chats ends voice chat and drops a dictation in progress, so nothing lands in the wrong chat.
    LaunchedEffect(state.activeConversationId) {
        endVoiceChat(null)
        speech.cancel()
    }
    val voiceChatPhase = when {
        !voiceChat -> null
        speaker.speakingId != null -> VoiceConversation.Phase.SPEAKING
        voiceAwaitFrom != null -> VoiceConversation.Phase.THINKING
        else -> VoiceConversation.Phase.LISTENING
    }
    val voiceListening = voiceChat && speech.active && speech.mode == InAppSpeech.Mode.TURN
    val dictating = speech.active && speech.mode == InAppSpeech.Mode.DICTATION
    val toggleVoiceMute: () -> Unit = {
        if (voiceMuted) {
            voiceMuted = false
            voiceQuietTurns = 0
            if (voiceChatPhase == VoiceConversation.Phase.LISTENING) listenTurn()
        } else {
            voiceMuted = true
            if (voiceListening) speech.cancel()
        }
    }
    val onVoiceOrbTap: () -> Unit = {
        when {
            speaker.speakingId != null -> {
                // Interrupt: stop reading and listen straight away.
                speaker.stop()
                voiceMuted = false
                voiceQuietTurns = 0
                listenTurn()
            }
            voiceAwaitFrom != null -> Unit
            voiceListening -> speech.finish()
            else -> {
                voiceMuted = false
                voiceQuietTurns = 0
                listenTurn()
            }
        }
    }
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hostView = LocalView.current
    // Launcher shortcut / Quick Settings tile / assistant gesture: always a fresh chat, then the matching hand-off.
    LaunchedEffect(quickAction) {
        val action = quickAction ?: return@LaunchedEffect
        // Consuming clears this effect's key, which would cancel it at its first suspension point;
        // the follow-up work therefore runs in the screen's own scope.
        onQuickActionConsumed()
        scope.launch {
            if (drawerState.isOpen) drawerState.snapTo(DrawerValue.Closed)
            viewModel.createNewConversation()
            when (action) {
                QuickAction.ASK_PHOTO -> if (cameraAvailable) takePhoto() else imagePicker.launch("image/*")
                QuickAction.VOICE -> if (voiceAvailable) startDictation()
                QuickAction.NEW_CHAT -> Unit
            }
            if (action == QuickAction.NEW_CHAT || (action == QuickAction.VOICE && !voiceAvailable)) {
                // A shortcut can cold-start the app: wait (up to ~10 s) until the composer is attached
                // and the window has focus, then focus it and raise the keyboard.
                for (attempt in 0 until 40) {
                    delay(250)
                    if (hostView.hasWindowFocus() && runCatching { composerFocus.requestFocus() }.isSuccess) {
                        keyboard?.show()
                        break
                    }
                }
            }
        }
    }
    val documentImporter = remember(attachmentStorage) { DocumentImporter(attachmentStorage) }
    // Selected documents stay on this device until the user taps Send.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val chosen = uris.take(6)
                val notices = mutableListOf<String>()
                if (uris.size > chosen.size) {
                    notices += "Only the first 6 documents were imported. Select the rest separately."
                }
                var importedCount = 0
                for (uri in chosen) {
                    val config = viewModel.uiState.value.relayConfig
                    val remainingImages = config.maxImagesPerRequest -
                        viewModel.uiState.value.stagedAttachments.size
                    when (val result = documentImporter.import(context, uri, remainingImages.coerceAtLeast(1))) {
                        is DocumentImportResult.Text -> {
                            viewModel.updateDraft(
                                ShareIntentParser.mergeIntoDraft(viewModel.uiState.value.draftText, result.draftText)
                            )
                            importedCount++
                            if (result.truncated) notices += "${result.displayName}: only the beginning was added."
                        }
                        is DocumentImportResult.Images -> {
                            if (remainingImages <= 0) {
                                result.items.forEach { attachmentStorage.deleteIfOwned(it.localUri) }
                                notices += "PDF image limit reached. No pages attached."
                            } else {
                                result.items.take(remainingImages).forEach { viewModel.addStagedAttachment(it) }
                                result.items.drop(remainingImages).forEach { attachmentStorage.deleteIfOwned(it.localUri) }
                                importedCount++
                                if (result.totalPages > result.items.size || result.items.size > remainingImages) {
                                    notices += "Some PDF pages were omitted. Check the attachment tray."
                                }
                            }
                        }
                        is DocumentImportResult.Failure -> {
                            viewModel.reportAttachmentImportFailure(result.reason)
                            notices += "A selected document could not be imported."
                        }
                    }
                }
                if (importedCount > 0) {
                    notices.add(0, "$importedCount document(s) added to the draft. Review before Send.")
                }
                if (notices.isNotEmpty()) {
                    Toast.makeText(context, notices.take(3).joinToString("\n"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Content shared from another app goes into the composer for review; it is never auto-sent.
    LaunchedEffect(sharedContent) {
        val shared = sharedContent ?: return@LaunchedEffect
        if (!shared.text.isNullOrBlank()) {
            viewModel.updateDraft(
                ShareIntentParser.mergeIntoDraft(viewModel.uiState.value.draftText, shared.text)
            )
        }
        var importedImages = 0
        shared.imageUris.forEach { raw ->
            val imported = runCatching {
                attachmentStorage.importContentUri(context, Uri.parse(raw), shared.declaredImageType)
            }
                .getOrElse { AttachmentImportResult.Failure("Could not read a shared image.") }
            when (imported) {
                is AttachmentImportResult.Success -> {
                    viewModel.addStagedAttachment(imported.item)
                    importedImages++
                }
                is AttachmentImportResult.Failure -> viewModel.reportAttachmentImportFailure(imported.reason)
            }
        }
        val parts = buildList {
            if (!shared.text.isNullOrBlank()) add("text added to the message box")
            if (importedImages > 0) add("$importedImages image(s) attached")
            if (shared.droppedItems > 0) add("${shared.droppedItems} unsupported item(s) skipped")
        }
        if (parts.isNotEmpty()) {
            Toast.makeText(context, "Shared: " + parts.joinToString(", ") + ".", Toast.LENGTH_LONG).show()
        }
        onSharedContentConsumed()
    }

    val backupExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val payload = viewModel.exportBackupJson()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(payload.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) "Chats backed up." else "Backup failed: could not write the file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val markdownExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            val payload = viewModel.exportAllMarkdown()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(payload.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) "Chats exported as Markdown." else "Export failed: could not write the file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val backupImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readNBytesCompat(ChatBackupCodec.MAX_IMPORT_CHARS + 1)
                            String(bytes, Charsets.UTF_8)
                        }
                    }.getOrNull()
                }
                if (text == null) {
                    Toast.makeText(context, "Import failed: could not read the file.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.importBackup(text) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Tablets and open foldables: the chat list stays open beside the chat. Phones keep the slide-in drawer.
    val configuration = LocalConfiguration.current
    val twoPane = AdaptiveLayout.isTwoPane(configuration.screenWidthDp, configuration.screenHeightDp)
    // A drawer left open before the screen grew would stay open (and catch Back) behind the fixed list.
    LaunchedEffect(twoPane) {
        if (twoPane && drawerState.isOpen) drawerState.snapTo(DrawerValue.Closed)
    }
    val chatList: @Composable () -> Unit = {
        ConversationDrawerContent(
            state = state,
            permanent = twoPane,
            view = drawerView,
            backEnabled = !twoPane && drawerState.isOpen,
            onViewChange = { drawerView = it },
            onNewChat = {
                viewModel.createNewConversation()
                if (!twoPane) scope.launch { drawerState.close() }
            },
            onNewChatInProject = { projectId ->
                viewModel.newChatInProject(projectId)
                if (!twoPane) scope.launch { drawerState.close() }
            },
            onSelect = { id ->
                viewModel.selectConversation(id)
                if (!twoPane) scope.launch { drawerState.close() }
            },
            onDelete = viewModel::deleteConversation,
            onRename = viewModel::renameConversation,
            onTogglePin = viewModel::togglePinned,
            onArchive = viewModel::setArchived,
            onMoveToProject = viewModel::moveToProject,
            onEditProject = { projectId -> projectSheet = projectId },
            onNewProject = { moveChatId ->
                pendingMoveChat = moveChatId
                projectSheet = ""
            },
            onBackup = { backupExporter.launch("orbit-chats-backup.json") },
            onRestore = { backupImporter.launch(arrayOf("application/json", "text/plain")) },
            onExportMarkdown = {
                if (state.savedConversations.any { c -> c.messages.isNotEmpty() }) {
                    markdownExporter.launch("orbit-chats.md")
                } else {
                    Toast.makeText(context, "No chats to export yet.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val chatPane: @Composable () -> Unit = {
        ChatScaffold(
            state = state,
            onOpenDrawer = if (twoPane) null else openDrawer,
            // Inside a project, a new chat stays in that project.
            onNewChat = {
                val project = state.activeProject
                if (project != null) viewModel.newChatInProject(project.id) else viewModel.createNewConversation()
            },
            onTemporaryChat = { on -> if (on) viewModel.startTemporaryChat() else viewModel.leaveTemporaryChat() },
            onOpenPersonal = { showPersonal = true },
            onOpenProject = { projectSheet = it },
            onOpenCompanion = { showCompanionSheet = true },
            onOpenSettings = {
                settingsPreset = null
                showSettings = true
            },
            onSwitchModel = { showSwitcher = true },
            onSetupProvider = { preset ->
                settingsPreset = preset
                showSettings = true
            },
            onDraftChange = viewModel::updateDraft,
            onPickImages = { imagePicker.launch("image/*") },
            onTakePhoto = if (cameraAvailable) takePhoto else null,
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            voiceAvailable = voiceAvailable,
            onVoiceInput = startDictation,
            dictation = if (dictating) speech else null,
            speaker = speaker,
            voiceChatPhase = voiceChatPhase,
            onStartVoiceChat = if (voiceAvailable) startVoiceChat else null,
            composerFocus = composerFocus,
            onRemoveAttachment = viewModel::removeStagedAttachment,
            onClearAttachments = viewModel::clearStagedAttachments,
            onSend = viewModel::sendMessage,
            onCancelSend = viewModel::cancelActiveSend,
            onRetryMessage = viewModel::retryMessage,
            onShowVersion = viewModel::showVersion,
            onCompare = viewModel::compareWith,
            onBranch = viewModel::branchFrom,
            onQuickReply = { reply ->
                viewModel.updateDraft(reply)
                viewModel.sendMessage()
            },
            onLoadModels = viewModel::loadModelList,
            onSetSystemPrompt = viewModel::setSystemPrompt,
            onEditMessage = viewModel::startEditingMessage,
            onCancelEdit = viewModel::cancelEditing,
            onOpenMemory = { showMemory = true },
            onOpenPrivacy = { showPrivacy = true },
            onAskPcAction = { messageId, action ->
                // None of these approves anything: approval only ever happens on the PC.
                when (action) {
                    AskPcCardAction.OPEN_COMPANION -> showCompanionSheet = true
                    AskPcCardAction.SEND -> viewModel.sendAskPcProposal(messageId)
                    AskPcCardAction.SEND_AGAIN -> viewModel.sendAskPcProposal(messageId, newRequest = true)
                    AskPcCardAction.CHECK_APPROVAL -> viewModel.startWaitingForCompanionApproval()
                    AskPcCardAction.RUN -> viewModel.startCompanionWorkTask()
                    AskPcCardAction.CHECK_TASK -> viewModel.refreshCompanionWorkTaskStatus()
                    AskPcCardAction.ADD_RESULT -> viewModel.insertCompanionResultIntoDraft()
                }
            }
        )
    }
    Box(Modifier.fillMaxSize()) {
        if (twoPane) {
            PermanentNavigationDrawer(drawerContent = chatList) { chatPane() }
        } else {
            ModalNavigationDrawer(drawerState = drawerState, drawerContent = chatList) { chatPane() }
        }
        AnimatedVisibility(
            visible = voiceChatPhase != null,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(180))
        ) {
            VoiceChatScreen(
                phase = voiceChatPhase ?: VoiceConversation.Phase.LISTENING,
                listening = voiceListening,
                level = speech.level,
                muted = voiceMuted,
                caption = if (voiceListening) Dictation.transcript(speech.committed, speech.partial) ?: voiceCaption else voiceCaption,
                note = if (speech.usesOnDevice) Dictation.ON_DEVICE_NOTE else Dictation.SERVICE_NOTE,
                onOrbTap = onVoiceOrbTap,
                onToggleMute = toggleVoiceMute,
                onEnd = { endVoiceChat(null) }
            )
        }
    }
    if (showMicNeeded) {
        AlertDialog(
            onDismissRequest = { showMicNeeded = false },
            title = { Text("Microphone is off") },
            text = {
                Text(
                    Dictation.MIC_PERMISSION_MESSAGE + " " +
                        (if (speech.usesOnDevice) Dictation.ON_DEVICE_NOTE else Dictation.SERVICE_NOTE)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicNeeded = false
                    try {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Couldn't open Settings.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showMicNeeded = false }) { Text("Not now") } }
        )
    }
    if (showPrivacy) {
        val promptCount by produceState(initialValue = 0, showPrivacy) {
            value = withContext(Dispatchers.IO) {
                // The built-in starter prompts ship with the app, so only the user's own (or edited) ones count.
                runCatching {
                    PromptLibraryStore(File(context.filesDir, "hydrogen_prompts.json")).load()
                        .count { it !in PromptLibrary.starterPrompts }
                }.getOrDefault(0)
            }
        }
        PrivacySheet(
            facts = PrivacyFacts(
                route = state.relayConfig.route,
                endpointUrl = state.relayConfig.effectiveBaseUrl,
                companionPaired = state.companion.session != null,
                chatCount = state.savedConversations.count { it.messages.isNotEmpty() },
                memoryCount = state.memoryItems.size,
                memoryEnabled = state.memoryEnabled,
                savedPromptCount = promptCount
            ),
            onDeleteAll = {
                showPrivacy = false
                viewModel.deleteAllLocalData()
                savedConnections = emptyList()
                scope.launch(Dispatchers.IO) {
                    listOf("hydrogen_connections.json", "hydrogen_prompts.json", "hydrogen_personas.json")
                        .forEach { runCatching { File(context.filesDir, it).delete() } }
                    // Orphaned attachment copies and camera captures too; the folders themselves stay.
                    listOf(File(context.filesDir, "hydrogen_attachments"), File(context.cacheDir, "camera"))
                        .forEach { dir -> dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
                }
            },
            onDismiss = { showPrivacy = false }
        )
    }
    if (state.whatsNew) {
        WhatsNewSheet(onDismiss = viewModel::dismissWhatsNew)
    }
    if (showPersonal) {
        PersonalSheet(
            profile = state.personal,
            onSave = { profile ->
                viewModel.savePersonal(profile)
                showPersonal = false
            },
            onDismiss = { showPersonal = false }
        )
    }
    projectSheet?.let { sheetId ->
        val project = state.projects.firstOrNull { it.id == sheetId }
        if (sheetId.isNotEmpty() && project == null) {
            // Its project was deleted (or a restore replaced it): nothing left to edit.
            LaunchedEffect(sheetId) { projectSheet = null }
        } else {
            ProjectSheet(
                project = project,
                chatCount = project?.let { p -> ConversationListTools.inProject(state.conversations, p.id).size } ?: 0,
                importer = documentImporter,
                onCreate = { name, instructions ->
                    viewModel.createProject(name, instructions)?.let { id ->
                        pendingMoveChat?.let { chatId -> viewModel.moveToProject(chatId, id) }
                        pendingMoveChat = null
                        // Stay open on the new project so files can be added, and show it in the chat list.
                        projectSheet = id
                        drawerView = DrawerView.project(id)
                    }
                },
                onSave = viewModel::updateProject,
                onAddFile = viewModel::addProjectFile,
                onRemoveFile = viewModel::removeProjectFile,
                onDelete = { id ->
                    viewModel.deleteProject(id)
                    projectSheet = null
                    if (DrawerView.projectId(drawerView) == id) drawerView = DrawerView.MAIN
                },
                onDismiss = {
                    projectSheet = null
                    pendingMoveChat = null
                }
            )
        }
    }
    if (showMemory) {
        MemorySheet(
            items = state.memoryItems,
            enabled = state.memoryEnabled,
            onSetEnabled = viewModel::setMemoryEnabled,
            onAdd = viewModel::addMemory,
            onUpdate = viewModel::updateMemory,
            onRemove = viewModel::removeMemory,
            onClearAll = viewModel::clearMemory,
            onDismiss = { showMemory = false }
        )
    }

    if (showSwitcher) {
        ConnectionSwitcherSheet(
            current = state.relayConfig,
            connections = savedConnections,
            onSwitch = { target ->
                val cfg = viewModel.uiState.value.relayConfig
                // token = null: the ViewModel loads the key bound to this exact service/endpoint.
                viewModel.saveRelaySettings(
                    target.route,
                    target.baseUrl,
                    target.modelId,
                    null,
                    cfg.maxContextTokensBudget,
                    cfg.maxImagesPerRequest,
                    cfg.streamingEnabled,
                    cfg.rememberTokenInKeystore,
                    if (target.route == ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) target.webSearchEnabled else cfg.webSearchEnabled
                )
                if (target.needsKey && !viewModel.uiState.value.hasToken) {
                    settingsPreset = null
                    showSettings = true
                    Toast.makeText(context, "Enter the API key for this server.", Toast.LENGTH_LONG).show()
                }
            },
            onRemove = { target ->
                val updated = SavedConnections.remove(savedConnections, target)
                savedConnections = updated
                scope.launch(Dispatchers.IO) { runCatching { connectionStore.save(updated) } }
            },
            onRename = { target ->
                renameConnection = target
                renameConnectionLabel = target.label
            },
            onOpenSettings = {
                showSwitcher = false
                settingsPreset = null
                showSettings = true
            },
            onDismiss = { showSwitcher = false }
        )
    }

    renameConnection?.let { target ->
        AlertDialog(
            onDismissRequest = { renameConnection = null },
            title = { Text("Name this connection") },
            text = {
                OutlinedTextField(
                    value = renameConnectionLabel,
                    onValueChange = { renameConnectionLabel = it.take(SavedConnections.MAX_LABEL) },
                    label = { Text("Nickname (optional)") },
                    placeholder = { Text(ModelDisplayName.of(target.modelId)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = SavedConnections.rename(savedConnections, target, renameConnectionLabel)
                    savedConnections = updated
                    scope.launch(Dispatchers.IO) { runCatching { connectionStore.save(updated) } }
                    renameConnection = null
                }) { Text("Save name") }
            },
            dismissButton = {
                TextButton(onClick = { renameConnection = null }) { Text("Cancel") }
            }
        )
    }

    if (showSettings) {
        RelaySettingsSheet(
            relayConfig = state.relayConfig,
            hasToken = state.hasToken,
            maskedTokenLabel = state.maskedTokenLabel,
            capabilities = state.capabilities,
            readiness = state.readiness,
            isCheckingHealth = state.isCheckingHealth,
            onDismiss = { showSettings = false },
            onSave = viewModel::saveRelaySettings,
            onCheckHealth = viewModel::checkRelayHealth,
            onDeleteCredential = viewModel::deleteActiveProviderCredential,
            onOpenDiagnostics = {
                showSettings = false
                showDiagnostics = true
            },
            modelList = state.modelList,
            onLoadModels = viewModel::loadModelList,
            initialPreset = settingsPreset,
            // The PC's Wi-Fi address, learned from the Companion pairing (or the address typed for it).
            pcWifiHost = (state.companion.session?.baseUrl ?: state.companion.config.baseUrl)
                .let { com.hydrogen.ai.companion.CompanionUrlSafety.lanIpv4Host(it) },
            backupKeyLabels = state.backupKeyLabels,
            onAddBackupKey = viewModel::addBackupKey,
            onRemoveBackupKey = viewModel::removeBackupKey
        )
    }

    if (showDiagnostics) {
        DiagnosticsSheet(
            entries = state.errorLog,
            onClear = viewModel::clearErrorLog,
            onDismiss = { showDiagnostics = false }
        )
    }

    if (showCompanionSheet) {
        CompanionPairingSheet(
            companionState = state.companion,
            onDismiss = { showCompanionSheet = false },
            onCheckHealth = viewModel::checkCompanionHealth,
            onPair = viewModel::pairWithCompanion,
            onRevoke = viewModel::revokeCompanionPairing,
            onClearLocalSession = viewModel::clearLocalCompanionSession,
            onRequestApproval = { action, targetPath, summary ->
                viewModel.requestCompanionApproval(action, targetPath, summary, autoWait = true)
            },
            onRefreshApproval = viewModel::refreshCompanionApprovalStatus,
            onWaitForApproval = viewModel::startWaitingForCompanionApproval,
            onStopWaitingForApproval = viewModel::stopWaitingForCompanionApproval,
            onStartWorkTask = { viewModel.startCompanionWorkTask() },
            onRefreshWorkTask = viewModel::refreshCompanionWorkTaskStatus,
            onCancelWorkTask = viewModel::cancelCompanionWorkTask,
            onStageWorkTarget = viewModel::stageCompanionWorkTarget,
            onClearWorkState = viewModel::clearCompanionWorkState,
            onInsertResultIntoDraft = {
                if (viewModel.insertCompanionResultIntoDraft()) {
                    showCompanionSheet = false
                }
            }
        )
    }
}

/** Which list the chat drawer shows: all chats, archived chats or one project. A string, so it survives rotation. */
internal object DrawerView {
    const val MAIN = "main"
    const val ARCHIVED = "archived"
    private const val PROJECT_PREFIX = "project:"

    fun project(projectId: String): String = PROJECT_PREFIX + projectId

    fun projectId(view: String): String? =
        if (view.startsWith(PROJECT_PREFIX)) view.removePrefix(PROJECT_PREFIX).ifEmpty { null } else null
}

/** How many projects the main list shows before "Show all". */
private const val DRAWER_PROJECTS_SHOWN = 5

/** Pinned chats first, then Today / Yesterday / ... sections. */
private fun groupForDrawer(chats: List<Conversation>): List<Pair<String, List<Conversation>>> {
    val byId = chats.associateBy { it.id }
    val pinned = chats.filter { it.pinned }
    val dated = ChatListGrouping.group(
        chats.filterNot { it.pinned }.map { it.id to it.updatedAtEpochMs },
        System.currentTimeMillis(),
        ZoneId.systemDefault()
    ).map { group -> group.title to group.conversationIds.mapNotNull { byId[it] } }
    return (if (pinned.isEmpty()) emptyList() else listOf("Pinned" to pinned)) + dated
}

/** Where a search result lives when that is not the main list: its project, the archive, or both. */
private fun searchContextLabel(conversation: Conversation, projectNames: Map<String, String>): String? {
    val project = conversation.projectId?.let { projectNames[it] }
    return when {
        project != null && conversation.archived -> "$project, archived"
        project != null -> project
        conversation.archived -> "Archived"
        else -> null
    }
}

private fun fileCountLabel(count: Int): String = when (count) {
    0 -> "No files"
    1 -> "1 file"
    else -> "$count files"
}

@Composable
private fun ConversationDrawerContent(
    state: ChatUiState,
    permanent: Boolean = false,
    view: String,
    backEnabled: Boolean,
    onViewChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewChatInProject: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onMoveToProject: (String, String?) -> Unit,
    onEditProject: (String) -> Unit,
    onNewProject: (moveChatId: String?) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportMarkdown: () -> Unit
) {
    // Saveable: rotating, unfolding or a light/dark switch recreates the screen and would otherwise clear the search.
    var query by rememberSaveable { mutableStateOf("") }
    var showAllProjects by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var moveTarget by remember { mutableStateOf<Conversation?>(null) }

    val projects = state.projects
    val projectIds = remember(projects) { projects.mapTo(HashSet()) { it.id } }
    val projectNames = remember(projects) { projects.associate { it.id to it.name } }
    val openProject = DrawerView.projectId(view)?.let { id -> projects.firstOrNull { it.id == id } }
    val openProjectId = openProject?.id
    val inArchive = view == DrawerView.ARCHIVED
    val inMain = !inArchive && openProject == null
    // A deleted project (or one gone since the view was saved) falls back to all chats.
    LaunchedEffect(view, openProjectId) {
        if (view != DrawerView.MAIN && !inArchive && openProjectId == null) onViewChange(DrawerView.MAIN)
    }
    // Back inside the archive or a project returns to all chats before it closes the drawer.
    BackHandler(enabled = backEnabled && !inMain) { onViewChange(DrawerView.MAIN) }

    val searching = inMain && query.isNotBlank()
    val chats = remember(state.conversations, projectIds, inArchive, openProjectId, searching, query) {
        when {
            inArchive -> ConversationListTools.archived(state.conversations)
            openProjectId != null -> ConversationListTools.inProject(state.conversations, openProjectId)
            // Search reaches every saved chat, archived and project chats included.
            searching -> ConversationListTools.filter(ConversationListTools.searchable(state.conversations), query)
            // No temporary chats, no empty new chat until its first message, no archived or project chats.
            else -> ConversationListTools.forDrawer(state.conversations, projectIds)
        }
    }
    val archivedCount = remember(state.conversations) { ConversationListTools.archived(state.conversations).size }
    val projectChatCounts = remember(state.conversations) {
        ConversationListTools.searchable(state.conversations)
            .mapNotNull { c -> c.projectId?.takeIf { !c.archived } }
            .groupingBy { it }
            .eachCount()
    }
    // While searching, each result shows its highlighted title plus a snippet from the newest matching message.
    val previews = remember(chats, searching, query) {
        if (!searching) emptyMap() else chats.associate { it.id to ConversationListTools.preview(it, query) }
    }
    // Search shows one flat "Results" list and the archive one flat list; otherwise pinned chats, then by date.
    val sections: List<Pair<String, List<Conversation>>> = remember(chats, searching, inArchive) {
        when {
            searching -> listOf("Results" to chats)
            inArchive -> if (chats.isEmpty()) emptyList() else listOf("" to chats)
            else -> groupForDrawer(chats)
        }
    }
    val showProjects = inMain && !searching
    val shownProjects =
        if (showAllProjects || projects.size <= DRAWER_PROJECTS_SHOWN) projects else projects.take(DRAWER_PROJECTS_SHOWN - 1)
    val hiddenProjects = projects.size - shownProjects.size
    // Index of the first chat section, so a newly pinned chat can be scrolled into view below the projects.
    val firstChatIndex = if (showProjects) 2 + shownProjects.size + (if (hiddenProjects > 0) 1 else 0) else 0

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(ConversationListTools.MAX_TITLE_LENGTH) },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.id, renameText)
                        renameTarget = null
                    },
                    enabled = ConversationListTools.normalizeTitle(renameText) != null
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete conversation?") },
            text = { Text("\"${target.title}\" and its ${target.messages.size} messages will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    moveTarget?.let { target ->
        val current = target.projectId?.takeIf { it in projectIds }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move to project") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (current != null) {
                        DrawerNavRow(
                            icon = Icons.Outlined.FolderOff,
                            label = "No project",
                            onClick = {
                                onMoveToProject(target.id, null)
                                moveTarget = null
                            }
                        )
                    }
                    projects.forEach { project ->
                        DrawerNavRow(
                            icon = Icons.Outlined.Folder,
                            label = project.name,
                            selected = project.id == current,
                            onClick = {
                                if (project.id != current) onMoveToProject(target.id, project.id)
                                moveTarget = null
                            }
                        )
                    }
                    DrawerNavRow(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "New project",
                        onClick = {
                            moveTarget = null
                            onNewProject(target.id)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("Cancel") }
            }
        )
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Each list starts at its top.
    LaunchedEffect(view) { listState.scrollToItem(0) }

    ChatListSheet(permanent = permanent) {
        when {
            openProject != null -> {
                DrawerSubHeader(title = openProject.name, onBack = { onViewChange(DrawerView.MAIN) }) {
                    IconButton(onClick = { onEditProject(openProject.id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit project")
                    }
                }
                DrawerActionButton(
                    icon = Icons.Default.Add,
                    label = "New chat in this project",
                    onClick = { onNewChatInProject(openProject.id) }
                )
                ProjectSummaryCard(project = openProject, onClick = { onEditProject(openProject.id) })
            }
            inArchive -> {
                DrawerSubHeader(title = "Archived chats", onBack = { onViewChange(DrawerView.MAIN) })
                Text(
                    text = "Archived chats stay on this phone. Sending a message moves a chat back to your list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HydrogenMark(size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Orbit",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Outlined.EditNote, contentDescription = "New chat")
                    }
                }
                DrawerActionButton(icon = Icons.Default.Add, label = "New chat", onClick = onNewChat)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search chats",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showProjects) {
                item(key = "section-projects", contentType = "header") {
                    DrawerSectionTitle("Projects", Modifier.animateItem())
                }
                items(shownProjects, key = { "project-${it.id}" }, contentType = { "nav" }) { project ->
                    val count = projectChatCounts[project.id] ?: 0
                    DrawerNavRow(
                        icon = Icons.Outlined.Folder,
                        label = project.name,
                        trailing = if (count > 0) count.toString() else null,
                        onClick = { onViewChange(DrawerView.project(project.id)) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (hiddenProjects > 0) {
                    item(key = "projects-more", contentType = "nav") {
                        DrawerNavRow(
                            icon = Icons.Default.KeyboardArrowDown,
                            label = "Show all ${projects.size} projects",
                            onClick = { showAllProjects = true },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item(key = "project-new", contentType = "nav") {
                    DrawerNavRow(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "New project",
                        onClick = { onNewProject(null) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (chats.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = when {
                            searching -> "No chats match \"${query.trim()}\"."
                            inArchive -> "No archived chats."
                            openProject != null -> "Chats you start here use this project's instructions and files."
                            else -> "Your chats will show up here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }
            sections.forEach { (title, sectionChats) ->
                if (title.isNotEmpty()) {
                    item(key = "section-$title", contentType = "header") {
                        DrawerSectionTitle(title, Modifier.animateItem())
                    }
                }
                items(sectionChats, key = { it.id }, contentType = { "chat" }) { conversation ->
                    DrawerConversationRow(
                        conversation = conversation,
                        selected = conversation.id == state.activeConversationId,
                        preview = previews[conversation.id],
                        contextLabel = if (searching) searchContextLabel(conversation, projectNames) else null,
                        onSelect = { onSelect(conversation.id) },
                        onRename = {
                            renameText = conversation.title
                            renameTarget = conversation
                        },
                        onTogglePin = {
                            val pinning = !conversation.pinned
                            onTogglePin(conversation.id)
                            // A newly pinned chat jumps to the top section; follow it so the change is visible.
                            if (pinning) scope.launch { listState.animateScrollToItem(firstChatIndex) }
                        },
                        onArchive = { onArchive(conversation.id, !conversation.archived) },
                        onMove = { moveTarget = conversation },
                        onDelete = { deleteTarget = conversation },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (showProjects && archivedCount > 0) {
                item(key = "archived-link", contentType = "nav") {
                    DrawerNavRow(
                        icon = Icons.Outlined.Archive,
                        label = "Archived chats",
                        trailing = archivedCount.toString(),
                        onClick = { onViewChange(DrawerView.ARCHIVED) },
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 10.dp)
                    )
                }
            }
        }
        if (inMain) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val footerButtons: @Composable () -> Unit = {
                TextButton(onClick = onBackup) {
                    Text("Back up", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRestore) {
                    Text("Restore", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onExportMarkdown) {
                    Text("Export", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val footerModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
            // Large system font sizes would break the side-by-side buttons mid-word; stack them.
            if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
                Column(modifier = footerModifier) { footerButtons() }
            } else {
                Row(modifier = footerModifier) { footerButtons() }
            }
        }
    }
}

/** Back arrow and title for the archive and project lists, with optional actions on the right. */
@Composable
private fun DrawerSubHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to all chats")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        actions()
    }
}

/** The outlined "New chat" style button at the top of a list. */
@Composable
private fun DrawerActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A project's instructions (first lines) and file count; tapping opens the project to edit. */
@Composable
private fun ProjectSummaryCard(project: Project, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = project.instructions.ifBlank { "None yet. Tap to say how Orbit should help in this project." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (project.instructions.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = fileCountLabel(project.files.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/** A plain row with an icon: a project, "New project", "Archived chats", or a choice in the move dialog. */
@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Current project",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

/**
 * The sheet behind the chat list: a rounded slide-in sheet on phones, or (when [permanent], on tablets and open
 * foldables) a flat panel that stays beside the chat with a thin divider.
 */
@Composable
private fun ChatListSheet(permanent: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (permanent) {
        Row(modifier = Modifier.fillMaxHeight()) {
            PermanentDrawerSheet(
                modifier = Modifier.width(AdaptiveLayout.CHAT_LIST_PANE_WIDTH_DP.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                content = content
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    } else {
        ModalDrawerSheet(
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            content = content
        )
    }
}

/** [text] with each of [ranges] drawn in [style]; ranges that fall outside the text are ignored. */
private fun highlightRanges(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            if (r.first >= 0 && r.first <= r.last && r.last < text.length) addStyle(style, r.first, r.last + 1)
        }
    }

/**
 * One chat in the drawer: a single-line title, quiet selected fill and its actions behind a menu. While searching,
 * [preview] adds highlighted matches and a snippet from the newest matching message, and [contextLabel] says where
 * the chat lives (a project, or the archive). An archived chat's menu offers Unarchive, Rename and Delete.
 */
@Composable
private fun DrawerConversationRow(
    conversation: Conversation,
    selected: Boolean,
    preview: ConversationListTools.SearchPreview? = null,
    contextLabel: String? = null,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            if (selected) {
                // A short accent bar marks the open chat.
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(width = 3.dp, height = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(OrbitTheme.colors.accent)
                )
            }
            val highlight = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = highlightRanges(conversation.title, preview?.titleHighlights.orEmpty(), highlight),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                contextLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                preview?.snippet?.let { snippet ->
                    Text(
                        text = highlightRanges(snippet, preview.snippetHighlights, highlight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (preview != null && preview.matchingMessages > 1) {
                    Text(
                        text = "${preview.matchingMessages} matching messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (conversation.pinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Chat options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!conversation.archived) {
                        DropdownMenuItem(
                            text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                            leadingIcon = {
                                Icon(
                                    if (conversation.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onTogglePin()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    if (!conversation.archived) {
                        DropdownMenuItem(
                            text = { Text("Move to project") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (conversation.archived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(
                                if (conversation.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInstructionsDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat instructions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sent as a system prompt with every message in this chat only. Leave blank for none.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(SystemPromptPolicy.MAX_LENGTH) },
                    placeholder = { Text("e.g. You are a concise coding assistant. Answer in English.") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${text.length} / ${SystemPromptPolicy.MAX_LENGTH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initialText.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScaffold(
    state: ChatUiState,
    /** Null when the chat list is already on screen beside the chat (two-pane), so there is no menu button. */
    onOpenDrawer: (() -> Unit)?,
    onNewChat: () -> Unit,
    /** True starts a temporary chat; false ends it and goes back to the chat that was open before. */
    onTemporaryChat: (Boolean) -> Unit = {},
    onOpenPersonal: () -> Unit = {},
    /** Opens the project sheet for a project id (from the pill above a project chat). */
    onOpenProject: (String) -> Unit = {},
    onOpenCompanion: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchModel: () -> Unit,
    onSetupProvider: (ProviderPreset) -> Unit,
    onDraftChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onTakePhoto: (() -> Unit)?,
    onPickFile: () -> Unit,
    voiceAvailable: Boolean,
    onVoiceInput: () -> Unit,
    /** The recognizer while a dictation is running, else null. */
    dictation: InAppSpeech?,
    speaker: MessageSpeaker,
    voiceChatPhase: VoiceConversation.Phase?,
    onStartVoiceChat: (() -> Unit)?,
    composerFocus: FocusRequester,
    onRemoveAttachment: (String) -> Unit,
    onClearAttachments: () -> Unit,
    onSend: () -> Unit,
    onCancelSend: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onShowVersion: (messageId: String, target: Int) -> Unit = { _, _ -> },
    onCompare: (String) -> Unit = {},
    onBranch: (String) -> Unit = {},
    onQuickReply: (String) -> Unit = {},
    onLoadModels: () -> Unit = {},
    onSetSystemPrompt: (conversationId: String, text: String) -> Unit = { _, _ -> },
    onEditMessage: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onAskPcAction: (messageId: String, action: AskPcCardAction) -> Unit = { _, _ -> }
) {
    var editingInstructionsFor by remember { mutableStateOf<Conversation?>(null) }
    editingInstructionsFor?.let { conversation ->
        ChatInstructionsDialog(
            initialText = conversation.systemPrompt,
            onDismiss = { editingInstructionsFor = null },
            onSave = { text ->
                onSetSystemPrompt(conversation.id, text)
                editingInstructionsFor = null
            }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    // Model switcher, like ChatGPT/Claude: tap the model name to change provider or model.
                    // Hidden until the saved settings load, so the built-in default name never flashes on start.
                    if (!state.isLoading) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Switch model", onClick = onSwitchModel)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (!state.readiness.configured ||
                            state.readiness.relayReachable == VerificationState.FAIL ||
                            state.readiness.authenticated == VerificationState.FAIL
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(readinessColor(state.readiness))
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            // ORBIT_DESIGN "Header": the model name in 15 at weight 600, then a muted chevron.
                            text = ModelDisplayName.friendly(state.relayConfig.modelId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = OrbitTheme.colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open conversations")
                        }
                    }
                },
                actions = {
                    // On an empty chat "New chat" would do nothing, so that spot offers a temporary chat instead.
                    // Once a chat has messages it is "New chat" again (which also ends a temporary chat).
                    if (state.activeConversation?.messages.isNullOrEmpty()) {
                        TemporaryChatToggle(on = state.isTemporaryChat, onChange = onTemporaryChat)
                    } else {
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Outlined.EditNote, contentDescription = "New chat")
                        }
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    var showAbout by remember { mutableStateOf(false) }
                    var showLicenses by remember { mutableStateOf(false) }
                    var showAppearance by remember { mutableStateOf(false) }
                    var showWhatsNewAgain by remember { mutableStateOf(false) }
                    val appearanceController = LocalAppearanceController.current
                    if (showAppearance && appearanceController != null) {
                        AppearanceSheet(controller = appearanceController, onDismiss = { showAppearance = false })
                    }
                    if (showWhatsNewAgain) {
                        WhatsNewSheet(onDismiss = { showWhatsNewAgain = false })
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        val shareContext = LocalContext.current
                        val conversation = state.activeConversation
                        val shareable = conversation?.takeIf { conv -> conv.messages.any { it.content.isNotBlank() } }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // Empty chats have the switch in the top bar; this is the way in (and out) mid-chat.
                            if (!conversation?.messages.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.isTemporaryChat) TemporaryChatCopy.MENU_END
                                            else TemporaryChatCopy.MENU_START
                                        )
                                    },
                                    leadingIcon = { TemporaryChatGlyph(on = state.isTemporaryChat, size = 20.dp) },
                                    onClick = {
                                        menuOpen = false
                                        onTemporaryChat(!state.isTemporaryChat)
                                    }
                                )
                            }
                            if (conversation != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (conversation.systemPrompt.isNotBlank()) "Chat instructions (on)"
                                            else "Chat instructions"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        editingInstructionsFor = conversation
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (state.personal.isEmpty) "Personal" else "Personal (on)") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenPersonal()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            !state.memoryEnabled -> "Memory (off)"
                                            state.memoryItems.isEmpty() -> "Memory"
                                            else -> "Memory (${state.memoryItems.size})"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenMemory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Privacy & data") },
                                leadingIcon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenPrivacy()
                                }
                            )
                            if (shareable != null) {
                                DropdownMenuItem(
                                    text = { Text("Share chat") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, shareable.title)
                                            putExtra(Intent.EXTRA_TEXT, ConversationMarkdownExporter.export(shareable))
                                        }
                                        runCatching {
                                            shareContext.startActivity(Intent.createChooser(send, "Share chat"))
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (state.companion.isPaired) "PC Companion (paired)" else "PC Companion") },
                                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenCompanion()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Models & API keys") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                }
                            )
                            if (appearanceController != null) {
                                DropdownMenuItem(
                                    text = { Text("Appearance") },
                                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        showAppearance = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("About Orbit") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showAbout = true
                                }
                            )
                        }
                    }
                    if (showAbout) {
                        AboutSheet(
                            onDismiss = { showAbout = false },
                            onWhatsNew = {
                                showAbout = false
                                showWhatsNewAgain = true
                            },
                            onPrivacy = {
                                showAbout = false
                                onOpenPrivacy()
                            },
                            onLicenses = {
                                showAbout = false
                                showLicenses = true
                            }
                        )
                    }
                    if (showLicenses) {
                        LicensesSheet(
                            onDismiss = { showLicenses = false },
                            onBack = {
                                showLicenses = false
                                showAbout = true
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            val activeMessages = state.activeConversation?.messages.orEmpty()
            val activeSystemPrompt = state.activeConversation?.systemPrompt.orEmpty()
            val contextUsage = remember(
                activeMessages, activeSystemPrompt, state.relayConfig.route,
                state.draftText.length, state.stagedAttachments.size
            ) {
                ContextMeter.measure(
                    messages = activeMessages,
                    systemPrompt = activeSystemPrompt,
                    route = state.relayConfig.route,
                    draftChars = state.draftText.length,
                    draftImages = state.stagedAttachments.size
                )
            }
            // Voice chat draws its own full-screen view on top, so the composer stays put underneath.
            ComposerBar(
                onStartVoiceChat = onStartVoiceChat,
                dictation = dictation,
                contextUsage = contextUsage,
                maxImagesPerRequest = state.relayConfig.maxImagesPerRequest,
                draftText = state.draftText,
                stagedAttachments = state.stagedAttachments,
                isSending = state.isSending,
                onDraftChange = onDraftChange,
                onPickImages = onPickImages,
                onTakePhoto = onTakePhoto,
                onPickFile = onPickFile,
                voiceAvailable = voiceAvailable,
                onVoiceInput = onVoiceInput,
                composerFocus = composerFocus,
                onRemoveAttachment = onRemoveAttachment,
                onClearAttachments = onClearAttachments,
                onSend = onSend,
                onCancelSend = onCancelSend,
                isEditing = state.editingMessageId != null,
                onCancelEdit = onCancelEdit
            )
        }
    ) { innerPadding ->
        ChatBodyContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = state,
            onOpenSettings = onOpenSettings,
            onSetupProvider = onSetupProvider,
            onOpenCompanion = onOpenCompanion,
            onRetryMessage = onRetryMessage,
            onShowVersion = onShowVersion,
            onCompare = onCompare,
            onBranch = onBranch,
            // Hidden during voice chat: that loop sends what you say, not taps.
            onQuickReply = if (voiceChatPhase == null) onQuickReply else null,
            onLoadModels = onLoadModels,
            onDraftChange = onDraftChange,
            onEditMessage = onEditMessage,
            onSetSystemPrompt = onSetSystemPrompt,
            onAskPcAction = onAskPcAction,
            onOpenProject = onOpenProject,
            speaker = speaker
        )
    }
}

@Composable
private fun ChatBodyContent(
    modifier: Modifier,
    speaker: MessageSpeaker,
    state: ChatUiState,
    onOpenSettings: () -> Unit,
    onSetupProvider: (ProviderPreset) -> Unit,
    onOpenCompanion: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onShowVersion: (messageId: String, target: Int) -> Unit,
    onCompare: (String) -> Unit,
    onBranch: (String) -> Unit,
    onQuickReply: ((String) -> Unit)?,
    onLoadModels: () -> Unit,
    onDraftChange: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onSetSystemPrompt: (conversationId: String, text: String) -> Unit,
    onAskPcAction: (messageId: String, action: AskPcCardAction) -> Unit,
    onOpenProject: (String) -> Unit = {}
) {
    val messages = state.activeConversation?.messages.orEmpty()
    // What the screen, copy, share and read-aloud use: replies without their hidden ```orbit-pc block.
    val shownMessages = remember(messages) { messages.map(::withoutAskPcBlock) }
    var showCompare by remember { mutableStateOf(false) }
    val canCompare = !state.isSending && CompareModelsPolicy.canCompare(messages)
    if (showCompare && canCompare) {
        val baseUrl = state.relayConfig.baseUrl
        CompareModelsDialog(
            // A list from another server's URL would offer models this one does not have.
            modelList = state.modelList.takeIf { it.isLoading || it.baseUrl == baseUrl } ?: ModelListUiState(),
            answered = CompareModelsPolicy.answeredModels(messages),
            onLoadModels = onLoadModels,
            onDismiss = { showCompare = false },
            onAsk = { model ->
                showCompare = false
                onCompare(model)
            }
        )
    }
    // Stop reading aloud when the user switches to another chat.
    LaunchedEffect(state.activeConversationId) { speaker.stop() }
    Column(modifier = modifier) {
        AnimatedVisibility(visible = state.isTemporaryChat, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            TemporaryChatBanner()
        }
        // An empty project chat names its project in the start screen's heading instead.
        val project = state.activeProject
        if (project != null && messages.isNotEmpty()) {
            ProjectChatBanner(name = project.name, onClick = { onOpenProject(project.id) })
        }
        // On an empty chat the start screen already offers "Connect a model"; only nag mid-conversation,
        // and not when the newest message's own error card already says the same thing with a Set up button.
        val lastSaysNotConnected = messages.lastOrNull()?.let {
            it.status == MessageStatus.FAILED && it.error?.category == ProviderErrorCategory.UNCONFIGURED
        } == true
        if (!state.readiness.configured && messages.isNotEmpty() && !lastSaysNotConnected) {
            NoticeCard(
                text = state.readiness.statusDetail,
                actionLabel = "Set up",
                onAction = onOpenSettings
            )
        }
        if (state.preflightWarnings.isNotEmpty()) {
            NoticeCard(text = state.preflightWarnings.joinToString("\n"))
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (messages.isEmpty()) {
            EmptyChatPlaceholder(
                modelId = state.relayConfig.modelId,
                configured = state.readiness.configured,
                companion = state.companion,
                workMode = state.readiness.workMode,
                onOpenSettings = onOpenSettings,
                onSetupProvider = onSetupProvider,
                onOpenCompanion = onOpenCompanion,
                onUsePrompt = { onDraftChange(PromptLibrary.insertIntoDraft(state.draftText, it)) },
                systemPrompt = state.activeConversation?.systemPrompt.orEmpty(),
                temporary = state.isTemporaryChat,
                project = state.activeProject,
                firstName = Personal.greetingName(state.personal),
                onPickPersona = state.activeConversationId?.let { id ->
                    { persona: Persona? -> onSetSystemPrompt(id, persona?.instructions.orEmpty()) }
                }
            )
        } else {
            val listState = rememberLazyListState()
            // Flipping a turn's versions ("< 2 / 3 >") is reading in place, so it must not jump to the bottom the
            // way a new message does. Plain holder, not state: setting it never needs a recomposition.
            val flippedAt = remember { longArrayOf(0L) }
            val justFlipped = { android.os.SystemClock.uptimeMillis() - flippedAt[0] < 1_000 }
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty() && !justFlipped()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
            // Follow a reply as it streams in or finishes (actions row appears), but only while the
            // latest message is still on screen so reading older messages is never interrupted.
            val last = messages.lastOrNull()
            LaunchedEffect(last?.id, last?.content?.length, last?.status) {
                if (last != null &&
                    !justFlipped() &&
                    listState.layoutInfo.visibleItemsInfo.any { it.index == messages.lastIndex }
                ) {
                    listState.scrollBy(100_000f) // clamped to the end of the content
                }
            }
            // "Jump to latest" appears once the newest message is scrolled out of view.
            val latestIndex = messages.lastIndex
            val latestOffScreen by remember(latestIndex) {
                derivedStateOf { listState.layoutInfo.visibleItemsInfo.none { it.index >= latestIndex } }
            }
            val jumpScope = rememberCoroutineScope()
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(shownMessages, key = { _, it -> it.id }) { index, message ->
                    val isLast = index == messages.lastIndex
                    // Parsed from the stored reply (the shown copy has the block removed), once it has finished.
                    val stored = messages[index]
                    val askPc = if (stored.role == MessageRole.ASSISTANT && stored.status == MessageStatus.COMPLETED) {
                        remember(stored.content) { AskPcProposals.parse(stored.content) }
                    } else {
                        null
                    }
                    // Landscape and tablets: keep lines at a readable length, centred, instead of
                    // stretching replies edge to edge. Phones in portrait are narrower than the cap.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                    MessageItem(
                        message = message,
                        isSending = state.isSending,
                        onRetry = { onRetryMessage(message.id) },
                        // Flipping waits while a reply is on its way or a message is being edited.
                        versionsEnabled = !state.isSending && state.editingMessageId == null,
                        onShowVersion = { target ->
                            flippedAt[0] = android.os.SystemClock.uptimeMillis()
                            onShowVersion(message.id, target)
                        },
                        canRegenerate = isLast &&
                            RegeneratePolicy.modeFor(messages, message.id) == RegeneratePolicy.Mode.REGENERATE,
                        modelLabel = if (CompareModelsPolicy.showsModelLabel(messages, index)) {
                            ModelDisplayName.of(message.modelId)
                        } else {
                            null
                        },
                        onCompare = if (isLast && canCompare) ({ showCompare = true }) else null,
                        // Not on compared answers: a branch's context never includes them. Not in a temporary
                        // chat either: a branch would be a saved copy of it.
                        onBranch = if (
                            !state.isTemporaryChat &&
                            message.role == MessageRole.ASSISTANT &&
                            message.status == MessageStatus.COMPLETED &&
                            !message.comparison
                        ) {
                            { onBranch(message.id) }
                        } else {
                            null
                        },
                        shareText = if (message.role == MessageRole.ASSISTANT) ShareAnswer.text(shownMessages, index) else null,
                        offerPdf = PdfMarkdown.offersPdf(shownMessages, index),
                        canEdit = EditResendPolicy.canEdit(messages, message.id, state.isSending),
                        isBeingEdited = message.id == state.editingMessageId,
                        onEdit = { onEditMessage(message.id) },
                        isSpeaking = speaker.speakingId == message.id,
                        onToggleSpeak = { speaker.toggle(message.id, PdfMarkdown.plainText(message.content)) },
                        onOpenSettings = onOpenSettings,
                        extra = askPc?.let { proposal ->
                            {
                                AskPcSuggestionCard(
                                    proposal = proposal,
                                    model = AskPcCard.model(state.companion, message.id),
                                    onAction = { action -> onAskPcAction(message.id, action) }
                                )
                            }
                        },
                        modifier = Modifier.widthIn(max = MESSAGE_COLUMN_MAX_WIDTH)
                    )
                    }
                }
                // One-tap follow-ups under the latest finished answer. Only with an empty composer, so a
                // tap never sends half-typed text, staged files or an edit in progress along with it.
                val latest = messages.last()
                val quickReplies = if (
                    onQuickReply != null &&
                    !state.isSending &&
                    latest.role == MessageRole.ASSISTANT &&
                    latest.status == MessageStatus.COMPLETED &&
                    !latest.comparison &&
                    state.draftText.isBlank() &&
                    state.stagedAttachments.isEmpty() &&
                    state.editingMessageId == null
                ) {
                    QuickReplies.suggest(AskPcProposals.displayText(latest.content))
                } else {
                    emptyList()
                }
                if (quickReplies.isNotEmpty() && onQuickReply != null) {
                    item(key = "quick-replies") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .animateItem()
                        ) {
                            quickReplies.forEach { reply ->
                                SuggestionChip(
                                    onClick = { onQuickReply(reply) },
                                    label = { Text(reply) }
                                )
                            }
                        }
                    }
                }
            }
            // Qualified: the enclosing Column would otherwise pick ColumnScope.AnimatedVisibility.
            androidx.compose.animation.AnimatedVisibility(
                visible = latestOffScreen,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        jumpScope.launch {
                            listState.animateScrollToItem(latestIndex)
                            listState.scrollBy(100_000f) // to the very end of a long answer and any reply chips
                        }
                    },
                    // A round button in the page colour with a thin outline, so it never reads as part of a
                    // message bubble it floats over (user bubbles use surfaceVariant).
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest")
                }
            }
            }
        }
    }
}

/** [message] as the screen shows it: an assistant reply without its ```orbit-pc block (same object if none). */
private fun withoutAskPcBlock(message: ChatMessage): ChatMessage {
    if (message.role != MessageRole.ASSISTANT) return message
    val shown = AskPcProposals.displayText(message.content)
    return if (shown == message.content) message else message.copy(content = shown)
}

/** Small rounded notice used for setup hints and preflight warnings instead of full-width coloured slabs. */
@Composable
private fun NoticeCard(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** System font scale above which side-by-side layouts switch to stacked ones. */
private const val LARGE_FONT_SCALE = 1.3f

/** Messages never stretch wider than this (landscape, tablets, foldables): ~80 characters per line. */
private val MESSAGE_COLUMN_MAX_WIDTH = 720.dp

/** Your own bubble stays a bubble on wide screens instead of becoming a full-width banner. */
private val USER_BUBBLE_MAX_WIDTH = 560.dp

/** Tappable first-message ideas on an empty chat: title + hint shown, prompt placed in the composer (never sent). */
private data class StarterPrompt(val title: String, val hint: String, val prompt: String, val icon: ImageVector)

private val STARTER_PROMPTS = listOf(
    StarterPrompt(
        "Explain something", "A concept, a word, or how something works",
        "Explain this in plain words: ", Icons.Outlined.Lightbulb
    ),
    StarterPrompt(
        "Help me write", "An email, a message, or a cover letter",
        "Help me write ", Icons.Outlined.EditNote
    ),
    StarterPrompt(
        "Look at a file", "Summarize, check or pull out the key points",
        "Read the file I'm attaching and pull out the key points.", Icons.Outlined.Description
    ),
    StarterPrompt(
        "Make a plan", "A trip, a study week, or a project",
        "Help me make a plan for ", Icons.Outlined.CalendarMonth
    )
)

private data class OnboardingChoice(
    val preset: ProviderPreset,
    val title: String,
    val detail: String,
    val icon: ImageVector
)

private val ONBOARDING_CHOICES = listOf(
    OnboardingChoice(ProviderPreset.LM_STUDIO, "On my PC with LM Studio", "Free and private. No key needed.", Icons.Outlined.Computer),
    OnboardingChoice(ProviderPreset.OLLAMA, "On my PC with Ollama", "Free and private. No key needed.", Icons.Outlined.Terminal),
    OnboardingChoice(
        ProviderPreset.GOOGLE_GEMINI, "Google Gemini", "Gemini models with your own AI Studio API key.",
        Icons.Outlined.AutoAwesome
    ),
    OnboardingChoice(ProviderPreset.CUSTOM, "Another service", "Any OpenAI-compatible URL and key.", Icons.Outlined.Cloud)
)

/**
 * The tappable cards on the start screen (docs/ORBIT_DESIGN.md, "Empty state"): `surface` with a hairline,
 * radius 14, a small accent icon, a title and a quiet hint. [stacked] puts the icon above the text for the
 * two-per-row idea cards, so titles get the whole card width on narrow phones.
 */
@Composable
private fun StartCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val orbit = OrbitTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = orbit.surface,
        contentColor = orbit.text,
        border = BorderStroke(1.dp, orbit.line),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        val text: @Composable () -> Unit = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = orbit.text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = orbit.muted
                )
            }
        }
        if (stacked) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                Icon(icon, contentDescription = null, tint = orbit.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(10.dp))
                text()
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = orbit.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(14.dp))
                text()
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder(
    modelId: String,
    configured: Boolean,
    companion: CompanionUiState,
    workMode: VerificationState,
    onOpenSettings: () -> Unit,
    onSetupProvider: (ProviderPreset) -> Unit,
    onOpenCompanion: () -> Unit,
    onUsePrompt: (String) -> Unit,
    systemPrompt: String = "",
    /** A temporary chat: the heading says so and the line under it explains what that means. */
    temporary: Boolean = false,
    /** A new chat in a project: the heading is the project's name and the line under it says what it uses. */
    project: Project? = null,
    /** From Settings → Personal, for "Good evening, Sam". */
    firstName: String? = null,
    onPickPersona: ((Persona?) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val personaStore = remember(context) {
        CustomPersonaStore(File(context.filesDir, "hydrogen_personas.json"))
    }
    var customPersonas by remember { mutableStateOf(emptyList<Persona>()) }
    var showImport by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    LaunchedEffect(personaStore) {
        customPersonas = withContext(Dispatchers.IO) { personaStore.load() }
    }
    val updateCustomPersonas: (List<Persona>) -> Unit = { updated ->
        customPersonas = updated
        scope.launch(Dispatchers.IO) { runCatching { personaStore.save(updated) } }
    }
    val persona = Personas.matching(systemPrompt, customPersonas)
    val customInstructions = Personas.hasCustomInstructions(systemPrompt, customPersonas)
    if (showImport && onPickPersona != null) {
        ImportPersonaDialog(
            onDismiss = { showImport = false },
            onImport = { shared ->
                val updated = CustomPersonaLibrary.add(
                    customPersonas, shared, CustomPersonaLibrary.ID_PREFIX + java.util.UUID.randomUUID()
                )
                if (updated !== customPersonas) updateCustomPersonas(updated)
                val imported = Personas.matching(shared.instructions, updated)
                showImport = false
                if (imported != null) {
                    onPickPersona(imported)
                    Toast.makeText(context, "Imported ${imported.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    if (showEditor && onPickPersona != null) {
        val editing = editingPersona
        PersonaEditorDialog(
            editing = editing,
            onDismiss = { showEditor = false },
            onImportInstead = if (editing == null) ({ showEditor = false; showImport = true }) else null,
            onSave = { shared ->
                val id = editing?.id ?: (CustomPersonaLibrary.ID_PREFIX + java.util.UUID.randomUUID())
                val result = if (editing == null) CustomPersonaLibrary.create(customPersonas, shared, id)
                else CustomPersonaLibrary.update(customPersonas, id, shared)
                result.fold(
                    onSuccess = { updated ->
                        updateCustomPersonas(updated)
                        showEditor = false
                        // Select it so this chat uses the new (or edited) instructions right away.
                        updated.firstOrNull { it.id == id }?.let(onPickPersona)
                        Toast.makeText(
                            context, if (editing == null) "Created ${shared.name}" else "Saved ${shared.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        null
                    },
                    onFailure = { it.message ?: "Couldn't save this assistant." }
                )
            }
        )
    }
    val workSummary = when {
        workMode == VerificationState.PASS ->
            "PC paired (${companion.session?.deviceName ?: "PC"}), Work mode on."
        companion.isPaired && workMode == VerificationState.FAIL ->
            "PC paired, Work mode off."
        companion.isPaired -> "PC paired."
        else -> ""
    }
    val orbit = OrbitTheme.colors
    val now = rememberLocalMinute()
    val subline = rememberSaveable { StartGreeting.SUBLINES.random() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spare height splits 2:3 above and below, so the greeting sits about 40% down rather than dead centre.
        // When the content is taller than the screen both spacers shrink to nothing and the page scrolls.
        Spacer(Modifier.weight(2f))
        HorizonArc(time = now)
        Spacer(Modifier.height(18.dp))
        Text(
            text = when {
                !configured -> "Welcome to Orbit"
                temporary -> TemporaryChatCopy.TITLE
                project != null -> project.name
                else -> StartGreeting.greeting(now.hour, firstName)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = orbit.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(6.dp))
        Crossfade(targetState = if (configured) persona else null, label = "start-subline") { shown ->
            Text(
                text = when {
                    !configured -> "Where should your AI run? Pick one to get started."
                    temporary -> TemporaryChatCopy.START_LINE
                    shown != null -> shown.greeting
                    customInstructions -> "Your own chat instructions are on."
                    project != null -> ProjectCopy.startLine(project)
                    else -> subline
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = orbit.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (configured) {
            // A persona never overwrites instructions the user wrote; those are edited in Chat instructions.
            if (onPickPersona != null && !customInstructions) {
                Spacer(Modifier.height(18.dp))
                PersonaPicker(
                    active = persona,
                    onPick = onPickPersona,
                    modifier = Modifier.bleedHorizontally(20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    custom = customPersonas,
                    onCreate = { editingPersona = null; showEditor = true }
                )
                if (persona != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { sharePersona(context, persona) }) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share ${persona.name}", style = MaterialTheme.typography.labelMedium)
                        }
                        if (CustomPersonaLibrary.isCustom(persona.id)) {
                            TextButton(onClick = { editingPersona = persona; showEditor = true }) {
                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = {
                                updateCustomPersonas(CustomPersonaLibrary.remove(customPersonas, persona.id))
                                onPickPersona(null)
                            }) {
                                Text("Remove", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (!configured) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                ONBOARDING_CHOICES.forEach { choice ->
                    StartCard(
                        icon = choice.icon,
                        title = choice.title,
                        detail = choice.detail,
                        onClick = { onSetupProvider(choice.preset) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Then load the models, pick one and say hi. No account needed, and keys stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = orbit.muted,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onOpenSettings) { Text("All providers and settings") }
        } else {
            // One card per row with large system fonts so titles are not cut off.
            val perRow = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 1 else 2
            // A persona brings its own ideas; plain Hydrogen and custom instructions keep the general ones.
            Crossfade(targetState = persona, label = "persona-starters") { shown ->
                val starters = shown?.starters?.takeIf { it.isNotEmpty() }
                    ?.map { StarterPrompt(it.title, it.hint, it.prompt, starterIcon(it.icon)) }
                    ?: STARTER_PROMPTS
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                ) {
                    starters.chunked(perRow).forEach { pair ->
                        // Equal-height cards in each row, however the text wraps.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.height(IntrinsicSize.Min)
                        ) {
                            pair.forEach { starter ->
                                StartCard(
                                    icon = starter.icon,
                                    title = starter.title,
                                    detail = starter.hint,
                                    onClick = { onUsePrompt(starter.prompt) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    stacked = true
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(3f))
        Spacer(Modifier.height(28.dp))
        Text(
            text = (if (configured) "${ModelDisplayName.friendly(modelId)} · " else "") +
                "Chats stay on this phone. $workSummary".trim(),
            style = MaterialTheme.typography.labelSmall,
            color = orbit.muted,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onOpenCompanion) {
            Text(
                text = if (companion.isPaired) "PC Companion" else "Pair your Windows PC (optional)",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Opens the share sheet with a one-line persona code anyone can paste into Hydrogen's Import. */
private fun sharePersona(context: Context, persona: Persona) {
    val code = runCatching { PersonaShareCodec.encode(PersonaShareCodec.fromPersona(persona)) }.getOrNull() ?: return
    val text = "Try my Orbit AI persona \"${persona.name}\": open Orbit, tap Create on the new-chat " +
        "screen, then \"Import an assistant\", and paste this whole message.\n\n$code"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Orbit persona: ${persona.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share ${persona.name}")) }
}

/** Opens the share sheet with a one-line prompt-pack code (up to 20 saved prompts) anyone can import into Orbit. */
private fun sharePromptPack(context: Context, prompts: List<SavedPrompt>) {
    val shared = PromptPackCodec.fromSaved(prompts).take(PromptPackCodec.MAX_PROMPTS)
    if (shared.isEmpty()) return
    val code = PromptPackCodec.encode(shared)
    val text = "Here are ${shared.size} prompts for Orbit: open Saved prompts, tap \"Import pack\" and paste " +
        "this whole message.\n\n$code"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Orbit prompt pack")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share prompts")) }
}

/** Paste a prompt-pack code (or a whole message containing one); shows why a code cannot be read. */
@Composable
private fun ImportPromptPackDialog(onDismiss: () -> Unit, onImport: (List<PromptPackCodec.SharedPrompt>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val preview = remember(text) { PromptPackCodec.decode(text).getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a prompt pack") },
        text = {
            Column {
                Text(
                    "Paste an Orbit prompt pack someone shared with you. Prompts are only text added to your " +
                        "Saved prompts; nothing is sent or run.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text("orbit-prompts:v1:…") },
                    isError = error != null,
                    supportingText = (error ?: preview?.let { "${it.size} ${if (it.size == 1) "prompt" else "prompts"}: " + it.joinToString { p -> p.title } })
                        ?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    PromptPackCodec.decode(text)
                        .onSuccess(onImport)
                        .onFailure { error = it.message ?: "This code can't be read." }
                }
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Paste a persona code (or a whole message containing one); shows why a code cannot be read. */
@Composable
private fun ImportPersonaDialog(onDismiss: () -> Unit, onImport: (SharedPersona) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Face, contentDescription = null) },
        title = { Text("Import a persona") },
        text = {
            Column {
                Text(
                    "Paste an Orbit persona code someone shared with you. It only sets this chat's " +
                        "instructions; it can't run anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text("orbit-persona:v1:…") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    PersonaShareCodec.decode(text)
                        .onSuccess(onImport)
                        .onFailure { error = it.message ?: "This code can't be read." }
                }
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Chat-app style message: the user's turn is a brand-gradient bubble on the right, the model's reply is
 * flat full-width text (no bubble, no label) with quiet icon actions underneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: ChatMessage,
    isSending: Boolean,
    onRetry: () -> Unit,
    versionsEnabled: Boolean = false,
    onShowVersion: (Int) -> Unit = {},
    canRegenerate: Boolean = false,
    canEdit: Boolean = false,
    isBeingEdited: Boolean = false,
    onEdit: () -> Unit = {},
    isSpeaking: Boolean = false,
    onToggleSpeak: () -> Unit = {},
    modelLabel: String? = null,
    onCompare: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    shareText: String? = null,
    /** The user asked for a PDF and the reply has none: offer to make one from it. */
    offerPdf: Boolean = false,
    onOpenSettings: () -> Unit = {},
    /** Shown between an assistant reply and its action row (the "Ask your PC" card). */
    extra: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (message.role == MessageRole.USER) {
        val context = LocalContext.current
        val haptics = LocalHapticFeedback.current
        var menuOpen by remember { mutableStateOf(false) }
        val orbit = OrbitTheme.colors
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 64.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MessageAttachments(message)
            if (message.content.isNotBlank()) {
                Box {
                    // Your own words sit in a soft bubble with one sharp corner toward you; tap or long-press for
                    // Copy / Edit (docs/ORBIT_DESIGN.md, "Messages").
                    Box(
                        modifier = Modifier
                            .widthIn(max = USER_BUBBLE_MAX_WIDTH)
                            .alpha(if (isBeingEdited) 0.45f else 1f)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(orbit.bubble)
                            .combinedClickable(
                                onClickLabel = "Message options",
                                onLongClickLabel = "Message options",
                                onClick = { menuOpen = true },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = true
                                }
                            )
                            .semantics {
                                // "You, 2 minutes ago: …" — the whole text, since this node replaces it for TalkBack.
                                contentDescription = MessageA11y.label(
                                    role = message.role,
                                    content = message.content,
                                    createdAtMs = message.createdAtEpochMs,
                                    nowMs = System.currentTimeMillis(),
                                    attachmentCount = message.attachments.size,
                                    maxBodyChars = Int.MAX_VALUE
                                )
                            }
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = orbit.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("Orbit message", message.content))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text("Edit and resend") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                        }
                    }
                }
            }
            VersionSwitcher(message, versionsEnabled, onShowVersion)
            if (isBeingEdited) {
                Text(
                    text = "Editing below",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (message.status == MessageStatus.FAILED || message.status == MessageStatus.CANCELLED) {
                MessageStatusContent(message, isSending, onRetry, showBody = false, onOpenSettings = onOpenSettings)
            }
        }
    } else {
        // A finished reply is one TalkBack stop: "Orbit, 2 minutes ago, 1 code block: …". Buttons inside
        // (copy, speak, links' own controls) stay separate stops. Failed and streaming messages keep
        // their own readable error / progress text instead.
        val announceAsOne = message.content.isNotBlank() &&
            (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.CANCELLED)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .then(
                    if (announceAsOne) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = (modelLabel?.let { "$it. " } ?: "") + MessageA11y.label(
                                role = message.role,
                                content = message.content,
                                createdAtMs = message.createdAtEpochMs,
                                nowMs = System.currentTimeMillis(),
                                attachmentCount = message.attachments.size
                            ) + if (message.status == MessageStatus.CANCELLED) ". Stopped before it finished." else ""
                        }
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val working = message.status == MessageStatus.DISPATCHED || message.status == MessageStatus.STREAMING
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReplyHeader(
                    // Compared answers pass their own label; everything else names the model that wrote it.
                    name = modelLabel ?: ModelDisplayName.friendly(message.modelId),
                    working = working,
                    // While words are arriving the spinning mark is the only sign of life, so it speaks for itself.
                    // Before the first word, the "Thinking" line below says it instead.
                    spokenState = if (working && message.content.isNotBlank()) "Still writing" else null,
                    modifier = Modifier.weight(1f)
                )
                // A regenerated reply's versions sit at the end of its header line: the action row under the
                // newest reply is already full on a phone.
                VersionSwitcher(message, versionsEnabled, onShowVersion)
            }
            MessageAttachments(message)
            MessageStatusContent(message, isSending, onRetry, showBody = true, onOpenSettings = onOpenSettings)
            extra?.invoke()
            if (offerPdf) MakePdfOffer(PdfMarkdown.plainText(message.content))
            MessageActions(message, isSending, onRetry, canRegenerate, isSpeaking, onToggleSpeak, onCompare, onBranch, shareText)
        }
    }
}

/**
 * Above each reply: the Orbit mark and, in small quiet type, the model that wrote it. The mark turns while the
 * reply is on its way (docs/ORBIT_DESIGN.md, "Messages"). Screen readers skip it unless [spokenState] is set,
 * since a finished reply already announces its model as part of the message.
 */
@Composable
private fun ReplyHeader(name: String, working: Boolean, spokenState: String? = null, modifier: Modifier = Modifier) {
    val orbit = OrbitTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            if (spokenState != null) contentDescription = spokenState
        }
    ) {
        OrbitIndicator(spinning = working, size = 22.dp, ringColor = orbit.muted, dotColor = orbit.accent)
        if (name.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = orbit.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "< 2 / 3 >" for a turn you edited or regenerated (docs/ORBIT_DESIGN.md, "Branches"). Nothing at all for the
 * usual single version. Arrows are 24 dp in 40 dp targets, the count 12 sp in the faint ink. The count speaks as
 * "Version 2 of 3"; the arrows stay their own stops so they work inside a reply that TalkBack reads as one.
 */
@Composable
private fun VersionSwitcher(message: ChatMessage, enabled: Boolean, onShowVersion: (Int) -> Unit) {
    val count = MessageVersions.count(message)
    if (count < 2) return
    val position = MessageVersions.position(message)
    val orbit = OrbitTheme.colors
    val haptics = LocalHapticFeedback.current
    @Composable
    fun Arrow(target: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
        val active = enabled && target in 0 until count
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShowVersion(target)
            },
            enabled = active,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (active) orbit.muted else orbit.faint.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(position - 1, Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous version")
        Text(
            text = "${position + 1} / $count",
            fontSize = 12.sp,
            color = orbit.faint,
            modifier = Modifier.semantics { contentDescription = "Version ${position + 1} of $count" }
        )
        Arrow(position + 1, Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next version")
    }
}

/**
 * The thought line (docs/ORBIT_DESIGN.md, "Thought line"): a thinking model's reasoning folded into one quiet line
 * above the answer. The small orbit turns while it thinks. Tapping opens the thoughts beside a thin rule, at most
 * 320 dp tall with their own scroll, so a long chain of thought never pushes the answer off screen.
 */
@Composable
private fun ThoughtLine(messageId: String, thinking: String, live: Boolean, thinkingMs: Long?) {
    val orbit = OrbitTheme.colors
    var open by rememberSaveable(messageId) { mutableStateOf(false) }
    val label = com.hydrogen.ai.model.ThoughtLineText.label(live = live, thinkingMs = thinkingMs)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide thinking" else "Show thinking") {
                    open = !open
                }
                .heightIn(min = 36.dp)
                .padding(end = 6.dp)
                .semantics { stateDescription = if (open) "Shown" else "Hidden" }
        ) {
            OrbitIndicator(spinning = live, size = 14.dp, ringColor = orbit.muted, dotColor = orbit.accent)
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = orbit.muted)
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = orbit.muted,
                modifier = Modifier.padding(start = 2.dp).size(18.dp)
            )
        }
        if (open) {
            val rule = orbit.line
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .heightIn(max = 320.dp)
                    // The 2 dp rule is drawn, not laid out, so the scrolling text needs no intrinsic measuring.
                    .drawBehind {
                        drawRect(color = rule, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                    }
                    .padding(start = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = thinking.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = orbit.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageAttachments(message: ChatMessage) {
    if (message.attachments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttachmentThumbnail(
                        localUri = attachment.localUri,
                        contentDescription = attachment.displayName,
                        maxDimensionPx = 256,
                        modifier = Modifier.size(48.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = attachment.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        Text(
                            text = formatBytes(attachment.sizeBytes) +
                                if (attachment.omittedFromRemote) " · Kept on device" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageStatusContent(
    message: ChatMessage,
    isSending: Boolean,
    onRetry: () -> Unit,
    showBody: Boolean,
    onOpenSettings: () -> Unit = {}
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (message.status) {
        MessageStatus.DISPATCHED, MessageStatus.STREAMING -> {
            val thinking = message.turnMetadata?.thinkingSummary?.takeIf { it.isNotBlank() }
            if (showBody && thinking != null) {
                // A thinking model: its thoughts arrive first, on the thought line, which says "Thinking" until
                // the answer's first word and "Thought for N seconds" after.
                ThoughtLine(
                    messageId = message.id,
                    thinking = thinking,
                    live = message.content.isBlank(),
                    thinkingMs = message.thinkingMs
                )
            }
            if (showBody && message.content.isNotBlank()) {
                MessageBody(message)
            }
            // Before the first word: the orbit turning plus "Thinking". Once words arrive, the mark in the reply
            // header keeps turning instead, so the text isn't followed by a second spinner.
            if (message.content.isBlank() && (thinking == null || !showBody)) {
                TypingIndicator(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        MessageStatus.CANCELLED -> {
            val thinking = message.turnMetadata?.thinkingSummary?.takeIf { it.isNotBlank() }
            if (showBody && thinking != null) {
                ThoughtLine(messageId = message.id, thinking = thinking, live = false, thinkingMs = message.thinkingMs)
            }
            if (showBody && message.content.isNotBlank()) {
                MessageBody(message)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stopped",
                    style = MaterialTheme.typography.labelMedium,
                    color = OrbitTheme.colors.faint
                )
                if (!message.hasSideEffectsInProgress) {
                    TextButton(onClick = onRetry, enabled = !isSending) { Text("Retry") }
                }
            }
        }
        MessageStatus.FAILED -> {
            if (showBody && message.content.isNotBlank()) {
                MessageBody(message)
            }
            val err = message.error
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val onError = MaterialTheme.colorScheme.onErrorContainer
                    Text(
                        text = err?.category?.title ?: "Something went wrong",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onError,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = err?.userMessage?.takeIf { it.isNotBlank() }
                            ?: "Something went wrong. Your message is saved; tap Retry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onError
                    )
                    // Technical detail (HTTP code, server body, exception) stays available for bug
                    // reports but is folded away so the headline reads in plain language.
                    val detail = listOfNotNull(
                        err?.httpStatus?.let { "HTTP $it" },
                        err?.technicalDetail?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    var showDetail by remember(message.id) { mutableStateOf(false) }
                    // Step-by-step help for this kind of failure, folded away with the raw detail.
                    val steps = remember(err) { err?.let { com.hydrogen.ai.model.ErrorExplainer.steps(it) }.orEmpty() }
                    if (showDetail) {
                        steps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = onError
                            )
                        }
                        if (detail.isNotEmpty()) {
                            SelectionContainer {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onError.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                    val needsSetup = err != null && (
                        err.category == ProviderErrorCategory.UNCONFIGURED ||
                            err.category == ProviderErrorCategory.UNAUTHORIZED_RELAY_401 ||
                            err.category == ProviderErrorCategory.UNAUTHORIZED_UPSTREAM_401 ||
                            err.category == ProviderErrorCategory.OUT_OF_CREDITS ||
                            err.category == ProviderErrorCategory.MODEL_NOT_FOUND ||
                            err.category == ProviderErrorCategory.REGION_UNAVAILABLE ||
                            err.sourceLayer == "local_model"
                        )
                    val cardContext = LocalContext.current
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (needsSetup) {
                            TextButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set up", color = onError)
                            }
                        }
                        if (!message.hasSideEffectsInProgress) {
                            TextButton(onClick = onRetry, enabled = !isSending) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry", color = onError)
                            }
                        }
                        if (detail.isNotEmpty() || steps.isNotEmpty()) {
                            TextButton(
                                onClick = { showDetail = !showDetail },
                                modifier = Modifier.semantics {
                                    stateDescription = if (showDetail) "Shown" else "Hidden"
                                }
                            ) {
                                Text(if (showDetail) "Hide help" else "How to fix", color = onError.copy(alpha = 0.85f))
                            }
                        }
                        if (showDetail && err != null) {
                            TextButton(onClick = {
                                // Keys are already hidden in the detail; the copy holds no chat text.
                                val report = buildString {
                                    appendLine("Orbit ${com.hydrogen.ai.BuildConfig.VERSION_NAME}: ${err.category.title}")
                                    appendLine(err.userMessage)
                                    if (detail.isNotEmpty()) appendLine(detail)
                                }.trimEnd()
                                cardContext.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("Orbit error", report))
                                Toast.makeText(cardContext, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy details", color = onError.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }
        }
        else -> {
            val meta = message.turnMetadata
            val thinking = meta?.thinkingSummary?.takeIf { it.isNotBlank() }
            if (showBody && thinking != null) {
                ThoughtLine(messageId = message.id, thinking = thinking, live = false, thinkingMs = message.thinkingMs)
            }
            if (showBody && message.content.isNotBlank()) {
                MessageBody(message)
            }
            if (showBody && meta != null && (meta.toolCalls.isNotEmpty() || meta.isTruncated)) {
                val metaNotes = buildList {
                    if (meta.toolCalls.isNotEmpty()) {
                        add("Tools requested: ${meta.toolCalls.joinToString(", ") { it.name }}")
                    }
                    if (meta.isTruncated) {
                        add("Truncated (${meta.finishReason ?: "max tokens"})")
                    }
                }.joinToString(" · ")
                Text(text = metaNotes, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/** Quiet icon row under a finished reply: copy, and regenerate on the latest answer. */
@Composable
private fun MessageActions(
    message: ChatMessage,
    isSending: Boolean,
    onRetry: () -> Unit,
    canRegenerate: Boolean,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onCompare: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    shareText: String? = null
) {
    if (message.status == MessageStatus.DISPATCHED || message.status == MessageStatus.STREAMING) return
    val copyText = ConversationMarkdownExporter.messageText(message)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tint = OrbitTheme.colors.muted
    Row(modifier = Modifier.offset(x = (-8).dp)) {
        if (copyText.isNotEmpty()) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Orbit message", copyText))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                // 48dp: the accessibility minimum touch target (36dp overlapped its neighbour).
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (shareText != null) {
            IconButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, "Share answer")) }
                        .onFailure { Toast.makeText(context, "No app can share text", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (message.role == MessageRole.ASSISTANT && message.content.isNotBlank()) {
            // ORBIT_DESIGN "Read aloud": the button becomes Stop while speaking. The 32 dp accent-soft disc marks
            // the one reply being read; the 48 dp button around it keeps the touch target.
            IconButton(onClick = onToggleSpeak, modifier = Modifier.size(48.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaking) OrbitTheme.colors.accentSoft else Color.Transparent)
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                        tint = if (isSpeaking) OrbitTheme.colors.accent else tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (canRegenerate) {
            IconButton(onClick = onRetry, enabled = !isSending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Try again", tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (onCompare != null) {
            IconButton(onClick = onCompare, enabled = !isSending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Compare, contentDescription = "Ask another model", tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (onBranch != null) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBranch()
                    Toast.makeText(context, "Branched into a new chat", Toast.LENGTH_SHORT).show()
                },
                enabled = !isSending,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.AltRoute, contentDescription = "Branch from here", tint = tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * User text is shown verbatim; model replies are rendered as Markdown with copyable code blocks, and a document the
 * model wrote between `<pdf>` tags becomes a PDF card.
 */
@Composable
private fun MessageBody(message: ChatMessage) {
    if (message.role == MessageRole.USER) {
        Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
    } else if (!PdfMarkdown.hasDocument(message.content)) {
        MarkdownMessage(content = message.content)
    } else {
        val streaming = message.status == MessageStatus.DISPATCHED || message.status == MessageStatus.STREAMING
        val parts = remember(message.content) { PdfMarkdown.splitReply(message.content) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            parts.forEach { part ->
                when (part) {
                    is ReplyPart.Text -> MarkdownMessage(content = part.markdown)
                    is ReplyPart.Document -> DocumentCard(document = part, streaming = streaming)
                }
            }
        }
    }
}

/**
 * Orbit's composer (docs/ORBIT_DESIGN.md, "Composer"): one raised card with the message on top and a toolbar
 * underneath. Toolbar: + (photos, camera, files, saved prompts) on the left; dictation mic and the sun button on
 * the right. While dictating, the words appear in the card as they are heard and the toolbar becomes the
 * recording bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerBar(
    maxImagesPerRequest: Int,
    draftText: String,
    stagedAttachments: List<AttachmentManifestItem>,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onTakePhoto: (() -> Unit)?,
    onPickFile: () -> Unit,
    voiceAvailable: Boolean,
    onVoiceInput: () -> Unit,
    composerFocus: FocusRequester,
    onRemoveAttachment: (String) -> Unit,
    onClearAttachments: () -> Unit,
    onSend: () -> Unit,
    onCancelSend: () -> Unit,
    isEditing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    contextUsage: ContextUsage? = null,
    onStartVoiceChat: (() -> Unit)? = null,
    dictation: InAppSpeech? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val orbit = OrbitTheme.colors
    val keyboardController = LocalSoftwareKeyboardController.current
    val promptStore = remember(context) {
        PromptLibraryStore(File(context.filesDir, "hydrogen_prompts.json"))
    }
    var showPrompts by remember { mutableStateOf(false) }
    var fillingTemplate by remember { mutableStateOf<SavedPrompt?>(null) }
    var importingPack by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var prompts by remember { mutableStateOf(PromptLibrary.starterPrompts) }
    LaunchedEffect(promptStore) {
        prompts = withContext(Dispatchers.IO) { promptStore.load() }
    }
    val updatePrompts: (List<SavedPrompt>) -> Unit = { updated ->
        prompts = updated
        scope.launch(Dispatchers.IO) {
            runCatching { promptStore.save(updated) }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(orbit.bg)
            .navigationBarsPadding()
            .imePadding()
            // Wide screens: keep the composer lined up with the centred message column.
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MESSAGE_COLUMN_MAX_WIDTH + 24.dp)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContextMeterRow(usage = contextUsage)
        if (stagedAttachments.isNotEmpty()) {
            // The tray (docs/ORBIT_DESIGN.md, Attachments): 56 dp photos with a remove button on the corner. The
            // count only shows when it is over what the model takes in one message.
            val overLimit = stagedAttachments.size > maxImagesPerRequest
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(stagedAttachments, key = { it.id }) { item ->
                        StagedPhoto(
                            item = item,
                            enabled = !isSending,
                            onRemove = { onRemoveAttachment(item.id) }
                        )
                    }
                }
                if (stagedAttachments.size > 1) {
                    TextButton(
                        onClick = onClearAttachments,
                        enabled = !isSending,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Clear all", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (overLimit) {
                Text(
                    text = "${stagedAttachments.size} of $maxImagesPerRequest attachments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        if (showPrompts) {
            PromptLibraryDialog(
                prompts = prompts,
                canSaveDraft = draftText.isNotBlank(),
                onInsert = { prompt ->
                    if (PromptTemplate.variables(prompt.text).isEmpty()) {
                        onDraftChange(PromptLibrary.insertIntoDraft(draftText, prompt.text))
                    } else {
                        fillingTemplate = prompt
                    }
                    showPrompts = false
                },
                onSaveDraft = {
                    updatePrompts(
                        PromptLibrary.add(prompts, draftText, id = "p-${System.currentTimeMillis()}")
                    )
                },
                onDelete = { prompt -> updatePrompts(PromptLibrary.remove(prompts, prompt.id)) },
                onAddExamples = { updatePrompts(PromptTemplate.withExamples(prompts, "ex-${System.currentTimeMillis()}")) },
                onShare = { sharePromptPack(context, prompts) },
                onImport = { importingPack = true },
                onDismiss = { showPrompts = false }
            )
        }
        if (importingPack) {
            ImportPromptPackDialog(
                onDismiss = { importingPack = false },
                onImport = { shared ->
                    val (merged, _) = PromptPackCodec.mergeInto(prompts, shared, "imp-${System.currentTimeMillis()}")
                    updatePrompts(merged)
                    importingPack = false
                }
            )
        }
        fillingTemplate?.let { template ->
            PromptTemplateDialog(
                prompt = template,
                onDismiss = { fillingTemplate = null },
                onUse = { filled ->
                    onDraftChange(PromptLibrary.insertIntoDraft(draftText, filled))
                    fillingTemplate = null
                }
            )
        }
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Editing message. You can switch back to this version later.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancelEdit) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        val cardShape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(orbit.raised)
                .border(1.dp, orbit.line, cardShape)
                .padding(top = 12.dp, bottom = 6.dp)
        ) {
            val fieldStyle = MaterialTheme.typography.bodyLarge.copy(color = orbit.text)
            val fieldModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
            if (dictation != null) {
                // Read-only while listening: what you already typed, the words heard so far, and the guess for the
                // word being said now in a softer color. Done keeps it all; X throws the new words away.
                val heard = buildAnnotatedString {
                    val committed = dictation.committed.trim()
                    val partial = dictation.partial.trim()
                    val before = draftText.trimEnd()
                    if (before.isNotEmpty()) append(before)
                    if (committed.isNotEmpty()) {
                        if (length > 0) append(' ')
                        append(committed)
                    }
                    if (partial.isNotEmpty()) {
                        if (length > 0) append(' ')
                        withStyle(SpanStyle(color = orbit.muted)) { append(partial) }
                    }
                }
                val scroll = rememberScrollState()
                LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
                Box(
                    fieldModifier
                        .heightIn(min = 24.dp, max = 168.dp)
                        .verticalScroll(scroll)
                ) {
                    if (heard.isEmpty()) {
                        Text("Listening", style = fieldStyle.copy(color = orbit.faint))
                    } else {
                        Text(heard, style = fieldStyle)
                    }
                }
            } else {
                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(orbit.accent),
                    maxLines = 6,
                    modifier = fieldModifier.focusRequester(composerFocus),
                    decorationBox = { inner ->
                        Box {
                            if (draftText.isEmpty()) {
                                Text(text = "Message Orbit", style = fieldStyle.copy(color = orbit.faint))
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            if (dictation != null) {
                DictationToolbar(
                    speech = dictation,
                    onCancel = dictation::cancel,
                    onDone = dictation::finish,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(orbit.surface2)
                            .clickable(
                                enabled = !isSending,
                                role = Role.Button,
                                onClickLabel = "Open the attach menu"
                            ) { showAttachMenu = true }
                            .semantics { contentDescription = "Add photos and files" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = if (isSending) orbit.faint else orbit.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                            onClick = { showAttachMenu = false; onPickImages() }
                        )
                        if (onTakePhoto != null) {
                            DropdownMenuItem(
                                text = { Text("Camera") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = { showAttachMenu = false; onTakePhoto() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("File (text, code, PDF)") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = { showAttachMenu = false; onPickFile() }
                        )
                        DropdownMenuItem(
                            text = { Text("Saved prompts") },
                            leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                            onClick = { showAttachMenu = false; showPrompts = true }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (voiceAvailable && !isSending) {
                    IconButton(onClick = {
                        keyboardController?.hide()
                        onVoiceInput()
                    }) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Dictate", tint = orbit.muted)
                    }
                }
                when {
                    isSending -> SunButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = "Stop response",
                        onClick = onCancelSend,
                        stopping = true
                    )
                    onStartVoiceChat != null && VoiceInputPolicy.showMicInsteadOfSend(
                        voiceAvailable, isSending, draftText, stagedAttachments.isNotEmpty()
                    ) -> SunButton(
                        icon = Icons.Rounded.GraphicEq,
                        contentDescription = "Start voice chat",
                        onClick = {
                            keyboardController?.hide()
                            onStartVoiceChat()
                        }
                    )
                    else -> SunButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = if (isEditing) "Send edited message" else "Send message",
                        enabled = draftText.isNotBlank() || stagedAttachments.isNotEmpty(),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        }
                    )
                }
            }
        }
        // The reminder is for a glance at an empty screen; it steps aside while the keyboard is up.
        if (!WindowInsets.isImeVisible) {
            Text(
                text = "Answers can be wrong. Double-check anything important.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = orbit.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PromptLibraryDialog(
    prompts: List<SavedPrompt>,
    canSaveDraft: Boolean,
    onInsert: (SavedPrompt) -> Unit,
    onSaveDraft: () -> Unit,
    onDelete: (SavedPrompt) -> Unit,
    onDismiss: () -> Unit,
    onAddExamples: () -> Unit = {},
    onShare: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved prompts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tap a prompt to add it to your message. Write {{topic}} in a saved prompt to " +
                        "fill in that blank each time. Stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (prompts.isEmpty()) {
                    Text("No saved prompts yet.", style = MaterialTheme.typography.bodyMedium)
                }
                val missingExamples = PromptTemplate.EXAMPLES.any { ex -> prompts.none { it.text.trim() == ex.trim() } }
                if (missingExamples) {
                    TextButton(onClick = onAddExamples) { Text("Add fill-in-the-blank templates") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onShare, enabled = prompts.isNotEmpty()) {
                        Text(if (prompts.size > PromptPackCodec.MAX_PROMPTS) "Share first ${PromptPackCodec.MAX_PROMPTS}" else "Share all")
                    }
                    TextButton(onClick = onImport) { Text("Import pack") }
                }
                val listState = rememberLazyListState()
                // A newly saved prompt goes to the top; keep it in view instead of keeping the old scroll anchor.
                LaunchedEffect(prompts.firstOrNull()?.id) { listState.scrollToItem(0) }
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                    items(prompts, key = { it.id }) { prompt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onInsert(prompt) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = prompt.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(onClick = { onDelete(prompt) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete prompt ${prompt.title}"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveDraft, enabled = canSaveDraft) {
                Text("Save current message")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun readinessSummary(readiness: ProviderReadiness): String = when {
    !readiness.configured -> "Setup Needed"
    readiness.textGeneration == VerificationState.PASS -> "Verified Chat"
    readiness.authenticated == VerificationState.FAIL -> "Auth Failed"
    readiness.relayReachable == VerificationState.PASS -> "Relay Ready"
    readiness.relayReachable == VerificationState.FAIL -> "Relay Unreachable"
    else -> "Untested"
}

@Composable
private fun readinessColor(readiness: ProviderReadiness) = when {
    readiness.textGeneration == VerificationState.PASS -> MaterialTheme.colorScheme.tertiary
    readiness.authenticated == VerificationState.FAIL || readiness.relayReachable == VerificationState.FAIL ->
        MaterialTheme.colorScheme.error
    !readiness.configured -> Color(0xFFE8A317)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * One photo in the composer tray: a 56 dp square with a small round remove button sitting on its top right
 * corner. The button is drawn at 22 dp; Compose still gives it a full 48 dp touch area inside the tray.
 */
@Composable
private fun StagedPhoto(item: AttachmentManifestItem, enabled: Boolean, onRemove: () -> Unit) {
    val orbit = OrbitTheme.colors
    Box(Modifier.padding(top = 8.dp, end = 8.dp)) {
        AttachmentThumbnail(
            localUri = item.localUri,
            contentDescription = item.displayName,
            maxDimensionPx = 192,
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, orbit.line, RoundedCornerShape(12.dp))
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(orbit.surface)
                .border(1.dp, orbit.line, CircleShape)
                .clickable(enabled = enabled, role = Role.Button) { onRemove() }
                .semantics { contentDescription = "Remove ${item.displayName}" }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = if (enabled) orbit.text else orbit.muted,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    localUri: String,
    contentDescription: String,
    maxDimensionPx: Int,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, localUri, maxDimensionPx) {
        value = decodeSampledAttachmentBitmap(localUri, maxDimensionPx)
    }
    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private suspend fun decodeSampledAttachmentBitmap(
    localUri: String,
    maxDimensionPx: Int
): ImageBitmap? = withContext(Dispatchers.IO) {
    val file = resolveLocalAttachmentFile(localUri) ?: return@withContext null
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx, maxDimensionPx)
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "unknown size"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}



/** Reads at most [limit] bytes (API-level independent replacement for InputStream.readNBytes). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
