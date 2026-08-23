package no.javazone.elevator.shared.render;

import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.MediaType;

/**
 * One serialisation of a {@link Representation}. One affordance model,
 * N renderers: adding a format means adding a class here, never
 * touching the domain -- see {@code docs/plan.html} &sect;7.
 *
 * <p>A renderer also renders a {@code Problem} representation (see
 * {@link Representation}'s class documentation): every renderer accepts
 * whatever properties and affordances it is given, whether they came
 * from a success or a refusal.
 */
public interface Renderer {

    MediaType mediaType();

    String render(Representation representation);
}
