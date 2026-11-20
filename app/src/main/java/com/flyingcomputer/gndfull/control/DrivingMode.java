package com.flyingcomputer.gndfull.control;

public enum DrivingMode {
    RC("RC"),
    CLI("CLI"),
    FILES("FILES"),
    PLN("PLN (TBA)");

    public final String label;

    DrivingMode(String label) {
        this.label = label;
    }
}
