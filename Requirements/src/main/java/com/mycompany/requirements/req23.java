package com.mycompany.requirements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher; // Import Matcher
import java.util.regex.Pattern;

public class req23 {
    
    private Set<String> allowedCommands = new HashSet<>(Arrays.asList("mv", "cp", "ls", "mkdir", "ps", "whoami", "tree", "nano"));

    public String runCommand(String command) throws IOException, InterruptedException {
        if (command == null || command.trim().isEmpty()) {
            return "No command entered. Please try again."; 
        }

        String[] parts = command.split("\\s+");
        String baseCommand = parts[0];

        StringBuilder output = new StringBuilder(); //result

        if (allowedCommands.contains(baseCommand)) {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
            Process process = processBuilder.start();

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = outputReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            while ((line = errorReader.readLine()) != null) {
                output.append("Error: ").append(line).append("\n");
            }

            int exitCode = process.waitFor();
            output.append("Process finished with exit code: ").append(exitCode).append("\n"); // Keep this for now

            String outputString = output.toString();
            Pattern pattern = Pattern.compile("Process finished with exit code: \\d+\\n");
            Matcher matcher = pattern.matcher(outputString);
            outputString = matcher.replaceAll("");

            return outputString; // Return the modified string
        } else {
            return "Command '" + command + "' is not allowed.\n";
        }
    }

    public List<String> getAllowedCommands() {
        return new ArrayList<>(allowedCommands);
    }

    public static void main(String[] args) throws IOException, InterruptedException {    //tsting
        req23 terminal = new req23();
        String command = "ls -l";
        String output = terminal.runCommand(command);
        System.out.println(output);
    }
}