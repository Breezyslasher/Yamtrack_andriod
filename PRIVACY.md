# Privacy Policy — Yamtrack Unofficial

_Effective date: November 17, 2026_

Yamtrack Unofficial (package `io.github.breezyslasher.yamtrack`) is an
unofficial Android client for [Yamtrack](https://github.com/FuzzyGrim/Yamtrack),
a self-hosted media tracker. It is developed by Breezyslasher and is not
affiliated with or endorsed by the Yamtrack project.

The app has no server, account system or backend of its own. It connects only
to the Yamtrack server that **you** choose, and the developer does not receive
any of your data.

## What the app stores on your device

- **Server address** — the URL of the Yamtrack server you log in to.
- **API token** — the token that lets the app use your Yamtrack account. It is
  stored encrypted, with a key protected by the Android Keystore.
- **App settings** — your preferences in the app (for example, whether to hide
  details of unwatched episodes).

This data stays on your device and is excluded from Android backups and
device-to-device transfers. Logging out deletes the API token; the server
address and settings are kept so you can log back in. Uninstalling the app, or
clearing its storage in Android settings, deletes all of it.

## What the app sends, and to whom

- **Your Yamtrack server.** Everything you do in the app — viewing your
  library, searching, tracking media, changing scores, statuses, notes, lists
  and artwork — is sent to the Yamtrack server you chose, together with your
  API token. That server, not this app, stores your tracking data. Its owner's
  privacy practices apply to it; if you run your own server, that is you.
- **Your country, in one case.** To show where a title can be streamed, the
  app asks your server for streaming providers. If the server has no region
  configured, the app retries with your device's country code (for example,
  `US`). This is sent only to your Yamtrack server.
- **Image hosts.** Posters, photos and logos are loaded from the addresses your
  server provides, usually public image services such as The Movie Database
  (TMDB). Like any image request, these services can see your IP address.
- **Links you open.** When you tap a link (such as a streaming service or a
  "where to watch" page), it opens in your browser or that service's app,
  under that service's own privacy policy.

Connections use HTTPS whenever your server supports it. Self-hosted servers
using plain HTTP are allowed, in which case that traffic is not encrypted.

## What the app does not do

- No analytics, tracking, advertising or crash-reporting services.
- No selling or sharing of data with third parties.
- No access to your contacts, location, camera, microphone, photos or files.
  The only permissions it uses are internet access and checking whether the
  device is online.

## Children

The app is not directed at children under 13.

## Deleting your data

Uninstall the app, or clear its storage in Android settings, to delete
everything it stores on your device. Logging out deletes your API token.
Data on your Yamtrack server is managed through that server, for example in
Yamtrack's web interface or by its administrator.

## Changes

Changes to this policy will be published at this address, with a new
effective date.

## Contact

Questions about this policy: open an issue at
<https://github.com/Breezyslasher/Yamtrack_andriod/issues>.
