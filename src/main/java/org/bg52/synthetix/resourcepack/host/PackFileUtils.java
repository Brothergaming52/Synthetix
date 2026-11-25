package org.bg52.synthetix.resourcepack.host;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * High-performance file and zip manipulation utilities for resource pack processing.
 */
public final class PackFileUtils {

    private PackFileUtils() {}

    /**
     * Recursively copies a file or directory tree.
     */
    public static void copyRecursively(File source, File target) throws IOException {
        if (!source.exists()) return;

        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child, new File(target, child.getName()));
                }
            }
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Writes a clean host zip containing strictly the assets/ hierarchy,
     * explicitly omitting pack.png and pack.mcmeta.
     * This prevents host plugins (such as Oraxen, Nexo, or ItemsAdder) from throwing duplicate
     * file errors or corrupting their ZipOutputStream during pack generation.
     */
    public static void writeHostCleanZip(File packZip, File buildDir, File targetZip) throws IOException {
        File parent = targetZip.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tempZip = new File(parent, targetZip.getName() + ".tmp");
        if (tempZip.exists()) {
            tempZip.delete();
        }

        byte[] buffer = new byte[65536];

        try (FileOutputStream fos = new FileOutputStream(tempZip);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED);

            if (buildDir != null && buildDir.exists() && buildDir.isDirectory()) {
                Path sourcePath = buildDir.toPath();
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryPath = sourcePath.relativize(file).toString().replace('\\', '/');
                        // Strictly exclude root metadata files and only include assets/
                        if (entryPath.equalsIgnoreCase("pack.png") || entryPath.equalsIgnoreCase("pack.mcmeta")
                                || !entryPath.startsWith("assets/")) {
                            return FileVisitResult.CONTINUE;
                        }

                        ZipEntry entry = new ZipEntry(entryPath);
                        zos.putNextEntry(entry);
                        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536)) {
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else if (packZip != null && packZip.exists() && packZip.isFile()) {
                try (ZipFile zf = new ZipFile(packZip)) {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        String name = e.getName().replace('\\', '/');
                        if (name.equalsIgnoreCase("pack.png") || name.equalsIgnoreCase("pack.mcmeta")
                                || !name.startsWith("assets/") || e.isDirectory()) {
                            continue;
                        }

                        ZipEntry newEntry = new ZipEntry(name);
                        zos.putNextEntry(newEntry);
                        try (InputStream is = zf.getInputStream(e)) {
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
        }

        Files.move(tempZip.toPath(), targetZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads all file entries of a zip into an in-memory Map of entry path to byte array.
     */
    public static Map<String, byte[]> readZipEntries(File zipFile) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                if (entry.isDirectory()) continue;
                try (InputStream is = zf.getInputStream(entry)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    entries.put(entry.getName().replace('\\', '/'), baos.toByteArray());
                }
            }
        }
        return entries;
    }

    /**
     * Extracts a zip archive to the target destination folder.
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File newFile = new File(destDir, entry.getName());
                // Guard against Zip Slip vulnerability
                String destDirPath = destDir.getCanonicalPath();
                String newFilePath = newFile.getCanonicalPath();
                if (!newFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    /**
     * Recursively deletes a directory.
     */
    public static void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}

