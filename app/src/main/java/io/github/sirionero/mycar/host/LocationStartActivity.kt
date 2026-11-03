package io.github.sirionero.mycar.host

import android.app.Activity
import android.os.Bundle

/**
 * Nothing to show, gone at once: created as a car arrives with myCar's screen already open behind
 * the lock screen ([LocationKeepAlive.screenForArrival]). A screen of myCar just created is what
 * Android wants, for the next 10 s, to let the car map's position service start with the phone locked.
 */
class LocationStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocationKeepAlive.retry(this, "a screen of myCar was just created for the car's arrival")
        finish()
    }
}
