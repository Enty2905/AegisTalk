package org.example.demo2.net.moderation;

import org.example.demo2.model.ModerationResult;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface RMI cho dịch vụ moderation.
 * Sau này sẽ gọi OpenAI thật; hiện tại Day 6 chỉ mock ALLOW.
 */
public interface ModerationRemote extends Remote {

    ModerationResult moderateText(String text) throws RemoteException;

    ModerationResult moderateImage(byte[] jpegData) throws RemoteException;
}