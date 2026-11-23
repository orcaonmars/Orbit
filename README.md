# Orbit

An Android chat app for AI models where you bring your own key, and nothing goes anywhere you didn't send it.

I built this because I wanted one app on my phone that could talk to whatever model I felt like using that day,
without signing up for yet another subscription or handing my chats to some middleman server. You paste a key,
pick a model, and talk. That's most of it.

It's an alpha. The core stuff is solid and well tested, but it hasn't been on many phones yet, so expect a few rough
edges.

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
