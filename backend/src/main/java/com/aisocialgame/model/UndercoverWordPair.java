package com.aisocialgame.model;

public class UndercoverWordPair {
    private String civilian;
    private String undercover;

    public UndercoverWordPair() {}

    public UndercoverWordPair(String civilian, String undercover) {
        this.civilian = civilian;
        this.undercover = undercover;
    }

    public String getCivilian() { return civilian; }
    public String getUndercover() { return undercover; }

    public void setCivilian(String civilian) { this.civilian = civilian; }
    public void setUndercover(String undercover) { this.undercover = undercover; }
}
