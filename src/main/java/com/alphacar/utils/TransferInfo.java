package com.alphacar.utils;

public class TransferInfo {

    private String ethAddress;

    private double amount;

    public String getEthAddress() {
        return ethAddress;
    }

    public void setEthAddress(String ethAddress) {
        this.ethAddress = ethAddress;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String toString() {

        return "ethAddr:" + getEthAddress() + " amount:" + getAmount();

    }
}
