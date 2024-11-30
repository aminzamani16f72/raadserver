package org.traccar.device;

import org.junit.jupiter.api.Test;
import org.traccar.api.resource.DeviceResource;
import org.traccar.model.Device;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeviceServiceTest {
    @Test
    public void testParseExcelFileIgnoresEmptyRows() throws Exception {
        DeviceResource deviceService = new DeviceResource();

        // Generate a test Excel file
        InputStream excelFile = TestUtils.createTestExcelFile();
        Method parseExcelFileMethod=DeviceResource.class.getDeclaredMethod("parseExcelFile", InputStream.class);
        parseExcelFileMethod.setAccessible(true);

        // Parse the file
        @SuppressWarnings("unchecked")
        List<Device> devices =(List<Device>) parseExcelFileMethod.invoke(deviceService,excelFile);

        // Validate the result
        assertEquals(2, devices.size()); // Expecting 2 devices (empty row ignored)
        assertEquals("Device 1", devices.get(0).getName());
        assertEquals("123", devices.get(0).getUniqueId());
        assertEquals("Device 2", devices.get(1).getName());
        assertEquals("456", devices.get(1).getUniqueId());
    }
}
