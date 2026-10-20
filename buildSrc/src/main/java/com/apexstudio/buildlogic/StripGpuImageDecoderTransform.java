package com.apexstudio.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Removes GPUImage 2.1.0's bundled decoder so the app can supply its 16KB-aligned rebuild. */
public abstract class StripGpuImageDecoderTransform
    implements TransformAction<TransformParameters.None> {
  public static final String OUTPUT_ARTIFACT_TYPE = "gpuimage-aar-without-yuv-decoder";
  private static final String DECODER_ENTRY_PATTERN = "jni/[^/]+/libyuv-decoder\\.so";

  @InputArtifact
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract Provider<FileSystemLocation> getInputArtifact();

  @Override
  public void transform(TransformOutputs outputs) {
    File input = getInputArtifact().get().getAsFile();
    File output = outputs.file("gpuimage-2.1.0-without-yuv-decoder.aar");
    try {
      copyWithoutDecoder(input, output);
    } catch (IOException e) {
      throw new RuntimeException("Could not remove GPUImage's bundled yuv-decoder", e);
    }
  }

  /** Copies every archive entry other than jni/<abi>/libyuv-decoder.so. */
  static int copyWithoutDecoder(File input, File output) throws IOException {
    int removed = 0;
    File parent = output.getParentFile();
    if (parent != null) Files.createDirectories(parent.toPath());
    try (ZipFile source = new ZipFile(input);
        OutputStream fileOutput = Files.newOutputStream(output.toPath());
        ZipOutputStream target = new ZipOutputStream(fileOutput)) {
      Enumeration<? extends ZipEntry> entries = source.entries();
      byte[] buffer = new byte[8192];
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.getName().matches(DECODER_ENTRY_PATTERN)) {
          removed++;
          continue;
        }
        ZipEntry copy = new ZipEntry(entry.getName());
        if (entry.getTime() >= 0) copy.setTime(entry.getTime());
        target.putNextEntry(copy);
        try (InputStream contents = source.getInputStream(entry)) {
          int count;
          while ((count = contents.read(buffer)) != -1) target.write(buffer, 0, count);
        }
        target.closeEntry();
      }
    }
    if (removed == 0) {
      output.delete();
      throw new IOException("Expected at least one jni/<abi>/libyuv-decoder.so entry in " + input);
    }
    return removed;
  }
}
