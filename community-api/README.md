# Community experiences API

People read and share short experiences with the app and with dhikr. The Android/KMP app
talks only to this service. This service is the only piece that talks to GitHub, and it
writes new submissions into the moderation queue. Nothing is published until you move it
by hand.

The app learns this service's origin from Firebase Remote Config (`experiences_api_base_url` on project `kamapp-3b3ac`). Stories are not stored in Firebase. Realtime Database, Firestore, and Auth are not used here, and `GITHUB_TOKEN` stays on Deno Deploy.

```
app  --Remote Config-->  experiences_api_base_url
app  --GET /experiences-->  Deno Deploy  --read-->  community/experiences.json
app  --POST /experiences->  Deno Deploy  --write->  community/pending.json
```

`GET /experiences` never reads `community/pending.json`. A submission stays in the queue
until you copy it into `community/experiences.json`.

## Item shape

```json
{
  "id": "server-generated-uuid",
  "displayName": "أخ/أخت",
  "body": "the text",
  "category": "app",
  "createdAt": "2026-11-09T16:40:00.000Z"
}
```

`category` is `app` or `dhikr`. The server assigns `id` and `createdAt` (UTC). A blank
name is stored as `أخ/أخت`. Name length is at most 40, body at most 1000, after trimming.
Empty text and raw HTML (`<` or `>`) are rejected.

## 1. Create the token

Create a fine-grained personal access token on GitHub:

- Resource owner: your user
- Repository access: **only** `MahmoudMabrok/SaloAleh`
- Permissions: **Contents: Read and write**

Do not grant other repositories or other permissions. Do not put the token in the app,
in git, or in a log.

## 2. Set the Deno Deploy secrets

In the Deno Deploy project, set environment variables (not in this repo):

| Name | Required | Value |
| --- | --- | --- |
| `GITHUB_TOKEN` | yes | the fine-grained PAT |
| `GITHUB_REPO` | no | `MahmoudMabrok/SaloAleh` |
| `GITHUB_BRANCH` | no | `main` |

The process reads the token from the environment and sends it only as a GitHub
`Authorization` header. It does not print the token or the request headers.

## 3. Deploy

This task does not deploy the service. From [Deno Deploy](https://dash.deno.com):

1. Create a project from this Git repository.
2. Set the entrypoint to `community-api/main.ts`.
3. Add the environment variables above.
4. Deploy.

Local check, without deploying:

```bash
cd community-api
deno task test
GITHUB_TOKEN=... deno task dev
curl -s localhost:8000/health
curl -s localhost:8000/experiences
```

`GET /health` does not touch GitHub. `GET /experiences` and `POST /experiences` return
503 until `GITHUB_TOKEN` is set.

`POST /experiences` example:

```bash
curl -s -X POST localhost:8000/experiences \
  -H 'content-type: application/json' \
  -d '{"displayName":"","body":"نص التجربة","category":"dhikr"}'
```

A successful response is `{ "ok": true, "status": "pending", "id": "..." }`. The text is
in `community/pending.json` on the configured branch. It is not in the GET response.

Limits, per isolate (Deno Deploy may run more than one): 5 posts per IP per hour, and
60 posts per hour overall. A full isolate returns 429.

`GET /experiences` sends `Cache-Control: public, max-age=60`.

## 4. Point the app at the base URL

The app reads the Deno origin from **Firebase Remote Config** on the existing project
`kamapp-3b3ac`. This is not Realtime Database, Firestore, or Auth. The parameter holds
only the public origin. Do not put `GITHUB_TOKEN` in Remote Config.

| | |
| --- | --- |
| Parameter key | `experiences_api_base_url` |
| Type | String |
| Value | the Deno origin, no path and no trailing slash, for example `https://saloaleh-community.deno.dev` |

In the Firebase console:

1. Open [Remote Config for kamapp-3b3ac](https://console.firebase.google.com/project/kamapp-3b3ac/config).
2. Add parameter `experiences_api_base_url`.
3. Set the value to the Deno Deploy origin.
4. Publish the template.

The app fetches that parameter when the experiences screen opens. A debug build checks
again on every open. A release build caches the fetch for one hour, and still uses the
last published value if a later fetch fails.

If the parameter is missing, empty, illegal, or the fetch fails and nothing is cached,
the experiences screen shows an error. The rest of the app keeps working.

Android debug builds can skip Remote Config. Set one of these and rebuild the debug APK:

- `local.properties` (gitignored): `experiencesApiBaseUrl=https://your-project.deno.dev`
- or `./gradlew -PexperiencesApiBaseUrl=https://your-project.deno.dev assembleDebug`

Release builds ignore that property.

iOS uses the same Remote Config parameter. The Podfile includes
`Firebase/RemoteConfig`. Run `pod install` in `iosApp/` before an iOS build so the
native SDK is linked.

The app calls `GET {origin}/experiences` and `POST {origin}/experiences`. It does not
call GitHub.

## 5. Approve a pending experience

New submissions receive a server id: 12 lowercase hex characters, for example
`0001020a0b0c`. The client cannot choose it. An item that is already in either JSON
file keeps the id it has, including an older UUID. The published list and the queue
both store that `id` on every object. The app only shows rows that have one.

The review page reads the public queue and does not publish anything:

[https://mahmoudmabrok.github.io/SaloAleh/experiences-review.html](https://mahmoudmabrok.github.io/SaloAleh/experiences-review.html)

The page is `community/review.html`. GitHub Pages copies it to that URL when this
change is on `main`. It loads
`https://raw.githubusercontent.com/MahmoudMabrok/SaloAleh/main/community/pending.json`.

On the page, select the stories to approve and press **نسخ المعرّفات المحددة**.
The copied text is one id per line:

```
0001020a0b0c
a1b2c3d4e5f6
```

Spaces, commas, and semicolons are also accepted.

Then open
[Publish community experiences](https://github.com/MahmoudMabrok/SaloAleh/actions/workflows/publish-experiences.yml),
choose **Run workflow** on branch `main`, paste the ids into `ids`, and run it.

The workflow is `publish-experiences.yml`. GitHub offers **Run workflow** after the
file is on the default branch, which happens when this pull request is merged.
Before that, GitHub may list it on the feature branch once the file is on that
branch. Run it against `main`. The Deno API reads `main`, so a commit on another
branch does not show up in the app.

The job uses the repository `GITHUB_TOKEN` with `contents: write` only. That is the
smallest permission that can commit. It does not use a personal access token, and
the token is not in the app, Firebase, or the HTML page. The job adds only
`community/pending.json` and `community/experiences.json`.

For each id it:

- moves the row from the queue onto the end of the published list when the id is pending
- skips an id that is already published, and leaves both files as they are for that id
- reports an id that is in neither file, and does not invent a row

Other rows stay in place, including a second pending row that happens to reuse an id.
The Actions log prints the three groups in Arabic: تم النقل، منشورة مسبقًا، غير معروفة.

The app shows a newly published row on the next fetch, after the 60 second cache.
It does not show the row at the moment it was submitted.

## Public queue

The app hides the queue. This GitHub repository is public, so anyone who opens
`community/pending.json` on GitHub can read submissions before you approve them.
That is a property of storing the queue in this repo. Do not put anything there that
must stay private.

## Tests

```bash
deno test community-api
```
