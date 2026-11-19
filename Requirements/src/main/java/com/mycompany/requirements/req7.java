package com.mycompany.requirements;

import java.io.File;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class req7 {

    // Make the decrypted folder constant static so it's available in static methods.
    private static final String DECRYPTED_FOLDER
            = "/home/ntu-user/NetBeansProjects/BufferFolder/DecryptedFile/";

    public static void encrypt(String fileName, String password) throws ZipException {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setEncryptFiles(true);
        zipParameters.setCompressionLevel(CompressionLevel.MAXIMUM);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);

        // Create the zip file with the same base name plus ".zip"
        ZipFile zipFile = new ZipFile(fileName + ".zip");
        zipFile.setPassword(password.toCharArray());
        zipFile.addFile(new File(fileName), zipParameters);

        System.out.println("Encrypted file created: " + fileName + ".zip");
    }

    public static void decrypt(String fileName, String password) throws ZipException {
        ZipFile zipFile = new ZipFile(fileName);
        if (zipFile.isEncrypted()) {
            zipFile.setPassword(password.toCharArray());
        }
        // Extract all files to the designated DECRYPTED_FOLDER.
        zipFile.extractAll(DECRYPTED_FOLDER);
        System.out.println("Decrypted file extracted to: " + DECRYPTED_FOLDER);

        File decryptedFile = new File(DECRYPTED_FOLDER + "mmmmm.txt");
        if (decryptedFile.exists()) {
            System.out.println("Decrypted file found at: " + decryptedFile.getAbsolutePath());
        } else {
            System.out.println("Decrypted file not found in: " + decryptedFile.getAbsolutePath());
        }
    }
}
