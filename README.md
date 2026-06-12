# AnswerLens

AnswerLens is a side-loadable Android study overlay for study/practice apps you create yourself. It uses a floating Analyze bubble, MediaProjection screen capture, ML Kit OCR, a question parser, and a local/remote answer engine to show study help in an overlay panel.

This version also includes a **Google AI Study.com Search** button in the result panel. It opens Google AI Mode with a browser search focused on Study.com lesson pages, using the detected question, answer choices, topic, and the optional course code/course title saved in Settings.

## Why this version is flat

This version puts the Android project files in one folder so you can upload the files through GitHub's web upload screen without selecting nested folders.

The Android source files are intentionally in the root folder. The Gradle file tells Android Studio/GitHub Actions to compile Kotlin files from the project root.

## Important GitHub Actions step

GitHub will only run workflow files from this path:

```text
.github/workflows/build-debug-apk.yml
```

Because this flat upload version cannot include folders, the workflow is included as:

```text
build-debug-apk.yml
```

After uploading the files to GitHub:

1. Click **Add file**.
2. Click **Create new file**.
3. In the filename box, type exactly:

```text
.github/workflows/build-debug-apk.yml
```

4. Open the uploaded `build-debug-apk.yml` file.
5. Copy its contents into the new workflow file.
6. Commit it.
7. Go to **Actions**.
8. Run **Build AnswerLens Debug APK**.

## GitHub build output

The workflow builds:

```bash
gradle assembleDebug --stacktrace
```

The debug APK should appear in the workflow artifacts as:

```text
AnswerLens-debug-apk
```

Download the artifact and install:

```text
AnswerLens-debug.apk
```

## Local Android Studio build

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build > Build APK(s).
4. The APK should be under:

```text
build/outputs/apk/debug/
```

## Runtime permissions

AnswerLens requires:

- Draw over other apps
- Screen capture permission
- Notification permission on Android 13+
- Internet permission if using a remote answer endpoint

## Answer endpoint

The app works locally with a small fallback reasoner. For stronger answer generation, open Settings and enter a POST endpoint that accepts JSON like:

```json
{
  "mode": "answer",
  "prompt": "...",
  "question": {
    "question": "Which keyword is used to define a class in Java?",
    "choices": ["method", "class", "object", "define"],
    "type": "multiple_choice",
    "topic": "Java programming"
  }
}
```

The endpoint should return JSON with:

```json
{
  "likely_answer": "class",
  "explanation": "The keyword 'class' is used to define a class in Java.",
  "confidence": 0.96,
  "study_tip": "A class is a blueprint for creating objects.",
  "related_concepts": ["classes", "objects", "constructors"]
}
```

## Main files

- `MainActivity.kt` - main permission/start screen
- `OverlayService.kt` - floating Analyze bubble and result panel
- `ScreenCaptureService.kt` - MediaProjection screen capture
- `OcrProcessor.kt` - ML Kit OCR
- `QuestionParser.kt` - OCR cleanup and question parsing
- `AnswerEngine.kt` - local/remote answer flow
- `SearchRepository.kt` - remote API call support
- `StudyComSearch.kt` - Google AI Study.com search URL builder
- `HistoryRepository.kt` - local history storage
- `SettingsActivity.kt` - settings UI
- `HistoryActivity.kt` - history UI

## GitHub build fix note

This one-folder version keeps Kotlin files at the repository root for easy browser upload. The GitHub Actions workflow prepares the normal Android source tree before building:

```text
src/main/AndroidManifest.xml
src/main/java/com/example/answerlens/
src/main/java/com/example/answerlens/models/
```

Do not change `build.gradle.kts` back to `java.srcDirs(".")`. That makes Gradle treat the whole repository as source input and can trigger `compileDebugKotlin uses this output of task ... without declaring an explicit or implicit dependency`.

If you upload through GitHub mobile/browser, keep the root files flat, then create this workflow path manually:

```text
.github/workflows/build-debug-apk.yml
```

Copy the contents of the root `build-debug-apk.yml` file into that workflow file.

## Update: selected area and movable answer panel

This version adds two overlay improvements:

- **Area button**: Tap the floating **Area** button to draw a rectangle around only the question and answer choices. Tap **Save area**. Future Analyze taps crop the screenshot to that saved rectangle before OCR.
- **Movable result panel**: After analysis, drag the title line that says **AnswerLens • drag here** to move the answer panel around the screen.

You can also use the result panel buttons:

- **Select analysis area** to redraw the crop rectangle.
- **Clear analysis area** to go back to full-screen analysis.
- **Analyze again** to re-run OCR and answer generation.

The Analyze and Area bubbles temporarily hide themselves during screenshot capture so OCR does not read the overlay buttons as part of the quiz.

## Update: close buttons

This version adds dedicated close controls:

- **Close app** on the main AnswerLens screen stops the overlay service, stops screen capture, and closes the Activity.
- **Exit** floating bubble stops AnswerLens from anywhere while the overlay is running.
- **Close AnswerLens** in the result panel stops the overlay and screen-capture session.

Use **Minimize** when you only want to hide the answer panel but keep the Analyze bubble running. Use **Exit** or **Close AnswerLens** when you want AnswerLens fully stopped.

## Google AI Study.com search settings

Open Settings and enter a Study.com course code or course title. After an analysis result appears, tap **Google AI Study.com Search** to open Google AI Mode with a search focused on Study.com lesson pages.

The result panel is now simplified. It only shows:

```text
Answer:
<likely answer>
```

The previous explanation, confidence, study tip, and related-concepts blocks are no longer shown in the overlay panel.

The browser launch uses Android best-effort flags to reuse the existing browser task/tab instead of creating a new tab. Browser apps can still override this behavior, so it cannot be guaranteed on every device or browser.


## Fix10 update

This update adds a **Settings / Course code** button inside the floating result panel so the Study.com course code can be edited from the overlay flow. It also improves screen-capture reliability by retrying capture several times before showing an error, and replaces the old generic `Answer: Unavailable` message with a clearer instruction to clear the analysis area or restart screen capture.
