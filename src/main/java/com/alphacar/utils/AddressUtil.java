package com.alphacar.utils;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.util.Arrays;

public class AddressUtil {

    public static String checkedAddress(final String address) {
        final String cleanAddress = Numeric.cleanHexPrefix(address).toLowerCase();
        //
        StringBuilder o = new StringBuilder();
        String keccak = Hash.sha3String(cleanAddress);
        char[] checkChars = keccak.substring(2).toCharArray();

        char[] cs = cleanAddress.toLowerCase().toCharArray();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            c = (Character.digit(checkChars[i], 16) & 0xFF) > 7 ? Character.toUpperCase(c) : Character.toLowerCase(c);
            o.append(c);
        }
        return Numeric.prependHexPrefix(o.toString());
    }

    public static boolean isCheckedAddress(final String address) {
        return Numeric.prependHexPrefix(address).equals(checkedAddress(address));
    }

    public static String formatAddress(final String rawAddress) {

        String rawAddressTemp = rawAddress.toLowerCase();
        rawAddressTemp = rawAddressTemp.replace(".eth", "");

        if (!rawAddressTemp.startsWith("0x")) {
            System.out.println("not startsWith 0x! rawAddressTemp:" + rawAddressTemp);
            return "";
        }

        if (rawAddressTemp.length() != 42) {
            System.out.println("not 42! rawAddressTemp:" + rawAddressTemp);
            return "";
        }

        String temp = checkedAddress(rawAddressTemp);

        if (!checkedAddress(temp).equals(temp)) {
            System.out.println("checkedAddress failed");
            return "";
        }

        return temp;

    }

    public static void main(String[] args) {
        Arrays.asList("0xc60540f1765d3c015c56393602ee52ab059a73b8",
                "0x88688d66D51ddAe1E75242d3Ba8aF81c21c53093.eth",
                "00Be17C6aD2738fb20B80f290C8fa1F4F8aB5902", // valid address
                "0x00Be17C6aD2738fb20B80f290C8fa1F4F8aB5902", // valid address
                "00be17C6aD2738fb20B80f290C8fa1F4F8aB5902", // invalid address
                "0x00be17C6aD2738fb20B80f290C8fa1F4F8aB5902"// invalid address
        ).forEach(addr -> {
            System.out.printf("%s => [%s]\n", addr, formatAddress(addr));
        });
    }

}
