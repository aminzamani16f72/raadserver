package org.traccar.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class SmsService {
    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);
    private static final int smsLength=4;
    private final RandomGenerator randomGenerator;
    private final AuthSmsRepository authSmsRepository;
    private final SmsProvider smsProvider;
    public SmsService(
                      AuthSmsRepository authSmsRepository,
                      RandomGenerator randomGenerator,
                      SmsProvider smsProvider) {
        this.authSmsRepository = authSmsRepository;
        this.randomGenerator = randomGenerator;
        this.smsProvider = smsProvider;
    }
//    public static SmsProvider createSmsProvider(String providerType) {
//        switch (providerType) {
//            case "provider1":
//                return new SmsProviderImpl1();
//            case "provider2":
//                return new SmsProviderImpl2();
//            default:
//                throw new IllegalArgumentException("Unknown provider type: " + providerType);
//        }
//    }
    public void sendAuthSms(String phoneNumber,String userId) {

        try {
            String code = randomGenerator.randomCode(smsLength);
            if (code != null) {
                authSmsRepository.store(userId,code);
                smsProvider.send(phoneNumber, code);
            }
        } catch (Exception e) {

        }
    }
    public Boolean verifyAuthSms(String userId,String command) {

        String code = authSmsRepository.getcode(userId);
        return code != null && code.equals(command.trim());
    }
}
