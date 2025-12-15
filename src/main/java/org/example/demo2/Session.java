package org.example.demo2;

import java.util.HashSet;
import java.util.Set;

public class Session {
    private static long userId;
    private static String displayName;
    private static boolean notificationEnabled = true;
    private static Set<String> mutedConversations = new HashSet<>(); // Conversation IDs bị tắt thông báo

    public static void setUser(long id, String name) {
        userId = id;
        displayName = name;
    }

    public static long getUserId() {
        return userId;
    }

    public static String getDisplayName() {
        return displayName;
    }
    
    public static void clear() {
        userId = 0;
        displayName = null;
        notificationEnabled = true;
        mutedConversations.clear();
    }
    
    // Notification settings
    public static boolean isNotificationEnabled() {
        return notificationEnabled;
    }
    
    public static void setNotificationEnabled(boolean enabled) {
        notificationEnabled = enabled;
    }
    
    public static void toggleNotification() {
        notificationEnabled = !notificationEnabled;
    }
    
    // Mute specific conversation
    public static boolean isConversationMuted(String conversationId) {
        return mutedConversations.contains(conversationId);
    }
    
    public static void muteConversation(String conversationId) {
        mutedConversations.add(conversationId);
    }
    
    public static void unmuteConversation(String conversationId) {
        mutedConversations.remove(conversationId);
    }
    
    public static void toggleConversationMute(String conversationId) {
        if (mutedConversations.contains(conversationId)) {
            mutedConversations.remove(conversationId);
        } else {
            mutedConversations.add(conversationId);
        }
    }
}
