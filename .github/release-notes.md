myCar 0.6.8-alpha: hide apps from the car's grid, one Spotify instead of two, the player beside the map in three sizes, Spotify inside myCar that plays by itself, a steadier map, and a day of fixes for connecting, the sound, the assistant and the car's screen.

## New
- **Hide an app** from the car's app grid: hold it (on the grid or in the dock) and choose "Hide from the app grid". The car says where it comes back from: Settings > Hidden apps, each with Show. On the phone, Settings > Car screen > "Apps on the car's app grid" lists every app with a tick. Settings itself can't be hidden; a hidden app pinned to the dock stays there, and the voice assistant still opens it by name.
- **One Spotify, not two**: with Spotify (or YouTube Music, Deezer, TIDAL, SoundCloud, Qobuz, Apple Music, Amazon Music) on inside myCar and its app on the phone, Settings > Music has "Hide the phone's Spotify app", with a line saying what it does. Ticked, the phone's app is off the grid and the lists beside the map, and its pinned icon, the choice beside the map and the music the drive starts with go to Spotify inside myCar; if the phone's app is playing when the car connects, it pauses and the one inside myCar plays.
- **Size of the player beside the map**: Small (as it was), Medium (10% bigger) and Large (20% bigger), in the car's settings and on the phone. The whole player grows, its column widens and the map gives it the room; an app beside the map keeps its size.

## In the car
- During a call its bar covered the top of every screen: Back, the player's and the Phone's tabs, a car app's Stop and a message's X couldn't be tapped. The screens now move below it for the call (the map keeps it on top).
- A call ringing closes the assistant, and a message being read or answered (a reply already confirmed still goes): they talked and listened over the call's screen.
- A new choice for "Beside the map", in the car or on the phone, shows at once even after the player's X; the lyrics' X brings the player back instead of closing everything.
- The car's Back key closes What's new and the Bluetooth, location and pairing cards (pairing is left to the driver) instead of changing the screen hidden under them.
- Settings > Web page: the keyboard comes into sight (it opened below the edge) and Back closes it. What's new no longer closes mid-read when its switch is turned off. The Phone's tabs and the assistant's X are 56 dp.

