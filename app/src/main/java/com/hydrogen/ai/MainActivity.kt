package com.hydrogen.ai

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hydrogen.ai.data.AppearanceStore
import com.hydrogen.ai.model.AppearancePrefs
import com.hydrogen.ai.ui.theme.AppearanceController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrogen.ai.companion.AndroidKeystoreCompanionSessionStore
import com.hydrogen.ai.companion.HttpCompanionClient
import com.hydrogen.ai.data.AndroidContentImagePayloadLoader
import com.hydrogen.ai.data.AndroidKeystoreCredentialStore
import com.hydrogen.ai.data.CompositeRelayCredentialStore
import com.hydrogen.ai.data.FileConversationStore
import com.hydrogen.ai.data.InMemoryRelayCredentialStore
import com.hydrogen.ai.data.LocalAttachmentStorage
import com.hydrogen.ai.data.SharedPrefsRelayConfigStore
import com.hydrogen.ai.model.LocalServerPresets
import com.hydrogen.ai.model.QuickAction
import com.hydrogen.ai.model.ShareIntentParser
import com.hydrogen.ai.model.SharedContent
import com.hydrogen.ai.network.HttpGeminiHydrogenRelayBackend
import com.hydrogen.ai.network.HttpGoogleAiStudioBackend
import com.hydrogen.ai.network.RoutingChatBackend
import com.hydrogen.ai.ui.ChatScreen
import com.hydrogen.ai.ui.ChatViewModel
import com.hydrogen.ai.ui.theme.HydrogenAITheme
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val memoryCredentialStore by lazy { InMemoryRelayCredentialStore() }

    /** Pending share from another app; consumed once by ChatScreen. */
    private var pendingShare by mutableStateOf<SharedContent?>(null)

    /** Pending launcher-shortcut or Quick Settings tile action; consumed once by ChatScreen. */
    private var pendingQuickAction by mutableStateOf<QuickAction?>(null)

    /** Theme, accent and pure black from the Appearance sheet; saved on every change. */
    private var appearance by mutableStateOf(AppearancePrefs())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The emulator reaches the PC as 10.0.2.2; a real phone uses loopback through a USB adb reverse tunnel.
        LocalServerPresets.pcHost = if (isEmulator()) LocalServerPresets.EMULATOR_HOST else LocalServerPresets.DEVICE_HOST
        // Only on a fresh launch, so a rotation never re-applies the same share.
        if (savedInstanceState == null) {
            pendingShare = readShare(intent)
            pendingQuickAction = QuickAction.fromIntentAction(intent?.action)
        }

        val conversationStore = FileConversationStore(File(filesDir, "hydrogen_chats"))
        val configStore = SharedPrefsRelayConfigStore(applicationContext)
        val initialConfig = configStore.loadConfig()
        val credentialStore = CompositeRelayCredentialStore(
            memoryStore = memoryCredentialStore,
            persistentStore = AndroidKeystoreCredentialStore(applicationContext),
            persistEnabled = initialConfig.rememberTokenInKeystore
        )
        val attachmentStorage = LocalAttachmentStorage(File(filesDir, "hydrogen_attachments"))
        val imageLoader = AndroidContentImagePayloadLoader(applicationContext)
        val backend = RoutingChatBackend(
            relayBackend = HttpGeminiHydrogenRelayBackend(imageLoader = imageLoader),
            googleAiStudioBackend = HttpGoogleAiStudioBackend(imageLoader = imageLoader),
            anthropicBackend = com.hydrogen.ai.network.HttpAnthropicMessagesBackend(imageLoader = imageLoader)
        )
        val companionClient = HttpCompanionClient()
        val companionSessionStore = AndroidKeystoreCompanionSessionStore(applicationContext)

        val appearanceStore = AppearanceStore(applicationContext)
        appearance = appearanceStore.load()

        setContent {
            val dark = appearance.isDark(isSystemInDarkTheme())
            // A forced Light/Dark theme must also flip the status and navigation bar icons, or they
            // turn invisible (e.g. light icons on the light theme while the phone itself is dark).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LightNavScrim, DarkNavScrim) { dark }
                )
                onDispose {}
            }
            val appearanceController = remember(appearance) {
                AppearanceController(appearance) { updated ->
                    appearance = updated
                    appearanceStore.save(updated)
                }
            }
            HydrogenAITheme(appearance = appearance, darkTheme = dark, controller = appearanceController) {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(
                        conversationStore = conversationStore,
                        configStore = configStore,
                        credentialStore = credentialStore,
                        backend = backend,
                        companionClient = companionClient,
                        companionSessionStore = companionSessionStore,
                        onDeleteLocalAttachment = { uri -> attachmentStorage.deleteIfOwned(uri) },
                        memoryStore = com.hydrogen.ai.data.FileUserMemoryStore(filesDir),
                        personalStore = com.hydrogen.ai.data.FilePersonalStore(filesDir),
                        projectStore = com.hydrogen.ai.data.FileProjectStore(filesDir),
                        appVersionStore = com.hydrogen.ai.data.SharedPrefsAppVersionStore(applicationContext),
                        errorLogStore = com.hydrogen.ai.data.FileErrorLogStore(filesDir),
                        imageSheetComposer = com.hydrogen.ai.data.AndroidImageSheetComposer(
                            applicationContext, File(cacheDir, "orbit_grids")
                        )
                    )
                )
                // Not lifecycle-bound on purpose: it must see a reply finish while backgrounded.
                LaunchedEffect(chatViewModel) {
                    chatViewModel.uiState.map { it.isSending }.distinctUntilChanged().collect { sending ->
                        ReplyKeepAliveService.setActive(applicationContext, sending)
                    }
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    sharedContent = pendingShare,
                    onSharedContentConsumed = { pendingShare = null },
                    quickAction = pendingQuickAction,
                    onQuickActionConsumed = { pendingQuickAction = null }
                )
            }
        }
    }

    override fun onDestroy() {
        // A finished activity takes its ViewModel (and any reply) with it.
        if (isFinishing) ReplyKeepAliveService.setActive(applicationContext, false)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShare(intent)?.let { pendingShare = it }
        QuickAction.fromIntentAction(intent.action)?.let { pendingQuickAction = it }
    }

    private companion object {
        // The same scrims enableEdgeToEdge() uses by default (only visible with 3-button navigation).
        val LightNavScrim = AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkNavScrim = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.PRODUCT.startsWith("sdk_gphone") || Build.FINGERPRINT.startsWith("generic")

    @Suppress("DEPRECATION")
    private fun readShare(intent: Intent?): SharedContent? {
        if (intent == null) return null
        // Extras come from another app: a malformed parcel must not crash Hydrogen.
        return runCatching {
            val streams: List<String?> = when (intent.action) {
                Intent.ACTION_SEND ->
                    listOf(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString())
                Intent.ACTION_SEND_MULTIPLE ->
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().map { it?.toString() }
                else -> emptyList()
            }
            ShareIntentParser.parse(
                action = intent.action,
                mimeType = intent.type,
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                streams = streams
            )
        }.getOrNull()
    }
}

