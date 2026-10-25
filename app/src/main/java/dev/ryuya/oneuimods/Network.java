package dev.ryuya.oneuimods;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Public GitHub HTTPS requests only. Redirects cannot carry downloads to arbitrary hosts. */
final class Network {
  record Response(String text, String etag, boolean unchanged) {}

  static final class Failure extends IOException {
    final int status;
    final long retryAt;

    Failure(int status, long retryAt) {
      super("HTTP " + status);
      this.status = status;
      this.retryAt = retryAt;
    }
  }

  static HttpURLConnection open(String address, String accept, String etag) throws IOException {
    for (int redirects = 0; redirects < 6; redirects++) {
      URL url = new URL(address);
      String host = url.getHost();
      if (!"https".equals(url.getProtocol())
          || !(host.equals("api.github.com")
              || host.equals("github.com")
              || host.endsWith(".githubusercontent.com"))
          || url.getUserInfo() != null) throw new IOException("Unsupported download origin");
      HttpURLConnection c = (HttpURLConnection) url.openConnection();
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(15000);
      c.setReadTimeout(20000);
      c.setRequestProperty("User-Agent", "OneUIModsManager/" + BuildConfig.VERSION_NAME);
      c.setRequestProperty("Accept", accept);
      if (host.equals("api.github.com")) c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
      if (etag != null && !etag.isEmpty()) c.setRequestProperty("If-None-Match", etag);
      int status = c.getResponseCode();
      if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (location == null) throw new IOException("Missing redirect");
        address = new URL(url, location).toString();
        continue;
      }
      if (status == 200 || status == 304) return c;
      long reset = 0;
      try {
        if ((status == 403 && "0".equals(c.getHeaderField("X-RateLimit-Remaining")))
            || status == 429) reset = Long.parseLong(c.getHeaderField("X-RateLimit-Reset")) * 1000;
      } catch (NumberFormatException ignored) {
      }
      c.disconnect();
      throw new Failure(status, reset);
    }
    throw new IOException("Too many redirects");
  }

  static Response text(String url, String etag, int limit) throws IOException {
    HttpURLConnection c = open(url, "application/vnd.github+json", etag);
    try {
      if (c.getResponseCode() == 304) return new Response("", etag, true);
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] bytes = new byte[8192];
        int size;
        while ((size = in.read(bytes)) != -1) {
          if (out.size() + size > limit) throw new IOException("Response too large");
          out.write(bytes, 0, size);
        }
        return new Response(out.toString(StandardCharsets.UTF_8), c.getHeaderField("ETag"), false);
      }
    } finally {
      c.disconnect();
    }
  }

  private Network() {}
}
