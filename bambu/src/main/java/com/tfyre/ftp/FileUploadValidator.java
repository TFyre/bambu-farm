package com.tfyre.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUploadValidator {

    // Method to check if the file is a valid 3mf file
    public static boolean isValid3mf(File file) {
        if (!file.getName().toLowerCase().endsWith(".3mf")) {
            return false; // Reject immediately if the extension is not .3mf
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(".model")) {
                    return true; // Check for a valid .3mf structure (has .model file inside)
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}