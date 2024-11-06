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
public class BatteryEventHandler extends BaseEventHandler {


    private final CacheManager cacheManager;
    private Boolean batteryFlag=false;

    @Inject
    public BatteryEventHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    @Override
    protected Map<Event, Position> analyzePosition(Position position) {
        Map<Event, Position> result = null;
        long deviceId = position.getDeviceId();
        Device device = cacheManager.getObject(Device.class, deviceId);
        if(position.hasAttribute(Position.KEY_BATTERY_LEVEL)) {
            if (!batteryFlag && (Long) position.getAttributes().get("io113") < 15) {
                Map<String, Object> attribute = new HashMap<>();
                attribute.put("alarm", "lowBattery");
                attribute.put("messageFa", "باطری دستگاه" + " " + device.getName() + " " + "خیلی  ضعیف است");

                Event eventLowBattery = new Event(Event.TYPE_ALARM, deviceId);
                eventLowBattery.set(Position.KEY_ALARM, Position.ALARM_LOW_BATTERY);
                eventLowBattery.setPositionId(position.getId());
                eventLowBattery.setAttributes(attribute);
                batteryFlag = true;
                return Map.of(eventLowBattery, position);
            } else if ((Long) position.getAttributes().get("io113") > 15) {
                batteryFlag = false;
            }
        }
        return result;
    }
}
