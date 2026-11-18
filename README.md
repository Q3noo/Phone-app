# Notelet

An Android home-screen widget for jotting down notes and tasks, with pop-up
reminders at whatever times you choose.

- **Widget:** shows your notes and tasks. Tap **+** to write a new one in a small window
  that opens over the home screen. Tap a row to edit it, or tap a task's checkbox to tick it off.
- **Pop-ups:** give any note a time (in 15 min, in 1 hour, tomorrow morning, or any
  date and time you pick). It can repeat every hour, every day, on weekdays, or every week.
  At that time it drops down from the top of the screen as a heads-up pop-up, even when the
  app is closed. The pop-up has **Done** (for tasks) and **Snooze 10 min** buttons.
- **Custom pop-up text:** the first line of a note becomes the pop-up title,
  and the rest becomes its message.
- Reminders come back after the phone restarts. If one was missed while the phone was off,
  it shows as soon as the phone is back on.
- Notes stay on the phone. The app has no account, no internet access and no tracking.

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
| `WidgetClickActivity.kt` | Invisible screen that handles taps on widget rows |
| `EditNoteActivity.kt` | The floating editor (text, note/task, pop-up time, repeat) |
| `MainActivity.kt`, `NotesAdapter.kt` | The in-app list of everything |
| `Note.kt` | The note model and the repeat/snooze rules |
| `NoteStore.kt` | Saves notes on the phone |
| `NoteActions.kt` | Keeps alarms, pop-ups and the widget in sync whenever a note changes |
| `ReminderScheduler.kt`, `ReminderReceiver.kt` | Sets exact alarms and fires the pop-ups |
| `Notifications.kt`, `NotificationActionReceiver.kt` | Builds the pop-up and handles Done and Snooze |
| `BootReceiver.kt` | Puts alarms back after a restart |

The rules for when a reminder fires are covered by unit tests in `app/src/test`.
