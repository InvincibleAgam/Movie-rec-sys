const state = {
    movies: [],
    filteredMovies: [],
    selectedMovie: null,
    activeGenre: "All",
    searchTerm: "",
    loading: true,
    submittingReview: false,
    authToken: localStorage.getItem("movieAtlasToken") || "",
    currentUser: null,
    watchlist: [],
    watchlistMovies: [],
    ratings: [],
    forYouMovies: [],
    selectedRecommendations: [],
    authMode: "login",
    otpStep: "email",
    otpVerified: false,
    otpEmail: ""
};

const movieGrid = document.getElementById("movieGrid");
const spotlightContent = document.getElementById("spotlightContent");
const detailsContent = document.getElementById("detailsContent");
const genreFilters = document.getElementById("genreFilters");
const searchInput = document.getElementById("searchInput");
const surpriseButton = document.getElementById("surpriseButton");
const statusBanner = document.getElementById("statusBanner");
const loadingTemplate = document.getElementById("loadingCardTemplate");
const accountPanel = document.getElementById("accountPanel");
const forYouRail = document.getElementById("forYouRail");
const heroStats = document.getElementById("heroStats");
const watchlistPanel = document.getElementById("watchlistPanel");

document.addEventListener("DOMContentLoaded", async () => {
    renderLoadingCards();
    wireEvents();
    renderAccountPanel();
    renderForYouRail();
    renderWatchlistPanel();
    await loadMovies();
    await restoreSession();
});

