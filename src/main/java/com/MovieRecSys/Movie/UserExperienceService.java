package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserExperienceService {
    private final AuthService authService;
    private final AppUserRepository appUserRepository;
    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final InteractionEventPublisher interactionEventPublisher;
    private final RecommendationProfileProjector recommendationProfileProjector;
    private final RecommendationCacheService recommendationCacheService;

    public UserExperienceService(
            AuthService authService,
            AppUserRepository appUserRepository,
            MovieRepository movieRepository,
            RatingRepository ratingRepository,
            InteractionEventPublisher interactionEventPublisher,
            RecommendationProfileProjector recommendationProfileProjector,
            RecommendationCacheService recommendationCacheService
    ) {
        this.authService = authService;
        this.appUserRepository = appUserRepository;
        this.movieRepository = movieRepository;
        this.ratingRepository = ratingRepository;
        this.interactionEventPublisher = interactionEventPublisher;
        this.recommendationProfileProjector = recommendationProfileProjector;
        this.recommendationCacheService = recommendationCacheService;
    }

    /**
     * Rebuild the user's preference profile from their now-current ratings and
     * watchlist, then evict their cached recommendations. Done synchronously on
     * each interaction so personalized recommendations update immediately (the
     * scheduled projector still runs as the durable, replayable path).
     */
    private void refreshRecommendations(ObjectId userId) {
        recommendationProfileProjector.rebuildProfile(userId);
        recommendationCacheService.invalidateUserCaches(userId);
    }

    public WatchlistResponse getWatchlist(String authorizationHeader) {
        AppUser user = authService.requireUser(authorizationHeader);
        List<String> imdbIds = user.getWatchlistImdbIds() == null ? List.of() : user.getWatchlistImdbIds();
        return new WatchlistResponse(movieRepository.findByImdbIdIn(imdbIds));
    }

    public AuthResponse.UserProfile addToWatchlist(String authorizationHeader, String imdbId) {
        AppUser user = authService.requireUser(authorizationHeader);
        Movie movie = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        List<String> watchlist = user.getWatchlistImdbIds() == null ? new ArrayList<>() : new ArrayList<>(user.getWatchlistImdbIds());
        if (!watchlist.contains(movie.getImdbId())) {
            watchlist.add(movie.getImdbId());
            user.setWatchlistImdbIds(watchlist);
            user = appUserRepository.save(user);
            interactionEventPublisher.publishWatchlistAdded(user.getId(), movie.getImdbId());
            refreshRecommendations(user.getId());
        }

        return AuthResponse.UserProfile.from(user);
    }

    public AuthResponse.UserProfile removeFromWatchlist(String authorizationHeader, String imdbId) {
        AppUser user = authService.requireUser(authorizationHeader);
        List<String> watchlist = user.getWatchlistImdbIds() == null ? new ArrayList<>() : new ArrayList<>(user.getWatchlistImdbIds());
        if (watchlist.remove(imdbId)) {
            user.setWatchlistImdbIds(watchlist);
            user = appUserRepository.save(user);
            interactionEventPublisher.publishWatchlistRemoved(user.getId(), imdbId);
            refreshRecommendations(user.getId());
        }
        return AuthResponse.UserProfile.from(user);
    }

    public Rating rateMovie(String authorizationHeader, RatingRequest request) {
        AppUser user = authService.requireUser(authorizationHeader);
        Movie movie = movieRepository.findMovieByImdbId(request.imdbId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        Rating rating = ratingRepository.findByUserIdAndImdbId(user.getId(), movie.getImdbId())
                .orElseGet(Rating::new);
        rating.setUserId(user.getId());
        rating.setImdbId(movie.getImdbId());
        rating.setValue(request.rating());
        rating.setUpdatedAt(Instant.now());

        Rating savedRating = ratingRepository.save(rating);
        refreshMovieRatingStats(movie.getImdbId());
        interactionEventPublisher.publishRatingSet(user.getId(), movie.getImdbId(), request.rating());
        refreshRecommendations(user.getId());
        return savedRating;
    }

    public List<Rating> userRatings(String authorizationHeader) {
        AppUser user = authService.requireUser(authorizationHeader);
        return ratingRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(Rating::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void refreshMovieRatingStats(String imdbId) {
        Movie movie = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        List<Rating> ratings = ratingRepository.findByImdbId(imdbId);
        double average = ratings.stream()
                .mapToInt(Rating::getValue)
                .average()
                .orElse(0.0);

        movie.setAverageRating(ratings.isEmpty() ? null : Math.round(average * 10.0) / 10.0);
        movie.setRatingCount(ratings.size());
        movieRepository.save(movie);
    }
}
