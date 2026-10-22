package com.juca.homewatermonitorbackend.session.application.control;

public class ControlException extends RuntimeException {
    private final int status;
    private final String code;
    public ControlException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
}