function wireEvents() {
    searchInput.addEventListener("input", (event) => {
        state.searchTerm = event.target.value.trim().toLowerCase();
        applyFilters();
    });

    surpriseButton.addEventListener("click", () => {
        if (!state.filteredMovies.length) {
            return;
        }
        const randomMovie = state.filteredMovies[Math.floor(Math.random() * state.filteredMovies.length)];
        selectMovie(randomMovie.imdbId);
        document.getElementById("collection")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    window.addEventListener("hashchange", handleHashSelection);
}

async function loadMovies() {
    state.loading = true;
    setStatus("");

    try {
        const movies = await fetchJson("/api/v1/movies");
        state.movies = Array.isArray(movies) ? movies : [];
        state.loading = false;
        applyFilters();
        handleHashSelection();
    } catch (error) {
        state.loading = false;
        state.movies = [];
        state.filteredMovies = [];
        renderMovieGrid();
        renderSpotlight();
        renderDetails();
        renderHeroStats();
        setStatus("We could not load the movie collection. Make sure the Spring app is running and MongoDB is reachable.", true);
        console.error(error);
    }
}

async function restoreSession() {
    if (!state.authToken) {
        renderAccountPanel();
        renderForYouRail();
        return;
    }

    try {
        const profile = await api("/api/v1/auth/me");
        state.currentUser = profile;
        await Promise.all([loadWatchlist(), loadRatings(), loadForYou()]);
        renderAccountPanel();
        renderForYouRail();
        renderWatchlistPanel();
        renderDetails();
    } catch (error) {
        resetAuthState();
        renderAccountPanel();
        renderForYouRail();
        renderWatchlistPanel();
        setStatus("Your saved session expired. Please log in again.");
        console.error(error);
    }
}

function applyFilters() {
    const filtered = state.movies.filter((movie) => {
        const matchesSearch = [movie.title, movie.imdbId, movie.director]
            .filter(Boolean)
            .some((value) => value.toLowerCase().includes(state.searchTerm));
        const matchesGenre = state.activeGenre === "All" || (movie.genres || []).includes(state.activeGenre);
        return matchesSearch && matchesGenre;
    });

    state.filteredMovies = filtered;

    if (state.selectedMovie) {
        const refreshedSelection = state.movies.find((movie) => movie.imdbId === state.selectedMovie.imdbId);
        state.selectedMovie = refreshedSelection || filtered[0] || null;
    } else {
        state.selectedMovie = filtered[0] || state.movies[0] || null;
    }

    renderGenreFilters();
    renderMovieGrid();
    renderSpotlight();
    renderHeroStats();
    renderDetails();
}

function renderGenreFilters() {
    const genres = new Set(["All"]);
    state.movies.forEach((movie) => (movie.genres || []).forEach((genre) => genres.add(genre)));

    genreFilters.innerHTML = "";
    [...genres].forEach((genre) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `genre-button${genre === state.activeGenre ? " active" : ""}`;
        button.textContent = genre;
        button.addEventListener("click", () => {
            state.activeGenre = genre;
            applyFilters();
        });
        genreFilters.appendChild(button);
    });
}

function renderHeroStats() {
    const totalReviews = state.movies.reduce((count, movie) => count + (movie.reviews?.length || 0), 0);
    heroStats.innerHTML = `
        <div class="stat-pill"><strong>${state.movies.length}</strong><span>movies</span></div>
        <div class="stat-pill"><strong>${totalReviews}</strong><span>reviews</span></div>
        <div class="stat-pill"><strong>${state.currentUser ? state.watchlist.length : "0"}</strong><span>watchlist</span></div>
    `;
}

function renderLoadingCards() {
    movieGrid.innerHTML = "";
    for (let index = 0; index < 6; index += 1) {
        movieGrid.appendChild(loadingTemplate.content.firstElementChild.cloneNode(true));
    }
}

function renderMovieGrid() {
    movieGrid.innerHTML = "";

    if (state.loading) {
        renderLoadingCards();
        return;
    }

    if (!state.filteredMovies.length) {
        const empty = document.createElement("div");
        empty.className = "empty-grid";
        empty.innerHTML = "<p>No movies matched your filters. Try another title or clear the genre filter.</p>";
        movieGrid.appendChild(empty);
        return;
    }

    state.filteredMovies.forEach((movie, index) => {
        const card = document.createElement("article");
        card.className = `movie-card${state.selectedMovie?.imdbId === movie.imdbId ? " active" : ""}`;
        card.style.animationDelay = `${Math.min(index * 50, 300)}ms`;
        card.innerHTML = `
            <div class="card-art">
                ${renderImage(movie.poster, `${escapeHtml(movie.title)} poster`)}
                <div class="card-overlay"></div>
            </div>
            <div class="card-body">
                <div class="card-title-row">
                    <h3 class="card-title">${escapeHtml(movie.title || "Untitled")}</h3>
                    <span class="tag">${escapeHtml(formatYear(movie.releaseDate))}</span>
                </div>
                <p class="movie-meta">${escapeHtml(formatReleaseDate(movie.releaseDate))}</p>
                <p class="card-summary">${escapeHtml(buildSummary(movie))}</p>
                <div class="card-badges">
                    <span class="tag rating-tag">${renderStars(movie.averageRating)}</span>
                    ${state.watchlist.includes(movie.imdbId) ? '<span class="tag accent-tag">In watchlist</span>' : ""}
                </div>
            </div>
        `;

        card.addEventListener("click", () => selectMovie(movie.imdbId));
        movieGrid.appendChild(card);
    });
}

function renderSpotlight() {
    if (!state.movies.length) {
        spotlightContent.innerHTML = '<div class="loading-copy">Movie spotlight will appear once the catalog loads.</div>';
        return;
    }

    const featuredMovie = state.filteredMovies[0] || state.movies[0];
    const spotlightImage = featuredMovie.backdrops?.[0] || featuredMovie.poster;

    spotlightContent.innerHTML = `
        ${spotlightImage ? `<img class="spotlight-backdrop" src="${escapeAttribute(spotlightImage)}" alt="${escapeAttribute(featuredMovie.title || "Movie")} backdrop">` : ""}
        <h2 class="spotlight-title">${escapeHtml(featuredMovie.title || "Untitled")}</h2>
        <div class="spotlight-meta">
            <span>${escapeHtml(formatReleaseDate(featuredMovie.releaseDate))}</span>
            <span>${escapeHtml(featuredMovie.imdbId || "No IMDb ID")}</span>
            <span>${renderStars(featuredMovie.averageRating)}</span>
        </div>
        <div class="spotlight-tags">
            ${(featuredMovie.genres || []).slice(0, 4).map((genre) => `<span class="tag">${escapeHtml(genre)}</span>`).join("")}
        </div>
        <div class="detail-actions">
            <button class="button button-primary" type="button" id="spotlightOpenButton">Open details</button>
            ${featuredMovie.trailerLink ? `<a class="button button-secondary" href="${escapeAttribute(featuredMovie.trailerLink)}" target="_blank" rel="noreferrer">Watch trailer</a>` : ""}
        </div>
    `;

    document.getElementById("spotlightOpenButton")?.addEventListener("click", () => selectMovie(featuredMovie.imdbId));
}

function renderAccountPanel() {
    if (state.currentUser) {
        accountPanel.innerHTML = `
            <div class="catalog-toolbar">
                <div>
                    <p class="section-kicker">Account</p>
                    <h2>${escapeHtml(state.currentUser.displayName)}</h2>
                </div>
                <button class="button button-secondary" id="logoutButton" type="button">Log out</button>
            </div>
            <p class="supporting-copy">${escapeHtml(state.currentUser.email)}</p>
            <div class="account-stats">
                <div class="stat-pill"><strong>${state.watchlist.length}</strong><span>saved</span></div>
                <div class="stat-pill"><strong>${state.ratings.length}</strong><span>ratings</span></div>
            </div>
        `;
        document.getElementById("logoutButton")?.addEventListener("click", logout);
        return;
    }

    if (state.authMode === "register" && !state.otpVerified) {
        if (state.otpStep === "email") {
            accountPanel.innerHTML = `
                <div class="catalog-toolbar">
                    <div>
                        <p class="section-kicker">Account</p>
                        <h2>Create account</h2>
                    </div>
                    <button class="button button-secondary" id="toggleAuthMode" type="button">Have an account?</button>
                </div>
                <p class="supporting-copy">We'll send a verification code to your email.</p>
                <form class="auth-form" id="otpEmailForm">
                    <input id="otpEmailInput" name="email" type="email" placeholder="Email" required>
                    <button class="button button-primary" type="submit">Send verification code</button>
                </form>
            `;
            document.getElementById("toggleAuthMode")?.addEventListener("click", () => {
                state.authMode = "login";
                state.otpStep = "email";
                state.otpVerified = false;
                renderAccountPanel();
            });
            document.getElementById("otpEmailForm")?.addEventListener("submit", handleSendOtp);
        } else {
            accountPanel.innerHTML = `
                <div class="catalog-toolbar">
                    <div>
                        <p class="section-kicker">Account</p>
                        <h2>Verify email</h2>
                    </div>
                    <button class="button button-secondary" id="backToEmail" type="button">Change email</button>
                </div>
                <p class="supporting-copy">A 6-digit code was sent to <strong>${escapeHtml(state.otpEmail)}</strong></p>
                <form class="auth-form" id="otpVerifyForm">
                    <input id="otpCodeInput" name="code" type="text" placeholder="Enter 6-digit code" maxlength="6" pattern="[0-9]{6}" required autocomplete="one-time-code" inputmode="numeric">
                    <button class="button button-primary" type="submit">Verify code</button>
                </form>
                <button class="button-link" id="resendOtp" type="button">Resend code</button>
            `;
            document.getElementById("backToEmail")?.addEventListener("click", () => {
                state.otpStep = "email";
                renderAccountPanel();
            });
            document.getElementById("otpVerifyForm")?.addEventListener("submit", handleVerifyOtp);
            document.getElementById("resendOtp")?.addEventListener("click", async () => {
                await fetchJson("/api/v1/auth/send-otp", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: state.otpEmail }) });
                setStatus("A new code has been sent.");
            });
        }
        return;
    }

    accountPanel.innerHTML = `
        <div class="catalog-toolbar">
            <div>
                <p class="section-kicker">Account</p>
                <h2>${state.authMode === "login" ? "Log in" : "Create account"}</h2>
            </div>
            <button class="button button-secondary" id="toggleAuthMode" type="button">${state.authMode === "login" ? "Need an account?" : "Have an account?"}</button>
        </div>
        ${state.authMode === "register" && state.otpVerified ? '<p class="supporting-copy otp-verified-badge">✅ Email verified: <strong>' + escapeHtml(state.otpEmail) + '</strong></p>' : ''}
        <form class="auth-form" id="authForm">
            ${state.authMode === "register" ? '<input id="displayNameInput" name="displayName" type="text" placeholder="Display name">' : ""}
            ${state.authMode === "register" ? '<input id="emailInput" name="email" type="email" value="' + escapeAttribute(state.otpEmail) + '" readonly>' : '<input id="emailInput" name="email" type="email" placeholder="Email">'}
            <input id="passwordInput" name="password" type="password" placeholder="Password">
            <button class="button button-primary" type="submit">${state.authMode === "login" ? "Log in" : "Create account"}</button>
        </form>
    `;

    document.getElementById("toggleAuthMode")?.addEventListener("click", () => {
        state.authMode = state.authMode === "login" ? "register" : "login";
        state.otpStep = "email";
        state.otpVerified = false;
        state.otpEmail = "";
        renderAccountPanel();
    });
    document.getElementById("authForm")?.addEventListener("submit", handleAuthSubmit);
}

