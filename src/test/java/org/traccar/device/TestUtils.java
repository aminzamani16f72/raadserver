package org.traccar.device;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class TestUtils {
    public static InputStream createTestExcelFile() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Test Data");

        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Unique ID");

        // Add some test data
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Device 1");
        row1.createCell(1).setCellValue("123");

        Row row2 = sheet.createRow(2); // Empty row

        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("Device 2");
        row3.createCell(1).setCellValue("456");

        // Write the workbook to an input stream
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayInputStream(out.toByteArray());
    }
}
