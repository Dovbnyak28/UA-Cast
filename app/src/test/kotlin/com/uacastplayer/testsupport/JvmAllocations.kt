package com.uacastplayer.testsupport

/** Android's compile stubs omit management APIs; these tests execute on the host JDK. */
internal object JvmAllocations {
    private val bean = Class.forName("java.lang.management.ManagementFactory")
        .getMethod("getThreadMXBean").invoke(null)
    private val allocated = Class.forName("com.sun.management.ThreadMXBean")
        .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val threadId = Thread::class.java.getMethod("threadId")

    fun currentThreadBytes(): Long = allocated.invoke(bean, threadId.invoke(Thread.currentThread())) as Long
}
