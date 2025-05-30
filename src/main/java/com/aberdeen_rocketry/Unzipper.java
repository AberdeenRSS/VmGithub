package com.aberdeen_rocketry;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class Unzipper {
    public static void unzip(String zipPath, String targetDir) throws IOException {
        Path destDir = Paths.get(targetDir);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }

        Path tempUnzipDir = destDir.resolve("tmp-unzip");
        if (Files.exists(tempUnzipDir)) {
            deleteRecursive(tempUnzipDir);
        }
        Files.createDirectories(tempUnzipDir);

        // Step 1: Unzip into temp directory
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = resolveZipEntry(tempUnzipDir, entry);
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        // Step 2: Find top-level folder inside temp-unzip
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempUnzipDir)) {
            for (Path innerDir : stream) {
                if (Files.isDirectory(innerDir)) {
                    // Move contents up
                    try (DirectoryStream<Path> innerStream = Files.newDirectoryStream(innerDir)) {
                        for (Path file : innerStream) {
                            Path targetPath = destDir.resolve(file.getFileName());
                            if (Files.exists(targetPath)) {
                                deleteRecursive(targetPath);
                            }
                            Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    // Delete inner folder
                    deleteRecursive(innerDir);
                    break; // Only one top-level folder is expected
                }
            }
        }

        // Step 3: Delete temp-unzip folder
        deleteRecursive(tempUnzipDir);
        System.out.println("Unzipped and flattened to: " + destDir.toAbsolutePath());
    }

    private static Path resolveZipEntry(Path baseDir, ZipEntry entry) throws IOException {
        Path resolved = baseDir.resolve(entry.getName()).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IOException("Unsafe zip entry: " + entry.getName());
        }
        return resolved;
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.notExists(path)) return;
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.delete(path);
    }

}
