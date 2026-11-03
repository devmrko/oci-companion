package io.github.devmrko.companion.service;

public final class ReportSettings {
    private ReportSettings() {}
    public static void prefix(String value){if(!value.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")||value.length()>200)throw Errors.INVALID.exception();}
    public static void path(String value){if(!value.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")||value.length()>500||java.util.Arrays.asList(value.split("/")).contains(".."))throw Errors.INVALID.exception();}
}
