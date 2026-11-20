package com.Proyect.Ecommerce.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Words of the product names, each pointing to the products that contain it. Finds the words that
 * <em>start</em> like the typed term, allowing a few typos (insertions, deletions, substitutions and
 * swapped neighbours): "sarteb", "satren" and "sart" all reach "sarten".
 * Not thread-safe while it is being filled; read-only afterwards.
 */
final class FuzzyTrie {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final Set<Long> productIds = new HashSet<>();
    }

    private final Node root = new Node();

    void add(String word, long productId) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.computeIfAbsent(word.charAt(i), c -> new Node());
        }
        node.productIds.add(productId);
    }

    /**
     * Products with a word whose beginning is at most {@code maxEdits} away from {@code term},
     * each with the smallest distance found (0 = the word starts exactly with the term).
     */
    Map<Long, Integer> findByPrefix(String term, int maxEdits) {
        int[] firstRow = new int[term.length() + 1];
        for (int i = 0; i < firstRow.length; i++) {
            firstRow[i] = i;
        }
        Map<Long, Integer> matches = new HashMap<>();
        for (Map.Entry<Character, Node> child : root.children.entrySet()) {
            walk(child.getValue(), child.getKey(), '\0', term, firstRow, null, maxEdits, Integer.MAX_VALUE, matches);
        }
        return matches;
    }

    /**
     * One step of the edit-distance table (optimal string alignment) per trie level. {@code best} is the
     * closest any prefix of the current word got to the whole term: every word below a close enough
     * prefix matches, so the walk keeps going while the row can still improve or a match is pending.
     */
    private void walk(Node node, char letter, char previousLetter, String term, int[] previousRow, int[] rowBefore,
                      int maxEdits, int best, Map<Long, Integer> matches) {
        int columns = term.length() + 1;
        int[] row = new int[columns];
        row[0] = previousRow[0] + 1;
        int rowMin = row[0];
        for (int i = 1; i < columns; i++) {
            int cost = term.charAt(i - 1) == letter ? 0 : 1;
            row[i] = Math.min(Math.min(row[i - 1] + 1, previousRow[i] + 1), previousRow[i - 1] + cost);
            if (rowBefore != null && i > 1 && term.charAt(i - 1) == previousLetter && term.charAt(i - 2) == letter) {
                row[i] = Math.min(row[i], rowBefore[i - 2] + 1);
            }
            rowMin = Math.min(rowMin, row[i]);
        }
        best = Math.min(best, row[columns - 1]);

        if (best <= maxEdits) {
            for (Long id : node.productIds) {
                matches.merge(id, best, Math::min);
            }
        }
        if (rowMin > maxEdits && best > maxEdits) {
            return;
        }
        for (Map.Entry<Character, Node> child : node.children.entrySet()) {
            walk(child.getValue(), child.getKey(), letter, term, row, previousRow, maxEdits, best, matches);
        }
    }
}
