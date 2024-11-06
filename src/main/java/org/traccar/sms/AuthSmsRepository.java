package org.traccar.sms;

import org.traccar.config.Config;
import org.traccar.config.Keys;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.time.Duration;

public class AuthSmsRepository {
    private static final String KEY = "AuthSms";
    private final Jedis redis;
    private final static int smsLifetime = 100;


    public AuthSmsRepository(Config config) throws IOException {
        String url = config.getString(Keys.BROADCAST_ADDRESS);

        try {
            this.redis = new Jedis(url);
            redis.connect();
        } catch (JedisConnectionException e) {
            throw new IOException(e);
        }

    }

    public void store(String userId, String verificationCode) {
        String key = KEY + userId;  // Use a unique key per user
        redis.setex(key, smsLifetime, verificationCode);

    }
    public String getcode(String userId){
        String key = KEY + userId;
        return redis.get(key);
    }

}

