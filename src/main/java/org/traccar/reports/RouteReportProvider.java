/*
 * Copyright 2016 - 2022 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 Andrey Kunitsyn (andrey@traccar.org)
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
package org.traccar.reports;

import org.apache.poi.ss.util.WorkbookUtil;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.common.TripsConfig;
import org.traccar.reports.model.DeviceReportSection;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RouteReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    private final Map<String, Integer> namesCount = new HashMap<>();

    @Inject
    public RouteReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public Collection<Position> getObjects(long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
                                           Date from, Date to) throws StorageException {
        reportUtils.checkPeriodLimit(from, to);

        ArrayList<Position> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(PositionUtil.getPositions(storage, device.getId(), from, to));
        }
        return result;
    }


    private String getUniqueSheetName(String key) {
        namesCount.compute(key, (k, value) -> value == null ? 1 : (value + 1));
        return namesCount.get(key) > 1 ? key + '-' + namesCount.get(key) : key;
    }

    public void getExcel(OutputStream outputStream,
                         long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
                         Date from, Date to) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(from, to);

        ArrayList<DeviceReportSection> devicesRoutes = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            var positions = PositionUtil.getPositions(storage, device.getId(), from, to);
            DeviceReportSection deviceRoutes = new DeviceReportSection();
            deviceRoutes.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(getUniqueSheetName(deviceRoutes.getDeviceName())));
            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    deviceRoutes.setGroupName(group.getName());
                }
            }
            deviceRoutes.setObjects(positions);
            devicesRoutes.add(deviceRoutes);
        }

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "route.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(userId);
            context.putVar("devices", devicesRoutes);
            context.putVar("sheetNames", sheetNames);
            context.putVar("from", from);
            context.putVar("to", to);
            reportUtils.processTemplateWithSheets(inputStream, outputStream, context);
        }
    }

    public String calculateIgnitionON(
            Long deviceId, Date from, Date to) throws StorageException {
        long l = 0;
        long timeStamp = 0;
        long diffInSeconds = 0;
        long diffInMinutes = 0;
        long diffInHours = 0;

        var positions = PositionUtil.getPositions(storage, deviceId, from, to);
        if (!positions.isEmpty()) {
            for (int i = 0; i < positions.size() - 1; i++) {
                boolean Motion = positions.get(i).getBoolean(Position.KEY_MOTION);
                if (Motion) {
                    l = positions.get(i + 1).getFixTime().getTime() - positions.get(i).getFixTime().getTime();
                    timeStamp += l;
                }
            }
            Duration duration = Duration.ofMillis(timeStamp);
            diffInHours = duration.toHours();
            diffInMinutes = duration.toMinutesPart();
            diffInSeconds = duration.toSecondsPart();
        }
        return String.format("%02d", diffInHours) + ":" + String.format("%02d", diffInMinutes) + ":" + String.format("%02d", diffInSeconds);
    }

    public Collection<Map<Date, Long>> calculateIgnitionOff(Long deviceId, Date from, Date to, Long threshold) throws StorageException {
        List<Map<Date, Long>> resultList = new ArrayList<>();
        var positions = PositionUtil.getPositions(storage, deviceId, from, to);

        if (!positions.isEmpty()) {
            long totalTimeOff = 0;  // Total time ignition was off
            Date lastTime = null;   // To track the last position's timestamp

            for (int i = 0; i < positions.size() - 1; i++) {
                boolean isMotion = positions.get(i).getBoolean(Position.KEY_MOTION);
                if (!isMotion) {
                    lastTime = positions.get(i).getFixTime();
                    while (i < positions.size() - 1 && !positions.get(i).getBoolean(Position.KEY_MOTION)) {
                        long timeDifference = positions.get(i + 1).getFixTime().getTime() - positions.get(i).getFixTime().getTime();
                        totalTimeOff += timeDifference;
                        i++;
                    }
                    long diffInMinutes = Duration.ofMillis(totalTimeOff).toMinutes();
                    if (diffInMinutes > threshold) {
                        Map<Date, Long> result = new HashMap<>();
                        result.put(lastTime, diffInMinutes);
                        resultList.add(result);
                    }
                    totalTimeOff = 0; // Reset time for the next period
                }
            }
        }
        return resultList;
    }
}
