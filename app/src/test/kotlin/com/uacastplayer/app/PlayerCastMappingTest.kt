package com.uacastplayer.app

import com.uacastplayer.cast.CastSideEffect
import com.uacastplayer.cast.CastStatusMessage
import com.uacastplayer.core.cast.AudioCodec
import com.uacastplayer.core.cast.VideoCodec
import com.uacastplayer.player.PlayerCastChannel
import com.uacastplayer.player.PlayerCastSideEffect
import com.uacastplayer.player.PlayerCastStatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerCastMappingTest {

    @Test
    fun `channel fields cross the composition adapter unchanged`() {
        val source = PlayerCastChannel(
            index = 7,
            streamUrl = "https://provider.example/live.m3u8",
            title = "Channel",
            userAgent = "UA",
            referrer = "https://provider.example/",
            logoUrl = "https://provider.example/logo.png",
        )

        val mapped = PlayerCastMapping.channel(source)

        assertEquals(source.index, mapped.index)
        assertEquals(source.streamUrl, mapped.streamUrl)
        assertEquals(source.title, mapped.title)
        assertEquals(source.userAgent, mapped.userAgent)
        assertEquals(source.referrer, mapped.referrer)
        assertEquals(source.logoUrl, mapped.logoUrl)
    }

    @Test
    fun `every repository side effect has a player-side equivalent`() {
        assertEquals(
            PlayerCastSideEffect.PauseLocalPlayer,
            PlayerCastMapping.effect(CastSideEffect.PauseLocalPlayer),
        )
        assertEquals(
            PlayerCastSideEffect.ResumeLocalPlayer,
            PlayerCastMapping.effect(CastSideEffect.ResumeLocalPlayer),
        )
        assertEquals(
            PlayerCastSideEffect.RecordIncompatibility("codec"),
            PlayerCastMapping.effect(CastSideEffect.RecordIncompatibility("codec")),
        )
        assertEquals(
            PlayerCastSideEffect.CloseProxySession,
            PlayerCastMapping.effect(CastSideEffect.CloseProxySession),
        )
        assertEquals(
            PlayerCastSideEffect.ApplyPendingChannelSwitch(4),
            PlayerCastMapping.effect(CastSideEffect.ApplyPendingChannelSwitch(4)),
        )
    }

    @Test
    fun `every status explanation crosses without losing codec detail`() {
        assertNull(PlayerCastMapping.status(null))
        assertEquals(
            PlayerCastStatusMessage.IncompatibleVideo(VideoCodec.Mpeg2Video),
            PlayerCastMapping.status(CastStatusMessage.IncompatibleVideo(VideoCodec.Mpeg2Video)),
        )
        assertEquals(
            PlayerCastStatusMessage.ProxyUnavailableIpv4Only,
            PlayerCastMapping.status(CastStatusMessage.ProxyUnavailableIpv4Only),
        )
        assertEquals(
            PlayerCastStatusMessage.Recovering,
            PlayerCastMapping.status(CastStatusMessage.Recovering),
        )
        assertEquals(
            PlayerCastStatusMessage.LikelyIncompatibleVideo(VideoCodec.Hevc),
            PlayerCastMapping.status(CastStatusMessage.LikelyIncompatibleVideo(VideoCodec.Hevc)),
        )
        assertEquals(
            PlayerCastStatusMessage.LikelyIncompatibleAudio(AudioCodec.Ac3),
            PlayerCastMapping.status(CastStatusMessage.LikelyIncompatibleAudio(AudioCodec.Ac3)),
        )
        assertEquals(
            PlayerCastStatusMessage.ReceiverLoadFailed,
            PlayerCastMapping.status(CastStatusMessage.ReceiverLoadFailed),
        )
    }
}
