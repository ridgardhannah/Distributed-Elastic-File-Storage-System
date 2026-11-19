/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.requirements;

import com.jcraft.jsch.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class RemoteTerminal {

    public String executeRemoteCommand(String command, String host, String user, String password, int port) throws JSchException, IOException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password); // Or, ideally, use SSH key authentication

        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no"); // INSECURE FOR PRODUCTION - ONLY FOR TESTING
        session.setConfig(config);

        session.connect();

        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        BufferedReader in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
        channel.connect();

        StringBuilder output = new StringBuilder();
        String msg;
        while ((msg = in.readLine()) != null) {
            output.append(msg).append("\n");
        }

        channel.disconnect();
        session.disconnect();

        return output.toString();
    }
}