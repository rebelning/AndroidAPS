package app.aaps.utils

import android.os.Process
import android.util.Log
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Writes uncaught exceptions to the local AAPS log before handing them to Android's original
 * exception handler. The original handler must still run so Crashlytics and Android can perform
 * their normal crash reporting and process termination.
 */
internal class AapsUncaughtExceptionHandler(
    private val aapsLogger: AAPSLogger,
    private val delegate: Thread.UncaughtExceptionHandler?,
    private val terminateProcess: () -> Unit = {
        Process.killProcess(Process.myPid())
        exitProcess(CRASH_EXIT_CODE)
    }
) : Thread.UncaughtExceptionHandler {

    private val handlingException = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (handlingException.compareAndSet(false, true)) {
            try {
                aapsLogger.error(
                    LTag.CORE,
                    "FATAL UNCAUGHT EXCEPTION thread='${thread.name}' state=${thread.state} " +
                        "type=${throwable.javaClass.name} message='${throwable.message.orEmpty()}'",
                    throwable
                )
            } catch (loggingFailure: Throwable) {
                logToLogcat(throwable, loggingFailure)
            }
        } else {
            // A second thread can fail while the first crash is being handled. Avoid recursion,
            // but retain a best-effort record in logcat.
            logToLogcat(throwable, null)
        }

        try {
            if (delegate != null && delegate !== this) {
                delegate.uncaughtException(thread, throwable)
            } else {
                terminateProcess()
            }
        } catch (delegateFailure: Throwable) {
            try {
                Log.e(TAG, "Original uncaught exception handler failed", delegateFailure)
            } catch (_: Throwable) {
                // There is no safer logging path left during process termination.
            }
            terminateProcess()
        }
    }

    private fun logToLogcat(throwable: Throwable, loggingFailure: Throwable?) {
        try {
            Log.e(TAG, "Unable to write uncaught exception to the AAPS log", throwable)
            loggingFailure?.let { Log.e(TAG, "AAPS logger failed while handling the crash", it) }
        } catch (_: Throwable) {
            // Best effort only. The original Android handler still receives the crash below.
        }
    }

    private companion object {

        const val TAG = "AAPS-UncaughtException"
        const val CRASH_EXIT_CODE = 10
    }
}
