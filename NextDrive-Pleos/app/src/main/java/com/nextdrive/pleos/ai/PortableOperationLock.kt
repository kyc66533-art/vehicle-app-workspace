package com.nextdrive.pleos.ai

import java.util.concurrent.locks.ReentrantLock

/** Serializes complete read/network/write operations on the shared encrypted root.
 * Reentrant status calls are allowed; another entry fails fast instead of losing pending/jobs. */
object PortableOperationLock {
    private val lock=ReentrantLock()
    private var cleanup:(()->Unit)?=null
    fun <T> run(action:()->T):T {
        if(!lock.tryLock())throw PortableFailure("REQUEST_PENDING")
        try{return action()}finally{lock.unlock();drain()}
    }
    @Synchronized private fun takeCleanup()=cleanup.also{cleanup=null}
    private fun drain(){if(!lock.isLocked)takeCleanup()?.let{runCatching{it()}}}
    fun whenIdle(action:()->Unit){synchronized(this){cleanup=action};drain()}
}
