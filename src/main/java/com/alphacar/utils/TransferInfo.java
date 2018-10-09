package com.alphacar.utils;

public class TransferInfo {

    @Override
    public String toString() {

        return String.format("%s,%s,%f,%s", getEthAddress(), getFormattedEthAddress(), getAmount(), getReceipt());

    }

    private String ethAddress;

    private double amount;

    private int id;

    private String formattedEthAddress;

    private String receipt;

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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public String getFormattedEthAddress() {
        return formattedEthAddress;
    }

    public void setFormattedEthAddress(String formattedEthAddress) {
        this.formattedEthAddress = formattedEthAddress;
    }
}
