package com.college.bustracker.util

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Manages atomic RTDB lease locks for secondary student beacons.
 *
 * Ensures that when multiple students with automatic ride sharing are riding the same bus,
 * only ONE elected student beacon uploads telemetry fixes at any given time (saving battery
 * across student phones and keeping Firebase RTDB Spark tier usage near 0).
 *
 * The lease is granted for 45 seconds and renewed on each GPS fix. If the elected beacon
 * disconnects or leaves the bus, the onDisconnect hook or 45s expiration automatically allows
 * another standby student to seamlessly take over.
 */
object StudentBeaconArbitrator {

    private const val TAG = "StudentArbitrator"
    const val LEASE_DURATION_MS = 45_000L

    fun tryAcquireOrRenewLease(
        busId: String,
        studentSourceId: String,
        onResult: (isLeaseHolder: Boolean) -> Unit
    ) {
        if (busId.isBlank() || studentSourceId.isBlank()) {
            onResult(false)
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("bus_beacons").child(busId)
        val now = System.currentTimeMillis()

        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentHolder = mutableData.child("sourceId").getValue(String::class.java)
                val expiresAt = mutableData.child("leaseExpiresAt").getValue(Long::class.java) ?: 0L

                val isExpired = now >= expiresAt
                val isCurrentHolder = currentHolder == studentSourceId

                return if (currentHolder == null || isExpired || isCurrentHolder) {
                    // Acquire or renew lease
                    mutableData.child("sourceId").value = studentSourceId
                    mutableData.child("leaseExpiresAt").value = now + LEASE_DURATION_MS
                    mutableData.child("updatedAt").value = now
                    Transaction.success(mutableData)
                } else {
                    // Another student holds an active, unexpired lease; abort write
                    Transaction.abort()
                }
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.w(TAG, "Arbitration transaction error for bus $busId: ${error.message}")
                    onResult(false)
                    return
                }

                if (committed) {
                    ref.onDisconnect().removeValue()
                    Log.d(TAG, "Lease GRANTED/RENEWED to $studentSourceId for bus $busId")
                    onResult(true)
                } else {
                    val activeHolder = currentData?.child("sourceId")?.getValue(String::class.java)
                    Log.d(TAG, "Lease HELD by another student ($activeHolder) for bus $busId; standing by")
                    onResult(false)
                }
            }
        })
    }

    fun releaseLease(busId: String, studentSourceId: String) {
        if (busId.isBlank() || studentSourceId.isBlank()) return
        val ref = FirebaseDatabase.getInstance().getReference("bus_beacons").child(busId)
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentHolder = mutableData.child("sourceId").getValue(String::class.java)
                return if (currentHolder == studentSourceId) {
                    mutableData.value = null
                    Transaction.success(mutableData)
                } else {
                    Transaction.abort()
                }
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Log.d(TAG, "Lease RELEASED by $studentSourceId for bus $busId")
                }
            }
        })
    }
}
