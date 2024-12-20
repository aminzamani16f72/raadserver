/*
 * Copyright 2016 - 2023 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 - 2018 Andrey Kunitsyn (andrey@traccar.org)
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

import com.github.sbahmani.jalcal.util.DateException;
import com.github.sbahmani.jalcal.util.JalaliDateHelper;
import org.apache.kafka.common.protocol.types.Field;
import org.traccar.api.SimpleObjectResource;
import org.traccar.api.security.LoginHistory;
import org.traccar.helper.LogAction;
import org.traccar.model.*;
import org.traccar.reports.CombinedReportProvider;
import org.traccar.reports.EventsReportProvider;
import org.traccar.reports.RouteReportProvider;
import org.traccar.reports.StopsReportProvider;
import org.traccar.reports.SummaryReportProvider;
import org.traccar.reports.TripsReportProvider;
import org.traccar.reports.common.ReportExecutor;
import org.traccar.reports.common.ReportMailer;
import org.traccar.reports.model.CombinedReportItem;
import org.traccar.reports.model.StopReportItem;
import org.traccar.reports.model.SummaryReportItem;
import org.traccar.reports.model.TripReportItem;
import org.traccar.storage.StorageException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.*;
import java.util.Calendar;
import java.util.stream.Collectors;

@Path("reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource extends SimpleObjectResource<Report> {

    private static final String EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Inject
    private CombinedReportProvider combinedReportProvider;

    @Inject
    private EventsReportProvider eventsReportProvider;

    @Inject
    private RouteReportProvider routeReportProvider;

    @Inject
    private StopsReportProvider stopsReportProvider;

    @Inject
    private SummaryReportProvider summaryReportProvider;

    @Inject
    private TripsReportProvider tripsReportProvider;

    @Inject
    private ReportMailer reportMailer;

    public ReportResource() {
        super(Report.class);
    }

    private Response executeReport(long userId, boolean mail, ReportExecutor executor) {
        if (mail) {
            reportMailer.sendAsync(userId, executor);
            return Response.noContent().build();
        } else {
            StreamingOutput stream = output -> {
                try {
                    executor.execute(output);
                } catch (StorageException e) {
                    throw new WebApplicationException(e);
                }
            };
            return Response.ok(stream)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx").build();
        }
    }

    @Path("loginhistory")
    @GET
    public Collection<LoginHistory> get() throws StorageException {
//            permissionsService.checkUser(getUserId(), userId);
            return storage.getObjects(LoginHistory.class,new Request(new Columns.All(),new Condition.Equals("userId", getUserId())));
        }


    @Path("combined")
    @GET
    public Collection<CombinedReportItem> getCombined(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "combined", from, to, deviceIds, groupIds);
        return combinedReportProvider.getObjects(getUserId(), deviceIds, groupIds, from, to);
    }

    @Path("route")
    @GET
    public Collection<Position> getRoute(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "route", from, to, deviceIds, groupIds);
        return routeReportProvider.getObjects(getUserId(), deviceIds, groupIds, from, to);
    }

    @Path("route")
    @GET
    @Produces(EXCEL)
    public Response getRouteExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("mail") boolean mail) throws StorageException, DateException {
        var fromDate= JalaliDateHelper.extractDateFromJalaliDateTime(from.substring(0,8),from.substring(8,14));
        var toDate=JalaliDateHelper.extractDateFromJalaliDateTime(to.substring(0,8),to.substring(8,14));
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        return executeReport(getUserId(), mail, stream -> {
            LogAction.logReport(getUserId(), "route", fromDate, toDate, deviceIds, groupIds);
            routeReportProvider.getExcel(stream, getUserId(), deviceIds, groupIds, fromDate, toDate);
        });
    }
    @Path("ignitionon")
    @GET
    public String getIgnitionOn(
            @QueryParam("deviceId") Long deviceId,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to)
            throws StorageException, DateException{

        return routeReportProvider.calculateIgnitionON(deviceId,from,to);
    }
    @Path("ignitionondiagram")
    @GET
    public Collection<Map<String,Object>> getIgnitionDiagram(
            @QueryParam("deviceId") Long deviceId,
            @QueryParam("from") Date fromDate,
            @QueryParam("to") Date toDate)
            throws StorageException, DateException{
//        var fromDate= JalaliDateHelper.extractDateFromJalaliDateTime(from.substring(0,8),from.substring(8,14));
//        var toDate=JalaliDateHelper.extractDateFromJalaliDateTime(to.substring(0,8),to.substring(8,14));
        List<Map<String, Object>> resultList = new ArrayList<>();
        java.util.Calendar calendar= java.util.Calendar.getInstance();
        calendar.setTime(fromDate);
        // Add one day to the current date
        calendar.add(Calendar.DAY_OF_YEAR, 1);

        // Get the new Date object
        Date nextDay = calendar.getTime();

        while(fromDate.compareTo(toDate) <= 0){
            var dayReport=routeReportProvider.calculateIgnitionON(deviceId,fromDate,nextDay);
            Map<String,Object> result=new HashMap<>();
            result.put("deviceTime",fromDate);
            result.put("ignition",dayReport);
            fromDate=nextDay;
            calendar.setTime(fromDate);
            // Add one day to the current date
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            nextDay=calendar.getTime();
            resultList.add(result);
        }
        return resultList;
    }
    @Path("ignitionoff")
    @GET
    public Collection<Map<String,Object>> getStopTime(
            @QueryParam("deviceId") Long deviceId,
            @QueryParam("from") Date fromDate,
            @QueryParam("to") Date toDate,
            @QueryParam("threshold") Long threshold)
            throws StorageException, DateException{
//        var fromDate= JalaliDateHelper.extractDateFromJalaliDateTime(from.substring(0,8),from.substring(8,14));
//        var toDate=JalaliDateHelper.extractDateFromJalaliDateTime(to.substring(0,8),to.substring(8,14));
        List<Map<String, Object>> resultList = new ArrayList<>();
        java.util.Calendar calendar= java.util.Calendar.getInstance();
        calendar.setTime(fromDate);
        // Add one day to the current date
        calendar.add(Calendar.DAY_OF_YEAR, 1);

        // Get the new Date object
        Date nextDay = calendar.getTime();

        while(fromDate.compareTo(toDate) <= 0){
            var dayReport=routeReportProvider.calculateIgnitionOff(deviceId,fromDate,nextDay,threshold);
            List<Map<String, Object>> dayResult = dayReport.stream()  // Convert to stream
                    .flatMap(entry -> entry.entrySet().stream())  // Flatten the nested maps to entrySet stream
                    .map(entryItem -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("deviceTime", entryItem.getKey());  // Map Date to deviceTime
                        result.put("timeOff", entryItem.getValue());   // Map Long to timeOff
                        return result;
                    })
                    .collect(Collectors.toList());
            fromDate=nextDay;
            calendar.setTime(fromDate);
            // Add one day to the current date
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            nextDay=calendar.getTime();
            resultList.addAll(dayResult);
        }
        return resultList;
    }



    @Path("route/{type:xlsx|mail}")
    @GET
    @Produces(EXCEL)
    public Response getRouteExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @PathParam("type") String type) throws StorageException, DateException {
        return getRouteExcel(deviceIds, groupIds, from, to, type.equals("mail"));
    }

    @Path("events")
    @GET
    public Collection<Event> getEvents(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("type") List<String> types,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "events", from, to, deviceIds, groupIds);
        return eventsReportProvider.getObjects(getUserId(), deviceIds, groupIds, types, from, to);
    }

    @Path("events")
    @GET
    @Produces(EXCEL)
    public Response getEventsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("type") List<String> types,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("mail") boolean mail) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        return executeReport(getUserId(), mail, stream -> {
            LogAction.logReport(getUserId(), "events", from, to, deviceIds, groupIds);
            eventsReportProvider.getExcel(stream, getUserId(), deviceIds, groupIds, types, from, to);
        });
    }

    @Path("events/{type:xlsx|mail}")
    @GET
    @Produces(EXCEL)
    public Response getEventsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("type") List<String> types,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @PathParam("type") String type) throws StorageException {
        return getEventsExcel(deviceIds, groupIds, types, from, to, type.equals("mail"));
    }

    @Path("summary")
    @GET
    public Collection<SummaryReportItem> getSummary(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("daily") boolean daily) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "summary", from, to, deviceIds, groupIds);
        return summaryReportProvider.getObjects(getUserId(), deviceIds, groupIds, from, to, daily);
    }

    @Path("summary")
    @GET
    @Produces(EXCEL)
    public Response getSummaryExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("daily") boolean daily,
            @QueryParam("mail") boolean mail) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        return executeReport(getUserId(), mail, stream -> {
            LogAction.logReport(getUserId(), "summary", from, to, deviceIds, groupIds);
            summaryReportProvider.getExcel(stream, getUserId(), deviceIds, groupIds, from, to, daily);
        });
    }

    @Path("summary/{type:xlsx|mail}")
    @GET
    @Produces(EXCEL)
    public Response getSummaryExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("daily") boolean daily,
            @PathParam("type") String type) throws StorageException {
        return getSummaryExcel(deviceIds, groupIds, from, to, daily, type.equals("mail"));
    }

    @Path("trips")
    @GET
    public Collection<TripReportItem> getTrips(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "trips", from, to, deviceIds, groupIds);
        return tripsReportProvider.getObjects(getUserId(), deviceIds, groupIds, from, to);
    }

    @Path("trips")
    @GET
    @Produces(EXCEL)
    public Response getTripsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("mail") boolean mail) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        return executeReport(getUserId(), mail, stream -> {
            LogAction.logReport(getUserId(), "trips", from, to, deviceIds, groupIds);
            tripsReportProvider.getExcel(stream, getUserId(), deviceIds, groupIds, from, to);
        });
    }

    @Path("trips/{type:xlsx|mail}")
    @GET
    @Produces(EXCEL)
    public Response getTripsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @PathParam("type") String type) throws StorageException {
        return getTripsExcel(deviceIds, groupIds, from, to, type.equals("mail"));
    }

    @Path("stops")
    @GET
    public Collection<StopReportItem> getStops(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        LogAction.logReport(getUserId(), "stops", from, to, deviceIds, groupIds);
        return stopsReportProvider.getObjects(getUserId(), deviceIds, groupIds, from, to);
    }

    @Path("stops")
    @GET
    @Produces(EXCEL)
    public Response getStopsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("mail") boolean mail) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        return executeReport(getUserId(), mail, stream -> {
            LogAction.logReport(getUserId(), "stops", from, to, deviceIds, groupIds);
            stopsReportProvider.getExcel(stream, getUserId(), deviceIds, groupIds, from, to);
        });
    }


    @Path("stops/{type:xlsx|mail}")
    @GET
    @Produces(EXCEL)
    public Response getStopsExcel(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @PathParam("type") String type) throws StorageException {
        return getStopsExcel(deviceIds, groupIds, from, to, type.equals("mail"));
    }


}
