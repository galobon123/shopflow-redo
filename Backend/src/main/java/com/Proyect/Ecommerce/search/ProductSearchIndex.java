package com.Proyect.Ecommerce.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.repository.ProductRepository;

/**
 * Search as you type over the names of the active products, kept in memory in a {@link FuzzyTrie}.
 * Ignores accents and capitals and forgives a typo or two, depending on how long the word is.
 * <p>
 * Rebuilt on the next search after a product is saved through JPA ({@link #productsChanged()}, called by
 * the entity listener), and at least every {@link #MAX_AGE} in case rows were changed by plain SQL.
 */
@Component
public class ProductSearchIndex {

    /** Fewer letters than this and there is nothing to suggest yet. */
    public static final int MIN_QUERY_LENGTH = 3;
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private static final AtomicLong CHANGES = new AtomicLong();

    public record Suggestion(Long id, String name, BigDecimal price, String imageUrl) {
    }

    private record Entry(Suggestion suggestion, String normalizedName) {
    }

    private record Snapshot(FuzzyTrie trie, Map<Long, Entry> products, long version, Instant builtAt) {
    }

    private final ProductRepository productRepository;
    private final Clock clock;
    private volatile Snapshot snapshot;

    @Autowired
    public ProductSearchIndex(ProductRepository productRepository) {
        this(productRepository, Clock.systemUTC());
    }

    ProductSearchIndex(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    /** A product was created, edited or removed (through JPA or plain SQL): the next search rebuilds the index. */
    public static void productsChanged() {
        CHANGES.incrementAndGet();
    }

    /** The best {@code limit} matches, closest first. Empty under {@link #MIN_QUERY_LENGTH} letters. */
    public List<Suggestion> suggest(String query, int limit) {
        Snapshot current = current();
        String typed = SearchText.normalize(query);
        Map<Long, Integer> scores = score(current, query);
        // Fewest typos first; then names that start with what was typed, then the shorter, then A to Z.
        Comparator<Entry> closest = Comparator
                .<Entry>comparingInt(p -> scores.get(p.suggestion().id()))
                .thenComparing(p -> !p.normalizedName().startsWith(typed))
                .thenComparingInt(p -> p.normalizedName().length())
                .thenComparing(Entry::normalizedName);
        return scores.keySet().stream()
                .map(current.products()::get)
                .sorted(closest)
                .limit(limit)
                .map(Entry::suggestion)
                .toList();
    }

    /** Every active product the query would suggest, for the catalog search. */
    public Set<Long> matchingIds(String query) {
        return score(current(), query).keySet();
    }

    /**
     * Each word of the query has to match a word of the name (the last one may be half typed).
     * Words under {@link #MIN_QUERY_LENGTH} letters, like "de" or "x", are left out unless they are all there is.
     * The score is the sum of the typos needed.
     */
    private Map<Long, Integer> score(Snapshot current, String query) {
        if (SearchText.normalize(query).replace(" ", "").length() < MIN_QUERY_LENGTH) {
            return Map.of();
        }
        List<String> words = SearchText.words(query);
        List<String> terms = words.stream().filter(w -> w.length() >= MIN_QUERY_LENGTH).toList();
        if (terms.isEmpty()) {
            terms = words;
        }
        Map<Long, Integer> scores = null;
        for (String term : terms) {
            Map<Long, Integer> matches = current.trie().findByPrefix(term, maxEdits(term));
            if (scores == null) {
                scores = new HashMap<>(matches);
            } else {
                scores.keySet().retainAll(matches.keySet());
                scores.replaceAll((id, total) -> total + matches.get(id));
            }
            if (scores.isEmpty()) {
                break;
            }
        }
        return scores;
    }

    /** Short words must be typed right; longer ones forgive one typo, and very long ones two. */
    static int maxEdits(String term) {
        if (term.length() < 4) {
            return 0;
        }
        return term.length() < 8 ? 1 : 2;
    }

    private Snapshot current() {
        Snapshot current = snapshot;
        if (current != null && current.version() == CHANGES.get()
                && current.builtAt().plus(MAX_AGE).isAfter(clock.instant())) {
            return current;
        }
        synchronized (this) {
            if (snapshot != current) {
                return snapshot;
            }
            snapshot = build();
            return snapshot;
        }
    }

    private Snapshot build() {
        long version = CHANGES.get();
        FuzzyTrie trie = new FuzzyTrie();
        Map<Long, Entry> products = new HashMap<>();
        for (ProductRepository.SearchView view : productRepository.findByActiveTrue()) {
            String normalizedName = SearchText.normalize(view.getName());
            products.put(view.getId(), new Entry(
                    new Suggestion(view.getId(), view.getName(), view.getPrice(), view.getImageUrl()), normalizedName));
            for (String word : SearchText.words(view.getName())) {
                trie.add(word, view.getId());
            }
        }
        return new Snapshot(trie, products, version, clock.instant());
    }
}
