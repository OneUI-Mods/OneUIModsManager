package dev.ryuya.oneuimods;

import static org.junit.Assert.*;

import java.util.Set;
import org.junit.Test;

public final class ReleaseMetadataTest {
  private final String sha = "a".repeat(64);
  private final AppEntry app =
      new AppEntry("Example", "dev.example.app", "Example", "", "device", Set.of("b".repeat(64)));
  private final Release release =
      new Release(
          "v1.0.0-build.2",
          "1.0.0",
          "",
          "",
          "2026-10-25",
          false,
          new Release.Asset("app-release.apk", "", "sha256:" + sha, 4096),
          null,
          null,
          0,
          "1.0.0-build.2");

  @Test
  public void apkMetadataDetectsAnotherBuildWithoutCatalogVersionMapping() {
    Release parsed =
        ReleaseMetadata.apply(
            app, release, app.packageName(), release.apk().name(), 3, "1.0.0", sha);
    assertTrue(ReleasePolicy.update(2, "1.0.0", parsed));
    assertFalse(ReleasePolicy.update(3, "1.0.0", parsed));
    assertEquals(3, parsed.versionCode());
  }

  @Test
  public void rejectsMetadataForAnotherPackageOrAsset() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReleaseMetadata.apply(
                app, release, "dev.other.app", release.apk().name(), 3, "1.0.0", sha));
    assertThrows(
        IllegalArgumentException.class,
        () -> ReleaseMetadata.apply(app, release, app.packageName(), "other.apk", 3, "1.0.0", sha));
  }

  @Test
  public void rejectsInvalidBuildsAndHashes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReleaseMetadata.apply(
                app, release, app.packageName(), release.apk().name(), 0, "1.0.0", sha));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReleaseMetadata.apply(
                app, release, app.packageName(), release.apk().name(), 3, "", sha));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReleaseMetadata.apply(
                app, release, app.packageName(), release.apk().name(), 3, "1.0.0", "b".repeat(64)));
  }

  @Test
  public void ordinaryNewReleasesWorkWithoutOptionalMetadata() {
    assertTrue(ReleasePolicy.update(1, "0.9.0", release));
    assertFalse(ReleasePolicy.update(3, "1.1.0", release));
  }
}
