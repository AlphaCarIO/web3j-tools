package com.alphacar.utils;

public class TransferInfo {

    @Override
    public String toString() {

        return String.format("%s,%s,%s,%f,%s,%s", getEthAddress(), getFormattedEthAddress(),
                getFlag(), getAmount(), getStatus(), getBaseUrl() + getTxHash());

    }

    private String ethAddress = "";

    private double amount = 0;

    private int id = -1;

    private String formattedEthAddress = "";

    private String flag;

    private String status = "";

    private String baseUrl;

    private String txHash = null;

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

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getFormattedEthAddress() {
        return formattedEthAddress;
    }

    public void setFormattedEthAddress(String formattedEthAddress) {
        this.formattedEthAddress = formattedEthAddress;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
