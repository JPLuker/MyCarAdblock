# Sourced by .github/car-preview.sh: the hunt for the crash "LayoutNode should be attached to an owner"
# (0.6.7 and 0.6.9 on a VW MIB2 800x480 at 160 dpi, Android 17). Each shot runs a script of the car's
# touches and events (CarPreviewActivity --es script, see app/src/debug/.../PreviewScript.kt): the
# touches sent the way the car's reach myCar, at the front of the main thread's queue, with the car's
# timing. Only when named in the workflow's "shots": minutes each.
#
# stress <name> <w> <h> <dpi> <script> <seconds at most>
# Watches myCar's process every 3 s and stops at once if it goes. In the shot's folder: result.txt,
# fatal.txt (the whole Java stack of a crash, from the debug build: nothing renamed), crash.txt (the
# crash buffer), exit-info.txt, stream.txt (myCar's log and Android's crash lines as they came), the
# last pictures before the end in ring/, alive.txt.
stress() {
    case " ${ONLY:-} " in *" $1 "*) ;; *) return ;; esac
    if ! adb get-state >/dev/null 2>&1; then
        echo "no emulator any more: $1 left out"
        return
    fi
    local d="previews/$1"
    mkdir -p "$d"
    adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
    adb shell am force-stop "$PKG"
    for p in $VELA $PLANCIA $XONOS; do adb shell am force-stop "$p"; done
    adb shell rm -rf "$FILES" 2>/dev/null
    adb logcat -G 16M >/dev/null 2>&1
    adb logcat -c
    adb logcat -b crash -c >/dev/null 2>&1
    timeout $(($6 + 180)) "$ADB_BIN" logcat -v threadtime -s myCar:V AndroidRuntime:V DEBUG:V libc:V ActivityManager:I ${LOGTAGS:-} > "$d/stream.txt" 2>&1 &
    local streaming=$!
    if [ -n "${BEFORE:-}" ]; then eval "$BEFORE"; fi
    adb shell "am start -n $ACT --ei w $2 --ei h $3 --ei dpi $4 --ez skipIntro true ${EXTRA:-} --es script '$5'"
    local start=$SECONDS pid now gone=""
    sleep 4
    pid=$(adb shell pidof "$PKG" | tr -d '\r')
    echo "--- $1: myCar's process ${pid:-none} at the start ---"
    while [ $((SECONDS - start)) -lt "$6" ]; do
        sleep 3
        now=$(adb shell pidof "$PKG" | tr -d '\r')
        if [ "$now" != "$pid" ]; then
            gone="myCar went after $((SECONDS - start)) s (process ${pid:-none} -> ${now:-none})"
            break
        fi
        grep -q "preview: script done" "$d/stream.txt" 2>/dev/null && break
    done
    sleep 2
    echo "${gone:-myCar still running after $((SECONDS - start)) s}" > "$d/result.txt"
    echo "--- $1: $(cat "$d/result.txt") ---"
    adb pull "$FILES/." "$d/" >/dev/null 2>&1 || echo "nothing to pull for $1"
    adb logcat -d -b crash > "$d/crash.txt" 2>&1
    adb logcat -d -v threadtime > "$d/logcat-all.txt" 2>&1
    # myCar's own: another app's crash (Google Play services goes now and then) is no news here.
    grep -B1 -A400 "Process: $PKG" "$d/logcat-all.txt" > "$d/fatal.txt"
    adb shell dumpsys activity exit-info "$PKG" | head -60 > "$d/exit-info.txt" 2>&1
    kill "$streaming" 2>/dev/null
    # The whole log only around the end: Chromium's lines make it large.
    tail -n 30000 "$d/logcat-all.txt" > "$d/logcat-tail.txt" && rm -f "$d/logcat-all.txt"
    alive "$1"
    echo "--- $1: the last steps ---"
    grep "preview: step" "$d/stream.txt" | tail -n 20 | cut -c1-200
    if [ -s "$d/fatal.txt" ] || grep -q "Process: $PKG" "$d/crash.txt"; then
        echo "--- $1: THE CRASH ---"
        { cat "$d/fatal.txt"; echo "--- crash buffer ---"; cat "$d/crash.txt"; } | head -n 400
    fi
}

