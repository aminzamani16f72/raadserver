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
import org.traccar.helper.UnitsConverter;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.*;
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
    private Boolean inputDigitalFlagON = false;
    private Boolean inputDigitalFlagOff = false;
    private Boolean outputDigitalFlagOn = false;
    private Boolean outputDigitalFlagOff = false;
    private Boolean batteryFlag=false;
    private Boolean towFlag=false;
    private Boolean powerCutFlag=false;


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
        Object slopeResult = device.getAttributes().get("slopeLimit");
        Optional<Integer> slopeLimitOptional = Optional.ofNullable((Integer) slopeResult);
        int slopeLimit = slopeLimitOptional.orElse(360);
        Object speedResult = device.getAttributes().get("speedLimit");
        Optional<Integer> speedLimitOptional = Optional.ofNullable((Integer) speedResult);
        int speedLimit = speedLimitOptional.orElse(200);

        if (device == null || !PositionUtil.isLatest(cacheManager, position)) {
            return null;
        }
        var speed= UnitsConverter.kphFromKnots(position.getSpeed()) ;
           try {
               if(!batteryFlag && (Long)position.getAttributes().get("io113")<15){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","lowBattery");
                   attribute.put("messageFa","باطری دستگاه"+" "+device.getName()+" " +" ضعیف است");

                   Event eventLowBattery=new Event(Event.TYPE_ALARM,deviceId);
                   eventLowBattery.setPositionId(position.getId());
                   eventLowBattery.setAttributes(attribute);
                   events.put(eventLowBattery, position);
                   batteryFlag=true;
                   } else if ((Long)position.getAttributes().get("io113")>15) {
                   batteryFlag=false;

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

               if(!inputDigitalFlagON && Optional.of(position.getAttributes().get("in1").equals(true)).orElse(false)){
                   Event eventDigitalInput=new Event(Event.TYPE_DIGITAL_INPUT,deviceId);
                   inputDigitalFlagON = true;
                   Map<String,Object> attribute=new HashMap<>();
                   String message="ورودی دیجیتال دستگاه "+" "+device.getName()+" " + " فعال شد";
                   attribute.put("messageFa",message);
                   eventDigitalInput.setAttributes(attribute);
                   eventDigitalInput.setPositionId(position.getId());
                   events.put(eventDigitalInput, position);
                   inputDigitalFlagOff=false;
               } else if (!inputDigitalFlagOff && Optional.ofNullable(position.getAttributes().get("in1"))
                       .map(out1 -> out1.equals(false))
                       .orElse(false)) {
                   inputDigitalFlagON =false;
                   Event eventDigitalInput=new Event(Event.TYPE_DIGITAL_INPUT,deviceId);
                   Map<String,Object> attribute=new HashMap<>();
                   String message="ورودی دیجیتال دستگاه "+" "+device.getName()+" " + " غیر فعال شد";
                   attribute.put("messageFa",message);
                   eventDigitalInput.setAttributes(attribute);
                   eventDigitalInput.setPositionId(position.getId());
                   events.put(eventDigitalInput, position);
                   inputDigitalFlagOff=true;

               }if(!outputDigitalFlagOn && Optional.ofNullable((Boolean)position.getAttributes().get("out1")).orElse(false)){
                   Event eventDigitalOut=new Event(Event.TYPE_DIGITAL_OUTPUT,deviceId);
                   outputDigitalFlagOn = true;
                   Map<String,Object> attribute=new HashMap<>();
                   String message="خروجی دیجیتال دستگاه "+" "+device.getName()+" " + "  فعال شد";
                   attribute.put("messageFa",message);
                   eventDigitalOut.setAttributes(attribute);
                   eventDigitalOut.setPositionId(position.getId());
                   events.put(eventDigitalOut, position);
                   outputDigitalFlagOff=false;
               } else if (!outputDigitalFlagOff && Optional.ofNullable(position.getAttributes().get("out1"))
                       .map(out1 -> out1.equals(false))
                       .orElse(false)) {
                   outputDigitalFlagOn =false;
                   Event eventDigitalOut=new Event(Event.TYPE_DIGITAL_OUTPUT,deviceId);
                   Map<String,Object> attribute=new HashMap<>();
                   String message="خروجی دیجیتال دستگاه "+" "+device.getName()+" " + " غیر فعال شد";
                   attribute.put("messageFa",message);
                   eventDigitalOut.setAttributes(attribute);
                   eventDigitalOut.setPositionId(position.getId());
                   events.put(eventDigitalOut, position);
                   outputDigitalFlagOff = true;
               }
               if(Optional.ofNullable ((Long) position.getAttributes().get("io161")).orElse(0L)>slopeLimit){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","slopeOfArm");
                   attribute.put("messageFa","شیب دستگاه"+" "+device.getName()+" " +" از حد مجاز بیشتر شد");

                   Event eventSlopeOfArm=new Event(Event.TYPE_Slope_Of_Arm,deviceId);
                   eventSlopeOfArm.setPositionId(position.getId());
                   eventSlopeOfArm.setAttributes(attribute);
                   events.put(eventSlopeOfArm, position);
               }
               if(Optional.ofNullable ((Long) position.getAttributes().get("io249")).orElse(0L)==1){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","jamming");
                   attribute.put("messageFa","جمینگ در دستگاه"+" "+device.getName()+" " +" رخ داد");

                   Event eventJamming=new Event(Event.TYPE_ALARM,deviceId);
                   eventJamming.setPositionId(position.getId());
                   eventJamming.setAttributes(attribute);
                   events.put(eventJamming, position);
               }
               if(!towFlag && Optional.ofNullable ((Long) position.getAttributes().get("io246")).orElse(0L)==1){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","tow");
                   attribute.put("messageFa","بکسل در  دستگاه"+" "+device.getName()+" " +" فعال شد");

                   Event eventTow=new Event(Event.TYPE_ALARM,deviceId);
                   eventTow.setPositionId(position.getId());
                   eventTow.setAttributes(attribute);
                   events.put(eventTow, position);
                   towFlag=true;
               }
               else if (Optional.ofNullable ((Long) position.getAttributes().get("io246")).orElse(2L)==0){
                   towFlag=false;
               }
               if(!powerCutFlag && Optional.ofNullable ((Long) position.getAttributes().get("io252")).orElse(2L)==1){
                   Map<String,Object> attribute=new HashMap<>();
                   attribute.put("alarm","powerCut");
                   attribute.put("messageFa","باطری  دستگاه"+" "+device.getName()+" " +" جدا شد");

                   Event eventPowerCut=new Event(Event.TYPE_ALARM,deviceId);
                   eventPowerCut.setPositionId(position.getId());
                   eventPowerCut.setAttributes(attribute);
                   events.put(eventPowerCut, position);
                   powerCutFlag=true;
               }
               else if (Optional.ofNullable ((Long) position.getAttributes().get("io252")).orElse(2L)==0){
                   powerCutFlag=false;
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
