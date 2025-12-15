package org.example.demo2.net.moderation;

import org.example.demo2.model.ModerationResult;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ModerationRemoteImpl extends UnicastRemoteObject implements ModerationRemote {

    private final GeminiModerationService service = new GeminiModerationService();

    public ModerationRemoteImpl() throws RemoteException {
        super();
    }

    @Override
    public ModerationResult moderateText(String text) throws RemoteException {
        return service.moderateText(text);
    }

    @Override
    public ModerationResult moderateImage(byte[] jpegBytes) throws RemoteException {
        return service.moderateImage(jpegBytes);
    }
}
