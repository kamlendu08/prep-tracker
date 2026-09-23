package dev.kamlendu.preptracker.timer

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether the stopwatch face is the thing currently on screen.
 *
 * Needed because "the app is in the background" is no longer enough to decide that studying has
 * stopped. Locking the phone backgrounds the app and the clock should keep running; walking off to
 * another app does the same thing to the lifecycle and it should not. The service reads this when
 * the phone is unlocked again, to tell "came back to the clock" from "unlocked into something
 * else".
 *
 * Atomic because it is written from the main thread and read from a broadcast receiver.
 */
object TimerFace {
    private val onScreen = AtomicBoolean(false)

    var visible: Boolean
        get() = onScreen.get()
        set(value) = onScreen.set(value)
}
