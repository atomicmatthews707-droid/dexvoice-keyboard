# DeX Voice

A voice keyboard for Android and Samsung DeX. Click into any text field, tap the mic, talk, tap again, and your words are typed into that field. It works on the phone screen and on the DeX monitor, because DeX Voice is a real keyboard (an Android input method), which is the one kind of app Android lets type into any display.

Speech is turned into text by an online Whisper service (Groq by default, free key). The Wispr Flow provider is not finished yet, so use Groq or OpenAI for now.

## Install (phone only, no computer needed)

1. On the phone, open this repo's **Releases** page in Chrome and open the release called **Latest build**.
2. Tap **DexVoice.apk** to download it.
3. Open the downloaded file. If Android asks, tap **Settings** and allow Chrome (or Files) to install unknown apps, then go back and tap **Install**.
4. To update later, download the new `DexVoice.apk` the same way and install it over the top. Your settings and key stay.

## First-time setup

Open **DeX Voice**. The setup checklist opens. Work down the list; each item shows a green check when done.

1. **Microphone**: tap Allow.
2. **Turn on the DeX Voice keyboard**: tap Fix, switch "DeX Voice" on, accept the warning.
3. **Choose DeX Voice as your keyboard**: tap Choose and pick "DeX Voice".
4. **Save your API key**: go to console.groq.com on the phone, sign up (free), create an API key, copy it, then open DeX Voice Settings and paste it into the **API key** box. Leave the other fields as they are. (Groq, model `whisper-large-v3-turbo`.) To use OpenAI instead, change **API base URL** to `https://api.openai.com/v1` and **Model** to `whisper-1`.
5. Optional items: floating bubble permission, the shortcut service, notifications, background running, and the voice input list. Do these when you want those features.

Then try the **Test it** box at the bottom of the checklist: tap in the box, tap **Talk**, speak, tap again.

## Daily use in DeX

1. Click into a text field on the phone or the DeX monitor.
2. Start dictation any of these ways: tap the big mic button on the DeX Voice keyboard, tap the floating bubble, press your shortcut (default Ctrl+Shift+Space), tap **Start** in the DeX Voice notification, or use the **DeX Voice** Quick Settings tile.
3. Talk.
4. Start again the same way to stop. The text appears in the field after a moment.

If no DeX Voice keyboard is active when the text arrives, it is copied to the clipboard and a message says "Copied, paste with Ctrl+V".

## Settings reference

Open DeX Voice, then **Open settings** (or open the app after setup is done).

- **Transcription provider**: OpenAI-compatible (Groq, OpenAI) or Wispr Flow API (not finished).
- **API base URL / Model**: where audio is sent and which model. Defaults are Groq and `whisper-large-v3-turbo`.
- **Language code**: leave blank to auto-detect, or enter a code such as `en`.
- **API key**: stored encrypted on the phone. It is never shown again, only "Key saved". Paste a new key to replace it.
- **Vocabulary hint**: names and jargon that help the transcription spell things right.
- **Add a trailing space**: adds a space after the dictated text.
- **Always show the on-screen keyboard**: keeps the DeX Voice keyboard visible even when a hardware keyboard is connected.
- **Vibrate briefly** and **Play start and stop sounds**: feedback when dictation starts and stops (sounds are off by default).
- **Show floating mic bubble** and **Start the bubble when the phone boots**: the draggable mic that floats over apps, including the DeX monitor.
- **Set shortcut** and **Open accessibility settings**: choose your key combination and switch the shortcut service on.
- **Open setup checklist**: go back to the checklist.
- **Share debug log**: sends a short log (no API key, no dictated text) to an app you pick, for example email, when something is wrong.
- **Test microphone (2 s)**: shows the microphone level.

## Troubleshooting

- **Text goes nowhere.** Make sure DeX Voice is the selected keyboard (checklist item 3). With another keyboard, the text is copied instead. Paste with Ctrl+V.
- **The bubble is on the wrong screen.** Click into a text field on the screen you want. The bubble follows the field.
- **The bubble does not show.** Turn on "Show floating mic bubble" and allow the overlay permission (checklist item 5).
- **The shortcut does not work.** Open the checklist and check item 6. After an app update, Samsung sometimes turns the shortcut service off, so switch it on again.
- **The app stops working in the background.** Allow background running (checklist item 8).
- **The hardware keyboard hides the on-screen one.** Turn on "Always show the on-screen keyboard" in Settings.
- **Choosing DeX Voice in the voice input list does not change the mic on Samsung's keyboard.** That is expected. Samsung's keyboard can ignore the system voice input choice. Use DeX Voice itself as the keyboard.
- **"Bad key" or rate limit messages.** Paste the key again in Settings. Free Groq keys have limits, so wait a minute and retry.

## Privacy

Your voice is recorded only while dictation is running. The recording goes only to the transcription provider you chose (Groq by default) and is deleted from the phone afterwards. The API key stays encrypted on the phone. The shortcut service only watches for your key combination; it cannot read the screen or anything you type.
