# LearnAIoffline: native Android app

An offline Android app (Kotlin + Jetpack Compose) that teaches the modern AI-engineering stack step by step, with **16 topics, 140 lessons, 1,116 key concepts and 52,800 multiple-choice questions (3,300 per topic)**. Every question has a hint, sample code where it helps, a full explanation and a link back to the lesson.

| # | Topic | Lessons | Questions |
|---|---|---|---|
| 1 | Python for AI Engineers | 10 | 3,300 |
| 2 | FastAPI | 9 | 3,300 |
| 3 | LangChain | 9 | 3,300 |
| 4 | LangGraph | 9 | 3,300 |
| 5 | Langfuse | 8 | 3,300 |
| 6 | LlamaIndex | 9 | 3,300 |
| 7 | Vector RAG | 9 | 3,300 |
| 8 | Graph RAG | 7 | 3,300 |
| 9 | Hybrid RAG | 7 | 3,300 |
| 10 | RAG Architectures (with examples) | 10 | 3,300 |
| 11 | Agentic AI & Frameworks (CrewAI, LangGraph, OpenAI Agents SDK, Google ADK, Microsoft Agent Framework/AutoGen, smolagents…) | 10 | 3,300 |
| 12 | Text Embedding Models | 8 | 3,300 |
| 13 | LLM Models | 9 | 3,300 |
| 14 | RAGAS | 8 | 3,300 |
| 15 | AI Frameworks Landscape | 9 | 3,300 |
| 16 | Model Context Protocol (MCP) | 9 | 3,300 |

## Features

- **Design:** the LearnAIoffline "Field Notes" design kit (`design/ui-kit/`), built with Material 3: forest-green handbook style, lattice illustrations, monospace metadata.
- **Themes:** light, dark or follow system; an animated day/night switch on Home, the tabs and Settings; 10 colour themes (Forest, Ocean, Violet, Sunset, Teal, Indigo, Rose, Amber, Berry, Graphite), all WCAG AA; wallpaper colours on Android 12+; AMOLED black; separate light/dark code theme.
- **First run:** a 4-step welcome tour (offline intro, practice, daily goal, look and colour theme) that can be replayed from Settings.
- **Home:** continue card with resume, daily goal, your topics and a 4-week study activity strip.
- **Learn:** course library with search, bookmarks and In progress / Completed filters; topic overview with course progress and shortcuts; lesson reader with a section index, field-note callouts, tables, copyable syntax-highlighted code and Previous / Mark complete / Next.
- **Key concepts:** a glossary of all 1,116 concepts (or one topic) with search, plus flashcards.
- **Practice:** quick set plus seven modes (continue in order, random, by lesson, mistakes, unanswered, bookmarked, from a number); quiz setup with topic, mode and set size; hints with sample code; explanations with a link to the lesson; a summary with accuracy, time and mistakes to review; a question bank with topic, lesson, type and state filters.
- **Progress:** streak, accuracy and study time, a this-week chart, per-topic and per-lesson progress, daily history and day detail, bookmarks.
- **Settings:** reading text size and line spacing, code size and theme, practice set size, daily goal, export / import / reset progress.
- **Private:** fully offline, no accounts, no ads, no analytics and no permissions. All progress is stored in local SQLite.

## Repository layout

```
android/                 Android Studio project (open this folder)
  app/src/main/java/...  Kotlin sources (data layer, navigation, Compose screens)
  app/src/main/assets/content/   generated course content (topics, lessons, questions, glossary JSON)
content/                 content pipeline (lessons in Markdown, fact files, question generators)
play_store/              store listing text, 512px icon, 1024×500 feature graphic, Play Console answers
.github/workflows/       CI that builds the debug APK, release APK and release AAB
PRIVACY_POLICY.md        privacy policy (docs/privacy-policy.html is the same, ready to host)
```

## 1. Build and run

### Android Studio (easiest)
1. Install a recent stable **Android Studio** (it must support Android Gradle Plugin 8.13).
2. **File › Open** the `android/` folder. Let Gradle sync; it downloads Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.20 and Compose (BOM 2025.10.01).
3. Pick an emulator or a USB-connected phone (Android 8.0 / API 26 or newer) and press **Run**.

### Command line
Requires JDK 17+ and the Android SDK (platform 36). Point to the SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=...`.
```bash
cd android
./gradlew assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions (no local setup)
Push this folder to a GitHub repository. The workflow in `.github/workflows/android.yml` builds on every push and uploads three artifacts: **debug-apk**, **release-apk** and **release-aab**. You can also start it by hand from the **Actions** tab (*Run workflow*).

