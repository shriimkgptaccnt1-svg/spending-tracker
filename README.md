# Expense tracker

An Android app that tracks monthly debits and credits by reading bank SMS on the phone.
Everything stays on the phone.

## Install

1. On your Android phone, open this repo's [`releases/expense-tracker-1.1.apk`](releases/expense-tracker-1.1.apk) and tap **Download** (or "View raw").
2. Open the downloaded file. If Android asks, allow **Install unknown apps** for your browser or Files app, then tap **Install**.
3. Open **Expense tracker** and tap **Allow SMS access**.
4. Optional: tap **Settings** on the Overview screen and enter your name as banks write it. This only helps label
   transfers between your own accounts as "Own account (likely)".

The app is not on the Play Store (Google limits SMS-reading apps there), so Play Protect may warn that it is from an
unknown developer. Tap **Install anyway**.

## What it does

- **Reads SMS automatically.** On first open it reads bank SMS from **1 Aug 2026**. After that, each new bank SMS is
  processed as it arrives, and the inbox is re-read whenever you open the app.
- **Four screens:**
  - **Overview:** month and year pickers (current month by default), total debits and credits, debits by category, credits by type.
  - **Over ₹10k / Under ₹10k:** debits and credits for the month. Exactly ₹10,000 counts as under.
    Rows are sorted by bank, then account, then date, with a subtotal per account, to match your bank statements.
    Each row has a small bin icon to delete it (with Undo) and a category button to change its category,
    for that row or for every transaction from that merchant.
  - **Year:** debits for each month of the chosen year.
- **Counting rules**
  - Credit card bill payments are listed but **not added to debits**, because the card purchases are already counted.
  - Reversed card payments are kept: the debit stays and the reversal is a credit.
  - One payment = one row. When a bank sends 2–3 SMS for one payment they are merged (shown as "2 SMS → 1"),
    and an SMS already processed is never counted again.
  - Nothing is removed automatically. Transfers between your own accounts, investments and so on are deleted by you.
  - OTPs, offers, login alerts and other notices are ignored.

## Privacy

- Only transaction details are saved (date, amount, debit or credit, account ending, payee, category). SMS text is never saved.
- Your inbox is never changed.
- No internet permission is requested, and Android backups are switched off for this app.
- **Settings → Clear all data** erases everything the app saved. Uninstalling also erases it.

## Supported SMS

HDFC (account, credit cards, AutoPay, refunds), IOB, SBI (account, YONO, loan, SBI Card), IndusInd, DCB and
Pluxee meal card. Formats are in `app/src/main/java/com/smsledger/parser/Parser.kt`.
New bank formats can be added there as a regular expression.

## Build

Needs JDK 17 and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest      # rule tests (made-up SMS)
./gradlew assembleRelease        # APK in app/build/outputs/apk/release/
```

Release signing reads `SMSLEDGER_KEYSTORE`, `SMSLEDGER_KEYSTORE_PASSWORD`, `SMSLEDGER_KEY_ALIAS` and
`SMSLEDGER_KEY_PASSWORD` from the environment. The key is not in this repo. Future versions must be signed with
the same key to install over the existing app without losing data.

Optional checks on a real SMS export kept on your own computer (never commit it):
`SMS_FIXTURE=/path/sms.tsv` (one SMS per line: `sender<TAB>ISO time<TAB>text`) runs `RealSmsCheck`, and
`./gradlew recordPaparazziDebug` renders the four screens to `app/build/screens/`.
