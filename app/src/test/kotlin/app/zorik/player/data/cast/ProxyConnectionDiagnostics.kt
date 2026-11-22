package app.zorik.player.data.cast

import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Failure-only diagnostics. Keep output limited to the test-owned TCP tuple. */
internal object ProxyConnectionDiagnostics {
    fun capture(socket: Socket, server: ProxyServer?): String {
        val local = "${socket.localAddress.hostAddress}:${socket.localPort}"
        val peer = "${socket.inetAddress.hostAddress}:${socket.port}"
        val state = server?.let { owner -> runCatching {
            val field = ProxyServer::class.java.getDeclaredField("httpServer").apply { isAccessible = true }
            val http = field.get(owner) as ProxyHttpServer
            "activeClients=${http.activeClientCountForTesting()}, metrics=${http.metricsSnapshot()}"
        }.getOrDefault("Server snapshot unavailable") }.orEmpty()
        val tcp = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) windowsTcp(local, peer) else ""
        return "$state\nclosed=${socket.isClosed}, connected=${socket.isConnected}\n$tcp"
    }

    private fun windowsTcp(local: String, peer: String): String = runCatching {
        val file = File.createTempFile("zorik-test-tcp-", ".txt")
        try {
            val process = ProcessBuilder("netstat", "-ano", "-p", "tcp")
                .redirectErrorStream(true).redirectOutput(file).start()
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(1, TimeUnit.SECONDS)
                matchingConnections(file, local, peer)
            } finally { if (process.isAlive) process.destroyForcibly() }
        } finally { file.delete() }
    }.getOrDefault("TCP snapshot unavailable")
    private fun matchingConnections(file: File, local: String, peer: String): String = file.useLines { lines ->
        lines.filter { line ->
            val fields = line.trim().split(Regex("\\s+"))
            fields.size >= 4 && ((fields[1] == local && fields[2] == peer) ||
                (fields[1] == peer && fields[2] == local))
        }.joinToString("\n")
    }

}
