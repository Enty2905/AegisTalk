package org.example.demo2.net.auth;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;

public class LoginServer {

    private static final int PORT = 5000;

    public static void main(String[] args) throws Exception {
        System.out.println("[LoginServer] Listening on port " + PORT);
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> handle(socket)).start();
            }
        }
    }

    private static void handle(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            String line = in.readLine();
            if (line == null) return;

            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                out.write("FAIL|Invalid format\n");
                out.flush();
                return;
            }

            String cmd = parts[0].toUpperCase();
            String username = parts[1];
            String password = parts[2];

            if ("REGISTER".equals(cmd)) {
                String res = register(username, password);
                out.write(res + "\n");
                out.flush();
            } else if ("LOGIN".equals(cmd)) {
                String res = login(username, password);
                out.write(res + "\n");
                out.flush();
            } else {
                out.write("FAIL|Unknown command\n");
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String register(String username, String password) {
        try (Connection c = org.example.demo2.ui.DBTest.getConnection()) {
            // Kiểm tra username tồn tại
            try (PreparedStatement st = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
                st.setString(1, username);
                ResultSet rs = st.executeQuery();
                if (rs.next()) return "FAIL|Username already exists";
            }

            // Băm password
            String hash = hashPassword(password);

            try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO users(username, password_hash, display_name) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, username);
                st.setString(2, hash);
                st.setString(3, username);
                st.executeUpdate();
                ResultSet rs = st.getGeneratedKeys();
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return "OK|" + id + "|" + username;
                }
            }
            return "FAIL|Insert error";
        } catch (Exception e) {
            return "FAIL|" + e.getMessage();
        }
    }

    private static String login(String username, String password) {
        try (Connection c = org.example.demo2.ui.DBTest.getConnection()) {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT id, password_hash, display_name FROM users WHERE username=?")) {
                st.setString(1, username);
                ResultSet rs = st.executeQuery();
                if (!rs.next()) return "FAIL|User not found";

                String hash = rs.getString("password_hash");
                String inputHash = hashPassword(password);
                if (!hash.equals(inputHash)) return "FAIL|Wrong password";

                long id = rs.getLong("id");
                String name = rs.getString("display_name");
                return "OK|" + id + "|" + name;
            }
        } catch (Exception e) {
            return "FAIL|" + e.getMessage();
        }
    }

    private static String hashPassword(String pw) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        // Đảm bảo dùng UTF-8 encoding để hash nhất quán
        byte[] digest = md.digest(pw.getBytes("UTF-8"));
        return HexFormat.of().formatHex(digest);
    }
}
