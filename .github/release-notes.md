myCar 0.7.0-alpha: the release that gathers the whole 0.6 series, and the steadiest myCar so far. The crash that closed myCar and ended the drive on the Home pages is fixed, and a round of bug hunting went through the new features first, then the rest.

## The crash
- **myCar closed and the drive ended** after a tap in Plancia's page (Home Assistant), on a page beside the map, followed by a turn back to the map or a tap on the app grid. It happened in 0.6.7 and 0.6.9, three times in the drivers' logs. The cause was in the library that draws the car's screen: the tap gave Plancia's page the focus, and when the page left the screen with the focus still inside, the focus moving on measured the pages again halfway through the removal. The next frame then placed a piece no longer on screen, and the app stopped. Now the page keeps its place for one more frame and gives the focus up first. The same goes for Plancia beside the map (a swipe to another page, the app grid, Close, the song in the dock), for pages changed on the phone while the car shows Home, and for the browser in the pages when Home leaves the screen or a page is changed.
- Reproduced in the emulator every time before the fix (Android 17 and 14, with the drivers' own touches and timings): myCar went down at the first round. With the fix, eight runs on Android 17 (the page in three parts left for the map, for the app grid and for changing it, Plancia beside the map, and the drivers' trips replayed) gave Plancia's page the focus 127 times in all, then took it away, and never went down.
- If myCar ever closes by itself, the next start writes in the log why Android closed it: a crash with its whole trace, memory, not responding, Android 17's memory limit, said in plain words. Each release now carries R8's map, so a trace from a driver's log can be read.

## Voice
- The voice models load one at a time (the listening one first), never after the car has gone, and ahead of the first command only with plenty of memory free. Each load writes the free memory in the log. The car's preview on the phone, without a car, loads none.
- The phone's speech recognition switched on in a phone that has none (GrapheneOS) no longer locks the language: the assistant listens with myCar's models.

## In the car
- **Mini player**: on low, narrow maps (800x480, Extra large) it starts left of the map's buttons instead of under them, where the map's "-" covered Next and the X. Let go under the app's row of actions or beside the map's buttons, it moves just clear of them (they're drawn over it: a tap on Pause went to Search). The map's faded buttons take only taps on themselves, not on the space between them. While the music's column waits, a song stopped by its app shows Play, so a tap doesn't start it again.
- On a narrow map the button with text in the map's action row (Search) gives way, not the last icon.
- During a call the pages beside the map and their menu stay below the call's bar. What's new waits for a call to end; opened by hand, it closes with the drive.
- Changing myCar's language during a drive changes the car's screen at once.
- A page being changed stops being changed if it's taken away on the phone.

## Music
- **Lyrics**: every song starts from its first line, without the previous song's words; a live version of a similar song no longer brings its words; a half-received answer from LRCLIB isn't saved; beside the map nothing is looked up while Plancia or an app is in the column.
- **Spotify and the music services inside myCar**: "play …" waits up to 25 seconds for the page before saying nothing played; a Play waiting for the page doesn't start if the car's music changed meanwhile; myCar's Back no longer covers YouTube Music's menu on small or scaled screens. Signing in on the phone: the web engine going down no longer closes myCar.
- **Choosing the car's radio** pauses a video or a radio playing in Cinema or the browser too, and closing Cinema afterwards doesn't start the music again by itself.
- **Autoplay** starts the music chosen for the drive only once the sound has a way to the car (Automatic deciding, Android's audio question waiting); before, it could play from the phone's speaker for a few seconds.
- Automatic: after the music's Bluetooth drops, waiting for a VoIP app that leaves the phone "in a call" now has a limit (half an hour); a real phone call is still waited for in full.

## Assistant and messages
- "Next", "pause", "stop" or "resume" act on the music only when they're the whole command: "when is my next appointment" or "pause the TV" no longer skip or stop the song, and reach the agenda or Home Assistant.
- A "cancel" said at the end of a reply's countdown is heard instead of being lost while the message goes.
- Reading a message aloud has a time limit, like the answers: a voice engine that never says it's done no longer leaves the music paused.

## Maps and connecting
- The map's position: a wireless start that tries again no longer loses the position for the whole drive; on Android 12 and 13, opening myCar gives the position back to a service started from the background.
- Wirelessly: turning "Use myCar in the car" off while a start waits to try again stops it; a start stopped while opening the channel or asking for the Wi-Fi no longer keeps the drive's service (and its wake lock) for good.
- The log keeps the car's Bluetooth address, the Wi-Fi's BSSID and song and video titles only in the test build.

## What 0.6 brought, together in 0.7
- **myCar speaks your language**: English, Italian, Spanish, German, French, Portuguese, Greek, Russian or Chinese, on the phone, on the car's screen and in the assistant.
- **Spotify and other services inside myCar** (YouTube Music, Amazon Music, Deezer, TIDAL, SoundCloud, Qobuz and more), each from its web player with the sound through myCar.
- **A mini player over the whole map**, the player beside the map in four sizes, hidden apps on the car's grid, lyrics checked against title, artist and length.
- **The map's position with the phone locked**, a map that starts at its final width.
- **Home Assistant answers what myCar can't**, in your language, with the phone locked too.
- **Connecting wirelessly by itself, sturdier**, and messages that say the real cause.
- **Every car setting on the phone too**, settings in groups, phone themes; a pirate flag after each update, and the pirate ship for this release.

The full list is in the README ("What's new in 0.7") and, release by release, in the app under Settings > About > What's new.

Every change was checked in the code by a second, skeptical reader, and the crash fix by an independent review that found the two places it first missed (beside the map, pages changed on the phone). Every commit compiled and unit-tested in CI. Not tried in a real car.
