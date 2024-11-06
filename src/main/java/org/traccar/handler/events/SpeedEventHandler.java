package org.traccar.handler.events;

import io.netty.channel.ChannelHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
@Singleton
@ChannelHandler.Sharable
public class SpeedEventHandler extends BaseEventHandler {


    private final CacheManager cacheManager;

    @Inject
    public SpeedEventHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    @Override
    protected Map<Event, Position> analyzePosition(Position position) {
        Map<Event, Position> result = null;
        long deviceId = position.getDeviceId();
        Device device = cacheManager.getObject(Device.class, deviceId);
        Object speedResult = device.getAttributes().get("speedLimit");
        Optional<Integer> speedLimitOptional = Optional.ofNullable((Integer) speedResult);
        int speedLimit = speedLimitOptional.orElse(200);
        var speed = UnitsConverter.kphFromKnots(position.getSpeed());
        if (speed > (speedLimit)) {
            Event eventSpeed = new Event(Event.TYPE_DEVICE_OVERSPEED, deviceId);
            eventSpeed.setPositionId(position.getId());
            Map<String, Object> attribute = new HashMap<>();
            String message = "سرعت دستگاه " + " " + device.getName() + " " + " از حد مجاز خیلی  فراتر رفت";
            String formattedSpeed = String.format("%.2f", speed);
            attribute.put("messageFa", message);
            eventSpeed.setAttributes(attribute);
            result=Collections.singletonMap(
                    eventSpeed, position);
        }
        return result;
    }
}
