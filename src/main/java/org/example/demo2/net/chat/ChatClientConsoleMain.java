package org.example.demo2.net.chat;

import org.example.demo2.model.ChatMessage;
import org.example.demo2.model.MessageType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;

public class ChatClientConsoleMain {

    public static void main(String[] args) throws Exception {
        // Hỏi tên & room cho dễ debug
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        System.out.print("Your name: ");
        String name = console.readLine();
        if (name == null || name.isBlank()) {
            name = "ConsoleUser";
        }

        System.out.print("Room: ");
        String room = console.readLine();
        if (room == null || room.isBlank()) {
            room = "general";
        }

        // Tạo client, callback in ra message nhận được từ server
        ChatClient client = new ChatClient(
                "localhost",
                5555,
                msg -> System.out.println("[INCOMING] " + msg)
        );

        client.connect();
        System.out.println("Connected to chat server. Type message, /quit to exit.");

        String line;
        while ((line = console.readLine()) != null) {
            String trimmed = line.trim();
            if ("/quit".equalsIgnoreCase(trimmed)) {
                break;
            }
            if (trimmed.isEmpty()) {
                continue;
            }

            // Khớp với constructor hiện tại của ChatMessage:
            // ChatMessage(String room, String from, MessageType type,
            //             String text, String payloadRef, long ts)
            ChatMessage msg = new ChatMessage(
                    room,
                    name,
                    MessageType.TEXT,
                    line,
                    null, // payloadRef: tạm thời không dùng
                    Instant.now().toEpochMilli()
            );

            client.send(msg);
        }

        client.close();
        System.out.println("Bye.");
    }
}
