package com.alphacar.utils;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author leo
 */
public class RocksDbHelper {

    static {
        RocksDB.loadLibrary();
    }

    private String dbPath;

    private RocksDB rocksDB = null;

    private Options options = null;

    public RocksDbHelper(String dbPath) {
        this.dbPath = dbPath;
        this.options = new Options();
        options.setCreateIfMissing(true);

        try {
            rocksDB = RocksDB.open(options, this.dbPath);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }

    public void release() {
        if (this.rocksDB != null) {
            this.rocksDB.close();
        }
    }

    public boolean insertData(String key, String value) {
        if (rocksDB != null) {
            try {
                rocksDB.put(key.getBytes(), value.getBytes());
                return true;
            } catch (RocksDBException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public List<String> getListColumnFamilies() {
        try {
            List<byte[]> cfs = RocksDB.listColumnFamilies(this.options, dbPath);

            return cfs.stream().map(String::new).collect(Collectors.toList());
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return null;

    }

    public String getData(String key) {
        try {
            byte[] getValue = rocksDB.get(key.getBytes());
            return new String(getValue);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean deleteData(String key) {
        try {
            rocksDB.delete(key.getBytes());
            return true;
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean clear() {
        List<String> keys = getListColumnFamilies();
        for (String key : keys) {
            deleteData(key);
        }
        return true;
    }

    public Map<byte[], byte[]> mulGetData(List<String> keys) {
        try {
            List<byte[]> key_bytes = keys.stream().map(String::getBytes).collect(Collectors.toList());
            return rocksDB.multiGet(key_bytes);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void testDefaultColumnFamily() {

        String key = "Hello";

        this.insertData(key, "World");

        List<String> cfs = this.getListColumnFamilies();
        for (String cf : cfs) {
            System.out.println(cf);
        }

        System.out.println(this.getData(key));

        this.insertData("SecondKey", "SecondValue");

        List<String> keys = new ArrayList<>();
        keys.add(key);
        keys.add("SecondKey");

        Map<byte[], byte[]> valueMap = this.mulGetData(keys);
        for (Map.Entry<byte[], byte[]> entry : valueMap.entrySet()) {
            System.out.println(new String(entry.getKey()) + ":" + new String(entry.getValue()));
        }

        RocksIterator iter = rocksDB.newIterator();
        for (iter.seekToFirst(); iter.isValid(); iter.next()) {
            System.out.println("iter key:" + new String(iter.key()) + ", iter value:" + new String(iter.value()));
        }

        this.deleteData(key);
        System.out.println("after remove key:" + key);

        iter = rocksDB.newIterator();
        for (iter.seekToFirst(); iter.isValid(); iter.next()) {
            System.out.println("iter key:" + new String(iter.key()) + ", iter value:" + new String(iter.value()));
        }

    }

    public static void main(String[] args) throws RocksDBException {
        RocksDbHelper test = new RocksDbHelper("test_data");
        test.testDefaultColumnFamily();
        test.release();
    }

}
