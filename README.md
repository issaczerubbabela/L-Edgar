# SheetSync: Lightweight Android Expense Tracker

An agent-built, offline-first Android application that syncs seamlessly with Google Sheets.

## 🚀 Features
- **Instant Logging:** Save expenses/income to local Room DB in <100ms.
- **Background Sync:** WorkManager automatically pushes data to Google Sheets when internet is available.
- **Three-Space Navigation:**
  - **Entry:** Fast form with auto-suggestions.
  - **Records:** Filterable list of all transactions.
  - **Visualization:** Real-time budget vs. spend charts using Vico.
- **Planned "Speed" Features:**
  - Home screen Widget (Glance API).
  - Post-payment trigger (Notification Listener for GPay/BHIM).

## 🛠️ Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Local Database:** Room SQLite
- **Dependency Injection:** Hilt
- **Networking:** Retrofit (to Google Apps Script)
- **Visuals:** Vico Charts

## 🔗 Connection & Live Preview
Since this is a native Android app (not Expo/React Native), use the following steps to see your app in progress:

### Option A: Physical Device (Recommended for speed)
1. **Enable Developer Options:** On your phone, go to Settings > About Phone > Tap "Build Number" 7 times.
2. **USB Debugging:** Enable "USB Debugging" in Developer Options.
3. **Connect:** Plug your phone into your PC/Mac via USB.
4. **Run:** In **Android Studio**, select your phone in the top toolbar and press the **Green Play button**.

### Option B: Wireless Debugging (The "Expo-like" experience)
1. Ensure your phone and PC are on the same Wi-Fi.
2. In Android Studio, go to **Device Manager** > **Physical Tab** > **Pair using Wi-Fi**.
3. Scan the QR code with your phone. You can now deploy the app wirelessly.

### Option C: Antigravity Phone Connect (For Agent Monitoring)
If you want to monitor what the Antigravity Agent is doing from your phone while away from your desk:
1. Run Antigravity with: `antigravity . --remote-debugging-port=9000`.
2. Use the **Antigravity Phone Connect** utility to view the agent's "thinking" and "artifacts" on your mobile browser.