function renderForYouRail() {
    if (!state.currentUser) {
        forYouRail.innerHTML = '<div class="empty-grid"><p>Log in to unlock personalized recommendations based on your watchlist and high ratings.</p></div>';
        return;
    }

    if (!state.forYouMovies.length) {
        forYouRail.innerHTML = '<div class="empty-grid"><p>Add movies to your watchlist or rate a few titles to generate a personalized rail.</p></div>';
        return;
    }

    forYouRail.innerHTML = state.forYouMovies.map((movie) => `
        <button class="mini-card" data-imdb="${escapeAttribute(movie.imdbId)}" type="button">
            ${renderImage(movie.poster, `${escapeHtml(movie.title)} poster`)}
            <span>${escapeHtml(movie.title)}</span>
        </button>
    `).join("");

    forYouRail.querySelectorAll("[data-imdb]").forEach((button) => {
        button.addEventListener("click", () => selectMovie(button.dataset.imdb));
    });
}

function renderWatchlistPanel() {
    if (!state.currentUser) {
        watchlistPanel.innerHTML = '';
        watchlistPanel.hidden = true;
        return;
    }

    watchlistPanel.hidden = false;

    if (!state.watchlistMovies.length) {
        watchlistPanel.innerHTML = `
            <div class="catalog-toolbar">
                <div>
                    <p class="section-kicker">Your Collection</p>
                    <h2>My Watchlist</h2>
                </div>
            </div>
            <div class="empty-grid"><p>Your watchlist is empty. Click "Save to watchlist" on any movie to add it here.</p></div>
        `;
        return;
    }

    watchlistPanel.innerHTML = `
        <div class="catalog-toolbar">
            <div>
                <p class="section-kicker">Your Collection</p>
                <h2>My Watchlist</h2>
            </div>
            <span class="supporting-copy">${state.watchlistMovies.length} movie${state.watchlistMovies.length !== 1 ? 's' : ''} saved</span>
        </div>
        <div class="watchlist-grid">
            ${state.watchlistMovies.map((movie) => `
                <div class="watchlist-card" data-imdb="${escapeAttribute(movie.imdbId)}">
                    <div class="watchlist-card-art">
                        ${renderImage(movie.poster, escapeHtml(movie.title) + ' poster')}
                    </div>
                    <div class="watchlist-card-info">
                        <h4>${escapeHtml(movie.title)}</h4>
                        <p class="movie-meta">${escapeHtml(formatYear(movie.releaseDate))} · ${escapeHtml((movie.genres || []).join(', '))}</p>
                        <span class="tag rating-tag">${renderStars(movie.averageRating)}</span>
                    </div>
                    <button class="watchlist-remove-btn" data-remove="${escapeAttribute(movie.imdbId)}" title="Remove from watchlist">✕</button>
                </div>
            `).join('')}
        </div>
    `;

    watchlistPanel.querySelectorAll('.watchlist-card').forEach((card) => {
        card.addEventListener('click', (e) => {
            if (e.target.closest('.watchlist-remove-btn')) return;
            selectMovie(card.dataset.imdb);
        });
    });

    watchlistPanel.querySelectorAll('.watchlist-remove-btn').forEach((btn) => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const imdbId = btn.dataset.remove;
            await api(`/api/v1/users/watchlist/${imdbId}`, { method: 'DELETE' });
            await loadWatchlist();
            await loadForYou();
            renderWatchlistPanel();
            renderAccountPanel();
            renderMovieGrid();
            renderHeroStats();
            if (state.selectedMovie?.imdbId === imdbId) renderDetails();
            setStatus('Removed from watchlist.');
        });
    });
}

