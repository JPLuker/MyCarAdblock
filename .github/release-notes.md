myCar 0.7.3-alpha: Xonos inside myCar gets the engine of Xonos 0.1.55.

## Xonos inside myCar
- **The engine of Xonos 0.1.55**, compared file by file with the Xonos app (Music Assistant, Sendspin, the player) and brought over, keeping what is myCar's own: the music straight into the car's channel, the bridge to the Xonos app, its settings.
- **Quicker skips.** Next and previous go straight to the song (Music Assistant starts its own next and previous a second later), and wrap around with repeat all. With Music Assistant on the phone, quick taps on a service that plays one stream at a time are gathered for a quarter of a second instead of 0.4.
- **The car's menu** waits for Music Assistant when the car opens it, instead of showing empty folders that the car doesn't ask for again. An artist no longer loads all its songs as it opens: "All tracks" loads when you open it (with Spotify it cost about three requests per album). Long lists come in parts, and a search asks again each time.
- **Music Assistant on your phone**, inside the Xonos app: since Xonos 0.1.55 it stops by itself a minute after nobody uses it, and stays on while myCar holds the Xonos app's bridge, which myCar does for the whole drive. If it has stopped, the bridge starts it again (6 seconds on a phone), and meanwhile playing, skipping and picking a song wait up to a minute (the car's menu 45 seconds), instead of saying it isn't connected. A pause or stop while it waits is kept, and shows no error. Starting a long playlist gets 3 minutes with that server, 1 minute with others.
- **Steadier.** A sound decoder that breaks down is replaced. The sign-in is renewed before it expires (Music Assistant 2.10 makes it last a year). Spotify left busy in Music Assistant by a stream nobody hears any more is freed, when no other player is using it (with an admin account). From away, the home address gets 3 seconds before the remote one is tried. With Music Assistant 2.10.8 or later Sendspin uses its newer format, and an error from the server comes with a clear reason and a 5-minute wait.
- **Two fixes from the review of the port.** With the sound through myCar, a car switched off during a network drop no longer makes the music start again from the phone's speaker, in your pocket, when the network comes back. And if the Xonos app moved to another Music Assistant, or took its own off the phone, myCar no longer waits a minute for a server that won't start for it.

## In the log
- Xonos's lines no longer hold song names, artists, searches, user, player or server names, addresses, tokens or the server's error texts: only what kind of thing it was. The test build keeps everything.

## If you skipped 0.7.2
- 0.7.2 made Autoplay wait for the car's Bluetooth music, up to a minute, instead of playing on the phone's speaker in your pocket.
