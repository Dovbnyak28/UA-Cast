package com.uacastplayer.data.remote

/** Explicit LAN IPv4 endpoints only: no credentials, redirects, DNS lookup, paths or public servers. */
data class RemoteEndpoint(val host: String, val port: Int) {
    init { require(isLocalIpv4(host) && port in 1..MAX_PORT) }

    override fun toString(): String = "$host:$port"

    companion object {
        private const val MAX_PORT = 65_535
        private const val PRIVATE_CLASS_A = 10
        private const val LOOPBACK = 127
        private const val PRIVATE_CLASS_B = 172
        private const val PRIVATE_B_MIN = 16
        private const val PRIVATE_B_MAX = 31
        private const val PRIVATE_CLASS_C = 192
        private const val PRIVATE_C_SECOND = 168
        private const val OCTET_MAX = 255
        private const val OCTET_COUNT = 4
        private const val OCTET_MAX_DIGITS = 3

        fun parse(value: String): RemoteEndpoint? {
            val parts = value.trim().split(':')
            if (parts.size != 2 || !isLocalIpv4(parts[0])) return null
            val port = parts[1].takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
                ?.toIntOrNull()?.takeIf { it in 1..MAX_PORT }
            return port?.let { RemoteEndpoint(parts[0], it) }
        }

        fun isLocalIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != OCTET_COUNT) return false
            val octets = parts.mapNotNull(::parseOctet)
            return octets.size == OCTET_COUNT && (octets[0] == PRIVATE_CLASS_A || octets[0] == LOOPBACK ||
                (octets[0] == PRIVATE_CLASS_B && octets[1] in PRIVATE_B_MIN..PRIVATE_B_MAX) ||
                (octets[0] == PRIVATE_CLASS_C && octets[1] == PRIVATE_C_SECOND))
        }

        private fun parseOctet(part: String): Int? {
            val canonical = part.length == 1 || !part.startsWith('0')
            return part.takeIf { canonical && it.isNotEmpty() && it.length <= OCTET_MAX_DIGITS }
                ?.takeIf { it.all { char -> char in '0'..'9' } }?.toIntOrNull()?.takeIf { it in 0..OCTET_MAX }
        }
    }
}
