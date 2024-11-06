package org.traccar.sms;

public interface SmsProvider {
        void send(String phoneNumber, String content);
}
