/*
 * Copyright 2019 - 2022 Anton Tananaev (anton@traccar.org)
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
package org.traccar.handler;

import com.github.mfathi91.time.PersianDateTime;
import com.github.sbahmani.jalcal.util.JalaliDateHelper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.traccar.BaseProtocolDecoder;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Position;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.*;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Singleton
@ChannelHandler.Sharable
public class TimeHandler extends ChannelInboundHandlerAdapter {

    private final boolean enabled;
    private final boolean useServerTime;
    private final Set<String> protocols;

    @Inject
    public TimeHandler(Config config) {
        enabled = config.hasKey(Keys.TIME_OVERRIDE);
        if (enabled) {
            useServerTime = config.getString(Keys.TIME_OVERRIDE).equalsIgnoreCase("serverTime");
        } else {
            useServerTime = false;
        }
        String protocolList = config.getString(Keys.TIME_PROTOCOLS);
        if (protocolList != null) {
            protocols = new HashSet<>(Arrays.asList(protocolList.split("[, ]")));
        } else {
            protocols = null;
        }
    }

    public static void main(String[] args) {
        String s1 = "javacup";
        String s2 = new String(s1);
        String s3 = new String("javacup");
        String s4 = "javacup";

        Set<Integer> ids = new HashSet<>();
        ids.add(System.identityHashCode(s1));
        ids.add(System.identityHashCode(s2));
        ids.add(System.identityHashCode(s3));
        ids.add(System.identityHashCode(s4));

        System.out.println("hello"+ids.size());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

            Position position = (Position) msg;
            {
                position.setFixTime(position.getDeviceTime());


                var jalalidate=JalaliDateHelper.convertToJalaliDateFormat(position.getDeviceTime());
                var year=jalalidate.substring(0,2);
                var month=jalalidate.substring(2,4);
                var day=jalalidate.substring(4,6);

                LocalDateTime utcTime = LocalDateTime.now(ZoneId.of("Asia/Tehran"));
//                var jalaliTime=JalaliDateHelper.convertToTimeFormat(position.getDeviceTime());
                var hours=utcTime.getHour()-1;
                var minutes=utcTime.getMinute();
                var seconds=utcTime.getSecond();


                var persianDate="'" +year + "/" + month + "/" + day + " " + hours + ":" + minutes + ":" + seconds+"'";

                position.setPersianFixTime((persianDate));
            }


        ctx.fireChannelRead(msg);
    }
    public static String persianDate(){
        LocalDateTime utcTime = LocalDateTime.now(ZoneId.of("Asia/Tehran"));
//                var jalaliTime=JalaliDateHelper.convertToTimeFormat(position.getDeviceTime());
        var hours=utcTime.getHour();
        var minutes=utcTime.getMinute();
        var seconds=utcTime.getSecond();
        LocalDateTime localDateTime = LocalDateTime.now();
        var jalaliDateFormat=JalaliDateHelper.convertToJalaliDateFormat( Date.from(localDateTime.atZone(ZoneId.of("Asia/Tehran")).toInstant()));
        var year=jalaliDateFormat.substring(0,2);
        var month=jalaliDateFormat.substring(2,4);
        var day=jalaliDateFormat.substring(4,6);
        return  "'" +year + "/" + month + "/" + day + " " + hours + ":" + minutes + ":" + seconds+"'";
    }

}
