# MISSION.md: SheetSync - Android Expense Tracker

## 🎯 Project Overview
SheetSync is a lightweight, offline-first Android application designed to track expenses and income. It relies on a local SQLite database for instant UI updates and uses background workers to seamlessly sync data to a Google Apps Script Web App, which acts as a bridge to an existing Google Sheet named "responses". 

**Core Philosophy:** No network calls on the critical path. The UI must react instantly.

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3) + Glance API (Widgets)
* **Architecture:** MVVM + Clean Architecture + StateFlow (for reactive UI state)
* **Local Database:** Room (sub-millisecond queries)
* **Dependency Injection:** Hilt
* **Networking:** Retrofit
* **Background Sync:** WorkManager
* **Charts:** Vico

## 📐 Architecture

```mermaid
graph TD
    subgraph "Android Application (Offline-First)"
        UI[Jetpack Compose UI<br/>(Log, History, Insights)]
        VM[ViewModel<br/>(StateFlow Management)]
        Repo[Expense Repository<br/>(Data Coordinator)]

        subgraph "Local Data Layer"
            Room[(Room Database<br/>SQLite)]
        end

        subgraph "Sync Engine"
            WM[WorkManager<br/>(SyncWorker)]
            Net[Retrofit<br/>(REST Client)]
        end

        UI <-->|Observes StateFlow / Sends Intents| VM
        VM <-->|Requests Data / Pre-computes Totals| Repo
        Repo <-->|Reads / Writes (Instant)| Room

        WM -->|1. Polls for 'isSynced=false'| Room
        WM -->|2. Pushes Payload| Net
    end

    subgraph "Google Cloud Infrastructure"
        GAS[Google Apps Script<br/>(Web App)]
        Sheet[(Google Sheets<br/>'responses' tab)]

        Net == "HTTPS POST / GET" ==> GAS
        GAS -->|Appends/Reads Rows| Sheet
    end