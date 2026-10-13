package com.college.bustracker

import com.college.bustracker.data.model.Announcement
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.util.AnnouncementTopicManager
import com.college.bustracker.util.LocaleHelper
import com.college.bustracker.util.TopicSubscriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementAndLocalizationTest {

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
    // 1. LOCALIZATION LOGIC & LANGUAGE CODES
    // =========================================================================

    @Test
    fun testLanguageConstants() {
        assertEquals("en", LocaleHelper.LANGUAGE_EN)
        assertEquals("kn", LocaleHelper.LANGUAGE_KN)
        assertEquals("fleetx_language_prefs", LocaleHelper.PREFS_NAME)
        assertEquals("selected_language", LocaleHelper.KEY_LANGUAGE)
    }

    // =========================================================================
    // 2. ANNOUNCEMENT MODEL & BILINGUAL DISPLAY HELPERS
    // =========================================================================

    @Test
    fun testAnnouncementBilingualDisplay_bothLanguagesPresent() {
        val announcement = Announcement(
            id = "ann-1",
            titleEn = "Bus Delay Notice",
            messageEn = "Bus 09 will be delayed by 20 minutes due to traffic.",
            titleKn = "ಬಸ್ ವಿಳಂಬ ಮಾಹಿತಿ",
            messageKn = "ಸಂಚಾರ ದಟ್ಟಣೆಯಿಂದಾಗಿ ಬಸ್ 09 ಸುಮಾರು 20 ನಿಮಿಷ ತಡವಾಗಿ ಬರಲಿದೆ."
        )

        // When English is selected
        assertEquals("Bus Delay Notice", announcement.getDisplayTitle(isKannada = false))
        assertEquals("Bus 09 will be delayed by 20 minutes due to traffic.", announcement.getDisplayMessage(isKannada = false))

        // When Kannada is selected
        assertEquals("ಬಸ್ ವಿಳಂಬ ಮಾಹಿತಿ", announcement.getDisplayTitle(isKannada = true))
        assertEquals("ಸಂಚಾರ ದಟ್ಟಣೆಯಿಂದಾಗಿ ಬಸ್ 09 ಸುಮಾರು 20 ನಿಮಿಷ ತಡವಾಗಿ ಬರಲಿದೆ.", announcement.getDisplayMessage(isKannada = true))
    }

    @Test
    fun testAnnouncementBilingualDisplay_fallbackWhenOneMissing() {
        // English only announcement
        val englishOnly = Announcement(
            id = "ann-en",
            titleEn = "Holiday on Monday",
            messageEn = "College will remain closed on Monday."
        )
        // Kannada user should gracefully fallback to English
        assertEquals("Holiday on Monday", englishOnly.getDisplayTitle(isKannada = true))
        assertEquals("College will remain closed on Monday.", englishOnly.getDisplayMessage(isKannada = true))

        // Kannada only announcement
        val kannadaOnly = Announcement(
            id = "ann-kn",
            titleKn = "ರಜೆಯ ಪ್ರಕಟಣೆ",
            messageKn = "ಸೋಮವಾರ ಕಾಲೇಜಿಗೆ ರಜೆ ಇರಲಿದೆ."
        )
        // English user should gracefully fallback to Kannada
        assertEquals("ರಜೆಯ ಪ್ರಕಟಣೆ", kannadaOnly.getDisplayTitle(isKannada = false))
        assertEquals("ಸೋಮವಾರ ಕಾಲೇಜಿಗೆ ರಜೆ ಇರಲಿದೆ.", kannadaOnly.getDisplayMessage(isKannada = false))
    }

    // =========================================================================
    // 3. ANNOUNCEMENT ACTIVE STATE & SCHEDULING / EXPIRY
    // =========================================================================

    @Test
    fun testAnnouncementActiveState_draftOrArchived() {
        val now = 1_000_000L

        val draft = Announcement(id = "1", status = Announcement.STATUS_DRAFT)
        assertFalse(draft.isCurrentlyActive(now))

        val archived = Announcement(id = "2", status = Announcement.STATUS_ARCHIVED)
        assertFalse(archived.isCurrentlyActive(now))

        val published = Announcement(id = "3", status = Announcement.STATUS_PUBLISHED)
        assertTrue(published.isCurrentlyActive(now))
    }

    @Test
    fun testAnnouncementActiveState_scheduledPublication() {
        val now = 1_000_000L

        // Scheduled for future -> not active yet
        val future = Announcement(
            id = "1",
            status = Announcement.STATUS_PUBLISHED,
            scheduledAt = now + 50_000L
        )
        assertFalse(future.isCurrentlyActive(now))

        // Scheduled in past -> active
        val past = Announcement(
            id = "2",
            status = Announcement.STATUS_PUBLISHED,
            scheduledAt = now - 10_000L
        )
        assertTrue(past.isCurrentlyActive(now))
    }

    @Test
    fun testAnnouncementActiveState_expiry() {
        val now = 1_000_000L

        // Expired in past -> not active
        val expired = Announcement(
            id = "1",
            status = Announcement.STATUS_PUBLISHED,
            expiresAt = now - 1_000L
        )
        assertFalse(expired.isCurrentlyActive(now))

        // Expires in future -> active
        val notExpired = Announcement(
            id = "2",
            status = Announcement.STATUS_PUBLISHED,
            expiresAt = now + 50_000L
        )
        assertTrue(notExpired.isCurrentlyActive(now))

        // No expiry set -> active
        val noExpiry = Announcement(
            id = "3",
            status = Announcement.STATUS_PUBLISHED,
            expiresAt = null
        )
        assertTrue(noExpiry.isCurrentlyActive(now))
    }

    // =========================================================================
    // 4. STUDENT RELEVANCE EVALUATION
    // =========================================================================

    @Test
    fun testStudentRelevance_generalAndRoleTargeting() {
        val now = 1_000_000L

        val allAnnouncement = Announcement(
            id = "1",
            targetAudience = Announcement.AUDIENCE_ALL,
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(allAnnouncement.isRelevantToStudent("BUS-01", "ROUTE-01", "STOP-01", now))
        assertTrue(allAnnouncement.isRelevantToStudent(null, null, null, now))

        val studentsOnly = Announcement(
            id = "2",
            targetAudience = Announcement.AUDIENCE_STUDENTS_ONLY,
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(studentsOnly.isRelevantToStudent("BUS-01", "ROUTE-01", "STOP-01", now))
        assertTrue(studentsOnly.isRelevantToStudent(null, null, null, now))

        val driversOnly = Announcement(
            id = "3",
            targetAudience = Announcement.AUDIENCE_DRIVERS_ONLY,
            status = Announcement.STATUS_PUBLISHED
        )
        assertFalse(driversOnly.isRelevantToStudent("BUS-01", "ROUTE-01", "STOP-01", now))
    }

    @Test
    fun testStudentRelevance_busRouteStopTargeting() {
        val now = 1_000_000L

        val busTargeted = Announcement(
            id = "1",
            targetAudience = Announcement.AUDIENCE_SELECTED_BUS,
            targetId = "BUS-09",
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(busTargeted.isRelevantToStudent("BUS-09", "ROUTE-01", "STOP-01", now))
        assertTrue(busTargeted.isRelevantToStudent(" bus-09 ", "ROUTE-01", "STOP-01", now)) // Case & whitespace insensitive
        assertFalse(busTargeted.isRelevantToStudent("BUS-10", "ROUTE-01", "STOP-01", now))
        assertFalse(busTargeted.isRelevantToStudent(null, "ROUTE-01", "STOP-01", now))

        val routeTargeted = Announcement(
            id = "2",
            targetAudience = Announcement.AUDIENCE_SELECTED_ROUTE,
            targetId = "ROUTE-02",
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(routeTargeted.isRelevantToStudent("BUS-09", "ROUTE-02", "STOP-01", now))
        assertFalse(routeTargeted.isRelevantToStudent("BUS-09", "ROUTE-01", "STOP-01", now))

        val stopTargeted = Announcement(
            id = "3",
            targetAudience = Announcement.AUDIENCE_SELECTED_STOP,
            targetId = "STOP-05",
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(stopTargeted.isRelevantToStudent("BUS-09", "ROUTE-02", "STOP-05", now))
        assertFalse(stopTargeted.isRelevantToStudent("BUS-09", "ROUTE-02", "STOP-04", now))
    }

    // =========================================================================
    // 5. DRIVER RELEVANCE EVALUATION
    // =========================================================================

    @Test
    fun testDriverRelevance_generalAndRoleTargeting() {
        val now = 1_000_000L

        val allAnnouncement = Announcement(
            id = "1",
            targetAudience = Announcement.AUDIENCE_ALL,
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(allAnnouncement.isRelevantToDriver("BUS-01", "ROUTE-01", now))

        val driversOnly = Announcement(
            id = "2",
            targetAudience = Announcement.AUDIENCE_DRIVERS_ONLY,
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(driversOnly.isRelevantToDriver("BUS-01", "ROUTE-01", now))

        val studentsOnly = Announcement(
            id = "3",
            targetAudience = Announcement.AUDIENCE_STUDENTS_ONLY,
            status = Announcement.STATUS_PUBLISHED
        )
        assertFalse(studentsOnly.isRelevantToDriver("BUS-01", "ROUTE-01", now))
    }

    @Test
    fun testDriverRelevance_busAndRouteTargeting() {
        val now = 1_000_000L

        val busTargeted = Announcement(
            id = "1",
            targetAudience = Announcement.AUDIENCE_SELECTED_BUS,
            targetId = "BUS-04",
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(busTargeted.isRelevantToDriver("BUS-04", "ROUTE-01", now))
        assertFalse(busTargeted.isRelevantToDriver("BUS-05", "ROUTE-01", now))

        val routeTargeted = Announcement(
            id = "2",
            targetAudience = Announcement.AUDIENCE_SELECTED_ROUTE,
            targetId = "ROUTE-03",
            status = Announcement.STATUS_PUBLISHED
        )
        assertTrue(routeTargeted.isRelevantToDriver("BUS-04", "ROUTE-03", now))
        assertFalse(routeTargeted.isRelevantToDriver("BUS-04", "ROUTE-01", now))
    }

    // =========================================================================
    // 6. BUS TIMING CHANGE WORKFLOW SERIALIZATION & DECOUPLING
    // =========================================================================

    @Test
    fun testBusTimingChangeSerialization() {
        val timingAnnouncement = Announcement(
            id = "time-1",
            titleEn = "BUS-09 Timing Change",
            messageEn = "Morning departure changed from 07:00 to 07:30.",
            titleKn = "BUS-09 ಸಮಯ ಬದಲಾವಣೆ",
            messageKn = "ಬೆಳಗಿನ ನಿರ್ಗಮನ ಸಮಯ 07:00 ರಿಂದ 07:30 ಕ್ಕೆ ಬದಲಾಗಿದೆ.",
            category = Announcement.CATEGORY_TIMING_CHANGE,
            priority = Announcement.PRIORITY_IMPORTANT,
            targetAudience = Announcement.AUDIENCE_SELECTED_BUS,
            targetId = "BUS-09",
            targetName = "Bus 09",
            timingBusId = "BUS-09",
            timingChangeType = "Morning Departure",
            previousTime = "07:00",
            newTime = "07:30",
            effectiveDate = "2026-10-20",
            endDate = "2026-10-25",
            changeReason = "College examination schedule"
        )

        val map = timingAnnouncement.toMap()
        assertEquals("BUS-09", map["timingBusId"])
        assertEquals("Morning Departure", map["timingChangeType"])
        assertEquals("07:00", map["previousTime"])
        assertEquals("07:30", map["newTime"])
        assertEquals("2026-10-20", map["effectiveDate"])
        assertEquals("2026-10-25", map["endDate"])
        assertEquals("College examination schedule", map["changeReason"])
        assertEquals(Announcement.CATEGORY_TIMING_CHANGE, map["category"])
        assertEquals(Announcement.PRIORITY_IMPORTANT, map["priority"])
    }

    // =========================================================================
    // 7. ANNOUNCEMENT TOPIC MANAGER SUBSCRIPTIONS
    // =========================================================================

    @Test
    fun testTopicManager_formatting() {
        assertEquals("announcements_bus_BUS-09", AnnouncementTopicManager.formatBusTopic("BUS-09"))
        assertEquals("announcements_bus_BUS_09", AnnouncementTopicManager.formatBusTopic("BUS 09"))
        assertEquals("announcements_route_ROUTE-01", AnnouncementTopicManager.formatRouteTopic("ROUTE-01"))
    }

    @Test
    fun testTopicManager_roleSubscriptions() {
        val driverSubscriber = FakeTopicSubscriber()
        AnnouncementTopicManager.updateRoleSubscriptions(UserRole.DRIVER, driverSubscriber)
        assertTrue(driverSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_ALL))
        assertTrue(driverSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_DRIVERS))
        assertTrue(driverSubscriber.unsubscribedTopics.contains(AnnouncementTopicManager.TOPIC_STUDENTS))

        val studentSubscriber = FakeTopicSubscriber()
        AnnouncementTopicManager.updateRoleSubscriptions(null, studentSubscriber)
        assertTrue(studentSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_ALL))
        assertTrue(studentSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_STUDENTS))
        assertTrue(studentSubscriber.unsubscribedTopics.contains(AnnouncementTopicManager.TOPIC_DRIVERS))

        val adminSubscriber = FakeTopicSubscriber()
        AnnouncementTopicManager.updateRoleSubscriptions(UserRole.ADMIN, adminSubscriber)
        assertTrue(adminSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_ALL))
        assertTrue(adminSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_DRIVERS))
        assertTrue(adminSubscriber.subscribedTopics.contains(AnnouncementTopicManager.TOPIC_STUDENTS))
    }

    @Test
    fun testTopicManager_busSubscriptionSwitching() {
        val subscriber = FakeTopicSubscriber()
        AnnouncementTopicManager.updateBusSubscription(
            oldBusId = "BUS-01",
            newBusId = "BUS-02",
            subscriber = subscriber
        )

        assertTrue(subscriber.unsubscribedTopics.contains("announcements_bus_BUS-01"))
        assertTrue(subscriber.subscribedTopics.contains("announcements_bus_BUS-02"))
    }
}
