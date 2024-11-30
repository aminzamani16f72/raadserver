/*
 * Copyright 2015 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.glassfish.jersey.media.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.BaseObjectResource;
import org.traccar.api.signature.TokenManager;
import org.traccar.broadcast.BroadcastService;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.MediaManager;
import org.traccar.helper.LogAction;
import org.traccar.model.*;
import org.traccar.session.ConnectionManager;
import org.traccar.session.cache.CacheManager;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

@Path("devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource extends BaseObjectResource<Device> {

    @Inject
    private Config config;

    @Inject
    private CacheManager cacheManager;

    @Inject
    private ConnectionManager connectionManager;

    @Inject
    private BroadcastService broadcastService;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private TokenManager tokenManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceResource.class);
    private static final Map<String, String[]> vehicleNamesMap = new HashMap<>();

    static {
        vehicleNamesMap.put(Device.TYPE_Car, new String[]{"Toyota", "Honda", "Ford"});
        vehicleNamesMap.put(Device.TYPE_BICYCLE, new String[]{"Harley", "Yamaha", "Ducati"});
        vehicleNamesMap.put(Device.TYPE_BUS, new String[]{"Volvo", "Scania", "Mack"});
    }

    public DeviceResource() {
        super(Device.class);
    }

    @GET
    @Path("types")
    public Collection<Typed> get() {
        List<Typed> types = new LinkedList<>();
        Field[] fields = Device.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("TYPE_")) {
                try {
                    types.add(new Typed(field.get(null).toString()));
                } catch (IllegalArgumentException | IllegalAccessException error) {
                    LOGGER.warn("Get event types error", error);
                }
            }
        }
        return types;
    }

    @GET
    @Path("subtypes")
    public String[] getVehicleNames(@QueryParam("type") String type) {
        return vehicleNamesMap.getOrDefault(type, new String[]{});
    }

    @GET
    public Collection<Device> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("uniqueId") List<String> uniqueIds,
            @QueryParam("id") List<Long> deviceIds) throws StorageException {

        if (!uniqueIds.isEmpty() || !deviceIds.isEmpty()) {

            List<Device> result = new LinkedList<>();
            for (String uniqueId : uniqueIds) {
                result.addAll(storage.getObjects(Device.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                                new Condition.Equals("uniqueId", uniqueId),
                                new Condition.Permission(User.class, getUserId(), Device.class)))));
            }
            for (Long deviceId : deviceIds) {
                result.addAll(storage.getObjects(Device.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                                new Condition.Equals("id", deviceId),
                                new Condition.Permission(User.class, getUserId(), Device.class)))));
            }
            return result;

        } else {

            var conditions = new LinkedList<Condition>();

            if (all) {
                if (permissionsService.notAdmin(getUserId())) {
                    conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
                }
            } else {
                if (userId == 0) {
                    String query =
                            "WITH RECURSIVE_CTE AS ( \n" +
                                    "                        SELECT \n" +
                                    "                            tuu.userid,\n" +
                                    "                            tuu.manageduserid\n" +
                                    "                        FROM  \n" +
                                    "                           tc_user_user tuu\n" +
                                    "                        WHERE  \n" +
                                    "                           tuu.userid = +" + getUserId() + "\n" +
                                    "                        UNION ALL \n" +
                                    "                        SELECT  \n" +
                                    "                            tuu.userid ,\n" +
                                    "                            tuu.manageduserid \n" +
                                    "                        FROM \n" +
                                    "                            tc_user_user tuu \n" +
                                    "                        INNER JOIN \n" +
                                    "                            RECURSIVE_CTE rc ON tuu.userid = rc.manageduserid)select * from tc_devices td \n" +
                                    "where td.id IN(SELECT tud.deviceid\n" +
                                    "FROM tc_user_device tud\n" +
                                    "LEFT JOIN RECURSIVE_CTE rc ON tud.userid = rc.manageduserid\n" +
                                    "WHERE tud.userid = +" + getUserId() + " OR rc.manageduserid IS NOT NULL\n" +
                                    " )";

                    conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
                    return storage.getobjectByQueryAndCondition(baseClass, query, Condition.merge(conditions));
                } else {
                    permissionsService.checkUser(getUserId(), userId);
                    conditions.add(new Condition.Permission(User.class, userId, baseClass).excludeGroups());
                }
            }

            return storage.getObjects(baseClass, new Request(new Columns.All(), Condition.merge(conditions)));

        }
    }

    @Path("{id}/accumulators")
    @PUT
    public Response updateAccumulators(DeviceAccumulators entity) throws Exception {
        if (permissionsService.notAdmin(getUserId())) {
            permissionsService.checkManager(getUserId());
            permissionsService.checkPermission(Device.class, getUserId(), entity.getDeviceId());
        }

        Position position = storage.getObject(Position.class, new Request(
                new Columns.All(), new Condition.LatestPositions(entity.getDeviceId())));
        if (position != null) {
            if (entity.getTotalDistance() != null) {
                position.getAttributes().put(Position.KEY_TOTAL_DISTANCE, entity.getTotalDistance());
            }
            if (entity.getHours() != null) {
                position.getAttributes().put(Position.KEY_HOURS, entity.getHours());
            }
            position.setId(storage.addObject(position, new Request(new Columns.Exclude("id"))));

            Device device = new Device();
            device.setId(position.getDeviceId());
            device.setPositionId(position.getId());
            storage.updateObject(device, new Request(
                    new Columns.Include("positionId"),
                    new Condition.Equals("id", device.getId())));

            try {
                cacheManager.addDevice(position.getDeviceId());
                cacheManager.updatePosition(position);
                connectionManager.updatePosition(true, position);
            } finally {
                cacheManager.removeDevice(position.getDeviceId());
            }
        } else {
            throw new IllegalArgumentException();
        }

        LogAction.resetDeviceAccumulators(getUserId(), entity.getDeviceId());
        return Response.noContent().build();
    }

    @Path("{id}/image")
    @POST
    @Consumes("image/*")
    public Response uploadImage(
            @PathParam("id") long deviceId, File file,
            @HeaderParam(HttpHeaders.CONTENT_TYPE) String type) throws StorageException, IOException {

        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("id", deviceId),
                        new Condition.Permission(User.class, getUserId(), Device.class))));
        if (device != null) {
            String name = "device";
            String extension = type.substring("image/".length());
            try (var input = new FileInputStream(file);
                 var output = mediaManager.createFileStream(device.getUniqueId(), name, extension)) {
                input.transferTo(output);
            }
            return Response.ok(name + "." + extension).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @Path("share")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @POST
    public String shareDevice(
            @FormParam("deviceId") long deviceId,
            @FormParam("expiration") Date expiration) throws StorageException, GeneralSecurityException, IOException {

        User user = permissionsService.getUser(getUserId());
        if (permissionsService.getServer().getBoolean(Keys.DEVICE_SHARE_DISABLE.getKey())) {
            throw new SecurityException("Sharing is disabled");
        }
        if (user.getTemporary()) {
            throw new SecurityException("Temporary user");
        }
        if (user.getExpirationTime() != null && user.getExpirationTime().before(expiration)) {
            expiration = user.getExpirationTime();
        }

        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("id", deviceId),
                        new Condition.Permission(User.class, user.getId(), Device.class))));

        String shareEmail = user.getEmail() + ":" + device.getUniqueId();
        User share = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("email", shareEmail)));

        if (share == null) {
            share = new User();
            share.setName(device.getName());
            share.setEmail(shareEmail);
            share.setExpirationTime(expiration);
            share.setTemporary(true);
            share.setReadonly(true);
            share.setLimitCommands(user.getLimitCommands() || !config.getBoolean(Keys.WEB_SHARE_DEVICE_COMMANDS));
            share.setDisableReports(user.getDisableReports() || !config.getBoolean(Keys.WEB_SHARE_DEVICE_REPORTS));

            share.setId(storage.addObject(share, new Request(new Columns.Exclude("id"))));

            storage.addPermission(new Permission(User.class, share.getId(), Device.class, deviceId));
        }

        return tokenManager.generateToken(share.getId(), expiration);
    }


    @Override
    public Response add(Device entity) throws Exception {

        // Retrieve all IMEIs and Device records from storage
        List<String> Imeis = storage.getObjects(Imei.class, new Request(new Columns.All()))
                .stream()
                .map(Imei::getUniqueId)
                .collect(Collectors.toList());

        List<String> registerIMEIS = storage.getObjects(Device.class, new Request(new Columns.All()))
                .stream()
                .map(Device::getUniqueId)
                .collect(Collectors.toList());

        // Check if device with the provided IMEI is already registered
        if (registerIMEIS.contains(entity.getUniqueId())) {
            return Response.status(Response.Status.OK)
                    .entity("قبلا ثبت شده است."+entity.getUniqueId() + "دستگاه باشناسه")
                    .build();
        }

        // Verify that the IMEI exists in the registered IMEIs list
        if (!Imeis.contains(entity.getUniqueId())) {
            return Response.status(Response.Status.OK)
                    .entity("شناسه دستگاه موجود نیست")
                    .build();
        }

        // Add the new device if all checks pass
        return super.add(entity);
    }

    @Override
    public Response update(Device entity) throws Exception {
        List<String> Imeis = getRegisteredImeis();

        List<String> registerIMEIS = getRegisteredDeviceImeis();

        // Check if device with the provided IMEI is already registered
        if (registerIMEIS.contains(entity.getUniqueId())) {
            return super.update(entity);
        }

        // Verify that the IMEI exists in the registered IMEIs list
        if (!Imeis.contains(entity.getUniqueId())) {
            return Response.status(Response.Status.OK)
                    .entity("شناسه دستگاه موجود نیست")
                    .build();
        }

        // Add the new device if all checks pass
        return super.update(entity);

    }

    // New method to add devices from an Excel file
    @POST
    @Path("/addDevicesFromExcel")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addDevicesFromExcel(@FormDataParam("file") InputStream excelFile,
                                        @FormDataParam("file") org.glassfish.jersey.media.multipart.FormDataContentDisposition fileMetaData) {
        try {
            List<Device> devices = parseExcelFile(excelFile);
            var invalidExelFile = validateExcelFile(devices);
            if (!invalidExelFile.isEmpty()) {
                // Message to be included with the list
                String message = "شناسه های زیر معتبر نمی باشند:";

                // Wrap the message and list in a Map
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("message", message);
                responseMap.put("devices", invalidExelFile);
                return Response.ok(responseMap).build();
            }
            for (Device device : devices) {
                 add(device);
            }

            return Response.ok("دستگاه ها با موفقیت افزوده شدند").build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("خطایی در اضافه کردن دستگاه ها رخ داد: " + e.getMessage())
                    .build();
        }
    }

    private List<String> validateExcelFile(List<Device> devices) throws StorageException {
        List<String> invaliImeis = new ArrayList<>();
        for (Device device : devices) {
            if (!isImeiValid(device.getUniqueId())) {
                invaliImeis.add(device.getUniqueId());
            }
        }
        return invaliImeis;
    }

    private List<Device> parseExcelFile(InputStream excelFile) throws Exception {
        List<Device> devices = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(excelFile);

        Sheet sheet = workbook.getSheetAt(0); // Assuming data is in the first sheet
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Skip header row

            // Check if the row is empty
            boolean isEmptyRow = true;
            for (Cell cell : row) {
                if ((cell.getCellType() != CellType.BLANK) && !cell.toString().trim().isEmpty()) {
                    isEmptyRow = false;
                    break;
                }
            }
            if (isEmptyRow) continue; // Skip this row if it's empty

            // Process the row to create a Device object
            Device device = new Device();
            device.setName(row.getCell(0).getStringCellValue());
            device.setUniqueId(row.getCell(1).getStringCellValue().trim());
            device.setCategory(row.getCell(2).getStringCellValue());
            // Set other fields based on your entity

            devices.add(device);
        }

        workbook.close();
        return devices;
    }

    // Check if the IMEI is valid (not already registered and exists in IMEI list)
    private boolean isImeiValid(String imei) throws StorageException {
        return getRegisteredImeis().contains(imei); // Duplicate IMEI

    }

    // Retrieve list of registered IMEIs from the database or storage
    private List<String> getRegisteredImeis() throws StorageException {
        return storage.getObjects(Imei.class, new Request(new Columns.All()))
                .stream()
                .map(Imei::getUniqueId)
                .collect(Collectors.toList());
    }

    // Retrieve list of IMEIs already assigned to devices from the database or storage
    private List<String> getRegisteredDeviceImeis() throws StorageException {
        return storage.getObjects(Device.class, new Request(new Columns.All()))
                .stream()
                .map(Device::getUniqueId)
                .collect(Collectors.toList());
    }


}
