package com.juca.homewatermonitorbackend.cultivation.application;

public class CultivationException extends RuntimeException {
    private final int status;
    private final String code;
    public CultivationException(int status,String code) { super(code);this.status=status;this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
}
