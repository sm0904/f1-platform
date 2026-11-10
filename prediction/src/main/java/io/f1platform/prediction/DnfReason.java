package io.f1platform.prediction;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Groups the provider's free-text retirement reasons into broad categories, so the frequency and pattern of
 * retirements can be reported without losing the detail stored in status_detail.
 *
 * Matching uses whole words, so "Retired" is not read as "tyre". Reasons the provider does not explain
 * ("Retired", "Lapped", "+N Laps", "Mechanical") form their own group rather than being guessed.
 * The first matching rule wins, so the order of the rules matters.
 */
public enum DnfReason {
    ACCIDENT("Accident or collision", "accident|collision|spun|crash|debris"),
    ENGINE("Engine or power unit", "engine|power ?unit|turbo|supercharger|throttle|injection|exhaust|power loss|ers|mgu|spark plugs?|ignition|magneto|distributor"),
    TRANSMISSION("Gearbox or transmission", "gearbox|transmission|clutch|driveshaft|halfshaft|differential|drivetrain|axle"),
    HYDRAULICS_BRAKES("Hydraulics or brakes", "hydraulics?|brakes?"),
    CHASSIS_WHEELS("Suspension, steering, wheels or tyres", "suspension|steering|handling|chassis|wheels?|wheel nut|wheel bearing|puncture|tyres?|tires?|front wing|rear wing|wing|undertray|vibrations?"),
    ELECTRICAL("Electrical or electronics", "electrical|electronics?|battery|alternator|software|pneumatics?"),
    COOLING_FUEL_OIL("Cooling, fuel or oil", "cooling|overheating|radiator|fuel|oil|water|pressure"),
    DRIVER_HEALTH("Driver health or injury", "driver unwell|injury|unwell"),
    UNSPECIFIED("No reason given", "retired|lapped|\\+\\d+ laps?|mechanical|technical|stalled|damage|physical"),
    OTHER("Other or unclassified", "");

    public final String label;
    private final List<Pattern> patterns;

    DnfReason(String label, String alternatives) {
        this.label = label;
        this.patterns = alternatives.isEmpty() ? List.of()
                : List.of(Pattern.compile("(?<![a-z])(?:" + alternatives + ")(?![a-z])", Pattern.CASE_INSENSITIVE));
    }

    public static DnfReason of(String detail) {
        if (detail == null || detail.isBlank()) {
            return UNSPECIFIED;
        }
        String d = detail.trim().toLowerCase(Locale.ROOT);
        for (DnfReason r : values()) {
            for (Pattern p : r.patterns) {
                if (p.matcher(d).find()) {
                    return r;
                }
            }
        }
        return OTHER;
    }
}
