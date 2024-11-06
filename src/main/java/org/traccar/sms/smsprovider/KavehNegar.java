package org.traccar.sms.smsprovider;

import com.google.api.client.util.Value;
import org.traccar.config.Config;
import org.traccar.config.Keys;

public class KavehNegar {

    private final String host;
    private final String apiKey;
    private final String sender;

    public KavehNegar(Config config) {
        this.host = config.getString(Keys.SMS_HOST_URL);
        this.apiKey = config.getString(Keys.SMS_API_KEY);
        this.sender = config.getString(Keys.SMS_SENDER);
    }

}
