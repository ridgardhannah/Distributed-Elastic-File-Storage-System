package com.mycompany.requirements;

import java.io.File;                                                                      //to use file class
import java.io.FileOutputStream;
import java.io.IOException;                                                               //for input/output exception handling
import java.io.InputStream;                                                               //abstract class - input stream of bytes
import java.nio.file.Files;                                                               //to work with files and paths
import java.util.ArrayList;                                                               //to use arrays
import java.util.List; 

/**
 *
 * @author ntu-user
 */
public class req25 {
    
    //TEST:
     /**
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) {
        try {
            String fileName = "example.txt"; 
            String filePath = "/home/ntu-user/NetBeansProjects/FileTempFolder/" + fileName;
           
            File bigFile = new File(filePath);

            req25 splitter = new req25();
            int numberOfChunks = 4;

            List<File> chunks = splitter.splitByNumberOfFiles(bigFile, numberOfChunks);

            System.out.println("File split into " + chunks.size() + " chunks.");
            for (int i = 0; i < chunks.size(); i++) {
                System.out.println("Chunk " + (i + 1) + ": " + chunks.get(i).getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("Error splitting file: " + e.getMessage());
        }
    }
    
    private final static String TEMP_DIRECTORY = "/home/ntu-user/NetBeansProjects/BufferFolder/";         //temporary location to store the 4 chunks of files

    
    public List<File> splitByNumberOfFiles(File bigFile, int noOfFiles) throws IOException {
        if (!bigFile.exists()) {
            throw new IllegalArgumentException("File '" + bigFile.getAbsolutePath() + "' does not exist.");
        }
        if (!bigFile.isFile()) {
            throw new IllegalArgumentException("File '" + bigFile.getAbsolutePath() + "' is not a file.");
        }

        // Read the entire file into a byte array.
        byte[] allBytes = Files.readAllBytes(bigFile.toPath());
        int totalBytes = allBytes.length;
        // Calculate the chunk size as the ceiling of totalBytes / noOfFiles.
        int chunkSize = (totalBytes + noOfFiles - 1) / noOfFiles;

        List<File> list = new ArrayList<>();
        String baseName = getBaseName(bigFile.getName());
        String extension = getExtension(bigFile.getName());
        
        // Create exactly noOfFiles chunks.
        for (int i = 0; i < noOfFiles; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, totalBytes);
            // If start >= totalBytes, then there is no more data. We'll create an empty chunk.
            byte[] chunkBytes = start < totalBytes ? java.util.Arrays.copyOfRange(allBytes, start, end) : new byte[0];
            File chunkFile = new File(TEMP_DIRECTORY + baseName + "-chunk" + (i + 1) + extension);
            try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                fos.write(chunkBytes);
            }
            list.add(chunkFile);
        }
        return list;
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
    }
    
    private static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(dotIndex) : "";
    }

    private int getSizeInBytes(long totalBytes, int numberOfFiles) {
        if (numberOfFiles <= 0) {
            throw new IllegalArgumentException("Number of files must be greater than zero.");
        }

        if (totalBytes <= 0) {
            throw new IllegalArgumentException("Total bytes must be greater than zero.");
        }

        if (totalBytes % numberOfFiles != 0) {
            totalBytes = ((totalBytes / numberOfFiles) + 1) * numberOfFiles;
        }

        long value = totalBytes / numberOfFiles;

        if (value > Integer.MAX_VALUE) {
            throw new NumberFormatException("Byte chunk too large");
        }

        return (int) value;
    }

    private File stageFile(byte[] buffer, int length, String baseName, int chunkNumber) throws IOException {
        String chunkFileName = baseName + "-chunk" + chunkNumber;
        File outPutFile = new File(TEMP_DIRECTORY + chunkFileName);
        try (FileOutputStream fos = new FileOutputStream(outPutFile)) {
            fos.write(buffer, 0, length);
        }
        return outPutFile;
    }
}
