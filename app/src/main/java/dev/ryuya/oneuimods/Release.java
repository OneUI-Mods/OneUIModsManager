package dev.ryuya.oneuimods;

record Release(
    String tag,
    String name,
    String notes,
    String pageUrl,
    String published,
    boolean preview,
    Asset apk,
    Asset checksums,
    long versionCode,
    String versionName) {
  record Asset(String name, String url, String digest, long size) {}
}
