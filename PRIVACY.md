# Rockscreen privacy policy

Effective 6 November 2026. Rockscreen is a free, open-source Android app (source:
[github.com/adit-0132/rockscreen](https://github.com/adit-0132/rockscreen)). Its developer runs no servers and collects
no data. This page describes exactly what the app reads, stores and sends, based on its source code.

## What Rockscreen reads on your phone

With the notification access you grant, Rockscreen reads the **media session** of the music app you are using:
- song title, artist, album, duration and artwork;
- playback state: playing or paused, position, speed.

Notification access technically lets an app see all notifications. Rockscreen only looks at notifications that belong
to a media session, and only at their artwork, as a fallback when the music app provides no artwork in its media
session. It does not read the content of any other notification.

## What leaves your phone

1. **Lyrics lookups.** For each new song, Rockscreen sends the **song title and artist** to
   [LRCLIB](https://lrclib.net) over HTTPS to find synced lyrics. The request identifies the app
   (`Rockscreen/<version>` and this project's URL), and, like any web request, LRCLIB sees your IP address. No lookup is
   made for songs already in the cache, for media without an artist, or for ads that the music app marks as ads.
   LRCLIB is an independent service; its own policies apply to the requests it receives.
2. **Artwork from the web.** If your music app provides artwork as a web address instead of a local image, Rockscreen
   downloads that image from the address the music app supplied, to show it.
3. **Android backup.** If Android backup is on, Android may back up Rockscreen's settings to your Google account, as it
   does for other apps. The lyrics cache is excluded from cloud backup and from device-to-device transfer.

Nothing else is sent anywhere. Rockscreen has no account, analytics, advertising, crash reporting or tracking, and
nothing is sold or shared.

## What is stored on your phone

- **Lyrics cache:** song title, artist, the lyrics, when they were fetched, and whether lyrics were found. Entries
  expire after 30 days, and the cache keeps at most 500 songs. Together this amounts to a list of songs you played
  recently, so it stays on the phone and out of backups.
- **Settings:** whether the lock-screen lyrics are on, and the appearance settings.
- **Artwork:** the current song's artwork when the music app hands it over as an image (replaced on each song), and
  artwork downloaded from web addresses, which the image loader caches. Both live in the app's cache folder, which
  Android may also clear when storage runs low.

Uninstalling Rockscreen, or clearing its storage in Android settings, deletes all of it.

## Changes and contact

Changes to this policy are published in this file, and its history is visible in the repository. Questions: open an
issue at [github.com/adit-0132/rockscreen/issues](https://github.com/adit-0132/rockscreen/issues).