## Connecting
- Wirelessly, a start that reached the car and failed (the certificate, the car's date) ended with "the car didn't connect over Bluetooth", hiding the real reason; now that's said only for starts that never reached a session.
- A start that tries again by itself no longer shows the red, final card for 3 seconds (Disconnect gone with it); Home says "connecting" until the last try.
- The drive's notification says what Home says, "myCar is connected to the car" only once the car shows myCar.
- After a drive the phone in a pocket keeps listening for the car: a short wake lock, so a short stop reconnects. Turning "Use myCar in the car" off stops a start under way. After the last failed try the car's Wi-Fi is let go (the phone stayed on it, off the home Wi-Fi). A start the car's Bluetooth began stops if the car leaves for 30 seconds. A car connecting during a call doesn't bring myCar over the call's screen.
- Over the cable, a start that stalls says what to do after 45 seconds (answer or tap Android Auto on the car, or plug in again). A car screen that can't be drawn after three tries ends the session and says so, instead of a black car screen and "connected" on the phone. A car that gives no density no longer turns every car app away. An encoder made again while the car shows its own screen waits for the car to hand the screen back; encoder restarts are counted within 10 minutes.
- Messages: Plancia no longer tells Tailscale users to let myCar in and use the cable while it connects through the Plancia app; the VPN message says Plancia and Xonos still reach Tailscale through their apps; "the car hung up during setup" blames the certificate only when it can be the certificate; wireless messages no longer talk about the cable or Android's app question.

## Sound
- Spoken directions not set by hand follow each car's route (turns were spoken twice in a car on Bluetooth, or never in one through myCar), and keep quiet during a call.
- "Music isn't reaching the car" also while Android's question waits, and in the full-screen player.
- Automatic, with a drive started during a call, waits for the call to end before choosing myCar (some cars give the Bluetooth music back only then).
- Cinema pauses when the car switches to the radio for good, and a film started during a call plays when the call ends.
- Turning Xonos off on the phone mid-drive closes the Xonos app too, if it was the car's music.

## On the phone
- Voice: the card for your language said "ready" with only the language's model, while the car asked for a model; it now counts the listening model too, and its button downloads what's missing. The first setup offers the voice download again (myCar's assistant is the default one).
- After signing in to a music service, back on its player, the bar says its name with "Done".
- "Beside the map" shows the music when the app chosen is gone. On Android 12 "Connect wirelessly" names the precise location when that's what is missing. An update ready during a wireless drive says "Disconnect from the car", not "Unplug".

## The map (Vela and the others)
- The player's column beside the map closed and opened again whenever the music app reconnected, the music changed app, between two songs or when a web player's page reloaded. Every width of the map is a new surface for its app, so the map app was handed its map twice and drew it again (Vela redrew from scratch). The column now waits 6 seconds without music before giving the map its width back; it still opens at once, and the X or music closed by the driver closes it at once.
- Beside the player (more so at Medium and Large, or with a large size for the car's screen) the area a map app was told it could use started at 42% of the map, while the list card next to it is at least 232 dp wide: 45-65 dp of map behind the card counted as visible, and a route or a result could end up there. The area now starts at the card's real edge.
- On a narrow map the turn card, always 320 dp, covered up to 80% of the width and went under the map's buttons: now at most 60%.

## Spotify and the music services inside myCar
- Opening a service from the grid, the dock or by voice plays the song in its bar, Autoplay or not (nothing chosen there: its Play does nothing). It used to open paused, and only the wheel's Play or myCar's Play started it.
- Autoplay at the start of a drive: the Play went to the page once, at its first message, and counted as done whatever the page answered, so with Spotify still starting or a slow network it was lost. It is now tried until 45 seconds after the page starts, and a Play the page's player took but that plays nothing yet is sent again after 4 seconds while this is the car's music and nothing else was pressed. Never a second click on Spotify's own button, which plays and pauses.
- When the car's screen is rebuilt (the phone's camera or a video call takes the encoder at a stop) or the web engine's page process ends, the services that were playing play again in the same drive; they stopped for good until someone pressed Play. Not after screen sharing, which stops the music as it stops the capture.
- Spotify Web controlling another device (Spotify Connect, the phone's app) looked paused, and myCar's Play clicked its one button and stopped that music. Now the bar's clock running by itself counts as music playing elsewhere: Play doesn't click, and the car says once that Spotify is playing on another device. Not tried with a Spotify account.
- Message popups make no sound, and the assistant and messages read aloud pause a web player for a moment: the web engine takes the music back by itself, so nothing else was needed there (checked in the code).

## Assistant
- One pause from the question to the answer: the listening gave the music back as soon as the words were read, so with Home Assistant (or an app connecting for "play X") the music came back in the wait and the answer stopped it again.
- No listening and no voice reply while a call rings (the dock's microphone and the wheel's voice key still worked over the ringtone, and a reply could send words heard in it).
- A phone speech engine that didn't start at the first drive, or was updated with myCar open, left the assistant mute until myCar's process ended: answers vanished at once and messages weren't read. It is now made again at the next drive (the turns' engine too), and an answer the engine refuses stays on screen long enough to read.
- The wheel's voice key during screen sharing listened with nothing on the car's screen and lost the command: the sharing now ends and myCar's screen comes back with the assistant (a phone assistant that really opens stays on the shared screen).
- With the phone's speech recognition the music now pauses while it listens, as with myCar's own; Cancel or the X give the music back even when the speech engine never says it stopped; a listen cancelled while the voice model loads no longer beeps.

Found by three independent bug hunts (connection, sound, car screen, phone; then the assistant, the web music services and the map on myCar's side), each point checked by a skeptical second reader and in the code, and an independent review of the new features and of the fixes. Checked in the emulator: the app menu with Hide, the grid without the app and the car's notice, Hidden apps, the player at Medium and Large (1920x720 and 800x480), the phone's list of apps and the size choice. Every commit compiled and unit-tested in CI. Not tried in a real car.

## Also in 0.6.7-alpha
### Fixes from 0.6.6
- "Sign in on the phone" for Spotify showed a black page with nothing to touch. myCar's web engine, placed on the screen the way myCar placed it, laid the page out as if it were 0 pixels tall (Android does that for a WebView whose height is "as tall as its content"), and Spotify's sign-in, which fills the window from its edges, was all cut away. The page now has the height of its place: the sign-in shows and works, for every music service.
- The same cause in the car: the browser and Cinema laid every site out 0 pixels tall. Sites that size themselves on the window - YouTube's bottom bar, a cookie notice, a page that fills the screen - were squashed or out of sight. They now show as in a browser.
- Paste in the car's keyboard closed the keyboard and lost the text: to read the phone's clipboard myCar opens a see-through screen on the phone for a moment, the page in the car lost the focus, and its field said it was left. Now the keyboard stays and the pasted text goes into the field.

### Connecting by itself
- While a start-up waits for the car, the phone stays awake (a timed wake lock, until the car shows myCar). In a pocket with the screen off it went to sleep, and the calls to the car every 8 seconds and the start-overs stopped until something woke the phone - often the driver, to tap Disconnect and Connect.
- The tries to reach the car's Android Auto over its Wi-Fi go on for a whole minute, still a second apart. A port nobody listens on yet is refused at once, so the twelve tries were over in about 12 seconds, and a car listening moments later found no one.
- Android's "Allow" for a car to connect by itself counts as a no only on "Don't allow". Closed any other way (Back, the phone turned, Android closing it) it answers nothing and the car is asked at a later drive; before, it turned connecting by itself off for that car for good while Settings said it was on.
- The listening after a drive stays quiet when it starts over, instead of turning into a 5-minute "waiting for the car" ending with "the car didn't connect"; Back on myCar's screen no longer ends it.
- With the phone's Wi-Fi off, myCar says so at once instead of waiting a minute and talking about a prompt or a password. On Android 17, Home asks for the local network permission when a car connects by itself (with Bluetooth allowed, Android gives it without a dialog), and a missing permission is said at the end of the tries.
- A start the car turned down over Wi-Fi (certificate, TLS, the car's date) ends after 30 seconds instead of staying open for good, so connecting by itself starts over. The session's timers use the clock that only goes forward: a phone whose time was corrected mid-drive could end the drive.
- A connection that fails before the car's picture no longer pauses the phone's music, and "Use myCar in the car" off no longer opens myCar at every Bluetooth connection of the car.

### Sound in the car
- After a "no" to Android's question for the sound through myCar, the car stayed silent for the whole drive. Now the player beside the map says "Music isn't reaching the car - Ask again", the phone's Home has a card with "Ask again", and opening music, a web player, Cinema or the browser asks once more (once a drive).
- With Xonos inside myCar as the car's music nothing is captured, so the assistant's replies and the messages read aloud came out of the phone's speaker: they now play on the car's channel for spoken prompts, as the turns do.
- After Xonos stops, another app's sound (a film, a web player, a message read out) is heard from its first second; the 2 seconds after Xonos dropped it.
- Switching to Xonos or a web player inside myCar pauses the app that played before, which went on under it and came back when Xonos stopped. "Play X on Spotify" uses Spotify Web inside myCar, not the Spotify app whose sound can't reach the car.
- In Automatic, a call shorter than 10 seconds no longer moves the music from Bluetooth to myCar for the rest of the drive; the Bluetooth warning is checked again when the sound changes route; spoken directions turned on by hand speak even with the sound on Bluetooth.
- Xonos inside myCar has 20 seconds at the start of a drive before the Xonos app takes its place (home address first, then the one away from home).
- The sound button in the player says where the sound goes now, for this car.

### In the car
- The next turn stays in the dock for the whole call, not only while it rings. What's new waits until the car stops (the car's own word, then the GPS) and never covers a ringing call. Back in the music list goes up a folder. The wheel's Play no longer starts the music over a film.
- The music app beside the map, played from there, becomes the one the next drive starts with. A web player signed in on the phone during a drive reloads in the car, and one made without network reloads at the first button.
- Lighter: the voice models load 20 seconds after the car connects, not while its first picture is made; the map app is sent its visible area only when it changes, and its template updates no longer write two log lines a second.
- Plancia and Xonos inside myCar no longer pass to their apps for the rest of the drive after a tunnel of over a minute: a mobile network suspended in a tunnel counted as online. Plancia, like Xonos, has 20 seconds at the start of a drive (home address first).
- "Video only when parked" on a car that doesn't say itself that it moves: with the GPS out (a tunnel) the film showed again after 15 seconds; now the last speed holds until a fix says the car stopped (at most 5 minutes).
- A message read out in the car that a turn cut short no longer leaves the reply waiting, and a reply under way when the drive ends is sent (if confirming) or dropped instead of coming back stuck.
- Cinema's settings on the phone no longer ask for the home network for the suggested sites (YouTube, RaiPlay, Twitch, "More sites"); sites you add still may.

Checked in the emulator (Android 14 and 17): Spotify's and Deezer's sign-in pages show and Spotify's email field takes a touch; YouTube in Cinema with its bottom bar; the browser's page and dropdown; every commit compiled and unit-tested in CI. Found by a night of independent code reviews, each point checked in the code before fixing. Not tried in a real car.
