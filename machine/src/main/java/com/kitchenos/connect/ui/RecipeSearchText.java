package com.kitchenos.connect.ui;

import java.text.Normalizer;
import java.util.*;

/** Immutable text index; no Android, network or UI work. Lower scores are better. */
final class RecipeSearchText {
    private static final Map<String, Set<String>> ALIASES = new HashMap<>();
    private static final List<String> PHRASES = new ArrayList<>();
    private static final Set<String> FILLERS = new HashSet<>();
    static {
        for (String[] terms : FoodSearchDictionary.GROUPS) group(terms);
        for (String[][] category : FoodSearchDictionary.CATEGORIES) for (String term : category[0]) {
            category(term, category[0]);
            category(term, category[1]);
        }
        for (String term : ALIASES.keySet()) if (term.contains(" ")) PHRASES.add(term);
        PHRASES.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String filler : FoodSearchDictionary.FILLERS) FILLERS.add(normalize(filler));
    }

    static String normalize(String text) {
        String result = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe")
                .replace("ü", "ue").replace("ß", "ss");
        result = Normalizer.normalize(result, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return result.replace("ae", "a").replace("oe", "o").replace("ue", "u")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
    }

    private static void group(String... terms) {
        Set<String> values = new LinkedHashSet<>();
        for (String term : terms) values.add(normalize(term));
        for (String term : values) ALIASES.computeIfAbsent(term, k -> new LinkedHashSet<>()).addAll(values);
    }

    private static void category(String term, String... children) {
        Set<String> values = ALIASES.computeIfAbsent(normalize(term), k -> new LinkedHashSet<>());
        for (String child : children) values.add(normalize(child));
    }

    private static final class Term {
        final String word;
        final Set<String> alternatives, corrections = new LinkedHashSet<>();
        Term(String word) {
            this.word = word;
            alternatives = ALIASES.getOrDefault(word, Collections.emptySet());
            if (!ALIASES.containsKey(word) && word.length() >= 5 && !word.contains(" "))
                for (String food : ALIASES.keySet()) if (!food.contains(" ") && oneEdit(word, food))
                    corrections.addAll(ALIASES.get(food));
        }
    }

    static final class Query {
        final String phrase;
        final List<Term> terms = new ArrayList<>();
        final boolean valid;
        Query(String value) {
            phrase = normalize(value);
            String[] tokens = phrase.isEmpty() ? new String[0] : phrase.split(" ");
            valid = phrase.length() <= 120 && tokens.length <= 16;
            if (!valid) return;
            for (int i = 0; i < tokens.length;) {
                String rest = String.join(" ", Arrays.copyOfRange(tokens, i, tokens.length));
                String word = tokens[i];
                for (String multi : PHRASES) if (rest.equals(multi) || rest.startsWith(multi + " ")) { word = multi; break; }
                i += word.split(" ").length;
                if (tokens.length > 1 && FILLERS.contains(word)) continue;
                terms.add(new Term(word));
            }
        }
    }

    private static final class Word {
        final String text;
        final boolean title;
        Word(String text, boolean title) { this.text = text; this.title = title; }
    }

    private final String title;
    private final List<Word> words = new ArrayList<>();

    RecipeSearchText(String title, List<String> ingredients) {
        this.title = normalize(title);
        add(this.title, true);
        for (String ingredient : ingredients) add(normalize(ingredient), false);
    }

    private void add(String text, boolean inTitle) {
        if (text.isEmpty()) return;
        Set<String> unique = new LinkedHashSet<>();
        for (String word : text.split(" ")) {
            unique.add(word);
            if (!ALIASES.containsKey(word)) {
                List<String> parts = new ArrayList<>();
                for (String food : ALIASES.keySet()) if (!food.contains(" ") && food.length() >= 4
                        && word.length() > food.length() && (word.startsWith(food) || word.indexOf(food) >= 3)) parts.add(food);
                for (String food : parts) if (parts.stream().noneMatch(longer -> longer.length() > food.length() && longer.contains(food))) unique.add(food);
            }
        }
        for (String phrase : PHRASES) if ((" " + text + " ").contains(" " + phrase + " ")) unique.add(phrase);
        for (String word : unique) words.add(new Word(word, inTitle));
    }

    int score(Query query) {
        if (!query.valid) return -1;
        if (query.phrase.isEmpty()) return 0;
        if (query.terms.isEmpty()) return -1;
        int worst = 0, relevance = 0;
        for (Term term : query.terms) {
            String requested = term.word;
            int best = Integer.MAX_VALUE;
            Set<String> alternatives = term.alternatives;
            for (Word candidate : words) {
                int direct = lexical(requested, candidate.text);
                int field = candidate.title ? 0 : 10;
                if (direct >= 0) best = Math.min(best, direct + field);
                for (String alternative : alternatives) {
                    if (lexical(alternative, candidate.text) >= 0) best = Math.min(best, 100 + field);
                }
                // Short input stays precise; fuzzy matching starts at four letters.
                if (requested.length() >= 4 && candidate.text.length() >= 4
                        && oneEdit(requested, candidate.text)) best = Math.min(best, 200 + field);
                for (String correction : term.corrections) if (lexical(correction, candidate.text) >= 0) best = Math.min(best, 200 + field);
            }
            if (best == Integer.MAX_VALUE) return -1;
            worst = Math.max(worst, best / 100);
            relevance += best % 100;
        }
        int phrase = title.equals(query.phrase) ? 0 : title.startsWith(query.phrase) ? 1 : 2;
        return worst * 10000 + phrase * 1000 + relevance;
    }

    private static int lexical(String query, String word) {
        if (word.equals(query)) return 0;
        // Known food names match indexed components, not parts of different foods
        // (Kümmel/Kreuzkümmel, Kartoffel/Süßkartoffel, Nüsse/Muskatnuss).
        if (ALIASES.containsKey(query)) return -1;
        if (word.startsWith(query)) return 1;
        // German compound words, but do not turn "Eis" into "Reis".
        if (query.length() >= 4 && word.contains(query)) return 2;
        return -1;
    }

    /** One insertion, deletion, replacement or adjacent transposition; linear time. */
    private static boolean oneEdit(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0;
        while (i < Math.min(a.length(), b.length()) && a.charAt(i) == b.charAt(i)) i++;
        if (i == Math.min(a.length(), b.length())) return true;
        if (a.length() == b.length()) {
            if (a.substring(i + 1).equals(b.substring(i + 1))) return true;
            return i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1)
                    && a.charAt(i + 1) == b.charAt(i) && a.substring(i + 2).equals(b.substring(i + 2));
        }
        return a.length() > b.length() ? a.substring(i + 1).equals(b.substring(i))
                : a.substring(i).equals(b.substring(i + 1));
    }
}