function renderDetails() {
    const movie = state.selectedMovie;
    if (!movie) {
        detailsContent.innerHTML = `
            <div class="empty-state">
                <p class="panel-label">Selection</p>
                <h3>No movie selected</h3>
                <p>Pick a title from the collection to see the movie dossier here.</p>
            </div>
        `;
        return;
    }

    const watchlistLabel = state.watchlist.includes(movie.imdbId) ? "Remove from watchlist" : "Save to watchlist";
    const userRating = state.ratings.find((rating) => rating.imdbId === movie.imdbId)?.value || 0;

    detailsContent.innerHTML = `
        <div class="detail-poster">
            ${renderImage(movie.poster || movie.backdrops?.[0], `${escapeHtml(movie.title)} poster`)}
        </div>
        <p class="panel-label">Movie dossier</p>
        <h3>${escapeHtml(movie.title || "Untitled")}</h3>
        <div class="detail-meta">
            <span>${escapeHtml(formatReleaseDate(movie.releaseDate))}</span>
            <span>${escapeHtml(movie.imdbId || "No IMDb ID")}</span>
            <span>${renderStars(movie.averageRating)} · ${movie.ratingCount || 0} ratings</span>
        </div>
        <div class="detail-tags">
            ${(movie.genres || []).map((genre) => `<span class="tag">${escapeHtml(genre)}</span>`).join("") || '<span class="tag">Unclassified</span>'}
        </div>
        <p class="detail-copy">${escapeHtml(buildDetailCopy(movie))}</p>
        <div class="detail-actions">
            ${movie.trailerLink ? `<a class="button button-primary" href="${escapeAttribute(movie.trailerLink)}" target="_blank" rel="noreferrer">Play trailer</a>` : ""}
            ${movie.streamLink ? `<a class="button button-primary" href="${escapeAttribute(movie.streamLink)}" target="_blank" rel="noreferrer">Watch Movie</a>` : ""}
            <button class="button button-secondary" type="button" id="watchlistButton">${watchlistLabel}</button>
        </div>
        <section class="rating-panel">
            <p class="panel-label">Your rating</p>
            <div class="rating-actions" id="ratingActions">
                ${[1, 2, 3, 4, 5].map((value) => `
                    <button class="rating-chip${value === userRating ? " active" : ""}" data-rating="${value}" type="button">${value}</button>
                `).join("")}
            </div>
        </section>
        <section class="recommendation-panel">
            <p class="panel-label">Recommended next</p>
            <div class="mini-rail" id="detailRecommendations">
                ${renderRecommendationRail(state.selectedRecommendations)}
            </div>
        </section>
        <section class="review-section">
            <p class="panel-label">Reviews</p>
            <div class="review-list" id="reviewList">
                ${renderReviews(movie.reviews)}
            </div>
            <form class="review-form" id="reviewForm">
                <label class="visually-hidden" for="reviewBody">Write a review</label>
                <textarea id="reviewBody" name="reviewBody" placeholder="What stood out? Performance, soundtrack, pacing, world-building..."></textarea>
                <div class="review-actions">
                    <button class="button button-primary" type="submit">${state.submittingReview ? "Posting..." : "Post review"}</button>
                    <p class="review-note">Reviews post under ${escapeHtml(state.currentUser?.displayName || "Anonymous viewer")}.</p>
                </div>
            </form>
        </section>
    `;

    document.getElementById("watchlistButton")?.addEventListener("click", toggleWatchlist);
    document.querySelectorAll("[data-rating]").forEach((button) => {
        button.addEventListener("click", () => submitRating(Number(button.dataset.rating)));
    });
    document.querySelectorAll(".mini-card[data-imdb]").forEach((button) => {
        button.addEventListener("click", () => selectMovie(button.dataset.imdb));
    });
    document.getElementById("reviewForm")?.addEventListener("submit", submitReview);
}

