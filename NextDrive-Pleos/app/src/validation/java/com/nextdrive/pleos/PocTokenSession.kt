package com.nextdrive.pleos

/** Validation process memory only. No preferences/files/Bundle/logs or sliding renewal. */
object PocTokenSession {
    const val TTL_MS=10*60*1000L
    private var secret:CharArray?=null
    private var expires=0L
    @Synchronized fun read(now:Long=android.os.SystemClock.elapsedRealtime()):String? {
        if(now>=expires)clear()
        return secret?.concatToString()
    }
    @Synchronized fun keep(value:String,now:Long=android.os.SystemClock.elapsedRealtime()) {
        require(Regex("[A-Za-z0-9_-]{43}").matches(value))
        if(read(now)==value)return
        clear();secret=value.toCharArray();expires=now+TTL_MS
    }
    @Synchronized fun remaining(now:Long=android.os.SystemClock.elapsedRealtime()):Long {
        read(now);return (expires-now).coerceAtLeast(0)
    }
    @Synchronized fun clear(){secret?.fill('\u0000');secret=null;expires=0}
}
