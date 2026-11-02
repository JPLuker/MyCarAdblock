myCar 0.6.9-alpha: a mini player over the whole map, song lyrics found far more often (and never another song's), a map that gets its position with the phone locked and starts at its final width, Autoplay on by default and an Extra large player.

## New
- **Mini player over the whole map**: close the player beside the map while music plays and a small pill floats over the map, with the cover (a tap opens the full player), back, play or pause and next. Hold it, then drag it anywhere over the map: it stays where it's let go, on the next drives too. Its X hides it for the drive (the song in the dock brings the player back, and the column with it); Settings > "Mini player over the whole map", in the car and on the phone, turns it off. It lies under the map app's own cards and buttons and every touch outside it still reaches the map; a slow tap on it is still a tap, and a swipe that starts on it doesn't turn the page. No progress line: when nothing changes it sends the car no new frames.
- **Extra large player** beside the map (40% bigger than Small), in the car and on the phone.
- **Autoplay on by default**: the music starts with the drive (if you had turned it off, it stays off).

## Song lyrics
- Measured on 47 songs written the way music apps, radios and YouTube write them: 0.6.8 found the right lyrics for 25 and once showed another song's; 0.6.9 finds 42 and never shows another song's (the other 5 were LRCLIB saying "busy" to the test's burst of requests, which myCar now says as a network problem, not "no lyrics").
- Titles are cleaned before asking LRCLIB: "Remastered 2011", "feat.", "Live", "From the film", "Official Video", a radio's "ARTIST - TITLE", several artists. Every result is checked against the title, the artist and the length: a featured artist or the second of "A & B" counts only at the same length, and timed lyrics are followed only when the length matches.
- Instrumental tracks say "instrumental" (from the title or from LRCLIB) instead of "no lyrics".
- With Xonos inside myCar, Music Assistant's own lyrics come first (tags and .lrc files next to the songs).
- While the lyrics are on screen the next song in the queue is looked up ahead, so they show at once. Lookups still happen only with the lyrics on screen or kept beside the map.
- A song without lyrics shows one short line (the cover above it where there's room) instead of an empty panel; beside the map the player comes back after a few seconds, and the lyrics come back with the next song that has some.
- No other lyrics sites: Musixmatch, Genius, NetEase and lyrics.ovh have unlicensed lyrics or terms that forbid this use, and F-Droid would mark myCar as depending on a non-free service.

## The map (from a drive's log, 2 November)
- **Position with the phone locked**: connecting by itself with the phone in a pocket, Android 17 refused myCar the service that lends the map its position, because by the time the map connected (14 s after the car's arrival, through a slow Wi-Fi) myCar no longer counted as just opened. The map had no position for 5 minutes, until myCar was opened on the phone. myCar now takes it as the car arrives, while Android still allows it, and keeps it for the whole drive; it tries again when myCar's accessibility service comes on or myCar comes on screen. If Android still refuses, the car says so, only when you're stopped. Checked on Android 17 in the emulator with the phone locked: taken 1 s after the arrival it runs and the map can borrow it; 15 s after, Android refuses.
- **Final width at once**: the map was given a full-width surface, then a narrower one 6 s later when the music's column opened, and the map app had to throw its first map away while still starting. The column is now kept from the start of the drive while the music is on its way ("Starting Spotify..."), for up to 20 s, so the map's first surface is already the right one (checked in the emulator).
- A surface made again at the same size is told its visible area again; a tap on a car app's surface is written in the log.

## Spotify and the music services inside myCar
- No more "Spotify is playing on another device" at every song change or pause: it counts only when the bar's clock surely runs with nothing played here for 5 s, and the car says it only if it lasts 3 s. A Play while the bar can't tell yet where the music is waits a moment instead of risking a click that would pause another device.

Every change was designed, then checked by a skeptical second reader in the code (and in Android 17's sources for the location rules), then reviewed independently. Checked in the emulator: the mini player (drag, slow tap, X, light theme, 800x480), the Extra large player, the lyrics beside the map and full screen, Music Assistant's lyrics with Xonos inside myCar, the location service with the phone locked on Android 17, the map's first surface. Every commit compiled and unit-tested in CI. Not tried in a real car.

## Also in 0.6.8-alpha
### New
- **Hide an app** from the car's app grid: hold it (on the grid or in the dock) and choose "Hide from the app grid". The car says where it comes back from: Settings > Hidden apps, each with Show. On the phone, Settings > Car screen > "Apps on the car's app grid" lists every app with a tick. Settings itself can't be hidden; a hidden app pinned to the dock stays there, and the voice assistant still opens it by name.
- **One Spotify, not two**: with Spotify (or YouTube Music, Deezer, TIDAL, SoundCloud, Qobuz, Apple Music, Amazon Music) on inside myCar and its app on the phone, Settings > Music has "Hide the phone's Spotify app", with a line saying what it does. Ticked, the phone's app is off the grid and the lists beside the map, and its pinned icon, the choice beside the map and the music the drive starts with go to Spotify inside myCar; if the phone's app is playing when the car connects, it pauses and the one inside myCar plays.
- **Size of the player beside the map**: Small (as it was), Medium (10% bigger) and Large (20% bigger), in the car's settings and on the phone. The whole player grows, its column widens and the map gives it the room; an app beside the map keeps its size.

### In the car
- During a call its bar covered the top of every screen: Back, the player's and the Phone's tabs, a car app's Stop and a message's X couldn't be tapped. The screens now move below it for the call (the map keeps it on top).
- A call ringing closes the assistant, and a message being read or answered (a reply already confirmed still goes): they talked and listened over the call's screen.
- A new choice for "Beside the map", in the car or on the phone, shows at once even after the player's X; the lyrics' X brings the player back instead of closing everything.
- The car's Back key closes What's new and the Bluetooth, location and pairing cards (pairing is left to the driver) instead of changing the screen hidden under them.
- With a language chosen for myCar that isn't the phone's, the car's screen stayed in the phone's language from Android 13 on: Android gives the chosen language to myCar's screens on the phone, not to the one made for another display. myCar now puts its own words in it.
- Settings > Web page: the keyboard comes into sight (it opened below the edge) and Back closes it. What's new no longer closes mid-read when its switch is turned off. The Phone's tabs and the assistant's X are 56 dp.

### Connecting
- Wirelessly, a start that reached the car and failed (the certificate, the car's date) ended with "the car didn't connect over Bluetooth", hiding the real reason; now that's said only for starts that never reached a session.
- A start that tries again by itself no longer shows the red, final card for 3 seconds (Disconnect gone with it); Home says "connecting" until the last try.
- The drive's notification says what Home says, "myCar is connected to the car" only once the car shows myCar.
- After a drive the phone in a pocket keeps listening for the car: a short wake lock, so a short stop reconnects. Turning "Use myCar in the car" off stops a start under way. After the last failed try the car's Wi-Fi is let go (the phone stayed on it, off the home Wi-Fi). A start the car's Bluetooth began stops if the car leaves for 30 seconds. A car connecting during a call doesn't bring myCar over the call's screen.
- Over the cable, a start that stalls says what to do after 45 seconds (answer or tap Android Auto on the car, or plug in again). A car screen that can't be drawn after three tries ends the session and says so, instead of a black car screen and "connected" on the phone. A car that gives no density no longer turns every car app away. An encoder made again while the car shows its own screen waits for the car to hand the screen back; encoder restarts are counted within 10 minutes.
- Messages: Plancia no longer tells Tailscale users to let myCar in and use the cable while it connects through the Plancia app; the VPN message says Plancia and Xonos still reach Tailscale through their apps; "the car hung up during setup" blames the certificate only when it can be the certificate; wireless messages no longer talk about the cable or Android's app question.

### Sound
- Spoken directions not set by hand follow each car's route (turns were spoken twice in a car on Bluetooth, or never in one through myCar), and keep quiet during a call.
- "Music isn't reaching the car" also while Android's question waits, and in the full-screen player.
- Automatic, with a drive started during a call, waits for the call to end before choosing myCar (some cars give the Bluetooth music back only then).
- Cinema pauses when the car switches to the radio for good, and a film started during a call plays when the call ends.
- Turning Xonos off on the phone mid-drive closes the Xonos app too, if it was the car's music.

### On the phone
- Voice: the card for your language said "ready" with only the language's model, while the car asked for a model; it now counts the listening model too, and its button downloads what's missing. The first setup offers the voice download again (myCar's assistant is the default one).
- After signing in to a music service, back on its player, the bar says its name with "Done".
- "Beside the map" shows the music when the app chosen is gone. On Android 12 "Connect wirelessly" names the precise location when that's what is missing. An update ready during a wireless drive says "Disconnect from the car", not "Unplug".

### The map (Vela and the others)
- The player's column beside the map closed and opened again whenever the music app reconnected, the music changed app, between two songs or when a web player's page reloaded. Every width of the map is a new surface for its app, so the map app was handed its map twice and drew it again (Vela redrew from scratch). The column now waits 6 seconds without music before giving the map its width back; it still opens at once, and the X or music closed by the driver closes it at once.
- Beside the player (more so at Medium and Large, or with a large size for the car's screen) the area a map app was told it could use started at 42% of the map, while the list card next to it is at least 232 dp wide: 45-65 dp of map behind the card counted as visible, and a route or a result could end up there. The area now starts at the card's real edge.
- On a narrow map the turn card, always 320 dp, covered up to 80% of the width and went under the map's buttons: now at most 60%.

### Spotify and the music services inside myCar
- Opening a service from the grid, the dock or by voice plays the song in its bar, Autoplay or not (nothing chosen there: its Play does nothing). It used to open paused, and only the wheel's Play or myCar's Play started it.
- Autoplay at the start of a drive: the Play went to the page once, at its first message, and counted as done whatever the page answered, so with Spotify still starting or a slow network it was lost. It is now tried until 45 seconds after the page starts, and a Play the page's player took but that plays nothing yet is sent again after 4 seconds while this is the car's music and nothing else was pressed. Never a second click on Spotify's own button, which plays and pauses.
- When the car's screen is rebuilt (the phone's camera or a video call takes the encoder at a stop) or the web engine's page process ends, the services that were playing play again in the same drive; they stopped for good until someone pressed Play. Not after screen sharing, which stops the music as it stops the capture.
- Spotify Web controlling another device (Spotify Connect, the phone's app) looked paused, and myCar's Play clicked its one button and stopped that music. Now the bar's clock running by itself counts as music playing elsewhere: Play doesn't click, and the car says once that Spotify is playing on another device. Not tried with a Spotify account.
- Message popups make no sound, and the assistant and messages read aloud pause a web player for a moment: the web engine takes the music back by itself, so nothing else was needed there (checked in the code).

### Assistant
- One pause from the question to the answer: the listening gave the music back as soon as the words were read, so with Home Assistant (or an app connecting for "play X") the music came back in the wait and the answer stopped it again.
- No listening and no voice reply while a call rings (the dock's microphone and the wheel's voice key still worked over the ringtone, and a reply could send words heard in it).
- A phone speech engine that didn't start at the first drive, or was updated with myCar open, left the assistant mute until myCar's process ended: answers vanished at once and messages weren't read. It is now made again at the next drive (the turns' engine too), and an answer the engine refuses stays on screen long enough to read.
- The wheel's voice key during screen sharing listened with nothing on the car's screen and lost the command: the sharing now ends and myCar's screen comes back with the assistant (a phone assistant that really opens stays on the shared screen).
- With the phone's speech recognition the music now pauses while it listens, as with myCar's own; Cancel or the X give the music back even when the speech engine never says it stopped; a listen cancelled while the voice model loads no longer beeps.

Found by three independent bug hunts (connection, sound, car screen, phone; then the assistant, the web music services and the map on myCar's side), each point checked by a skeptical second reader and in the code, and an independent review of the new features and of the fixes. Checked in the emulator: the app menu with Hide, the grid without the app and the car's notice, Hidden apps, the player at Medium and Large (1920x720 and 800x480), the phone's list of apps and the size choice. Every commit compiled and unit-tested in CI. Not tried in a real car.
