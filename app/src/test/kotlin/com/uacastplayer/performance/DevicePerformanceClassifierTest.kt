package com.uacastplayer.performance

import org.junit.Assert.assertEquals
import org.junit.Test

private const val GB = 1024L * 1024 * 1024

class DevicePerformanceClassifierTest {

    @Test
    fun `low RAM, few cores, old SDK classifies as LOW_END`() {
        val tier = DevicePerformanceClassifier.classify(totalRamBytes = 1 * GB, cpuCoreCount = 2, sdkInt = 23)
        assertEquals(DeviceTier.LOW_END, tier)
    }

    @Test
    fun `high RAM, many cores, recent SDK classifies as HIGH_END`() {
        val tier = DevicePerformanceClassifier.classify(totalRamBytes = 8 * GB, cpuCoreCount = 8, sdkInt = 33)
        assertEquals(DeviceTier.HIGH_END, tier)
    }

    @Test
    fun `moderate specs classify as MID_RANGE`() {
        val tier = DevicePerformanceClassifier.classify(totalRamBytes = 3 * GB, cpuCoreCount = 4, sdkInt = 28)
        assertEquals(DeviceTier.MID_RANGE, tier)
    }

    @Test
    fun `one strong factor and two weak factors still lands at MID_RANGE or below`() {
        val tier = DevicePerformanceClassifier.classify(totalRamBytes = 8 * GB, cpuCoreCount = 2, sdkInt = 23)
        assertEquals(DeviceTier.MID_RANGE, tier)
    }

    @Test
    fun `all factors at zero classifies as LOW_END`() {
        val tier = DevicePerformanceClassifier.classify(totalRamBytes = 512L * 1024 * 1024, cpuCoreCount = 1, sdkInt = 21)
        assertEquals(DeviceTier.LOW_END, tier)
    }

    @Test
    fun `large playlist downgrades HIGH_END by one tier`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.HIGH_END, playlistChannelCount = 2000, epgProgrammeCount = 0,
        )
        assertEquals(DeviceTier.MID_RANGE, result)
    }

    @Test
    fun `very large playlist downgrades HIGH_END by two tiers`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.HIGH_END, playlistChannelCount = 6000, epgProgrammeCount = 0,
        )
        assertEquals(DeviceTier.LOW_END, result)
    }

    @Test
    fun `downgrade never goes below LOW_END`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.MID_RANGE, playlistChannelCount = 6000, epgProgrammeCount = 0,
        )
        assertEquals(DeviceTier.LOW_END, result)
    }

    @Test
    fun `large EPG alone also triggers a downgrade`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.HIGH_END, playlistChannelCount = 0, epgProgrammeCount = 50_000,
        )
        assertEquals(DeviceTier.MID_RANGE, result)
    }

    @Test
    fun `very large EPG downgrades by two tiers`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.HIGH_END, playlistChannelCount = 0, epgProgrammeCount = 150_000,
        )
        assertEquals(DeviceTier.LOW_END, result)
    }

    @Test
    fun `small content sizes leave the tier unchanged`() {
        val result = DevicePerformanceClassifier.adjustForContentSize(
            DeviceTier.HIGH_END, playlistChannelCount = 100, epgProgrammeCount = 500,
        )
        assertEquals(DeviceTier.HIGH_END, result)
    }
}
