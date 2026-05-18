package net.chikach.jujutsuintellij.cli

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Decodes ISO 8601 / RFC 3339 timestamp strings (jj template `Timestamp.format("%+")` output)
 * into [Date]. Falls back to the epoch on parse failure.
 */
object JjDateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.util.Date", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Date {
        val raw = decoder.decodeString()
        if (raw.isBlank()) return Date(0)
        return try {
            val odt = OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Date.from(odt.toInstant())
        } catch (_: Exception) {
            Date(0)
        }
    }

    override fun serialize(encoder: Encoder, value: Date) {
        throw UnsupportedOperationException("JjDateSerializer is read-only")
    }
}
