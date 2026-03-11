package com.simhospital.pathway;

import java.util.List;

public record Pathway(String name, List<PathwayEvent> events) {}
