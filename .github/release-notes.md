myCar 0.7.4-alpha: the Jolly key, a bubble for calls, raised panels, the car's radio kept, faster wireless start-ups, and what Gearslip 0.3.32-0.3.40 brought that is safe for the cars that already work.

## New
- **Jolly key**, in the dock above the microphone. One tap does what you choose: call a contact (its usual number first), talk to an AI (the phone's assistant or an assistant app), run something in Home Assistant (a script, a scene, a button, a switch, a light, a gate or a lock; unlocking wants a second tap within 3 seconds), go to a place with the car's map app, give myCar's assistant a command as if you said it ("read my messages", "play Radio Deejay on Spotify"), or open an app. Hold it to choose again, in the car or on the phone in Settings > Car screen. Until you choose, a tap asks. It takes one place of the dock's apps (on an 800x480 screen, 2 places instead of 3; at Extra large it isn't there): turn it off in the same settings and the dock is exactly as before. A real Android widget can't sit on a key (it's another app's piece of screen, and its buttons open on the phone): the Home Assistant, call and command choices cover what such widgets are mostly for.
- **A bubble for a call under way.** Instead of the band along the top that pushed every screen down, a call that is under way (placed, picked up or on hold) is a small bubble over whatever the car shows, from the map to Cinema, until it ends: the photo of who you're talking to (or their initial), how long it has lasted, whether its voice stays on the phone, and a red button that hangs up. A tap opens the Phone; held, it lifts and goes anywhere you drag it, and stays there for the next calls. The mini player over the map moves out of its way. A ringing call still has the card over the whole screen.
- **Video cover** (Spotify Web): songs with a short looping video (Spotify's Canvas) can show it in the player beside the map in place of the still cover, also while driving, if you want it. A button on the cover switches it on the first time, and it stays so until you press it again; the same choice is in the car's settings and on the phone, under Music. Each video is downloaded once (the last 12 are kept) and uses a few megabytes of data; it plays muted, only while the music plays and the player is in view. If no video shows, open Spotify's page once, its Now playing view, and pick the Canvas view (Canvas must be on in Spotify's settings too). It relies on how Spotify's web player works today: if Spotify changes it, the still cover simply stays.
- **Raised panels.** The split screens (map and music, an app beside the map, the pages beside the map) are no longer square blocks side by side: each part is a soft rounded panel raised above the wallpaper, with a light edge and a shadow, in the dark and the light theme. The knob's focus ring follows their corners.

## Wireless start-up (from your logs)
- **A home Wi-Fi like the car's** (addresses in the same range, 192.168.1.x for example) no longer passes for the car's network: myCar tries it only if it isn't validated or has the name the car gave. Before, the start-up could lose two and a half minutes there.
- **The car's adapter restarting**: when the car's Wi-Fi goes away during the first connection, or the car keeps refusing an old port after its Bluetooth comes back, myCar starts over at once instead of trying for a minute without a picture.
- **Calling the adapter again**: when the first call fails at once and the car doesn't call the phone, myCar calls again 3 seconds after the car's Bluetooth arrives, then every 2 seconds up to 15, then every 8.
- **The session report** says how a drive ended: "the car switched off", "the car went silent (N s)", or "Wi-Fi dropped while driving", with the last thing the car said, its Wi-Fi, its Bluetooth audio and its own screen.

## The car's radio (DAB+, FM)
- **New "Keep the car's radio until the phone plays"** (off by default; in the car after "Car audio", on the phone in Connection > Car audio). With the sound through myCar, myCar no longer asks for the car's speakers as soon as Android says yes: the car stays on its own radio until something really plays on the phone (the car's music app, Cinema, or any app that says it is playing), and the first 300 ms you would have missed are sent first.
- **The radio stays chosen**: after you switch to the car's radio, only a player gets the speakers back. A map's voice, a web page or the assistant no longer take them, and Autoplay doesn't start at the next drive in a car that you keep on its radio (with the new choice on).
- **Turns, answers, messages read aloud and the listening beep** go on the car's announcement channel even while the car plays its radio, and before each announcement myCar asks the car to lower its sound for a moment and gives it back after, as Android Auto does.
- **The car taking the speakers for a moment** (its own voice, a traffic announcement, an assistant over Bluetooth): with the sound through myCar the phone's music pauses and starts again when the car gives the speakers back, instead of playing on unheard. It stays paused if you paused, chose a song or another app meanwhile, and waits for the end of a call.

## On the drive
- **Spotify Web** starts at the start of a drive even when the car's radio presses Play again while the page is still trying (on a Golf the second Play stopped the tries).
- **The phone's voice**: when the text-to-speech engine doesn't start speaking, myCar shows the answer for 5 seconds, gives the music back and tells you once. On the phone, Settings > Voice says which voice is missing and opens Android's text-to-speech settings. A failed language change no longer leaves the assistant mute.
- **The big voice model** loads only after 2 minutes of driving, and when you press the microphone only if the phone has the memory for it: otherwise in English the assistant uses its small model (the car tells you once), in Italian it says the phone is short of memory.
- **Home Assistant's voice assistant**: a pipeline whose conversation agent no longer exists is left out, and if Home Assistant still says the agent is missing, myCar tells you what to fix.
- The car no longer shows "paused" for a moment while the next song loads, or right after next and previous.

## Screen sharing
- The phone's screen stays on for the whole sharing (before, its timeout ended the sharing on Android 16 and later), and the phone turns the way the car's picture is.
- With the sound through myCar the music keeps playing in the car during the sharing, without a second question.
- The car tells you how to get back to myCar: the black bands around the phone, a button of the car, or Stop in the notification.

## Cable and first setup
- When the phone charges from a USB port for 20 seconds and no car answers, Home says what to check: a cable that carries data, "Always" when Android asks which app to open and, on GrapheneOS, the USB-C port setting (Settings > Security & privacy > Exploit protection > USB-C port).
- In the first setup, "Car audio" moves next to the drive's steps, with what each choice does at every drive and, if it isn't Bluetooth, the one-time adb command that spares you Android's question. The help no longer names Android's button, which is "Share screen" from Android 15.

## Upright screens
- A car whose touchscreen is taller than wide gets an upright picture when it offers one. myCar works upright, with the player beside the map narrower; the interface itself stays as it is.
- Screen fit, without a saved profile, says "No saved profile: myCar uses the car's sizes" instead of "No vehicle profile matched", which read like an error.

## Waze modified with Morphe
- The "Allow Gearslip" patch accepts only the hosts in its "Accepted hosts" option. A modified Waze that refuses myCar now gets a red X and doesn't start by itself, and the car tells you what to add to that option (the line is also in the README, "Some apps refuse myCar"). It is forgotten as soon as Waze accepts myCar, or with another copy.

## From Gearslip 0.3.32-0.3.40
Only what can't change how a car that works today connects:
- **Alerts from car apps** on the car's screen, over any screen (Waze asks whether a hazard is still there), with their buttons and countdown.
- **Older Hyundai cars** (protocol 1.0/1.1, 2017-2018) that stopped at the start get the icons and ids they wait for; every other car gets the request it gets today.
- **Spoken turns** come back when the text-to-speech engine didn't start, also in the middle of a drive.
- **Music that starts again by itself** after the drive is paused again 2 seconds later.
- **Metrolist's like button** lights up when only its icon changes; **AntennaPod** gets its Next chapter button in the car.
- **The name you gave your car** in Bluetooth settings, in the list and in the messages.
- **Cars that keep their screen black** with the music on Bluetooth (the Mitsubishi Mirage 2024): myCar opens the music channel 5 seconds after asking for the screen, and after two drives like that, at once.
- **Toyota head units** that stopped before a wireless start get the Wi-Fi channel type they wait for, only after stopping once without it.
- **The phone's Bluetooth address**, written by hand in Settings > Connection, for the cars that want it (Chevrolet Bolt, Honda Civic 2020).
- The log says how Android ended a start, also at the end of that start's log. Renault Clio V 2019-2024 (Easy Link) joins the cars that work.
- Not brought over: protocol 1.7, the strict checks on incoming messages, ending the session on protocol errors, the PhoneStatus channel, the car's data to apps, the video cluster, the adaptive bitrate, and the launcher mode.

## Checked
- Built and unit-tested in CI. In the Android emulator: the call bubble with a real call on the emulator's modem (answered, moved, ended with the red button and with the knob), the Jolly key and its choices in several languages and at 800x480 (a script and a lock with its second tap on a real Home Assistant, a place sent to Vela Maps), the video cover with a test video (fade, loop, crop, while moving), the raised panels at 800x480, 1920x720 and 1920x1080, light and dark and over the moving wallpapers, the upright screens, the charging card and the setup, the radio choice, Waze's alert.
- Not tried in a car: everything here waits for your drives. With the radio choice on, look in the log for "state=LOSS" when you pick DAB+, and tell me if the car keeps its radio.
