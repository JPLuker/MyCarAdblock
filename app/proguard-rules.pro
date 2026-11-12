# R8 shrinks the release build; these keep what it can't see being used.

# Names stay readable, so stack traces in shared logs need no mapping file. Shrinking does
# the real work on size.
-dontobfuscate

# myCar hosts other apps' car screens: their templates arrive as Bundles, and the car
# library rebuilds them by class name and reflection. Any model class an app might send has to
# survive, used by myCar's code or not.
-keep class androidx.car.app.** { *; }

# Speech-to-text: the native library calls back into these classes by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Cinema's optional Firefox factory is loaded by Class.forName only when its feature split
# has been installed. Keep the exported constructor/name across release R8 shrinking.
-keep class io.github.sirionero.mycar.cinema.gecko.GeckoBrowserFactory {
    public <init>();
    public *;
}

# Signed .xpi import and management live in the on-demand Gecko feature, but are
# discovered reflectively from the lightweight base app.
-keep class io.github.sirionero.mycar.cinema.gecko.CinemaSignedXpiInstallerImpl {
    public <init>();
    public *;
}
