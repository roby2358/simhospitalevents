package com.simhospital;

import com.simhospital.pathway.Pathway;
import com.simhospital.pathway.PathwayEvent;
import com.simhospital.pathway.PathwayLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathwayLoaderTest {

    private static final Path PATHWAYS_DIR = Path.of("../simhospital/configs/pathways");

    @Test
    void loadsAllYamlFiles() {
        PathwayLoader loader = new PathwayLoader();
        Map<String, Pathway> pathways = loader.load(PATHWAYS_DIR);

        assertFalse(pathways.isEmpty(), "Should load at least one pathway");
    }

    @Test
    void eachPathwayHasAtLeastOneEvent() {
        PathwayLoader loader = new PathwayLoader();
        Map<String, Pathway> pathways = loader.load(PATHWAYS_DIR);

        for (Map.Entry<String, Pathway> entry : pathways.entrySet()) {
            Pathway pathway = entry.getValue();
            assertFalse(pathway.events().isEmpty(),
                    "Pathway '" + entry.getKey() + "' should have at least one event");
        }
    }

    @Test
    void allEventTypesAreRecognised() {
        PathwayLoader loader = new PathwayLoader();
        Map<String, Pathway> pathways = loader.load(PATHWAYS_DIR);

        for (Map.Entry<String, Pathway> entry : pathways.entrySet()) {
            for (PathwayEvent event : entry.getValue().events()) {
                assertNotNull(event.type(),
                        "Event type should not be null in pathway '" + entry.getKey() + "'");
            }
        }
    }

    @Test
    void throwsOnNonExistentDirectory() {
        PathwayLoader loader = new PathwayLoader();
        assertThrows(IllegalArgumentException.class,
                () -> loader.load(Path.of("/nonexistent/path")));
    }
}
