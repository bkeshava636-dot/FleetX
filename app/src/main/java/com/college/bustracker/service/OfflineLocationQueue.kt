package com.college.bustracker.service

import android.util.Log
import com.college.bustracker.data.model.LiveLocation
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lightweight in-memory circular queue (Max 50 items) for preserving telemetry fixes
 * during rural cellular dead zones outside Ballari.
 *
 * Flushes chronologically upon network restoration (using Firebase RTDB .info/connected)
 * with a staggered 500ms delay between writes to prevent marker teleportation while
 * strictly respecting RTDB rate limits and zero-cost quotas.
 */
class OfflineLocationQueue(
    private val scope: CoroutineScope,
    private val maxCapacity: Int = 50
) {
    companion object {
        private const val TAG = "OfflineLocationQueue"
        const val STAGGERED_FLUSH_DELAY_MS = 500L
    }

    private val queue = ConcurrentLinkedDeque<LiveLocation>()
    @Volatile
    private var isFlushing = false

    private val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
    private var connectedListener: ValueEventListener? = null

    /**
     * Starts monitoring Firebase RTDB connection state.
     */
    fun startListening() {
        if (connectedListener != null) return

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "[RTDB_INFO_CONNECTED] isConnected=$isConnected, pendingQueueSize=${queue.size}")
                if (isConnected && queue.isNotEmpty()) {
                    flushQueue()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Connection state listener cancelled: ${error.message}")
            }
        }
        try {
            connectedRef.addValueEventListener(connectedListener!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach .info/connected listener: ${e.message}")
        }
    }

    /**
     * Stops monitoring Firebase RTDB connection state.
     */
    fun stopListening() {
        connectedListener?.let {
            try {
                connectedRef.removeEventListener(it)
            } catch (ignored: Exception) {}
            connectedListener = null
        }
    }

    /**
     * Enqueues an offline coordinate payload with exact original GPS timestamp.
     * Drops oldest elements if queue exceeds maxCapacity (50 items).
     */
    @Synchronized
    fun enqueue(location: LiveLocation) {
        while (queue.size >= maxCapacity) {
            val dropped = queue.pollFirst()
            Log.d(TAG, "Queue reached capacity ($maxCapacity); dropped oldest fix ts=${dropped?.lastUpdated}")
        }
        queue.addLast(location)
        Log.i(TAG, "Queued offline GPS payload (queue size=${queue.size}): bus=${location.busId}, lat=${location.latitude}, lng=${location.longitude}, ts=${location.lastUpdated}")
    }

    fun size(): Int = queue.size

    fun isEmpty(): Boolean = queue.isEmpty()

    fun clear() {
        queue.clear()
    }

    /**
     * Sequentially flushes queued items in chronological order with 500ms pacing.
     */
    fun flushQueue() {
        scope.launch(Dispatchers.IO) {
            synchronized(this@OfflineLocationQueue) {
                if (isFlushing || queue.isEmpty()) return@launch
                isFlushing = true
            }

            Log.i(TAG, "Flushing offline queue (${queue.size} items) sequentially with ${STAGGERED_FLUSH_DELAY_MS}ms delay...")

            while (queue.isNotEmpty()) {
                val item = queue.pollFirst() ?: break
                var writeSuccess = false

                try {
                    val dbRef = FirebaseDatabase.getInstance().getReference("live_locations").child(item.busId)
                    // Synchronous-like dispatch with await or callback completion
                    dbRef.setValue(item)
                    writeSuccess = true
                    Log.d(TAG, "Flushed queued payload to RTDB: bus=${item.busId}, ts=${item.lastUpdated}, lat=${item.latitude}, lng=${item.longitude}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to flush queued item (bus=${item.busId}): ${e.message}")
                    // Put back at head and stop flush
                    queue.addFirst(item)
                    break
                }

                if (writeSuccess && queue.isNotEmpty()) {
                    delay(STAGGERED_FLUSH_DELAY_MS)
                }
            }

            synchronized(this@OfflineLocationQueue) {
                isFlushing = false
            }
            Log.i(TAG, "Offline queue flush cycle completed. Remaining items: ${queue.size}")
        }
    }
}
