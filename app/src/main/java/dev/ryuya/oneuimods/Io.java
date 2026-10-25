package dev.ryuya.oneuimods;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class Io {
  static String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }

  static void write(File file, String value) throws IOException {
    Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
  }

  private Io() {}
}
