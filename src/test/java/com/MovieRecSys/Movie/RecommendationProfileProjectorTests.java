package com.MovieRecSys.Movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RecommendationProfileProjectorTests {
    private final InteractionEventRepository interactionEventRepository = mock(InteractionEventRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final UserPreferenceProfileRepository userPreferenceProfileRepository = mock(UserPreferenceProfileRepository.class);

    private final RecommendationProfileProjector projector = new RecommendationProfileProjector(
            interactionEventRepository,
            appUserRepository,
            ratingRepository,
            movieRepository,
            userPreferenceProfileRepository,
            100,
            true
    );

    @Test
    void processPendingBatchRebuildsProfileFromCurrentUserState() {
        ObjectId userId = new ObjectId();
        InteractionEvent event = new InteractionEvent();
        event.setUserId(userId);
        event.setImdbId("tt1");
        event.setType(InteractionEventType.WATCHLIST_ADDED);
        event.setStatus(InteractionEventStatus.PENDING);
        event.setOccurredAt(Instant.now());

        AppUser user = new AppUser();
        user.setId(userId);
        user.setWatchlistImdbIds(List.of("tt1"));

        Rating rating = new Rating();
        rating.setUserId(userId);
        rating.setImdbId("tt2");
        rating.setValue(5);

        Movie watchlistMovie = movie("tt1", List.of("Sci-Fi"), List.of("space"), "Christopher Nolan");
        Movie ratedMovie = movie("tt2", List.of("Sci-Fi", "Adventure"), List.of("wormhole"), "Christopher Nolan");

        when(interactionEventRepository.findByStatusOrderByOccurredAtAsc(InteractionEventStatus.PENDING)).thenReturn(List.of(event));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(ratingRepository.findByUserId(userId)).thenReturn(List.of(rating));
        when(movieRepository.findByImdbIdIn(List.of("tt1", "tt2"))).thenReturn(List.of(watchlistMovie, ratedMovie));
        when(userPreferenceProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userPreferenceProfileRepository.save(any(UserPreferenceProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(interactionEventRepository.countByUserId(userId)).thenReturn(1L);

        int processed = projector.processPendingBatch();

        assertEquals(1, processed);

        ArgumentCaptor<UserPreferenceProfile> profileCaptor = ArgumentCaptor.forClass(UserPreferenceProfile.class);
        Mockito.verify(userPreferenceProfileRepository).save(profileCaptor.capture());
        UserPreferenceProfile savedProfile = profileCaptor.getValue();
        assertEquals(2, savedProfile.getAnchorImdbIds().size());
        assertTrue(savedProfile.getGenreWeights().get("sci-fi") > 0.0);
        assertTrue(savedProfile.getDirectorWeights().get("christopher nolan") > 0.0);
        assertEquals(1L, savedProfile.getSourceEventCount());

        ArgumentCaptor<List<InteractionEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(interactionEventRepository).saveAll(eventsCaptor.capture());
        assertEquals(InteractionEventStatus.PROCESSED, eventsCaptor.getValue().get(0).getStatus());
    }

    private Movie movie(String imdbId, List<String> genres, List<String> keywords, String director) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setGenres(genres);
        movie.setKeywords(keywords);
        movie.setDirector(director);
        return movie;
    }
}
