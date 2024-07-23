/*
 * Copyright 2016 - 2023 Anton Tananaev (anton@traccar.org)
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
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.*;
import org.traccar.session.cache.CacheManager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.traccar.storage.Storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@ChannelHandler.Sharable
public class GeofenceEventHandler extends BaseEventHandler {

    private final CacheManager cacheManager;

    @Inject
    public GeofenceEventHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;

    }

    @Override
    protected Map<Event, Position> analyzePosition(Position position) {
        long deviceId = position.getDeviceId();
        Device device = cacheManager.getObject(Device.class, deviceId);
        if (!PositionUtil.isLatest(cacheManager, position)) {
            return null;
        }

        List<Long> oldGeofences = new ArrayList<>();
        Position lastPosition = cacheManager.getPosition(position.getDeviceId());
        if (lastPosition != null && lastPosition.getGeofenceIds() != null) {
            oldGeofences.addAll(lastPosition.getGeofenceIds());
        }

        List<Long> newGeofences = new ArrayList<>();
        if (position.getGeofenceIds() != null) {
            newGeofences.addAll(position.getGeofenceIds());
            newGeofences.removeAll(oldGeofences);
            oldGeofences.removeAll(position.getGeofenceIds());
        }

        Map<Event, Position> events = new HashMap<>();
        for (long geofenceId : oldGeofences) {
            Geofence geofence = cacheManager.getObject(Geofence.class, geofenceId);
            if (geofence != null) {
                long calendarId = geofence.getCalendarId();
                Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
                if (calendar == null || calendar.checkMoment(position.getFixTime())) {
                    Event eventExit = new Event(Event.TYPE_GEOFENCE_EXIT, position);
                    Map<String,Object> attribute=new HashMap<>();
                    attribute.put("messageFa","دستگاه  "+ device.getName()+" از حصار جغرافیایی "+" "+geofence.getName()+" "+ "خارج شد ");
                    eventExit.setAttributes(attribute);
                    eventExit.setGeofenceId(geofenceId);
                    events.put(eventExit, position);
                }
            }
        }
        for (long geofenceId : newGeofences) {
            long calendarId = cacheManager.getObject(Geofence.class, geofenceId).getCalendarId();
            Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
            if (calendar == null || calendar.checkMoment(position.getFixTime())) {
                Event eventEnter = new Event(Event.TYPE_GEOFENCE_ENTER, position);
                Geofence geofence=cacheManager.getObject(Geofence.class,geofenceId);
                Map<String,Object> attribute=new HashMap<>();
                attribute.put("messageFa","  دستگاه " +device.getName()+ " وارد حصار جغرافیایی"+" "+geofence.getName()+" "+ "شد ");
                eventEnter.setAttributes(attribute);
                eventEnter.setGeofenceId(geofenceId);
                events.put(eventEnter, position);
            }
        }
        return events;
    }

}
