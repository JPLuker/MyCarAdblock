myCar 0.7.2-alpha: Autoplay no longer plays in your pocket.

## Autoplay
- **Music from the phone's speaker, in the pocket.** In a driver's log the car's Bluetooth music had dropped with a reset of the car. At the reconnection, Autoplay started Spotify on the phone's own speaker until the car left: 27 seconds (54 in a log of 0.6.9). On the Bluetooth way, Autoplay now waits for an output other than the phone itself: the car's Bluetooth music, LE Audio, or a cable to the car's AUX. It waits up to a minute; after that it leaves the music alone, so it never starts by itself much later.
- Whatever you ask of the music while it waits stays, and Autoplay doesn't undo it: ▶, a pause (spoken too), a song. A ▶ pressed while the music app is still connecting no longer cancels Autoplay with nothing playing. Music already playing, however it started, counts as Autoplay done.
- Five rounds of independent review went through the change before the release and found eleven things to fix, all fixed: among them, Autoplay also waits while the assistant listens or answers, and leaves the music alone when Cinema opens.

## In the log
- The session report gives the car's own audio route (it read "link" for a car with Bluetooth music) and "Wi-Fi transport" for a wireless session that dropped (it read "USB transport").

## If you skipped 0.7.1
- 0.7.1 fixed the black map: the car map's position is kept between the connections of the same drive, and given when the car connects while myCar is still open on the locked phone. With pirate speak, the voice and phone numbers follow the phone's country again.
