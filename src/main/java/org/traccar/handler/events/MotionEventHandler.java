/*
 * Copyright 2016 - 2023 Anton Tananaev (anton@traccar.org)
 * Copyright 2017 Andrey Kunitsyn (andrey@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.handler.events;

import io.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Keys;
import org.traccar.helper.UnitsConverter;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.*;
import org.traccar.model.Calendar;
import org.traccar.reports.common.TripsConfig;
import org.traccar.session.cache.CacheManager;
import org.traccar.session.state.MotionProcessor;
import org.traccar.session.state.MotionState;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
@ChannelHandler.Sharable
public class MotionEventHandler extends BaseEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MotionEventHandler.class);

    private final CacheManager cacheManager;
    private final Storage storage;

    @Inject
    public MotionEventHandler(CacheManager cacheManager, Storage storage) {
        this.cacheManager = cacheManager;
        this.storage = storage;
    }

    @Override
    protected Map<Event, Position> analyzePosition(Position position) {
        Map<Event, Position> events = new HashMap<>();
        long deviceId = position.getDeviceId();
        Device device = cacheManager.getObject(Device.class, deviceId);
        Object result = device.getAttributes().get("speedLimit");
        Optional<Integer> speedLimitOptional = Optional.ofNullable((Integer) result);
        int speedLimit = speedLimitOptional.orElse(200);

        if (device == null || !PositionUtil.isLatest(cacheManager, position)) {
            return null;
        }
        var speed= UnitsConverter.kphFromKnots(position.getSpeed()) ;
        var intSpeed=(int)(Math.ceil(speed));
           try {
               if((Long)position.getAttributes().get("io113")<15){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","lowBattery");
                   attribute.put("messageFa","باطری دستگاه"+" "+device.getName()+" " +" ضعیف است");

                   Event eventLowBattery=new Event(Event.TYPE_ALARM,deviceId);
                   eventLowBattery.setPositionId(position.getId());
                   eventLowBattery.setAttributes(attribute);
                   events.put(eventLowBattery, position);
                   }
               if (speed > (speedLimit)) {
                   Event eventSpeed=new Event(Event.TYPE_DEVICE_OVERSPEED, deviceId);
                   eventSpeed.setPositionId(position.getId());
                   Map<String,Object> attribute=new HashMap<>();
                   String message="سرعت دستگاه "+" "+device.getName()+" " + " از حد مجاز فراتر رفت";
                   String formattedSpeed = String.format("%.2f", speed);
                   attribute.put("messageFa",message);
                   eventSpeed.setAttributes(attribute);
                   events.put(eventSpeed, position);
               }

               return events;

           } catch (ClassCastException | NullPointerException e) {
               // Handle exceptions appropriately
               // Log error or send error response via WebSocket
               System.out.println("catch");
           }



        TripsConfig tripsConfig = new TripsConfig(new AttributeUtil.CacheProvider(cacheManager, deviceId));
        MotionState state = MotionState.fromDevice(device);
        MotionProcessor.updateState(state, position, position.getBoolean(Position.KEY_MOTION), tripsConfig);
        if (state.isChanged()) {
            state.toDevice(device);
            try {
                storage.updateObject(device, new Request(
                        new Columns.Include("motionStreak", "motionState", "motionTime", "motionDistance"),
                        new Condition.Equals("id", device.getId())));
            } catch (StorageException e) {
                LOGGER.warn("Update device motion error", e);
            }
        }
        return state.getEvent() != null ? Collections.singletonMap(state.getEvent(), position) : null;
    }

}