# 800x480 at 160 dpi, as the VW: the dock is 96 px wide, the pages beside it 704.
C_MAP="48,116"      # the dock's map: back to the map's page, and the page dots wake
C_FOOT="32,447"     # the dock's foot, where the driver touched it: the app grid, on it "map and music"
C_DOT="404,450"     # the dot of the page left of the map
C_CLOSE="764,36"    # the X of the player beside the map: the column closes, the mini player comes
# To the page left of the map, by its dot.
TO_LEFT="tap:$C_MAP;wait:250-600;tap:$C_DOT"
# Back to the map as the driver did (log of 0.6.9): from the agenda's corner across to the dock in 940 ms.
DRIVER="drag:754,229,26,266,940,25"
# Any speed, a flick (120 ms) to a slow drag (1.5 s), let go anywhere from mid-page to over the dock.
ANY="drag:700-770,180-300,20-400,200-300,120-1500,12-40"
# Short flings, the page still on its way when the finger lifts.
FLING="fling:754,229,300-500,229,80-250,12"
# A page's width, let go as the map's page arrives.
EXACT="drag:754,229,46-60,240,500-1000,16"
# The rounds, the same for each state of the map's page; $1 goes first in each (the made-up song again).
rounds() {
    local p="${1:-}" r
    r="rep:12;$p$TO_LEFT;wait:600-2500;$DRIVER;wait:150-1500;end"
    r="$r;rep:12;$p$TO_LEFT;wait:300-2000;$ANY;wait:0-1200;end"
    r="$r;rep:8;$p$TO_LEFT;wait:300-1500;$FLING;wait:0-500;end"
    r="$r;rep:6;$p$TO_LEFT;wait:300-1500;$EXACT;wait:0-60;tap:$C_MAP;wait:300-900;end"
    r="$r;rep:6;$p$TO_LEFT;wait:300-1500;$DRIVER;wait:0-150;tap:$C_MAP;wait:200-1000;end"
    # To the left page by a swipe on Vela's card of places (the driver's way, 17:23:55), then back.
    r="$r;rep:4;${p}drag:130-200,240-300,650-760,250-300,300-900,20;wait:400-1500;$DRIVER;wait:200-1000;end"
    echo "$r"
}

if [ -n "${HA_TOKEN:-}" ]; then
    HA="--es haUrl http://10.0.2.2:8123 --es haToken $HA_TOKEN"
else
    HA=""
    case " ${ONLY:-} " in *" crash-"*) echo "--- crash shots without Home Assistant: no Plancia inside myCar ---" ;; esac
fi
PAGE3="--es pageLayout half-quarters --es pageParts plancia,agenda,weather"

# 1. The page in three parts (Plancia, the agenda, the weather) left of the map, turned back to the map
# 48 times: with the music's column beside the map; with the column closed and the mini player in the
# corner where the driver left it; and the same with Spotify's web player as the car's music.
EXTRA="$HA $PAGE3 --ez demoSong true" stress crash-swipe-column 800 480 160 \
    "wait:9000;snap:start;$(rounds);snap:end" 300
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-swipe-mini 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;snap:mini;$(rounds);snap:end" 300
EXTRA="$HA $PAGE3 --ez demoSong true --es webMusic spotify --ez webMusicPlay true --es miniAt 0.05,1" stress crash-swipe-spotify 800 480 160 \
    "wait:9000;demo;tap:$C_CLOSE;wait:2500;demo;snap:mini;$(rounds "demo;");snap:end" 320

# 2. The app grid and back, as in the 0.6.7 log: Plancia full screen from the grid, the foot (grid),
# the foot again (map and music: the column back), quick swipes along the top, the car showing its own
# screen for 2.5 s and handing it back, a tap on the map, then the foot - right after the column came
# or went, and quickly in a row.
TOGGLES="rep:10;demo;tap:$C_FOOT;wait:600-1500;tapText:Plancia;wait:2500-4000;tap:$C_FOOT;wait:300-900;tap:$C_FOOT;wait:100-600"
TOGGLES="$TOGGLES;drag:338,12,647,28,200,20;wait:200-800;drag:564,2,755,17,130,20;wait:300-800;drag:159,17,710,30,280,20;wait:50-200"
TOGGLES="$TOGGLES;focus:2500;wait:800-1500;tap:232,307;wait:1200-1800;tap:$C_FOOT;wait:0-300;tap:$C_FOOT;wait:300-1200"
TOGGLES="$TOGGLES;tap:$C_CLOSE;wait:0-200;tap:$C_FOOT;wait:500-1000;tap:$C_FOOT;wait:500-1500;end"
TOGGLES="$TOGGLES;rep:12;tap:$C_FOOT;wait:0-400;tap:$C_FOOT;wait:0-400;end"
TOGGLES="$TOGGLES;rep:6;$TO_LEFT;wait:400-1200;tap:$C_FOOT;wait:0-300;tap:$C_FOOT;wait:300-900;end"
EXTRA="$HA $PAGE3 --ez demoSong true --es webMusic spotify --ez webMusicPlay true" stress crash-grid-toggle 800 480 160 \
    "wait:9000;demo;snap:start;$TOGGLES;snap:end" 330
