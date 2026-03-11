package com.simhospital.pathway;

import java.time.Duration;
import java.util.Map;

/**
 * A single step in a pathway.
 *
 * @param type       the event type (never null)
 * @param delay      wall-clock delay before this event fires; null means fire immediately after prior event
 * @param parameters event-specific config from YAML (never null, may be empty)
 */
public record PathwayEvent(
    PathwayEventType type,
    Duration delay,
    Map<String, String> parameters
) {}