async function selectMovie(imdbId) {
    const movie = state.movies.find((entry) => entry.imdbId === imdbId);
    if (!movie) {
        return;
    }

    state.selectedMovie = movie;
    window.location.hash = imdbId;
    renderMovieGrid();
    renderDetails();
    await loadMovieRecommendations(imdbId);
}

function handleHashSelection() {
    const imdbId = window.location.hash.replace("#", "").trim();
    if (!imdbId) {
        return;
    }

    const movie = state.movies.find((entry) => entry.imdbId === imdbId);
    if (movie) {
        state.selectedMovie = movie;
        renderMovieGrid();
        renderDetails();
        loadMovieRecommendations(imdbId);
    }
}

async function handleSendOtp(event) {
    event.preventDefault();
    const email = document.getElementById("otpEmailInput").value.trim();
    if (!email) return;

    try {
        setStatus("Generating verification code...");
        const response = await fetch("/api/v1/auth/send-otp", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.message || "Failed to send OTP.");

        state.otpEmail = email;
        state.otpStep = "verify";
        state.otpCode = data.code || "";
        renderAccountPanel();
        setStatus("Demo Mode: Your verification code is " + state.otpCode);
    } catch (error) {
        setStatus(error.message || "Failed to send OTP.", true);
    }
}

async function handleVerifyOtp(event) {
    event.preventDefault();
    const code = document.getElementById("otpCodeInput").value.trim();
    if (!code) return;

    try {
        const response = await fetch("/api/v1/auth/verify-otp", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: state.otpEmail, code })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || !data.verified) {
            throw new Error(data.message || "Invalid or expired code. Please try again.");
        }
        state.otpVerified = true;
        state.otpStep = "email";
        renderAccountPanel();
        setStatus("✅ Email verified! Now fill in your name and password.");
    } catch (error) {
        setStatus(error.message, true);
    }
}

