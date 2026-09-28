package com.college.bustracker

import com.college.bustracker.data.model.BusSwap
import com.college.bustracker.util.BusTopicManager
import com.college.bustracker.util.TopicSubscriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusTopicManagerTest {

    // Test Fake Subscriber
    class FakeTopicSubscriber : TopicSubscriber {
        val subscribedTopics = mutableListOf<String>()
        val unsubscribedTopics = mutableListOf<String>()

        override fun subscribe(topic: String) {
            subscribedTopics.add(topic)
        }

        override fun unsubscribe(topic: String) {
            unsubscribedTopics.add(topic)
        }
    }

    // =========================================================================
    // 1. TOPIC FORMATTING & SANITIZATION
    // =========================================================================

    @Test
    fun testTopicFormatting_standardIds() {
        val topic = BusTopicManager.formatTopic("BUS-25", "STOP-01")
        assertEquals("bus_BUS-25_stop_STOP-01", topic)

        val topic2 = BusTopicManager.formatTopic("BUS-25", "STOP-02")
        assertEquals("bus_BUS-25_stop_STOP-02", topic2)

        val topic3 = BusTopicManager.formatTopic("BUS-25", "STOP-03")
        assertEquals("bus_BUS-25_stop_STOP-03", topic3)
    }

    @Test
    fun testTopicFormatting_sanitizationOfSpacesAndSpecialChars() {
        val topicSpaces = BusTopicManager.formatTopic("BUS 25", "STOP 02")
        assertEquals("bus_BUS_25_stop_STOP_02", topicSpaces)

        val topicSpecial = BusTopicManager.formatTopic("BUS#25!", "STOP@01$")
        assertEquals("bus_BUS_25__stop_STOP_01_", topicSpecial)

        // FCM regex test: [a-zA-Z0-9-_.~%]+
        val fcmRegex = Regex("^[a-zA-Z0-9-_.~%]+$")
        assertTrue("Topic must match FCM topic regex", fcmRegex.matches(topicSpecial))
    }

    // =========================================================================
    // 2. STOP-SPECIFIC STUDENT TARGETING EVALUATION
    // =========================================================================

    @Test
    fun testStudentTargeting_sameBusAndAffectedStop_isTargeted() {
        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertTrue(
            "Student on BUS-25 at affected STOP-01 must be targeted",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-01", swap)
        )
        assertTrue(
            "Student on BUS-25 at affected STOP-02 must be targeted",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-02", swap)
        )
    }

    @Test
    fun testStudentTargeting_sameBusUnaffectedStop_isNotTargeted() {
        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertFalse(
            "Student on BUS-25 at unaffected STOP-03 must NOT be targeted",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-03", swap)
        )
        assertFalse(
            "Student on BUS-25 at unaffected STOP-04 must NOT be targeted",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-04", swap)
        )
    }

    @Test
    fun testStudentTargeting_differentBus_isNotTargeted() {
        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertFalse(
            "Student on BUS-12 at STOP-02 must NOT be targeted for BUS-25 swap",
            BusTopicManager.isStudentTargeted("BUS-12", "STOP-02", swap)
        )
    }

    @Test
    fun testStudentTargeting_replacementBusUsedForTargeting_isNotTargeted() {
        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertFalse(
            "Student on replacement bus BUS-30 must NOT be targeted for original bus swap",
            BusTopicManager.isStudentTargeted("BUS-30", "STOP-02", swap)
        )
    }

    @Test
    fun testStudentTargeting_nullOrBlankFields_isNotTargeted() {
        val swap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "ACTIVE"
        )

        assertFalse(BusTopicManager.isStudentTargeted(null, "STOP-01", swap))
        assertFalse(BusTopicManager.isStudentTargeted("BUS-25", null, swap))
        assertFalse(BusTopicManager.isStudentTargeted("", "STOP-01", swap))
        assertFalse(BusTopicManager.isStudentTargeted("BUS-25", "", swap))
        assertFalse(BusTopicManager.isStudentTargeted(null, null, swap))
    }

    @Test
    fun testStudentTargeting_inactiveSwapStatus_isNotTargeted() {
        val cancelledSwap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02"),
            status = "CANCELLED"
        )

        assertFalse(
            "Cancelled swap must not trigger active swap notification",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-01", cancelledSwap)
        )

        val resolvedSwap = cancelledSwap.copy(status = "RESOLVED")
        assertFalse(
            "Resolved swap must not trigger active swap notification",
            BusTopicManager.isStudentTargeted("BUS-25", "STOP-01", resolvedSwap)
        )
    }

    // =========================================================================
    // 3. MULTI-STOP FLEET SIMULATION
    // =========================================================================

    @Test
    fun testMultiStopSimulation_threeStopsAffected_onlyCorrectStudentsNotified() {
        val swap = BusSwap(
            swapId = "SWAP-FLEET-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-01", "STOP-02", "STOP-03"),
            status = "ACTIVE"
        )

        data class Student(val id: String, val bus: String, val stop: String)

        val students = listOf(
            Student("S1", "BUS-25", "STOP-01"), // Should receive
            Student("S2", "BUS-25", "STOP-02"), // Should receive
            Student("S3", "BUS-25", "STOP-03"), // Should receive
            Student("S4", "BUS-25", "STOP-04"), // Should NOT receive
            Student("S5", "BUS-25", "STOP-05"), // Should NOT receive
            Student("S6", "BUS-12", "STOP-01")  // Should NOT receive
        )

        val notifiedStudents = students.filter { BusTopicManager.isStudentTargeted(it.bus, it.stop, swap) }

        assertEquals(3, notifiedStudents.size)
        assertEquals(listOf("S1", "S2", "S3"), notifiedStudents.map { it.id })
    }

    // =========================================================================
    // 4. CANCELLATION TARGETING
    // =========================================================================

    @Test
    fun testCancellationTargeting_notifiesPreviouslyAffectedStudentsOnly() {
        val cancelledSwap = BusSwap(
            swapId = "SWAP-01",
            originalBusId = "BUS-25",
            replacementBusId = "BUS-30",
            affectedStopIds = listOf("STOP-02"),
            status = "CANCELLED"
        )

        assertTrue(
            "Affected student should be notified of cancellation",
            BusTopicManager.isStudentTargetedForCancellation("BUS-25", "STOP-02", cancelledSwap)
        )
        assertFalse(
            "Unaffected student should NOT be notified of cancellation",
            BusTopicManager.isStudentTargetedForCancellation("BUS-25", "STOP-04", cancelledSwap)
        )
        assertFalse(
            "Different bus student should NOT be notified of cancellation",
            BusTopicManager.isStudentTargetedForCancellation("BUS-12", "STOP-02", cancelledSwap)
        )
    }

    // =========================================================================
    // 5. SUBSCRIPTION TRANSITIONS (Pure Transition Logic)
    // =========================================================================

    @Test
    fun testSubscriptionTransition_stopChange_unsubscribesOldAndSubscribesNew() {
        val fakeSubscriber = FakeTopicSubscriber()

        fun performTransition(
            currentTopic: String?,
            newBusId: String?,
            newStopId: String?
        ): String? {
            val newTopic = if (!newBusId.isNullOrBlank() && !newStopId.isNullOrBlank()) {
                BusTopicManager.formatTopic(newBusId, newStopId)
            } else null

            if (currentTopic == newTopic) return currentTopic

            if (!currentTopic.isNullOrBlank()) {
                fakeSubscriber.unsubscribe(currentTopic)
            }
            if (!newTopic.isNullOrBlank()) {
                fakeSubscriber.subscribe(newTopic)
            }
            return newTopic
        }

        // Step 1: Student selects BUS-25, STOP-02
        var activeTopic: String? = null
        activeTopic = performTransition(activeTopic, "BUS-25", "STOP-02")
        assertEquals("bus_BUS-25_stop_STOP-02", activeTopic)
        assertEquals(listOf("bus_BUS-25_stop_STOP-02"), fakeSubscriber.subscribedTopics)
        assertTrue(fakeSubscriber.unsubscribedTopics.isEmpty())

        // Step 2: Student changes STOP-02 -> STOP-04
        activeTopic = performTransition(activeTopic, "BUS-25", "STOP-04")
        assertEquals("bus_BUS-25_stop_STOP-04", activeTopic)
        assertEquals(listOf("bus_BUS-25_stop_STOP-02"), fakeSubscriber.unsubscribedTopics)
        assertEquals(
            listOf("bus_BUS-25_stop_STOP-02", "bus_BUS-25_stop_STOP-04"),
            fakeSubscriber.subscribedTopics
        )

        // Step 3: Student changes bus BUS-25 -> BUS-12
        activeTopic = performTransition(activeTopic, "BUS-12", "STOP-04")
        assertEquals("bus_BUS-12_stop_STOP-04", activeTopic)
        assertEquals(
            listOf("bus_BUS-25_stop_STOP-02", "bus_BUS-25_stop_STOP-04"),
            fakeSubscriber.unsubscribedTopics
        )
        assertEquals(
            listOf("bus_BUS-25_stop_STOP-02", "bus_BUS-25_stop_STOP-04", "bus_BUS-12_stop_STOP-04"),
            fakeSubscriber.subscribedTopics
        )

        // Step 4: Deselection / null stop
        activeTopic = performTransition(activeTopic, "BUS-12", null)
        assertEquals(null, activeTopic)
        assertEquals(
            listOf("bus_BUS-25_stop_STOP-02", "bus_BUS-25_stop_STOP-04", "bus_BUS-12_stop_STOP-04"),
            fakeSubscriber.unsubscribedTopics
        )
    }
}
