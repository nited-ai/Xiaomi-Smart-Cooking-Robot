package com.kitchenos.connect.control;

/** Product availability is separate from the native protocol's advertised capability. */
public final class ControlAvailability {
    /** Home-only, settled raw change; native cold/open/zero-output guards remain. */
    public static final boolean POT_CHECK_AUTO=true;
    // Reviewed R4 encoder uses the firmware open-lid flag and excludes other MCUs.
    // Pipe/parser validation is complete; physical validation remains separate.
    public static final boolean LIDLESS=true;
    public static final String LIDLESS_REASON="Kochen ohne Deckel ist vorübergehend nicht verfügbar. Der Geräteablauf wird noch geprüft.";
    private ControlAvailability(){}
}
