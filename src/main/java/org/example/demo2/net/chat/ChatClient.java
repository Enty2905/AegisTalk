package org.example.demo2.net.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo2.model.ChatMessage;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * TCP ChatClient:
 *  - connect(host, port)
 *  - send(ChatMessage)
 *  - nhận message qua callback onMessage
 */
public class ChatClient implements Closeable {

    private final String host;
    private final int port;
    private final Consumer<ChatMessage> onMessage;
    private final ObjectMapper mapper = new ObjectMapper();

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    public ChatClient(String host, int port, Consumer<ChatMessage> onMessage) {
        this.host = host;
        this.port = port;
        this.onMessage = onMessage;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

        Thread reader = new Thread(this::readLoop, "ChatClient-Reader");
        reader.setDaemon(true);
        reader.start();

        System.out.println("[ChatClient] Connected to " + host + ":" + port);
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    ChatMessage msg = mapper.readValue(line, ChatMessage.class);
                    System.out.println("[ChatClient] ===== RECEIVED MESSAGE =====");
                    System.out.println("[ChatClient] Room: " + msg.room());
                    System.out.println("[ChatClient] From: " + msg.from());
                    System.out.println("[ChatClient] Text: " + msg.text());
                    System.out.println("[ChatClient] Callback is null: " + (onMessage == null));
                    
                    if (onMessage != null) {
                        System.out.println("[ChatClient] Calling onMessage callback...");
                        onMessage.accept(msg);
                        System.out.println("[ChatClient] Callback executed");
                    } else {
                        System.err.println("[ChatClient] WARNING: onMessage callback is null!");
                    }
                } catch (Exception e) {
                    System.err.println("[ChatClient] Error parsing message: " + e.getMessage());
                    System.err.println("[ChatClient] Raw line: " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[ChatClient] readLoop error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void send(ChatMessage msg) throws IOException {
        if (out == null) throw new IllegalStateException("Not connected");
        String json = mapper.writeValueAsString(msg);
        out.write(json);
        out.write("\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) socket.close();
    }
}
