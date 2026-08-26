package no.javazone.elevator.feature.insertkey;

import no.javazone.elevator.shared.hypermedia.Representation;

/**
 * The RFC 9728 challenge {@link InsertKeyController} and
 * {@link KeySwitchSessionController} both answer with, factored out so
 * the two agree byte-for-byte: {@code insert-key} itself refuses
 * unconditionally (it is never anything but a challenge), and
 * completing the session it starts refuses the same way whenever the
 * caller has not actually presented a valid token yet.
 */
final class KeySwitchChallenge {

    static final String WWW_AUTHENTICATE =
            "Bearer resource_metadata=\"/.well-known/oauth-protected-resource\", "
                    + "scope=\"elevator:maintenance elevator:recall\"";

    private KeySwitchChallenge() {
    }

    static Representation representation() {
        return Representation.builder("Unauthorized")
                .property("type", "about:blank")
                .property("title", "Unauthorized")
                .property("status", 401)
                .property("detail", "Insert the technician key to discover how to authenticate.")
                .build();
    }
}
