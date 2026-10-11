# Parcel – serverless Android app store
No backend, no storage of our own, no data collected. The app downloads APKs from GitHub, verifies them, and hands them to Android's installer.

## How it stays fresh
- **Catalog:** `.github/workflows/catalog.yml` runs daily, re-reads every app's latest release and commits `www/apps.json`. The app loads that file live from your repo, falling back to the copy bundled in the APK.
- **Updates for installed apps:** the Library tab's "Check for updates" compares installed versions with the latest releases.
- **Parcel updates itself:** on launch it checks your repo's latest GitHub Release. If it's newer, a banner offers a one-tap update (needs the same signing key, see below).

## Build the APK
1. Create a GitHub account and an empty repo (e.g. `parcel`).
2. Push this folder: `git init -b main && git add . && git commit -m "Parcel" && git remote add origin https://github.com/YOU/parcel.git && git push -u origin main`
3. Open the repo's **Actions** tab. **Build APK** runs on every push. Download `Parcel-APK` → unzip → `Parcel.apk` (debug-signed, for testing).
4. Create your signing key (back it up; losing it means nobody can update):
   `keytool -genkeypair -v -keystore parcel.jks -alias parcel -keyalg RSA -keysize 2048 -validity 10000`
5. Repo → Settings → Secrets and variables → Actions: add `KEYSTORE_B64` (output of `base64 -w0 parcel.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`parcel`), `KEY_PASSWORD`.
6. Repo → Settings → Actions → General → Workflow permissions: **Read and write**.
7. Release: `git tag v1.0.0 && git push origin v1.0.0`. The signed `Parcel.apk` appears under **Releases**. Share that link. Later releases (`v1.0.1`, …) reach installed users through the in-app banner.

## Add apps
Add the repo to `scrape.py` (or an entry in `www/apps.json` with its Android `pkg`) and push; the daily job keeps it current.

## Sections and files
- **Phone / TV:** APKs install natively with progress and the system installer.
- **PC:** installers (Windows, macOS, Linux) download to the phone's `Download/Parcel` folder so you can copy them to a computer.
- Settings → **Test connection** shows which GitHub services the current network can reach.

## If downloads fail
Parcel tries GitHub's normal address, then alternate addresses, then GitHub's official API route. If all fail, the network is blocking GitHub: try mobile data, a VPN (for example Cloudflare WARP), or set Android Private DNS to `one.one.one.one`.
