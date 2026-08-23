package no.javazone.elevator.shared.hypermedia;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The rel(s) slice 0 itself introduces. Every later slice that adds a
 * command-carrying rel (e.g. {@code call-elevator} in slice 2) adds its
 * own small {@link RelDocumentationSource} alongside its
 * {@link AffordanceContributor}, rather than editing this class.
 */
@Component
public class CoreRels implements RelDocumentationSource {

    @Override
    public List<RelDefinition> rels() {
        return List.of(
                new RelDefinition(
                        "help",
                        "Points at documentation for this API: the rel "
                                + "vocabulary and the media types it uses. "
                                + "Not an affordance -- following it changes "
                                + "nothing."));
    }
}
