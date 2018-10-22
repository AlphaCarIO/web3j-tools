package com.alphacar.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


class MapKeyComparator implements Comparator<String> {

    @Override
    public int compare(String str1, String str2) {
        return str1.compareTo(str2);
    }

}

public class IOUtils {
    private final static String EOL = System.getProperty("line.separator");

    private static XSSFWorkbook readFile(String filename) throws IOException {
        return new XSSFWorkbook(new FileInputStream(filename));
    }

    private static Map<String, String> sortMapByKey(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Map<String, String> sortMap = new TreeMap<String, String>(
                new MapKeyComparator());

        sortMap.putAll(map);

        return sortMap;
    }

    public static void WriteResult(String output_file,
                                   ArrayList<TransferInfo> infos, Map<String, String> extraInfos) {

        File output = new File(output_file);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(output));
            writer.write("ethAddress,formattedEthAddress,flag,amount,status,receipt" + EOL);

            for (TransferInfo info : infos) {
                writer.write(info.toString() + EOL);
            }

            if (extraInfos != null && extraInfos.size() > 0) {
                Map<String, String> resultMap = sortMapByKey(extraInfos);
                for (Map.Entry<String, String> entry : resultMap.entrySet()) {
                    writer.write(entry.getKey() + "," + entry.getValue() + EOL);
                }
            }

            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static ArrayList<TransferInfo> POIReadExcel(String fileName) throws IOException {
        return POIReadExcel(fileName, true);
    }

    public static ArrayList<TransferInfo> POIReadExcel(String fileName, boolean skipHeader) throws IOException {

        XSSFWorkbook wb = IOUtils.readFile(fileName);

        ArrayList<TransferInfo> infos = new ArrayList<>();


        for (int k = 0; k < wb.getNumberOfSheets(); k++) {

            XSSFSheet sheet = wb.getSheetAt(k);

            int rows = sheet.getPhysicalNumberOfRows();

            System.out.println("Sheet" + k + " \"" + wb.getSheetName(k) + "\" has " + rows

                    + "row(s).");

            int ind = 0;

            if (skipHeader) {
                ind = 1;
            }

            for (int r = ind; r < rows; r++) {

                XSSFRow row = sheet.getRow(r);

                if (row == null) {
                    continue;
                }

                int cells = row.getPhysicalNumberOfCells();

                if (cells >= 2) {

                    String ethAddress = getCellValue(row.getCell(0));

                    if (ethAddress.equals("")) {
                        break;
                    }

                    String amt_str = getCellValue(row.getCell(1));

                    if (amt_str.equals("")) {
                        break;
                    }

                    double amount = Double.parseDouble(amt_str);

                    TransferInfo info = new TransferInfo();

                    info.setEthAddress(ethAddress);
                    info.setAmount(amount);

                    infos.add(info);

                }

            }

        }

        return infos;

    }

    private static String getCellValue(XSSFCell cell) {
        String cellValue = "";
        if (cell == null) {
            return cellValue;
        }
//把数字当成String来读，避免出现1读成1.0的情况
        if (cell.getCellType() == XSSFCell.CELL_TYPE_NUMERIC) {
            cell.setCellType(XSSFCell.CELL_TYPE_STRING);
        }
//判断数据的类型
        switch (cell.getCellType()) {
            case XSSFCell.CELL_TYPE_NUMERIC: //数字
                cellValue = String.valueOf(cell.getNumericCellValue());
                break;
            case XSSFCell.CELL_TYPE_STRING: //字符串
                cellValue = String.valueOf(cell.getStringCellValue());
                break;
            case XSSFCell.CELL_TYPE_BOOLEAN: //Boolean
                cellValue = String.valueOf(cell.getBooleanCellValue());
                break;
            case XSSFCell.CELL_TYPE_FORMULA: //公式
// cellValue = String.valueOf(cell.getCellFormula());
                cellValue = String.valueOf(cell.getStringCellValue());
                break;
            case XSSFCell.CELL_TYPE_BLANK: //空值
                cellValue = "";
                break;
            case XSSFCell.CELL_TYPE_ERROR: //故障
                cellValue = "非法字符";
                break;
            default:
                cellValue = "未知类型";
                break;
        }

        cellValue = cellValue.trim();

        return cellValue;
    }
}