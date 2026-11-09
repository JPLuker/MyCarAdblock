package io.github.sirionero.mycar.car

import androidx.car.app.model.Action
import androidx.car.app.model.Alert
import androidx.car.app.model.AlertCallback
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import io.github.sirionero.mycar.MyCarLog

/** The made-up app the preview's alert comes from. */
private val previewApp = Any()

/**
 * Debug only, for CarPreviewActivity's --ez alert true: an app's alert on the car's screen as Waze
 * puts one up (CarAlerts), with a button in the app's own colour, one in the neutral one and the
 * bar for its 12 s. What the driver does with it, and what the app is told, goes in the log.
 */
internal fun showDemoAlert() {
    val alert = Alert.Builder(PREVIEW_ALERT_ID, CarText.create("Police reported ahead"), PREVIEW_ALERT_MS)
        .setSubtitle(CarText.create("Is it still there?"))
        .setIcon(CarIcon.ALERT)
        .addAction(
            Action.Builder().setTitle("Still there").setBackgroundColor(CarColor.BLUE)
                .setOnClickListener { MyCarLog.i("preview: alert button \"Still there\"") }.build(),
        )
        .addAction(
            Action.Builder().setTitle("Not there")
                .setOnClickListener { MyCarLog.i("preview: alert button \"Not there\"") }.build(),
        )
        .setCallback(object : AlertCallback {
            override fun onCancel(reason: Int) = MyCarLog.i("preview: the app was told its alert was cancelled (reason $reason)")
            override fun onDismiss() = MyCarLog.i("preview: the app was told its alert was dismissed")
        })
        .build()
    CarAlerts.show(previewApp, alert)
}

private const val PREVIEW_ALERT_ID = 7
private const val PREVIEW_ALERT_MS = 12_000L
