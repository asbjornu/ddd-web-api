package no.javazone.elevator.shared.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Ordinary content negotiation over the four success renderers and the
 * one failure renderer, with one tie-break: where the client's stated
 * preferences are equal, prefer {@code application/problem+json} for a
 * refusal, because it is the more specific type for the situation and
 * its members are standardised -- see {@code docs/plan.html} &sect;6's
 * "Negotiating a failure" for the worked table this method implements.
 */
@Component
public class RendererRegistry {

    private record Candidate(MediaType accepted, Renderer renderer) {
    }

    private static int specificityRank(MediaType mediaType) {
        if (mediaType.isWildcardType()) {
            return 2;
        }
        if (mediaType.isWildcardSubtype()) {
            return 1;
        }
        return 0;
    }

    private final List<Renderer> successRenderers;
    private final Renderer problemRenderer;

    public RendererRegistry(List<Renderer> renderers, ProblemJsonRenderer problemRenderer) {
        this.successRenderers = renderers.stream()
                .filter(renderer -> renderer != problemRenderer)
                .toList();
        this.problemRenderer = problemRenderer;
    }

    /** Negotiates among the four success formats only. */
    public Optional<Renderer> select(String acceptHeader) {
        return bestMatch(acceptHeader, successRenderers, false);
    }

    /**
     * Negotiates a refusal: the four success formats plus
     * {@code problem+json}, which wins any tie and is the default when
     * the client stated no usable preference at all.
     */
    public Renderer selectForFailure(String acceptHeader) {
        List<Renderer> candidates = new ArrayList<>(successRenderers);
        candidates.add(problemRenderer);
        return bestMatch(acceptHeader, candidates, true).orElse(problemRenderer);
    }

    private Optional<Renderer> bestMatch(
            String acceptHeader, List<Renderer> candidates, boolean preferProblem) {
        List<MediaType> accepted;
        try {
            accepted = acceptHeader == null || acceptHeader.isBlank()
                    ? List.of(MediaType.ALL)
                    : MediaType.parseMediaTypes(acceptHeader);
        } catch (IllegalArgumentException invalidHeader) {
            accepted = List.of(MediaType.ALL);
        }

        List<Candidate> allMatches = new ArrayList<>();
        for (MediaType acceptedType : accepted) {
            for (Renderer renderer : candidates) {
                if (acceptedType.includes(renderer.mediaType())) {
                    allMatches.add(new Candidate(acceptedType, renderer));
                }
            }
        }
        if (allMatches.isEmpty()) {
            return Optional.empty();
        }

        // Every accepted type sharing the highest quality value present
        // among the matches is "equally preferred", per RFC 7231 -- and
        // it is exactly there, not within a single accepted type's own
        // matches, that the problem+json tie-break applies.
        double topQuality = allMatches.stream()
                .mapToDouble(candidate -> candidate.accepted().getQualityValue())
                .max()
                .orElse(0);
        List<Candidate> tier = allMatches.stream()
                .filter(candidate -> candidate.accepted().getQualityValue() == topQuality)
                .toList();

        if (preferProblem) {
            boolean tierHasMoreThanOneFormat = tier.stream()
                    .map(candidate -> candidate.renderer().mediaType())
                    .distinct()
                    .count() > 1;
            Optional<Renderer> problem = tier.stream()
                    .map(Candidate::renderer)
                    .filter(renderer -> renderer.mediaType().equals(
                            ElevatorMediaTypes.PROBLEM_JSON))
                    .findFirst();
            if (problem.isPresent() && tierHasMoreThanOneFormat) {
                return problem;
            }
        }

        return tier.stream()
                .min(Comparator.comparingInt(candidate -> specificityRank(candidate.accepted())))
                .map(Candidate::renderer);
    }
}
