package org.example.demo2.ui;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBTest {

    // TODO: sửa lại cho đúng cấu hình của bạn
    private static final String URL =
            "jdbc:mysql://localhost:3306/aegistalk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "root";       // tài khoản MySQL của bạn
    private static final String PASSWORD = "1234"; // mật khẩu MySQL của bạn

    /**
     * Hàm tiện dụng để các chỗ khác (DAO, server…) dùng chung.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Hàm main chỉ để test riêng: chạy thử xem kết nối được DB không
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            System.out.println("✅ Kết nối DB thành công");
        } catch (SQLException e) {
            System.out.println("❌ Kết nối DB thất bại");
            e.printStackTrace();
        }
    }
}
