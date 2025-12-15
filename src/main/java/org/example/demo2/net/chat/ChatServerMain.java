package org.example.demo2.net.chat;

public class ChatServerMain {

    public static void main(String[] args) {
        try {
            ChatServer server = new ChatServer(5555);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
