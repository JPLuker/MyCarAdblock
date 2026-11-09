package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.Caller
import io.github.sirionero.mycar.call.CarCalls
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug-only: a call under way on the car's screen with no modem behind it (the call-bubble shots), with
 * [name] at [number], under way since [since] (elapsedRealtime). Set straight into [CarCalls]'s state once
 * the drive's services watch the phone: false before then.
 */
internal fun showFakeCall(name: String?, number: String, since: Long): Boolean {
    if (!CarCalls.inCar) return false
    @Suppress("UNCHECKED_CAST")
    val state = CarCalls::class.java.getDeclaredField("_state").apply { isAccessible = true }
        .get(null) as MutableStateFlow<CallUiState>
    state.value = CallUiState.Active(Caller(number, name), since)
    return true
}
