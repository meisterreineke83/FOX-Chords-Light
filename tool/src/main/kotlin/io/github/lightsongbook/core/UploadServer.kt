package io.github.lightsongbook.core

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Minimal HTTP server for importing over Wi-Fi: the phone shows an address,
 * the iPad opens it in Safari and uploads the backup file.
 *
 * The URL contains a random code so other devices on the network can't guess it,
 * and the server only runs while the import screen is open.
 */
class UploadServer(
    private val onUpload: (bytes: ByteArray, fileName: String) -> String,
) {
    val code: String = randomCode()

    @Volatile
    private var socket: ServerSocket? = null

    val port: Int
        get() = socket?.localPort ?: -1

    /** Binds to the first free port of [ports]. Throws if none is free. */
    fun start(ports: IntRange = 8080..8090) {
        var lastError: IOException? = null
        for (p in ports) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(p))
                socket = s
                thread(name = "songbook-upload", isDaemon = true) { acceptLoop(s) }
                return
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Kein freier Port")
    }

    fun stop() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: SocketException) {
                break
            } catch (e: IOException) {
                continue
            }
            thread(name = "songbook-upload-client", isDaemon = true) {
                client.use { handle(it) }
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 60_000
        val input = BufferedInputStream(client.getInputStream())
        val output = client.getOutputStream()
        try {
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return respond(output, 400, "text/plain", "Bad request")
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")

            when {
                method == "GET" && path == "/$code" -> respond(output, 200, "text/html; charset=utf-8", uploadPage())
                method == "GET" && path == "/$code/" -> respond(output, 200, "text/html; charset=utf-8", uploadPage())
                method == "POST" && path == "/$code/upload" -> {
                    val length = headers["content-length"]?.toLongOrNull()
                        ?: return respond(output, 411, "text/plain; charset=utf-8", "Länge fehlt")
                    if (length <= 0 || length > Importer.MAX_UPLOAD_BYTES) {
                        return respond(output, 413, "text/plain; charset=utf-8", "Datei zu groß")
                    }
                    val body = readBody(input, length.toInt())
                        ?: return respond(output, 400, "text/plain; charset=utf-8", "Upload abgebrochen")
                    val name = queryParam(query, "name") ?: "upload"
                    val message = try {
                        onUpload(body, name)
                    } catch (e: ImportException) {
                        return respond(output, 422, "text/plain; charset=utf-8", e.message ?: "Fehler")
                    } catch (e: Exception) {
                        return respond(output, 500, "text/plain; charset=utf-8", "Import fehlgeschlagen")
                    }
                    respond(output, 200, "text/plain; charset=utf-8", message)
                }
                else -> respond(output, 404, "text/plain; charset=utf-8", "Nicht gefunden")
            }
        } catch (_: IOException) {
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.ISO_8859_1.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString(Charsets.ISO_8859_1.name())
    }

    private fun readBody(input: InputStream, length: Int): ByteArray? {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) return null
            read += n
        }
        return data
    }

    private fun queryParam(query: String, key: String): String? =
        query.split('&').firstNotNullOfOrNull { pair ->
            val k = pair.substringBefore('=')
            if (k == key) URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") else null
        }

    private fun respond(out: OutputStream, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 411 -> "Length Required"
            413 -> "Payload Too Large"; 422 -> "Unprocessable Entity"; else -> "Error"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun uploadPage(): String = """
<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Songbook → Light Phone</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: -apple-system, system-ui, sans-serif; max-width: 34rem; margin: 3rem auto; padding: 0 1.25rem; line-height: 1.5; background: #fff; color: #000; }
  @media (prefers-color-scheme: dark) { body { background: #000; color: #fff; } }
  h1 { font-weight: 400; font-size: 1.6rem; margin-bottom: .25rem; }
  p { opacity: .75; }
  label { display: block; border: 1px solid currentColor; padding: 1.25rem; text-align: center; margin: 2rem 0 1rem; cursor: pointer; }
  input { display: none; }
  #log { white-space: pre-wrap; font-size: 1.05rem; }
</style>
</head>
<body>
<h1>Songs auf das Light Phone</h1>
<p>Wähle dein Songbook-Pro-Backup (.sbpbackup) oder ChordPro-Dateien aus. Das Light Phone importiert sie sofort.</p>
<label>Datei auswählen<input id="file" type="file" multiple></label>
<div id="log"></div>
<script>
const log = document.getElementById('log');
document.getElementById('file').addEventListener('change', async (e) => {
  for (const f of e.target.files) {
    log.textContent += f.name + ' … ';
    try {
      const r = await fetch('/$code/upload?name=' + encodeURIComponent(f.name), { method: 'POST', body: f });
      log.textContent += (await r.text()) + '\n';
    } catch (err) {
      log.textContent += 'Fehler: Verbindung unterbrochen\n';
    }
  }
  e.target.value = '';
});
</script>
</body>
</html>
""".trimIndent()

    companion object {
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

        private fun randomCode(): String {
            val random = SecureRandom()
            return (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        }

        /** Wi-Fi/LAN IPv4 address of this device, or null if not on a local network. */
        fun localIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
