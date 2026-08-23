package no.javazone.elevator.shared.hypermedia;

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

    private Representation(
            String title,
            Map<String, Object> properties,
            List<Link> links,
            List<Affordance> affordances) {
        this.title = title;
        this.properties = properties;
        this.links = links;
        this.affordances = affordances;
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

    public static final class Builder {

        private final String title;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<Link> links = new java.util.ArrayList<>();
        private final List<Affordance> affordances = new java.util.ArrayList<>();

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

        public Builder link(String rel, String href) {
            return link(new Link(rel, href));
        }

        public Builder affordance(Affordance affordance) {
            affordances.add(affordance);
            return this;
        }

        public Builder affordances(List<Affordance> toAdd) {
            affordances.addAll(toAdd);
            return this;
        }

        public Representation build() {
            return new Representation(
                    title,
                    Map.copyOf(properties),
                    List.copyOf(links),
                    List.copyOf(affordances));
        }
    }
}
