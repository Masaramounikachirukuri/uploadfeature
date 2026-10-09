import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal multipart upload test server — no dependencies, runs with any JDK.
 *
 * Usage:
 *   javac TestServer.java && java TestServer
 *
 * Listens on port 8080. Accepts POST /upload with multipart/form-data.
 * Saves uploaded files to ./uploads/ and returns JSON response.
 *
 * The Android emulator reaches your Mac's localhost via 10.0.2.2:8080.
 */
public class TestServer {

    private static final int PORT = 8080;
    private static final File UPLOAD_DIR = new File("uploads");

    public static void main(String[] args) throws Exception {
        UPLOAD_DIR.mkdirs();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/upload", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = "{\"success\":false,\"message\":\"Method not allowed\",\"fileUrl\":null}";
                exchange.sendResponseHeaders(405, body.length());
                exchange.getResponseBody().write(body.getBytes());
                exchange.close();
                return;
            }

            try {
                // Read entire request body
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

                System.out.println("[" + now() + "] POST /upload  Content-Type: " + contentType);
                System.out.println("  Body size: " + requestBody.length + " bytes");

                // Extract boundary from Content-Type header
                // e.g.  multipart/form-data; boundary=----XXXXXXXX
                String boundary = null;
                if (contentType != null) {
                    for (String part : contentType.split(";")) {
                        part = part.trim();
                        if (part.startsWith("boundary=")) {
                            boundary = part.substring("boundary=".length()).trim();
                            break;
                        }
                    }
                }

                int savedCount = 0;
                if (boundary != null) {
                    savedCount = saveMultipartFiles(requestBody, boundary);
                } else {
                    // No boundary — save raw body as a single file
                    String fname = "upload_" + now().replace(":", "-") + ".bin";
                    Files.write(UPLOAD_DIR.toPath().resolve(fname), requestBody);
                    savedCount = 1;
                    System.out.println("  Saved (raw): " + fname);
                }

                String msg = "Uploaded " + savedCount + " file(s) successfully";
                String json = "{\"success\":true,\"message\":\"" + msg + "\",\"uploaded_count\":" + savedCount + ",\"fileUrl\":null}";
                byte[] resp = json.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                exchange.getResponseBody().write(resp);
                System.out.println("  [OK] " + msg);

            } catch (Exception e) {
                String json = "{\"success\":false,\"message\":\"" + e.getMessage() + "\",\"fileUrl\":null}";
                byte[] resp = json.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, resp.length);
                exchange.getResponseBody().write(resp);
                System.err.println("  [ERROR] " + e.getMessage());
                e.printStackTrace();
            } finally {
                exchange.close();
            }
        });

        // Health-check endpoint
        server.createContext("/", exchange -> {
            String body = "{\"status\":\"ok\",\"message\":\"Upload test server running\"}";
            byte[] resp = body.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("=== Upload Test Server ===");
        System.out.println("Listening on http://0.0.0.0:" + PORT);
        System.out.println("Emulator connects via http://10.0.2.2:" + PORT + "/upload");
        System.out.println("Files saved to: " + UPLOAD_DIR.getAbsolutePath());
        System.out.println("Press Ctrl+C to stop.");
        System.out.println("=========================");
    }

    /**
     * Parse multipart body and save each file part to UPLOAD_DIR.
     * Returns the number of files saved.
     */
    private static int saveMultipartFiles(byte[] body, String boundary) throws Exception {
        // Multipart boundary delimiter is "--" + boundary
        byte[] delimiter = ("--" + boundary).getBytes("UTF-8");
        int count = 0;

        // Split body into parts using boundary
        int pos = 0;
        while (pos < body.length) {
            int delimPos = indexOf(body, delimiter, pos);
            if (delimPos < 0) break;

            int partStart = delimPos + delimiter.length;
            if (partStart + 2 > body.length) break;

            // Check for final boundary "--" + boundary + "--"
            if (body[partStart] == '-' && body[partStart + 1] == '-') break;

            // Skip CRLF after boundary
            if (body[partStart] == '\r') partStart++;
            if (partStart < body.length && body[partStart] == '\n') partStart++;

            // Find end of this part (next delimiter)
            int nextDelimPos = indexOf(body, delimiter, partStart);
            if (nextDelimPos < 0) break;

            byte[] partBytes = new byte[nextDelimPos - partStart];
            System.arraycopy(body, partStart, partBytes, 0, partBytes.length);

            // Parse headers from part
            String partStr = new String(partBytes, 0, Math.min(1024, partBytes.length), "UTF-8");
            int headerEnd = partStr.indexOf("\r\n\r\n");
            if (headerEnd < 0) { pos = nextDelimPos; continue; }

            String headers = partStr.substring(0, headerEnd);
            int contentOffset = headerEnd + 4; // skip \r\n\r\n

            // Remove trailing \r\n before next boundary
            int contentLen = partBytes.length - contentOffset;
            if (contentLen >= 2 &&
                partBytes[partBytes.length - 2] == '\r' &&
                partBytes[partBytes.length - 1] == '\n') {
                contentLen -= 2;
            }

            // Extract filename from Content-Disposition
            String filename = null;
            for (String line : headers.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-disposition:")) {
                    for (String token : line.split(";")) {
                        token = token.trim();
                        if (token.startsWith("filename=")) {
                            filename = token.substring("filename=".length())
                                           .replace("\"", "").trim();
                        }
                    }
                }
            }

            if (filename != null && !filename.isEmpty() && contentLen > 0) {
                String ts = now().replace(":", "-").replace(" ", "_");
                String saveName = ts + "_" + filename;
                Path savePath = UPLOAD_DIR.toPath().resolve(saveName);
                byte[] fileContent = new byte[contentLen];
                System.arraycopy(partBytes, contentOffset, fileContent, 0, contentLen);
                Files.write(savePath, fileContent);
                System.out.println("  Saved: " + savePath.toAbsolutePath() + " (" + fileContent.length + " bytes)");
                count++;
            }

            pos = nextDelimPos;
        }
        return count;
    }

    /** Find byte array needle in haystack starting at offset. Returns -1 if not found. */
    private static int indexOf(byte[] haystack, byte[] needle, int offset) {
        outer:
        for (int i = offset; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
