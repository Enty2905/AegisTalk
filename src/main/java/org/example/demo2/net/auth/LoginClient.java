package org.example.demo2.net.auth;

import java.io.*;
import java.net.Socket;

public class LoginClient {
    private final String host;
    private final int port;

    public LoginClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String send(String command, String user, String pw) throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            out.write(command + "|" + user + "|" + pw + "\n");
            out.flush();
            return in.readLine();
        }
    }
}
