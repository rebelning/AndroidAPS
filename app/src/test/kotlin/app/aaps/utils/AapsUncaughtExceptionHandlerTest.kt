package app.aaps.utils

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify

class AapsUncaughtExceptionHandlerTest {

    private val aapsLogger: AAPSLogger = mock()
    private val delegate: Thread.UncaughtExceptionHandler = mock()

    @Test
    fun `logs uncaught exception and delegates to original handler`() {
        val thread = Thread("preference-crash-thread")
        val throwable = IllegalStateException("Unable to inflate WT preferences")
        val terminateProcess: () -> Unit = mock()
        val handler = AapsUncaughtExceptionHandler(aapsLogger, delegate, terminateProcess)

        handler.uncaughtException(thread, throwable)

        verify(aapsLogger).error(
            eq(LTag.CORE),
            argThat { contains("FATAL UNCAUGHT EXCEPTION thread='preference-crash-thread'") },
            same(throwable)
        )
        verify(delegate).uncaughtException(thread, throwable)
        verify(terminateProcess, never()).invoke()
    }

    @Test
    fun `terminates process when no original handler exists`() {
        val throwable = IllegalStateException("crash")
        val terminateProcess: () -> Unit = mock()
        val handler = AapsUncaughtExceptionHandler(aapsLogger, null, terminateProcess)

        handler.uncaughtException(Thread.currentThread(), throwable)

        verify(aapsLogger).error(eq(LTag.CORE), any(), same(throwable))
        verify(terminateProcess).invoke()
    }
}
