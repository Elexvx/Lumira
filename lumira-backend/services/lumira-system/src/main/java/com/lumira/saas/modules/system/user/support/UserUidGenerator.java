package com.lumira.saas.modules.system.user.support;

import java.security.SecureRandom;

public final class UserUidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int UID_LENGTH = 18;

    private UserUidGenerator() {
    }

    public static String nextNumericUid() {
        StringBuilder uid = new StringBuilder(UID_LENGTH);
        uid.append(RANDOM.nextInt(9) + 1);
        for (int index = 1; index < UID_LENGTH; index += 1) {
            uid.append(RANDOM.nextInt(10));
        }
        return uid.toString();
    }
}
