# iOS Deploy Secrets Guide

Required GitHub Actions secrets for the `deploy-ios` workflow job.

Add all secrets at: **Settings → Secrets and variables → Actions → New repository secret**

---

## 1. App Store Connect API Key

Go to **App Store Connect → Users and Access → Integrations → App Store Connect API → Team Keys**

| Secret | Where to find it |
|---|---|
| `APP_STORE_CONNECT_API_KEY_ID` | Key ID column in the keys table |
| `APP_STORE_CONNECT_API_ISSUER_ID` | "Issuer ID" shown at the top of the Keys page |
| `APP_STORE_CONNECT_API_KEY_BASE64` | Download the `.p8` file when creating the key, then run: `base64 -i AuthKey_XXXX.p8 \| pbcopy` |

> Key needs **App Manager** role. The `.p8` downloads **once** — save it.

---

## 2. Team ID

| Secret | Where to find it |
|---|---|
| `APPLE_TEAM_ID` | **developer.apple.com → Account** → top right, 10-char string like `A1B2C3D4E5` |

---

## 3. Distribution Certificate

In **Xcode → Settings → Accounts → your Apple ID → Manage Certificates**, create an **Apple Distribution** certificate if you don't have one. Then export from Keychain:

```bash
# Keychain Access → My Certificates → right-click Apple Distribution → Export
# Choose .p12, set a password, then:
base64 -i Certificates.p12 | pbcopy
```

| Secret | Value |
|---|---|
| `IOS_DIST_CERT_BASE64` | Base64 output above |
| `IOS_DIST_CERT_PASSWORD` | Password set when exporting |
| `KEYCHAIN_PASSWORD` | Any string (e.g. `ci-keychain-pass`) |

---

## 4. Provisioning Profile

Go to **developer.apple.com → Certificates, IDs & Profiles → Profiles** → create an **App Store Distribution** profile for bundle ID `com.mo3ta.saloalaihapp`.

```bash
base64 -i SaloAleh_AppStore.mobileprovision | pbcopy
```

| Secret | Value |
|---|---|
| `IOS_PROVISIONING_PROFILE_BASE64` | Base64 output above |

---

## 5. Firebase

| Secret | Where |
|---|---|
| `GOOGLE_SERVICE_INFO_PLIST` | Firebase Console → Project Settings → iOS app → download `GoogleService-Info.plist`, then `base64 -i GoogleService-Info.plist \| pbcopy` |
