# Orbit

An Android chat app for AI models where you bring your own key, and nothing goes anywhere you didn't send it.

I built this because I wanted one app on my phone that could talk to whatever model I felt like using that day,
without signing up for yet another subscription or handing my chats to some middleman server. You paste a key,
pick a model, and talk. That's most of it.

It's an alpha. The core stuff is solid and well tested, but it hasn't been on many phones yet, so expect a few rough
edges.

## What is new in 0.3.4-alpha

- Delete any saved model from its menu, including the one in use; Orbit switches to another saved model first.
- Delete a whole provider, with its saved models and saved key, from its menu or the header of its page.
- The provider in use is listed first, and favorites sit at the top of each provider.
- A service that blocks requests until billing is verified now says so, instead of showing out of credits.

## What is new in 0.3.3-alpha

- Each service's own photo limit per message; photos past it go into numbered grid images instead of being dropped.
- Photos are scaled to 1568 px before sending, so large camera photos are no longer turned away.
- A busy free OpenRouter model no longer looks like every saved key ran out, and other limit messages are clearer.
- A photo from earlier in a chat that can no longer be read no longer blocks later messages.

## What is new in 0.3.2-alpha

- A two-level model switcher: recent and favorite models first, then one list per provider.
- A new home screen with quiet start ideas; replies no longer dim while streaming or jump when they finish.
- Settings, About and other sheets clear the navigation bar, including with 3-button navigation.
- Pairing with a PC no longer crashes on Android 8 to 12, and read aloud picks the best on-device voice.
- New line icons, 12 dp buttons and clearer labels and error messages.

## What is new in 0.3.1-alpha

- A simpler provider picker and guided account, model, and connection setup.
- Model discovery no longer switches the active chat or saves unfinished credentials.
- Accurate daily-quota and temporary rate-limit messaging.

## What is new in 0.3.0-alpha

- In-conversation search, searchable prompts, compact reply actions and playback controls.
- Clean APIs preset, model favorites, supported reasoning options and retry isolation.
- Safer local storage, backups, attachments and project imports.
- Improved context accounting and code export snapshots.

The release candidate passed 1,020 unit tests (two skipped), the optimized Android build and an emulator launch smoke test. It is still an alpha; not all devices or provider services have been tested.

## What it does

- **Lots of providers.** Anthropic, OpenAI, Google Gemini, OpenRouter, Groq, Mistral, DeepSeek, xAI, Together,
  Fireworks, Cerebras, Hugging Face, NVIDIA, Moonshot, Qwen, SambaNova and Cohere each get their own setup card.
  Anything else that speaks the OpenAI or Anthropic format works through "Another service". Point it at
  LM Studio or Ollama on your own computer and it works fully offline.
- **Backup keys.** Add a few extra keys for the same service. If one hits a rate limit or runs out of credit, the
  message gets resent with the next one and you don't have to do anything.
- **Photos and files.** Send pictures, PDFs, Word files and plain text along with your message.
- **It can make PDFs.** Take a photo of a worksheet, ask for it filled in as a PDF, and you get a real document back
  that you can open, share or save. Do check the answers though. The model is guessing like everyone else.
- **Projects.** Group chats with their own instructions and files, for when you're working on one thing for a while.
- **Edit and retry.** Change a message or ask again, then flip between the versions with the little arrows.
- **Thinking, folded away.** For models that reason before answering, you can see how long it thought and open it up
  if you're curious.
- **Voice.** Talk instead of typing, plus a separate calm screen for back-and-forth voice chat.
- **Temporary chats** that never get saved, an archive for stuff you want out of the way, and proper rendering for
  tables, code blocks and quotes.

## Privacy

There's no account, no ads, no analytics and no tracking. Chats live on your phone. Messages go to the model you
picked and nowhere else. Keys are encrypted with the Android Keystore and each one is only ever sent to its own
service, so switching providers never leaks a key to the wrong place.

## Install

Grab the APK from [Releases](../../releases), open it on your phone, and allow installing from that source when
Android asks. Play Protect might warn you because it's not from the Play Store. Tap "More details" and "Install
anyway" if you're fine with that.

Needs Android 8.0 or newer.

## Building it yourself

You'll want JDK 21 and the Android SDK.

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

On Windows there's also `Build-Android.ps1`, which runs the tests, builds a release and can sign it with your own
key. If your Windows username has a space in it, point `TEMP` at a folder without one first, or Gradle gets confused.

## Known gaps

- So far it's been tested mostly on the Android emulator, plus a pile of unit tests. Real-phone bug reports are
  very welcome.
- Hosted providers change their APIs now and then. If one breaks, open an issue with the error text from
  Settings > Error log (it never includes your chats or keys).

## License

MIT. See [LICENSE](LICENSE).
