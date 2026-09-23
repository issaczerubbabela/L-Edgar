# Offline-first: every write goes to Room, the Sheet is synced later

Saving a Transaction never waits for the network. Every change commits to Room first and is marked with a pending Sync action. A WorkManager `SyncWorker` then sends it to the user's Sheet in the background. Room is what the UI reads, and the Sheet is a mirror that Import can read back from. We chose this so the app stays instant and usable offline. The cost is that the phone and the Sheet can briefly disagree, and deletes have to stay on the phone (hidden) until their delete has synced.