async function handleAuthSubmit(event) {
    event.preventDefault();
    const email = document.getElementById("emailInput").value.trim();
    const password = document.getElementById("passwordInput").value;
    const displayName = document.getElementById("displayNameInput")?.value?.trim() || "";

    try {
        const payload = state.authMode === "login"
            ? { email, password }
            : { displayName, email, password };
        const endpoint = state.authMode === "login" ? "/api/v1/auth/login" : "/api/v1/auth/register";
        
        const response = await fetch(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            let errorMessage = data.message || "We could not complete that request.";
            if (response.status === 401 && state.authMode === "login") {
                 errorMessage = "Incorrect password. Please try again.";
            } else if (response.status === 404 || (response.status === 401 && data.message?.toLowerCase().includes("user"))) {
                 errorMessage = "Account not found. You have to sign up first.";
            }
            throw new Error(errorMessage);
        }

        state.authToken = data.token;
        localStorage.setItem("movieAtlasToken", state.authToken);
        state.currentUser = data.user;
        state.otpVerified = false;
        state.otpStep = "email";
        state.otpEmail = "";
        await Promise.all([loadWatchlist(), loadRatings(), loadForYou()]);
        renderAccountPanel();
        renderForYouRail();
        renderWatchlistPanel();
        renderHeroStats();
        renderMovieGrid();
        renderDetails();
        setStatus(state.authMode === "login" ? "Logged in successfully." : "Account created successfully.");
    } catch (error) {
        setStatus(error.message, true);
    }
}

