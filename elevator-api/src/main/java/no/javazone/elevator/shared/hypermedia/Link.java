package no.javazone.elevator.shared.hypermedia;

/**
 * A plain, non-actionable link: {@code rel}, the target, and a hint at
 * what a client would get if it followed it. Distinct from an
 * {@link Affordance}, which additionally says what to send and how --
 * see {@code docs/plan.html} &sect;6's "The commands" for why the two are
 * not interchangeable, and &sect;6's "And Link headers underneath" for
 * why every link here is also emitted as an RFC 8288 {@code Link}
 * header, one per link, alongside the body.
 */
public record Link(String rel, String href, String type) {

    public Link(String rel, String href) {
        this(rel, href, null);
    }
}
