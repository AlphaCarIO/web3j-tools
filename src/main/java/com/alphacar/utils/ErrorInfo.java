package com.alphacar.utils;

public class ErrorInfo {

    private String addr;

    private int num;

    private double amt;

    @Override
    public String toString() {
        return "(num:" + getNum() + " addr:" + getAddr() + " amt:" + getAmt() + ")";
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public double getAmt() {
        return amt;
    }

    public void setAmt(double amt) {
        this.amt = amt;
    }

}
