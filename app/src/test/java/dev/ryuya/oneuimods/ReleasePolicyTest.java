package dev.ryuya.oneuimods;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public final class ReleasePolicyTest {
  private Release release(String version, long code, boolean preview) {
    return new Release(
        version,
        version,
        "",
        "",
        "2026-10-25",
        preview,
        new Release.Asset("app-release.apk", "", "", 4096),
        null,
        null,
        code,
        version);
  }

  @Test
  public void comparesNumbersRatherThanText() {
    assertTrue(VersionOrder.compare("1.10.0", "1.9.9").orElseThrow() > 0);
    assertEquals(0, VersionOrder.compare("v1.2", "1.2.0").orElseThrow());
  }

  @Test
  public void ranksPrereleasesAndNumericSuffixes() {
    assertTrue(VersionOrder.compare("1.0.0", "1.0.0-beta.12").orElseThrow() > 0);
    assertTrue(VersionOrder.compare("1.0.0-beta.12", "1.0.0-beta.2").orElseThrow() > 0);
  }

  @Test
  public void unknownTagsDoNotInventUpdates() {
    assertTrue(VersionOrder.compare("rolling", "1.0.0").isEmpty());
    assertFalse(ReleasePolicy.update(4, "1.0.0", release("rolling", 0, false)));
  }

  @Test
  public void knownBuildNumberDetectsSameVersionUpdate() {
    assertTrue(ReleasePolicy.update(1, "0.1.0", release("0.1.0", 3, true)));
    assertFalse(ReleasePolicy.update(4, "0.1.0", release("0.1.0", 3, true)));
  }

  @Test
  public void stableSwitchFiltersPreviewWithoutDowngrading() {
    var stable = release("1.0.0", 1, false);
    var preview = release("2.0.0-beta", 2, true);
    assertEquals(preview, ReleasePolicy.choose(List.of(stable, preview), true));
    assertEquals(stable, ReleasePolicy.choose(List.of(stable, preview), false));
    assertFalse(ReleasePolicy.update(2, "2.0.0-beta", stable));
  }

  @Test
  public void highestReleaseWinsEvenWhenBackportWasPublishedLater() {
    assertEquals(
        5,
        ReleasePolicy.choose(List.of(release("1.2.0", 5, false), release("1.1.5", 4, false)), true)
            .versionCode());
  }

  @Test
  public void rejectsForeignRepositoriesAndProtocols() {
    String good = "https://github.com/OneUI-Mods/Example/releases/download/v1/app-release.apk";
    assertTrue(ReleasePolicy.downloadUrl("Example", good));
    assertFalse(ReleasePolicy.downloadUrl("Example", good.replace("https:", "http:")));
    assertFalse(ReleasePolicy.downloadUrl("Other", good));
    assertFalse(
        ReleasePolicy.downloadUrl(
            "Example", good.replace("github.com", "github.com.evil.example")));
    assertFalse(
        ReleasePolicy.downloadUrl("Example", good.replace("github.com", "someone@github.com")));
  }
}
