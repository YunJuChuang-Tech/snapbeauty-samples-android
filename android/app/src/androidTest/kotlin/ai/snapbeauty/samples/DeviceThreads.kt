package ai.snapbeauty.samples

import android.app.Instrumentation

/** Instrumentation.runOnMainSync does not propagate exceptions back to the test thread. */
fun Instrumentation.checkedMain(block: () -> Unit) {
    var failure: Throwable? = null
    runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
    failure?.let { throw it }
}
