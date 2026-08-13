package com.nunnun.wake.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}