async function logout() {
    try {
        await api("/api/v1/auth/logout", { method: "DELETE" });
    } catch (error) {
        console.error(error);
    }

    resetAuthState();
    renderAccountPanel();
    renderForYouRail();
    renderWatchlistPanel();
    renderHeroStats();
    renderMovieGrid();
    renderDetails();
    setStatus("Logged out.");
}

function resetAuthState() {
    state.authToken = "";
    state.currentUser = null;
    state.watchlist = [];
    state.watchlistMovies = [];
    state.ratings = [];
    state.forYouMovies = [];
    localStorage.removeItem("movieAtlasToken");
}

async function loadWatchlist() {
    const response = await api("/api/v1/users/watchlist");
    state.watchlistMovies = response.movies || [];
    state.watchlist = state.watchlistMovies.map((movie) => movie.imdbId);
}

async function loadRatings() {
    state.ratings = await api("/api/v1/users/ratings");
}

async function loadForYou() {
    state.forYouMovies = await api("/api/v1/recommendations/for-you?limit=6");
}

async function loadMovieRecommendations(imdbId) {
    try {
        state.selectedRecommendations = await fetchJson(`/api/v1/recommendations/movie/${imdbId}?limit=6`);
    } catch (error) {
        state.selectedRecommendations = [];
        console.error(error);
    }
    renderDetails();
}

async function toggleWatchlist() {
    if (!state.currentUser) {
        setStatus("Log in to save a watchlist.", true);
        return;
    }

    const movie = state.selectedMovie;
    const inWatchlist = state.watchlist.includes(movie.imdbId);
    const endpoint = `/api/v1/users/watchlist/${movie.imdbId}`;

    await api(endpoint, { method: inWatchlist ? "DELETE" : "POST" });
    await loadWatchlist();
    await loadForYou();
    renderAccountPanel();
    renderForYouRail();
    renderWatchlistPanel();
    renderMovieGrid();
    renderDetails();
    renderHeroStats();
    setStatus(inWatchlist ? "Removed from watchlist." : "Added to watchlist.");
}

async function submitRating(value) {
    if (!state.currentUser) {
        setStatus("Log in to rate movies.", true);
        return;
    }

    const movie = state.selectedMovie;
    try {
        await api("/api/v1/users/ratings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                imdbId: movie.imdbId,
                rating: value
            })
        });

        await Promise.all([loadRatings(), loadForYou(), loadMovies()]);
        selectMovie(movie.imdbId);
        setStatus(`Saved your ${value}-star rating.`);
    } catch (error) {
        setStatus(error.message || "We could not save your rating.", true);
    }
}

