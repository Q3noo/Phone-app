# Notelet

An Android home-screen widget for jotting down notes and tasks, with pop-up
reminders at whatever times you choose.

- **Widget:** every note and task is its own rounded card. Tap **+** to write a new one in a
  small window that opens over the home screen. Tap a card to edit it, or tap a task's circle
  to tick it off. Finished tasks stay, greyed out and crossed out, until you tap **Clear done**
  (or **Clear all**, which asks first) at the bottom of the widget.
- **Pop-ups at an exact time:** in 15 min, in 1 hour, tomorrow morning, or any date and time you pick.
- **Pop-ups in a time frame:** choose a frame such as "this evening" or 14:00–16:00, and the
  pop-up arrives at a random moment inside it. A repeating frame picks a new random moment each time.
- **−15 / +15 buttons** on each card move its upcoming pop-up (or its whole time frame)
  15 minutes earlier or later.
- Reminders can repeat every hour, every day, on weekdays, or every week. When it's time,
  the pop-up drops down from the top of the screen, even when the app is closed, with
  **Done** (for tasks) and **Snooze 10 min** buttons.
- **Light and dark mode:** a calm warm-paper and soft-teal palette by day and a soft charcoal
  and mint palette at night, which switch automatically with the phone's mode.
- **Custom pop-up text:** the first line of a note becomes the pop-up title,
  and the rest becomes its message.
- Reminders come back after the phone restarts. If one was missed while the phone was off,
  it shows as soon as the phone is back on.
- **Offline by design:** the app has no internet permission, so Android itself blocks it from
  going online. Every GitHub build checks the finished APK and fails if that ever changes.
  Android's cloud backup is also turned off for the app, so your notes never leave the phone.
  The app has no account and no tracking.

Needs Android 8.0 or newer.

## Install it on your phone

Every push to GitHub builds the app automatically (see the **Actions** tab).

1. On your phone, open this repository's **Releases** page and download `Notelet.apk`
   from the **latest** release. Or open the newest run in the **Actions** tab and
   download the `Notelet-apk` file, which arrives as a zip with the APK inside.
2. Open the APK. If Android asks, allow your browser or file manager to install unknown apps.
3. Open Notelet and allow notifications, so the pop-ups can appear.
4. Add the widget: use the app's **Add widget to home screen** button, or long-press the
   home screen, tap **Widgets**, find **Notelet** and drag it out.

Every build is signed with the same key (`app/debug.keystore`), so a newer APK installs
over the old one and keeps your notes.

### If pop-ups arrive late or not at all

Some phones (Samsung, Xiaomi, OnePlus, Huawei and others) stop background apps to save battery.
If reminders don't show up on time, go to **Settings → Apps → Notelet → Battery** and set it
to **Unrestricted** (the name varies by phone). Also check that the **Pop-up reminders**
notification category is allowed to pop up on screen.

## Build it yourself

With Android Studio, open this folder and press Run. From a terminal, with the Android SDK installed:

```sh
./gradlew testDebugUnitTest assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## How it's put together

| File | What it does |
| --- | --- |
| `NoteWidgetProvider.kt`, `NoteWidgetService.kt` | The home-screen widget and its list |
| `WidgetClickActivity.kt` | Invisible screen that handles taps on widget cards (open, tick off, −15/+15) |
| `WidgetActionReceiver.kt`, `ConfirmClearActivity.kt` | The widget's Clear done and Clear all buttons |
| `EditNoteActivity.kt` | The floating editor (text, note/task, exact time or time frame, repeat) |
| `MainActivity.kt`, `NotesAdapter.kt` | The in-app list of everything |
| `Note.kt` | The note model and the rules for time frames, repeats, snoozing and moving pop-ups |
| `res/values/colors.xml`, `res/values-night/colors.xml` | The day and night colour palettes |
| `NoteStore.kt` | Saves notes on the phone |
| `NoteActions.kt` | Keeps alarms, pop-ups and the widget in sync whenever a note changes |
| `ReminderScheduler.kt`, `ReminderReceiver.kt` | Sets exact alarms and fires the pop-ups |
| `Notifications.kt`, `NotificationActionReceiver.kt` | Builds the pop-up and handles Done and Snooze |
| `BootReceiver.kt` | Puts alarms back after a restart |

The rules for when a reminder fires are covered by unit tests in `app/src/test`.
