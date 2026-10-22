package io.f1platform.ingestion;

/**
 * Maps provider position text and status text to the schema's result status.
 * The rules are in docs/STATUS_MAPPING.md. Unknown inputs fail loudly rather than being guessed.
 */
public final class StatusMapper {

    public enum Status { CLASSIFIED, RETIRED, DISQUALIFIED, EXCLUDED, WITHDRAWN, NOT_CLASSIFIED }

    private StatusMapper() {}

    public static Status map(String positionText, String statusText) {
        String pos = positionText == null ? "" : positionText.trim();
        String status = statusText == null ? "" : statusText.trim();
        if (pos.matches("\\d+")) {
            return Status.CLASSIFIED;
        }
        return switch (pos) {
            case "R" -> "Not classified".equals(status) ? Status.NOT_CLASSIFIED : Status.RETIRED;
            case "D" -> "Excluded".equals(status) ? Status.EXCLUDED : Status.DISQUALIFIED;
            case "W" -> "Did not start".equals(status) ? Status.NOT_CLASSIFIED : Status.WITHDRAWN;
            default -> throw new IllegalArgumentException(
                    "unmapped positionText '" + pos + "' with status '" + status + "'");
        };
    }
}
