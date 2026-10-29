package com.ftcloud.app.core.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class TokenBucketRateLimiter(
    private val minBotIntervalMs: Long = 350L,
) {
    private val botLocks = ConcurrentHashMap<String, Mutex>()
    private val botLastSendMs = ConcurrentHashMap<String, Long>()

    suspend fun acquireSendSlot(botId: String) {
        if (minBotIntervalMs <= 0L) return
        val mutex = botLocks.computeIfAbsent(botId) { Mutex() }
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = botLastSendMs[botId] ?: 0L
            val elapsed = now - last
            if (elapsed < minBotIntervalMs) {
                delay(minBotIntervalMs - elapsed)
            }
            botLastSendMs[botId] = System.currentTimeMillis()
        }
    }
}
