package com.apexstudio.buildlogic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StripGpuImageDecoderTransformTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void removesOnlyGpuImageDecoderEntriesAndPreservesTheRest() throws Exception {
    File input = temporaryFolder.newFile("input.aar");
    File output = new File(temporaryFolder.getRoot(), "output.aar");
    try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(input.toPath()))) {
      add(archive, "classes.jar", "java api");
      add(archive, "res/raw/gpuimage-resource.bin", "resource");
      add(archive, "jni/arm64-v8a/libyuv-decoder.so", "old arm64 decoder");
      add(archive, "jni/x86_64/libyuv-decoder.so", "old x86 decoder");
      add(archive, "jni/armeabi-v7a/libyuv-decoder.so", "old unused decoder");
      add(archive, "jni/arm64-v8a/libother.so", "unrelated native library");
      add(archive, "assets/readme.txt", "metadata");
    }

    assertEquals(3, StripGpuImageDecoderTransform.copyWithoutDecoder(input, output));
    Set<String> names = entries(output);
    assertFalse(names.contains("jni/arm64-v8a/libyuv-decoder.so"));
    assertFalse(names.contains("jni/x86_64/libyuv-decoder.so"));
    assertFalse(names.contains("jni/armeabi-v7a/libyuv-decoder.so"));
    assertTrue(names.contains("classes.jar"));
    assertTrue(names.contains("res/raw/gpuimage-resource.bin"));
    assertTrue(names.contains("jni/arm64-v8a/libother.so"));
    assertTrue(names.contains("assets/readme.txt"));
    assertEquals(4, names.size());
    try (ZipFile original = new ZipFile(input); ZipFile rebuilt = new ZipFile(output)) {
      for (String name : names) {
        assertArrayEquals(name,
            original.getInputStream(original.getEntry(name)).readAllBytes(),
            rebuilt.getInputStream(rebuilt.getEntry(name)).readAllBytes());
      }
    }
  }

  @Test
  public void failsClosedWhenAarDoesNotContainTheExpectedDecoder() throws Exception {
    File input = temporaryFolder.newFile("unexpected.aar");
    File output = new File(temporaryFolder.getRoot(), "unexpected-output.aar");
    try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(input.toPath()))) {
      add(archive, "classes.jar", "java api");
      add(archive, "jni/arm64-v8a/libanother.so", "unrelated native library");
    }

    try {
      StripGpuImageDecoderTransform.copyWithoutDecoder(input, output);
      fail("Expected a missing decoder entry to fail the transform");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("Expected at least one"));
      assertFalse(output.exists());
    }
  }

  private static void add(ZipOutputStream archive, String name, String value) throws IOException {
    archive.putNextEntry(new ZipEntry(name));
    archive.write(value.getBytes(StandardCharsets.UTF_8));
    archive.closeEntry();
  }

  private static Set<String> entries(File archive) throws IOException {
    Set<String> names = new HashSet<>();
    try (ZipFile zip = new ZipFile(archive)) {
      Enumeration<? extends ZipEntry> all = zip.entries();
      while (all.hasMoreElements()) names.add(all.nextElement().getName());
    }
    return names;
  }
}
