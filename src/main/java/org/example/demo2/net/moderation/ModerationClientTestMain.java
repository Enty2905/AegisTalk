package org.example.demo2.net.moderation;

import org.example.demo2.model.ModerationResult;

public class ModerationClientTestMain {

    public static void main(String[] args) throws Exception {
        ModerationClient client = new ModerationClient("localhost", 5100);

        String clean = "Hello, how are you today?";
        String bad   = "I will kill you, you stupid idiot.";

        ModerationResult r1 = client.moderateText(clean);
        ModerationResult r2 = client.moderateText(bad);
        System.out.println("Clean = " + r1);
        System.out.println("Bad   = " + r2);
    }
}