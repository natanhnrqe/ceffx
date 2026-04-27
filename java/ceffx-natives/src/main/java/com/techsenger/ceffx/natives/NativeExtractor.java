/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.natives;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.stream.Stream;

/**
 *
 * @author Pavel Castornii
 */
public final class NativeExtractor {

    private NativeExtractor() {
    }

    public static void extract(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        URL resourceUrl = NativeExtractor.class.getResource("/native/libs");
        if (resourceUrl == null) {
            throw new IOException("Native libs not found in jar");
        }
        URI uri;
        try {
            uri = resourceUrl.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to get URI for native libs", e);
        }
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path source = fs.getPath("/native/libs");
                copyDirectory(source, targetDir);
            }
        } else {
            copyDirectory(Path.of(uri), targetDir);
        }
    }

    private static void copyDirectory(Path source, Path targetDir) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path entry : (Iterable<Path>) stream::iterator) {
                Path relative = source.relativize(entry);
                Path target = targetDir.resolve(relative.toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
                    if (!target.toString().endsWith(".dll") && !target.toString().endsWith(".plist")) {
                        target.toFile().setExecutable(true);
                    }
                }
            }
        }
    }
}