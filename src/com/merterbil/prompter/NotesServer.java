package com.merterbil.prompter;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

/**
 * A tiny HTTP server, running only while the Prompter is open:
 *   GET  /         the teleprompter page (shown on the TV)
 *   GET  /edit     the notes editor (open it on a phone or computer)
 *   GET  /notes    the notes as text, with the save time in X-Version
 *   POST /notes    replace the notes
 *   GET  /version  the last save time, polled by the TV page
 *   GET  /address  this TV's address on the network
 */
class NotesServer {
    static final int PORT = 8080;
    private static final int MAX_BODY = 2 * 1024 * 1024;

    private final Context context;
    private final int port;
    private volatile ServerSocket socket;

    NotesServer(Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
    }

    void start() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try (ServerSocket ss = new ServerSocket()) {
                    // reopening the Prompter soon after closing it must not fail on a lingering port
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(port));
                    socket = ss;
                    while (!ss.isClosed()) {
                        final Socket client = ss.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handle(client);
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    if (socket != null && !socket.isClosed()) Log.e("Prompter", "server stopped", e);
                }
            }
        }, "notes-server");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private File notesFile() {
        return new File(context.getFilesDir(), "notes.txt");
    }

    private void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(15000);
            InputStream in = c.getInputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2) return;
            String method = req[0];
            String path = req[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            int length = 0;
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring(15).trim());
                }
            }
            OutputStream out = c.getOutputStream();
            if (method.equals("POST") && path.equals("/notes")) {
                if (length < 0 || length > MAX_BODY) {
                    send(out, 413, "text/plain", "Too large".getBytes(StandardCharsets.UTF_8), null);
                    return;
                }
                byte[] body = readBody(in, length);
                try (FileOutputStream f = new FileOutputStream(notesFile())) {
                    f.write(body);
                }
                send(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), null);
            } else if (method.equals("GET") && path.equals("/notes")) {
                File f = notesFile();
                byte[] body = f.exists() ? readAll(new FileInputStream(f)) : new byte[0];
                send(out, 200, "text/plain; charset=utf-8", body, "X-Version: " + version() + "\r\n");
            } else if (method.equals("GET") && path.equals("/version")) {
                send(out, 200, "text/plain", String.valueOf(version()).getBytes(StandardCharsets.UTF_8), null);
            } else if (method.equals("GET") && path.equals("/address")) {
                send(out, 200, "text/plain", ("http://" + address() + ":" + port).getBytes(StandardCharsets.UTF_8), null);
            } else if (method.equals("GET") && (path.equals("/") || path.equals("/edit"))) {
                String asset = path.equals("/") ? "prompter.html" : "edit.html";
                send(out, 200, "text/html; charset=utf-8", readAll(context.getAssets().open(asset)), null);
            } else {
                send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), null);
            }
        } catch (IOException | RuntimeException e) {
            Log.w("Prompter", "request failed", e);
        }
    }

    private long version() {
        File f = notesFile();
        return f.exists() ? f.lastModified() : 0;
    }

    private static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
        }
        return "this-tv";
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev3 = 0, prev2 = 0, prev1 = 0, ch;
        while ((ch = in.read()) != -1) {
            b.write(ch);
            if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && ch == '\n') {
                return b.toString("ISO-8859-1");
            }
            prev3 = prev2;
            prev2 = prev1;
            prev1 = ch;
            if (b.size() > 16384) return null;
        }
        return null;
    }

    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        return body;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String extra)
            throws IOException {
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + (extra == null ? "" : extra)
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
