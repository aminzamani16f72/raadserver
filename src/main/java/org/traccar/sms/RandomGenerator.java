package org.traccar.sms;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class RandomGenerator {
    public String randomCode(int length) {
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            return Integer.toString(sr.nextInt(9 * (int) Math.pow(10, length-1)) + (int) Math.pow(10, length-1));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
