package com.college.bustracker

import com.college.bustracker.data.repository.BusRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusRepositoryDeserializationTest {

    @Test
    fun test01_explicitNullRouteIdDefaultsToEmptyString() {
        val data = mapOf<String, Any?>(
            "busNumber" to "TEST-01",
            "registrationNumber" to "KA-34-TEST-0002",
            "routeId" to null,
            "driverId" to "drv_1",
            "driverName" to "Test Driver Alpha Updated",
            "capacity" to 45L,
            "active" to true
        )

        val result = BusRepository.deserializeBusData("BUS-TEST-01", data)

        assertTrue(result.isSuccess)
        val bus = result.getOrThrow()
        assertEquals("BUS-TEST-01", bus.busId)
        assertEquals("TEST-01", bus.busNumber)
        assertEquals("KA-34-TEST-0002", bus.registrationNumber)
        assertEquals("", bus.routeId)
        assertEquals("drv_1", bus.driverId)
        assertEquals("Test Driver Alpha Updated", bus.driverName)
        assertEquals(45, bus.capacity)
        assertTrue(bus.active)
    }

    @Test
    fun test02_missingRouteIdDefaultsToEmptyString() {
        val data = mapOf<String, Any?>(
            "busId" to "BUS-02",
            "busNumber" to "BUS 02",
            "registrationNumber" to "KA-34-A-0002",
            "capacity" to 50L,
            "active" to true
        )

        val result = BusRepository.deserializeBusData("BUS-02", data)

        assertTrue(result.isSuccess)
        val bus = result.getOrThrow()
        assertEquals("BUS-02", bus.busId)
        assertEquals("", bus.routeId)
    }

    @Test
    fun test03_missingBusIdFallsBackToDocumentId() {
        val data = mapOf<String, Any?>(
            "busNumber" to "BUS 03",
            "routeId" to "ROUTE-03"
        )

        val result = BusRepository.deserializeBusData("BUS-03", data)

        assertTrue(result.isSuccess)
        val bus = result.getOrThrow()
        assertEquals("BUS-03", bus.busId)
        assertEquals("BUS 03", bus.busNumber)
        assertEquals("ROUTE-03", bus.routeId)
    }

    @Test
    fun test04_nullDriverIdAndDriverNameAreHandledSafely() {
        val data = mapOf<String, Any?>(
            "busNumber" to "BUS 04",
            "routeId" to "ROUTE-04",
            "driverId" to null,
            "driverName" to null
        )

        val result = BusRepository.deserializeBusData("BUS-04", data)

        assertTrue(result.isSuccess)
        val bus = result.getOrThrow()
        assertNull(bus.driverId)
        assertNull(bus.driverName)
    }

    @Test
    fun test05_validRouteIdIsPreserved() {
        val data = mapOf<String, Any?>(
            "busId" to "BUS-05",
            "busNumber" to "BUS 05",
            "registrationNumber" to "KA-34-A-0005",
            "routeId" to "ROUTE-05",
            "driverId" to "drv_5",
            "driverName" to "Driver Five",
            "capacity" to 52,
            "active" to true
        )

        val result = BusRepository.deserializeBusData("BUS-05", data)

        assertTrue(result.isSuccess)
        val bus = result.getOrThrow()
        assertEquals("ROUTE-05", bus.routeId)
        assertEquals(52, bus.capacity)
    }

    @Test
    fun test06_missingRequiredDisplayFieldsAreRejectedWithReason() {
        val missingBusNumber = mapOf<String, Any?>(
            "busId" to "BUS-BAD",
            "routeId" to "ROUTE-01"
        )

        val result = BusRepository.deserializeBusData("BUS-BAD", missingBusNumber)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("busNumber") == true)
    }

    @Test
    fun test07_malformedDocumentDoesNotHideValidBusesInBatch() {
        val docs = listOf(
            "BUS-BAD" to mapOf<String, Any?>("routeId" to "ROUTE-01"),
            "BUS-NULL-DATA" to null,
            "BUS-TEST-01" to mapOf<String, Any?>(
                "busNumber" to "TEST-01",
                "routeId" to null,
                "active" to true
            ),
            "BUS-02" to mapOf<String, Any?>(
                "busNumber" to "BUS 02",
                "routeId" to "ROUTE-02",
                "active" to true
            )
        )

        val batch = BusRepository.deserializeBusBatch(docs)

        assertEquals(4, batch.receivedCount)
        assertEquals(2, batch.buses.size)
        assertEquals(2, batch.rejectedReasons.size)
        assertTrue(batch.rejectedReasons.containsKey("BUS-BAD"))
        assertTrue(batch.rejectedReasons.containsKey("BUS-NULL-DATA"))
        assertEquals(listOf("BUS-TEST-01", "BUS-02"), batch.buses.map { it.busId })
    }
}