# The same with a car app of templates full screen (the Plancia app, without Plancia inside myCar),
# as the driver had Home Assistant's own app; the page left of the map with the app in its half.
EXTRA="--es pageLayout half-quarters --es pageParts app:$PLANCIA,agenda,weather --ez demoSong true" stress crash-grid-app 800 480 160 \
    "wait:9000;demo;snap:start;$TOGGLES;snap:end" 330

# 3. As in the second 0.6.9 log: the whole map with the mini player, dragged to the corner; Cinema
# opened from the grid (YouTube) and left with the foot twice (grid, then map and music: the column
# back, the map narrower), a tap on the map, 10 s; then the column closed again and the left page.
CINEMA="rep:6;demo;tap:$C_FOOT;wait:800-1500;tapText:Cinema;wait:2500-4000;tapText:YouTube;wait:4000-8000"
CINEMA="$CINEMA;tap:$C_FOOT;wait:500-1200;tap:$C_FOOT;wait:200-800;tap:322,472;wait:10000"
CINEMA="$CINEMA;tap:$C_CLOSE;wait:1000-3000;$TO_LEFT;wait:600-1500;$DRIVER;wait:500-1500;end"
EXTRA="$HA $PAGE3 --ez demoSong true --es webMusic spotify --es miniAt 0.05,1" stress crash-cinema 800 480 160 \
    "wait:9000;demo;tap:$C_CLOSE;wait:2000;drag:266,388,140,400,700,20,900;wait:1500;snap:mini;$CINEMA;snap:end" 330

# 4. Both the driver's logs had, a moment before, a tap in Plancia's page on the page in three parts
# (0.6.9: (221,285), 14 s before turning back to the map; 0.6.7: (232,307), 1.7 s before the foot): the
# web view takes the touch, and the focus. So: the page in three parts, Home Assistant's page back to
# its start (its bar's arrow), a tap in it, then back to the map as the driver, or the app grid.
IN_PLANCIA="tap:120,22;wait:300-700;tap:221,285"
ANY_PLANCIA="tap:120-430,110-420"
tapback() {
    local p="${1:-}" r
    r="rep:16;$p$TO_LEFT;wait:800-2000;$IN_PLANCIA;wait:300-4000;$DRIVER;wait:500-1500;end"
    r="$r;rep:10;$p$TO_LEFT;wait:800-2000;$ANY_PLANCIA;wait:100-1500;$ANY;wait:300-1200;end"
    r="$r;rep:6;$p$TO_LEFT;wait:800-2000;$ANY_PLANCIA;wait:50-300;$FLING;wait:300-1200;end"
    r="$r;rep:3;$p$TO_LEFT;wait:800-2000;$IN_PLANCIA;wait:14000;$DRIVER;wait:500-1500;end"
    echo "$r"
}
tapgrid() {
    local p="${1:-}" r
    r="rep:14;$p$TO_LEFT;wait:800-2000;tap:120,22;wait:300-700;tap:232,307;wait:1200-2000;tap:$C_FOOT;wait:600-1500;tap:$C_FOOT;wait:800-1500;end"
    r="$r;rep:6;${p}drag:338,12,647,28,200,20;wait:600-1200;tap:232,307;wait:0-1800;tap:$C_FOOT;wait:300-1200;tap:$C_FOOT;wait:800-1500;end"
    r="$r;rep:5;$p$TO_LEFT;wait:800-1500;focus:2500;wait:1000-1500;tap:232,307;wait:1600-1800;tap:$C_FOOT;wait:500-1200;tap:$C_FOOT;wait:800-1500;end"
    echo "$r"
}
# The whole map with the mini player in the corner, as in the 0.6.9 log; then the column (the grid's
# "map and music" brings it back) for the 0.6.7 one.
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-plancia-mini 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;snap:mini;$(tapback);$(tapgrid);snap:end" 480
EXTRA="$HA $PAGE3 --ez demoSong true" stress crash-plancia-column 800 480 160 \
    "wait:9000;snap:start;$(tapgrid);$(tapback);snap:end" 480
