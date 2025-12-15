package org.example.demo2.net.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.demo2.model.ChatMessage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * TCP ChatServer:
 *  - Lắng trên port (mặc định 5555)
 *  - Mỗi client là 1 thread
 *  - Mỗi message là 1 dòng JSON (ChatMessage)
 *  - Broadcast lại cho các client khác
 */
public class ChatServer {

    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<ClientHandler> clients = new CopyOnWriteArraySet<>();

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("[ChatServer] Listening on port " + port);

            while (true) {
                Socket socket = server.accept();
                System.out.println("[ChatServer] New client from " + socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);

                Thread t = new Thread(handler, "ChatClient-" + socket.getPort());
                t.start();
            }
        }
    }

    /** Broadcast 1 message cho các client khác */
    private void broadcast(ChatMessage msg, ClientHandler from) {
        try {
            String json = mapper.writeValueAsString(msg);
            System.out.println("[ChatServer] Broadcasting message: room=" + msg.room() + ", from=" + msg.from() + ", to " + clients.size() + " clients");
            int sentCount = 0;
            for (ClientHandler c : clients) {
                // Gửi đến tất cả client khác (không gửi lại cho người gửi)
                if (c != from) {
                    c.send(json);
                    sentCount++;
                    System.out.println("[ChatServer] Sent to client " + c.socket.getRemoteSocketAddress());
                }
            }
            System.out.println("[ChatServer] Broadcast complete: sent to " + sentCount + " clients");
        } catch (Exception e) {
            System.err.println("[ChatServer] Cannot serialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Thread xử lý từng client */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader in;
        private final BufferedWriter out;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        // Mỗi dòng là 1 JSON ChatMessage
                        ChatMessage msg = mapper.readValue(line, ChatMessage.class);
                        System.out.println("[ChatServer] Received message from " + socket.getRemoteSocketAddress() + ": room=" + msg.room() + ", from=" + msg.from() + ", text=" + msg.text());
                        System.out.println("[ChatServer] Total connected clients: " + clients.size());
                        broadcast(msg, this);
                    } catch (Exception e) {
                        System.err.println("[ChatServer] Error parsing message from " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
                        System.err.println("[ChatServer] Raw line: " + line);
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                System.err.println("[ChatServer] Client " + socket.getRemoteSocketAddress()
                        + " disconnected: " + e.getMessage());
            } finally {
                clients.remove(this);
                System.out.println("[ChatServer] Client removed. Remaining clients: " + clients.size());
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void send(String jsonLine) {
            try {
                out.write(jsonLine);
                out.write("\n");
                out.flush();
                System.out.println("[ChatServer] Sent message to client " + socket.getRemoteSocketAddress() + ": " + jsonLine.substring(0, Math.min(100, jsonLine.length())) + "...");
            } catch (IOException e) {
                System.err.println("[ChatServer] Failed to send to client " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