## 2. Before publishing: make it yours

1. **Application ID:** set to `com.sjpixels.learnaioffline` in `android/app/build.gradle.kts` (debug builds add `.debug`). Play can't change it after the first upload. `namespace` stays `com.aiengineer.academy` (the Kotlin package).
2. **Version:** raise `versionCode` (an integer) for every upload, and update `versionName` (the label users see).
3. **Contact:** add your support email to `PRIVACY_POLICY.md` and `docs/privacy-policy.html`.

## 3. Create an upload key and sign the release

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep the `.jks` file and its passwords safe and backed up. Never commit them; `.gitignore` already excludes them.

- **Local:** copy `android/keystore.properties.example` to `android/keystore.properties`, fill it in, then run `./gradlew bundleRelease`. The signed bundle is written to `app/build/outputs/bundle/release/app-release.aab`.
- **CI:** add four repository secrets:
  - `SIGNING_KEYSTORE_BASE64`: the output of `base64 -w0 upload-keystore.jks`; on macOS, `base64 -i upload-keystore.jks`.
  - `SIGNING_STORE_PASSWORD`
  - `SIGNING_KEY_ALIAS`
  - `SIGNING_KEY_PASSWORD`

  The **release-aab** artifact is then signed and ready to upload.

Play App Signing is switched on automatically on your first upload. Google keeps the app-signing key, and you sign each upload with your upload key.

## 4. Publish on Google Play

1. Create a **Google Play Console** developer account. There is a one-time $25 fee and identity verification.
2. **Create app:** enter the name *LearnAIoffline*, choose App and Free, and accept the declarations.
3. **Store listing:** copy the texts from `play_store/store_listing.md`. Upload `play_store/icon-512.png` and `play_store/feature-graphic-1024x500.png`, plus 2–8 phone screenshots taken from the running app.
4. **App content:** fill in the privacy policy URL, ads (none), app access (no login), content rating, target audience (18+) and the **Data safety** form (*no data collected or shared*). `play_store/store_listing.md` has every answer.
   - To get a public privacy policy URL for free, enable GitHub Pages on the repository with source = `docs/` folder. The URL is then `https://<user>.github.io/<repo>/privacy-policy.html`.
5. **Testing:**
   - Upload the signed `.aab` to **Internal testing** first and install it on your own phone.
   - **New personal developer accounts** must then run a **closed test with at least 12 testers who stay opted in for 14 days in a row** before production access is unlocked. Organisation accounts are exempt.
6. **Production:** create a production release with the same `.aab`, or a newer `versionCode`, and submit it for review. The app targets API 36, which meets Play's current target-API requirement.

## 5. Updating the course content (optional)

The content is generated and validated by Python scripts in `content/`:

```bash
cd content
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
python3 build_all.py        # lessons, 52,800 questions, glossary and topics.json -> android/app/src/main/assets/content
python3 validate.py         # checks: 3,300 per topic, no duplicates, 4 unique options, valid answer/lesson/snippet links
```

Run `python3 build_all.py mcp` (any topic ids) to rebuild only those topics; the others in `topics.json` are kept.

- `lessons/*.md`: the lessons. `## ` starts a lesson and `### ` starts a section.
- `facts/*.txt`: the curated knowledge base used for concept questions and the glossary.
- `generators/*.py`: computed questions. Code-output and API-behaviour questions are produced by actually running the code with Python, FastAPI's TestClient, LangChain, LangGraph and LlamaIndex Workflows. Calculation questions use exact arithmetic.
- `enrich_hints.py`: points every hint to a lesson section and attaches sample code from that lesson.

To fix or add a question, edit the fact or lesson, re-run `build_all.py` and `validate.py`, then rebuild the app.

## How this was checked

- `validate.py` passes for all 52,800 questions with 0 problems. The correct answer position is evenly balanced across A to D.
- The Kotlin sources were type-checked with the Kotlin 2.2.20 compiler, with Compose's `@Composable` call checks, against signature stubs of the Android and Compose APIs they use. The result was 0 errors.
- The full Gradle build has **not** been run against the real Android SDK yet. The machine that produced this project couldn't reach Google's Maven repository. Your first Android Studio sync or GitHub Actions run is that final check. If the toolchain reports anything, it should be a small, local fix.

Content reflects these fast-moving libraries as of late 2026. Product names are trademarks of their owners, and this is an independent learning resource.