# Spotify's web player as the car's music, as the driver had it: opened by the drive's start (no media
# key), the made-up song put in its player now and then (no account plays here).
EXTRA="$HA $PAGE3 --es webMusic spotify --ez webMusicKey false --ez webMusicPlay true --es miniAt 0.05,1" stress crash-plancia-spotify 800 480 160 \
    "wait:12000;demo;wait:1500;tap:$C_CLOSE;wait:2500;demo;snap:mini;$(tapback "demo;");$(tapgrid "demo;");snap:end" 500

# 5. The trigger alone (crash-plancia-* went down within two minutes, each time right after a tap in
# Plancia's page that took the focus without opening anything): Home Assistant's page back to its start,
# a tap on "Summaries" (a heading: nothing opens), then away from the page in three parts. crash-focus:
# back to the map as the driver; crash-focus-grid: the app grid and back, as in the 0.6.7 log;
# crash-focus-cleared: the same as crash-focus with the focus taken from the web view first (unfocus):
# if that one doesn't go down, the focus is what does it. The focus changes are in the log with their
# stack ("preview: focus"), and on the crash the node still laying out ("preview: laying out").
FOCUS_TAP="tap:120,22;wait:400-700;tap:150,236"
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-focus 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;rep:30;$TO_LEFT;wait:800-1500;$FOCUS_TAP;wait:300-2500;$DRIVER;wait:500-1500;end;snap:end" 240
EXTRA="$HA $PAGE3 --ez demoSong true" stress crash-focus-grid 800 480 160 \
    "wait:9000;rep:30;$TO_LEFT;wait:800-1500;$FOCUS_TAP;wait:300-2000;tap:$C_FOOT;wait:600-1200;tap:$C_FOOT;wait:800-1500;end;snap:end" 260
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-focus-cleared 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;rep:30;$TO_LEFT;wait:800-1500;$FOCUS_TAP;wait:300-2500;unfocus;wait:150;$DRIVER;wait:500-1500;end;snap:end" 240
# Plancia beside the map instead (car Settings > Beside the map): a tap in its page, then the app grid
# and back. Its pane is in the Row of the map's page, which swaps it for an icon out of view as the
# part of a page does: does the same go down there too?
EXTRA="$HA --es sidePane mycar:plancia" stress crash-focus-side 800 480 160 \
    "wait:9000;rep:20;tap:640-760,200-400;wait:300-2000;tap:$C_FOOT;wait:600-1200;tap:$C_FOOT;wait:800-1500;end;snap:end" 200
# The same with the focus given to Plancia's web view directly (focusweb), as a tap in it gives it: a
# tap doesn't always (crash-focus-cleared's taps never did, so it proved nothing). crash-webfocus leaves
# the page with the focus there; crash-webfocus-cleared takes it away first (unfocus).
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-webfocus 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;rep:20;$TO_LEFT;wait:1000-1500;focusweb;wait:300-2000;$DRIVER;wait:500-1500;end;snap:end" 200
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-webfocus-cleared 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;rep:20;$TO_LEFT;wait:1000-1500;focusweb;wait:300-2000;unfocus;wait:150;$DRIVER;wait:500-1500;end;snap:end" 200

# 6. The fix (0.7.0, car/LeaveWithoutFocus.kt): the part keeps Plancia's page a frame longer and takes
# its focus first. crash-webfocus and crash-focus*, above, must not go down any more; and two more ways
# out of the page with the focus in it: the page changed (holding Plancia's bar, then Back twice: the
# changing ends, then the map), and the app grid and back.
EXTRA="$HA $PAGE3 --ez demoSong true --es miniAt 0.05,1" stress crash-webfocus-edit 800 480 160 \
    "wait:9000;tap:$C_CLOSE;wait:2500;rep:15;$TO_LEFT;wait:1000-1500;focusweb;wait:300-1500;tap:250,22,900;wait:600-1200;key:4;wait:300-800;key:4;wait:500-1200;end;snap:end" 200
EXTRA="$HA $PAGE3 --ez demoSong true" stress crash-webfocus-grid 800 480 160 \
    "wait:9000;rep:20;$TO_LEFT;wait:1000-1500;focusweb;wait:300-1500;tap:$C_FOOT;wait:600-1200;tap:$C_FOOT;wait:800-1500;end;snap:end" 200