async function submitReview(event) {
    event.preventDefault();

    if (!state.selectedMovie?.imdbId || state.submittingReview) {
        return;
    }

    const reviewInput = document.getElementById("reviewBody");
    const reviewBody = reviewInput.value.trim();
    if (!reviewBody) {
        setStatus("Please write something before posting a review.", true);
        return;
    }

    state.submittingReview = true;
    renderDetails();

    try {
        const createdReview = await fetchJson("/api/v1/reviews", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                reviewBody,
                imdbId: state.selectedMovie.imdbId,
                authorName: state.currentUser?.displayName || "Anonymous viewer"
            })
        });

        const movie = state.movies.find((entry) => entry.imdbId === state.selectedMovie.imdbId);
        if (movie) {
            movie.reviews = [createdReview, ...(movie.reviews || [])];
        }

        state.submittingReview = false;
        renderMovieGrid();
        renderDetails();
        setStatus("Review posted successfully.");
    } catch (error) {
        state.submittingReview = false;
        renderDetails();
        setStatus(error.message || "We could not post your review. Please try again in a moment.", true);
    }
}

function renderRecommendationRail(movies) {
    if (!movies?.length) {
        return '<div class="empty-grid"><p>We will surface related titles here as you explore.</p></div>';
    }

    return movies.map((movie) => `
        <button class="mini-card" data-imdb="${escapeAttribute(movie.imdbId)}" type="button">
            ${renderImage(movie.poster, `${escapeHtml(movie.title)} poster`)}
            <span>${escapeHtml(movie.title)}</span>
        </button>
    `).join("");
}

function renderReviews(reviews) {
    if (!reviews || !reviews.length) {
        return '<div class="empty-grid"><p>No reviews yet. Be the first to add one.</p></div>';
    }

    return reviews
        .slice()
        .reverse()
        .map((review) => `
            <article class="review-item">
                <p class="review-meta">${escapeHtml(review.authorName || "Anonymous viewer")} · ${escapeHtml(formatTimestamp(review.createdAt))}</p>
                <p>${escapeHtml(review.body || "No review text")}</p>
            </article>
        `)
        .join("");
}

function buildSummary(movie) {
    const genres = (movie.genres || []).slice(0, 2).join(" / ");
    if (genres) {
        return `${genres} · ${movie.director || "Director unavailable"}`;
    }
    return "Open the dossier for more details.";
}

function buildDetailCopy(movie) {
    const genres = (movie.genres || []).join(", ");
    const reviewCount = movie.reviews?.length || 0;
    const director = movie.director ? `Directed by ${movie.director}. ` : "";
    const runtime = movie.runtimeMinutes ? `${movie.runtimeMinutes} min. ` : "";
    return `${director}${runtime}${movie.overview || ""} ${genres ? `Genres: ${genres}. ` : ""}${reviewCount} review${reviewCount === 1 ? "" : "s"} currently attached to this title.`;
}

function renderStars(value) {
    if (!value) {
        return "No ratings yet";
    }
    return `${Number(value).toFixed(1)} / 5`;
}

function formatReleaseDate(value) {
    if (!value) {
        return "Release date unknown";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return value;
    }

    return parsed.toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric"
    });
}

function formatTimestamp(value) {
    if (!value) {
        return "just now";
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return value;
    }
    return parsed.toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit"
    });
}

function formatYear(value) {
    if (!value) {
        return "TBA";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return value.slice(0, 4);
    }

    return String(parsed.getFullYear());
}

function setStatus(message, isError = false) {
    if (!message) {
        statusBanner.hidden = true;
        statusBanner.textContent = "";
        statusBanner.className = "status-banner";
        return;
    }

    statusBanner.hidden = false;
    statusBanner.textContent = message;
    statusBanner.className = `status-banner${isError ? " error" : ""}`;
}

function renderImage(src, alt) {
    if (!src) {
        return `<div class="empty-state"><p>No artwork available for ${escapeHtml(alt)}.</p></div>`;
    }

    return `<img src="${escapeAttribute(src)}" alt="${escapeAttribute(alt)}" loading="lazy">`;
}

async function api(url, options = {}) {
    return fetchJson(url, {
        ...options,
        headers: {
            ...(options.headers || {}),
            Authorization: `Bearer ${state.authToken}`
        }
    });
}

async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(data.message || `Request failed (${response.status})`);
    }
    return data;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
    return escapeHtml(value);
}
