package com.alphacar.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


class MapKeyComparator implements Comparator<String> {

    @Override
    public int compare(String str1, String str2) {
        return str1.compareTo(str2);
    }

}

/**
 * @author leo
 */
public class IOUtils {

    private final static String EOL = System.getProperty("line.separator");

    private static XSSFWorkbook readFile(String filename) throws IOException {
        return new XSSFWorkbook(new FileInputStream(filename));
    }

    public static Map<String, String> sortMapByKey(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Map<String, String> sortMap = new TreeMap<String, String>(
                new MapKeyComparator());

        sortMap.putAll(map);

        return sortMap;
    }

    public static void WriteResult(String output_file,
                                   List<TransferInfo> infos, Map<String, String> extraInfos) {

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
            case XSSFCell.CELL_TYPE_NUMERIC:
                cellValue = String.valueOf(cell.getNumericCellValue());
                break;
            case XSSFCell.CELL_TYPE_STRING:
                cellValue = String.valueOf(cell.getStringCellValue());
                break;
            case XSSFCell.CELL_TYPE_BOOLEAN:
                cellValue = String.valueOf(cell.getBooleanCellValue());
                break;
            case XSSFCell.CELL_TYPE_FORMULA:
                cellValue = String.valueOf(cell.getStringCellValue());
                break;
            case XSSFCell.CELL_TYPE_BLANK:
                cellValue = "";
                break;
            case XSSFCell.CELL_TYPE_ERROR:
                cellValue = "Error!";
                break;
            default:
                cellValue = "未知类型";
                break;
        }

        cellValue = cellValue.trim();

        return cellValue;
    }

    public enum Results {
        LST, EXTRA
    }

    public static EnumMap<Results, Object> getTxInfos(String filePath) {

        System.out.println("filePath:" + filePath);

        ArrayList<TransferInfo> infos = new ArrayList<>();
        Map<String, String> extraInfos = new HashMap<>();

        try (
                Reader reader = Files.newBufferedReader(Paths.get(filePath));
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)
        ) {
            boolean firstLine = true;
            int mode = 0;
            for (CSVRecord csvRecord : csvParser) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String ethAddress = csvRecord.get(0).trim();
                if ("".equals(ethAddress) || "gas(eth) per tx".equals(ethAddress)) {
                    mode = 1;
                }
                switch (mode) {
                    case 0: {
                        TransferInfo info = new TransferInfo();
                        info.setEthAddress(ethAddress);
                        info.setFormattedEthAddress(csvRecord.get(1).trim());
                        info.setFlag(csvRecord.get(2).trim());
                        info.setAmount(Double.parseDouble(csvRecord.get(3).trim()));
                        info.setStatus(csvRecord.get(4).trim());
                        String tx = csvRecord.get(5).trim();
                        int tmp = tx.lastIndexOf('/') + 1;
                        info.setBaseUrl(tx.substring(0, tmp));
                        info.setTxHash(tx.substring(tmp));
                        infos.add(info);
                        break;
                    }
                    case 1: {
                        extraInfos.put(csvRecord.get(0).trim(), csvRecord.get(1).trim());
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e);
            return null;
        }
        EnumMap<Results, Object> results = new EnumMap<>(Results.class);

        results.put(Results.LST, infos);
        results.put(Results.EXTRA, extraInfos);

        return results;
    }
}