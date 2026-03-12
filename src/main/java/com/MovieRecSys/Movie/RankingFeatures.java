package com.MovieRecSys.Movie;

/**
 * Captures the individual ranking feature values used to score a candidate movie.
 * This is used for both the final ranking and for evaluation/debugging.
 */
public record RankingFeatures(
        String imdbId,
        double genreOverlap,
        double keywordOverlap,
        double castOverlap,
        double directorMatch,
        double collaborativeSignal,
        double ratingAffinity,
        double popularityBias,
        double recencyBoost,
        double explorationFactor,
        double preferenceAlignment,
        double totalScore
) {
    public static Builder builder(String imdbId) {
        return new Builder(imdbId);
    }

    public static class Builder {
        private final String imdbId;
        private double genreOverlap;
        private double keywordOverlap;
        private double castOverlap;
        private double directorMatch;
        private double collaborativeSignal;
        private double ratingAffinity;
        private double popularityBias;
        private double recencyBoost;
        private double explorationFactor;
        private double preferenceAlignment;

        Builder(String imdbId) {
            this.imdbId = imdbId;
        }

        public Builder genreOverlap(double v) { this.genreOverlap = v; return this; }
        public Builder keywordOverlap(double v) { this.keywordOverlap = v; return this; }
        public Builder castOverlap(double v) { this.castOverlap = v; return this; }
        public Builder directorMatch(double v) { this.directorMatch = v; return this; }
        public Builder collaborativeSignal(double v) { this.collaborativeSignal = v; return this; }
        public Builder ratingAffinity(double v) { this.ratingAffinity = v; return this; }
        public Builder popularityBias(double v) { this.popularityBias = v; return this; }
        public Builder recencyBoost(double v) { this.recencyBoost = v; return this; }
        public Builder explorationFactor(double v) { this.explorationFactor = v; return this; }
        public Builder preferenceAlignment(double v) { this.preferenceAlignment = v; return this; }

        public RankingFeatures build() {
            double total = genreOverlap + keywordOverlap + castOverlap + directorMatch
                    + collaborativeSignal + ratingAffinity + popularityBias
                    + recencyBoost + explorationFactor + preferenceAlignment;
            return new RankingFeatures(imdbId, genreOverlap, keywordOverlap, castOverlap,
                    directorMatch, collaborativeSignal, ratingAffinity, popularityBias,
                    recencyBoost, explorationFactor, preferenceAlignment, total);
        }
    }
}
