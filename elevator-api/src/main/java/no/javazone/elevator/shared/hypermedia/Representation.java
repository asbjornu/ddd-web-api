package no.javazone.elevator.shared.hypermedia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resource representation: properties, links and affordances, with no
 * opinion yet about which media type it will be serialised as -- that is
 * a renderer's job (see {@code shared.render}), and adding a renderer
 * must never require a change here.
 *
 * <p>A refusal is rendered from this same class: by convention, a
 * {@code Problem} representation carries {@code type}, {@code title},
 * {@code status}, {@code detail} and {@code instance} among its
 * properties (RFC 9457 &sect;3.1's own members). Renderers that know
 * about problem+json promote those to top-level members; the other three
 * render them as ordinary properties, which is exactly what RFC 9457
 * Appendix C anticipates for formats that embed a problem rather than
 * define it -- see {@code docs/plan.html} &sect;6's "One model, one more
 * renderer".
 */
public final class Representation {

    private final String title;
    private final Map<String, Object> properties;
    private final List<Link> links;
    private final List<Affordance> affordances;
    private final String containerId;
    private final String contentWrapperId;
    private final List<AutoInit> autoInits;

    /** A nested {@code data-init} div {@link no.javazone.elevator.shared.render.HtmlRenderer}
     * emits so a client fetches the next resource in a discovery chain
     * (or opens the live stream) the moment this one is rendered,
     * without the client ever constructing {@code href} itself -- see
     * {@code docs/architecture.md}'s "Identifiers and URIs" section. */
    public record AutoInit(String id, String href) {
    }

    private Representation(
            String title,
            Map<String, Object> properties,
            List<Link> links,
            List<Affordance> affordances,
            String containerId,
            String contentWrapperId,
            List<AutoInit> autoInits) {
        this.title = title;
        this.properties = properties;
        this.links = links;
        this.affordances = affordances;
        this.containerId = containerId;
        this.contentWrapperId = contentWrapperId;
        this.autoInits = autoInits;
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public String title() {
        return title;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public List<Link> links() {
        return links;
    }

    public List<Affordance> affordances() {
        return affordances;
    }

    /** The id of the outermost div {@link no.javazone.elevator.shared.render.HtmlRenderer}
     * wraps this representation's HTML in, for Datastar to morph by id
     * -- {@code null} for a representation never fetched that way (a
     * refusal, or a format other than HTML doesn't care either way). */
    public String containerId() {
        return containerId;
    }

    /** The id of the inner div wrapping just the properties/links/forms
     * (never the {@link #autoInits()}), so a later live patch can
     * replace that content alone without re-triggering this
     * representation's own auto-init divs. */
    public String contentWrapperId() {
        return contentWrapperId;
    }

    public List<AutoInit> autoInits() {
        return autoInits;
    }

    public static final class Builder {

        private final String title;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<Link> links = new java.util.ArrayList<>();
        private final List<Affordance> affordances = new java.util.ArrayList<>();
        private String containerId;
        private String contentWrapperId;
        private final List<AutoInit> autoInits = new java.util.ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        public Builder property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        public Builder link(Link link) {
            links.add(link);
            return this;
        }

        public Builder affordance(Affordance affordance) {
            affordances.add(affordance);
            return this;
        }

        public Builder affordances(List<Affordance> toAdd) {
            affordances.addAll(toAdd);
            return this;
        }

        public Builder containerId(String id) {
            this.containerId = id;
            return this;
        }

        public Builder contentWrapperId(String id) {
            this.contentWrapperId = id;
            return this;
        }

        public Builder autoInit(String id, String href) {
            autoInits.add(new AutoInit(id, href));
            return this;
        }

        public Representation build() {
            // Not Map.copyOf: several properties (e.g. destinationFloor)
            // are legitimately null when the state they describe does
            // not apply, and Map.copyOf/Map.of both reject null values.
            return new Representation(
                    title,
                    Collections.unmodifiableMap(new LinkedHashMap<>(properties)),
                    List.copyOf(links),
                    List.copyOf(affordances),
                    containerId,
                    contentWrapperId,
                    List.copyOf(autoInits));
        }
    }
}
