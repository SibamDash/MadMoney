# Mad Money

A personal finance tracker for Android. Log income, expenses, debts, and receivables — all stored locally on your device with no accounts or cloud sync required.

---

## Features

- **Transaction logging** — Add income, expenses, money lent, and money owed with date, amount, category, and an optional photo receipt
- **Daily view** — Grouped transaction list by day with running totals
- **Calendar view** — Monthly calendar with per-day income/expense summary
- **Budget tracking** — Set weekly, monthly, or custom budget limits with visual progress indicators
- **Charts** — Pie chart breakdown by category and bar chart for spending trends
- **Trends** — Period-over-period comparison across custom date ranges
- **Backup & restore** — Export and import all data as a JSON file
- **Dark mode** — Full dark theme support via system or manual toggle
- **100% offline** — All data lives in a local SQLite database on your device

---

## Tech Stack

| | |
|---|---|
| Language | Kotlin |
| Min SDK | Android 11 (API 30) |
| Target SDK | Android 15 (API 35) |
| UI | Material 3, ViewPager2, ConstraintLayout |
| Storage | SQLite via SQLiteOpenHelper |
| Build | Gradle with R8 minification |

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+

### Build & Run
```bash
git clone https://github.com/your-username/MadMoney.git
```
1. Open the project in Android Studio
2. Let Gradle sync complete
3. Run on a device or emulator (API 30+)

### Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

> A signing keystore is required for a distributable release build. Configure it in `app/build.gradle.kts` under `signingConfigs`.

---

## Project Structure

```
app/src/main/
├── java/com/example/myapplication/
│   ├── MainActivity.kt          # App shell, navigation, settings drawer
│   ├── AddTransactionActivity.kt
│   ├── DailyFragment.kt         # Home tab — grouped daily transactions
│   ├── CalendarFragment.kt      # Calendar tab
│   ├── ChartFragment.kt         # Pie chart tab
│   ├── BudgetFragment.kt        # Budget tab
│   ├── TrendsFragment.kt        # Trends tab
│   ├── DatabaseHelper.kt        # SQLite layer
│   └── BackupManager.kt         # JSON export/import
└── res/
    ├── layout/
    ├── values/                  # Colors, themes, strings
    └── xml/                     # Backup rules, file provider paths
```

---

## License

This project is for personal use. All rights reserved.

---

*Designed & built by Sibam Dash*
