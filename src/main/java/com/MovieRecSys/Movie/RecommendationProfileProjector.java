package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RecommendationProfileProjector {
    private final InteractionEventRepository interactionEventRepository;
    private final AppUserRepository appUserRepository;
    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final int batchSize;
    private final boolean projectorEnabled;

    public RecommendationProfileProjector(
            InteractionEventRepository interactionEventRepository,
            AppUserRepository appUserRepository,
            RatingRepository ratingRepository,
            MovieRepository movieRepository,
            UserPreferenceProfileRepository userPreferenceProfileRepository,
            @Value("${app.recommendations.projector.batch-size:100}") int batchSize,
            @Value("${app.recommendations.projector.enabled:true}") boolean projectorEnabled
    ) {
        this.interactionEventRepository = interactionEventRepository;
        this.appUserRepository = appUserRepository;
        this.ratingRepository = ratingRepository;
        this.movieRepository = movieRepository;
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.batchSize = batchSize;
        this.projectorEnabled = projectorEnabled;
    }

    @Scheduled(fixedDelayString = "${app.recommendations.projector.fixed-delay-ms:5000}")
    public void processPendingEventsOnSchedule() {
        if (!projectorEnabled) {
            return;
        }
        processPendingBatch();
    }

    public int processPendingBatch() {
        List<InteractionEvent> pendingEvents = interactionEventRepository.findByStatusOrderByOccurredAtAsc(InteractionEventStatus.PENDING).stream()
                .limit(batchSize)
                .toList();
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        Set<ObjectId> affectedUsers = pendingEvents.stream()
                .map(InteractionEvent::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (ObjectId userId : affectedUsers) {
            rebuildProfile(userId);
        }

        Instant processedAt = Instant.now();
        pendingEvents.forEach(event -> {
            event.setStatus(InteractionEventStatus.PROCESSED);
            event.setProcessedAt(processedAt);
        });
        interactionEventRepository.saveAll(pendingEvents);
        return pendingEvents.size();
    }

    public UserPreferenceProfile rebuildProfile(ObjectId userId) {
        AppUser user = appUserRepository.findById(userId).orElse(null);
        if (user == null) {
            UserPreferenceProfile deletedProfile = userPreferenceProfileRepository.findByUserId(userId).orElse(null);
            if (deletedProfile != null) {
                userPreferenceProfileRepository.delete(deletedProfile);
            }
            return emptyProfile(userId);
        }

        Map<String, Movie> moviesByImdbId = loadRelevantMovies(userId, user.getWatchlistImdbIds());
        List<Rating> ratings = ratingRepository.findByUserId(userId);
        UserPreferenceProfile profile = userPreferenceProfileRepository.findByUserId(userId).orElseGet(UserPreferenceProfile::new);
        profile.setUserId(userId);

        Set<String> anchors = new LinkedHashSet<>();
        Map<String, Double> genreWeights = new HashMap<>();
        Map<String, Double> keywordWeights = new HashMap<>();
        Map<String, Double> directorWeights = new HashMap<>();

        for (String imdbId : user.getWatchlistImdbIds() == null ? List.<String>of() : user.getWatchlistImdbIds()) {
            Movie movie = moviesByImdbId.get(imdbId);
            if (movie == null) {
                continue;
            }
            anchors.add(imdbId);
            applyMovieSignal(movie, 1.75, genreWeights, keywordWeights, directorWeights);
        }

        for (Rating rating : ratings) {
            Movie movie = moviesByImdbId.get(rating.getImdbId());
            if (movie == null) {
                continue;
            }
            double signal = ratingSignal(rating.getValue());
            if (signal >= 1.5) {
                anchors.add(rating.getImdbId());
            }
            applyMovieSignal(movie, signal, genreWeights, keywordWeights, directorWeights);
        }

        profile.setAnchorImdbIds(new ArrayList<>(anchors));
        profile.setGenreWeights(cleanWeights(genreWeights));
        profile.setKeywordWeights(cleanWeights(keywordWeights));
        profile.setDirectorWeights(cleanWeights(directorWeights));
        profile.setUpdatedAt(Instant.now());
        profile.setSourceEventCount(interactionEventRepository.countByUserId(userId));
        return userPreferenceProfileRepository.save(profile);
    }

    private Map<String, Movie> loadRelevantMovies(ObjectId userId, List<String> watchlistImdbIds) {
        Set<String> imdbIds = new LinkedHashSet<>(watchlistImdbIds == null ? List.of() : watchlistImdbIds);
        ratingRepository.findByUserId(userId).stream()
                .map(Rating::getImdbId)
                .forEach(imdbIds::add);

        return movieRepository.findByImdbIdIn(new ArrayList<>(imdbIds)).stream()
                .collect(Collectors.toMap(Movie::getImdbId, movie -> movie));
    }

    private double ratingSignal(int ratingValue) {
        return switch (ratingValue) {
            case 5 -> 2.5;
            case 4 -> 1.8;
            case 3 -> 0.5;
            case 2 -> -0.75;
            case 1 -> -1.5;
            default -> 0.0;
        };
    }

    private void applyMovieSignal(
            Movie movie,
            double signal,
            Map<String, Double> genreWeights,
            Map<String, Double> keywordWeights,
            Map<String, Double> directorWeights
    ) {
        addAll(movie.getGenres(), genreWeights, signal * 1.0);
        addAll(movie.getKeywords(), keywordWeights, signal * 0.7);
        if (movie.getDirector() != null && !movie.getDirector().isBlank()) {
            directorWeights.merge(movie.getDirector().toLowerCase(), signal * 1.2, Double::sum);
        }
    }

    private void addAll(List<String> values, Map<String, Double> target, double increment) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .forEach(value -> target.merge(value, increment, Double::sum));
    }

    private Map<String, Double> cleanWeights(Map<String, Double> source) {
        return source.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue()) >= 0.25)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(100)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private UserPreferenceProfile emptyProfile(ObjectId userId) {
        UserPreferenceProfile profile = new UserPreferenceProfile();
        profile.setUserId(userId);
        profile.setAnchorImdbIds(List.of());
        profile.setGenreWeights(Map.of());
        profile.setKeywordWeights(Map.of());
        profile.setDirectorWeights(Map.of());
        profile.setUpdatedAt(Instant.now());
        profile.setSourceEventCount(0);
        return profile;
    }
}
