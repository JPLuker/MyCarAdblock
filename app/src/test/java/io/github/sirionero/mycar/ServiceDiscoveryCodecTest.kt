package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a head unit says the video codec: for the service (MediaSinkService field 1), or in each
 * VideoConfiguration (field 10) and not for the service, as one unit does - its "unknown(0)" read
 * like a unit wanting another codec, while every configuration said H264_BP.
 */
class ServiceDiscoveryCodecTest {

    private fun varintField(field: Int, value: Long) = Protobuf.varintField(field, value)

    private fun lenField(field: Int, body: ByteArray): ByteArray =
        Protobuf.varint(((field shl 3) or 2).toLong()) + Protobuf.varint(body.size.toLong()) + body

    private fun response(serviceCodec: Long?, configCodec: Long?): ByteArray {
        var config = varintField(1, 2L) + varintField(2, 2L) + varintField(5, 160L)
        if (configCodec != null) config += varintField(10, configCodec)
        var sink = lenField(4, config) + lenField(4, config)
        if (serviceCodec != null) sink = varintField(1, serviceCodec) + sink
        return lenField(1, varintField(1, 1L) + lenField(3, sink))
    }

    @Test
    fun `the codec said in each configuration only`() {
        val video = ServiceDiscovery.findVideoService(response(serviceCodec = null, configCodec = 3L))!!
        assertEquals("unknown(0)", video.codecName)
        assertEquals(listOf("VIDEO_H264_BP"), video.configCodecNames)
        assertEquals(3, video.configs[0].codecType)
        assertTrue(video.saysH264Bp)
    }

    @Test
    fun `the codec said for the service`() {
        val video = ServiceDiscovery.findVideoService(response(serviceCodec = 3L, configCodec = null))!!
        assertEquals("VIDEO_H264_BP", video.codecName)
        assertEquals(emptyList<String>(), video.configCodecNames)
        assertTrue(video.saysH264Bp)
    }

    @Test
    fun `no H264_BP anywhere`() {
        assertFalse(ServiceDiscovery.findVideoService(response(serviceCodec = null, configCodec = null))!!.saysH264Bp)
        val h265 = ServiceDiscovery.findVideoService(response(serviceCodec = 7L, configCodec = 7L))!!
        assertFalse(h265.saysH264Bp)
        assertEquals(listOf("VIDEO_H265"), h265.configCodecNames)
    }
}
