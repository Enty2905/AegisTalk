package org.example.demo2.model;

public class PresenceEvent {

    public enum Type { JOIN, LEAVE, TYPING }

    private final Type type;
    private final String room;
    private final String userId;
    private final String displayName;

    public PresenceEvent(Type type, String room, String userId, String displayName) {
        this.type = type;
        this.room = room;
        this.userId = userId;
        this.displayName = displayName;
    }

    public Type getType() {
        return type;
    }

    public String getRoom() {
        return room;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
