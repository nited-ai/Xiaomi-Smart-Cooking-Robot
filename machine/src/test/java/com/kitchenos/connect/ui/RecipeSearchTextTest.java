package com.kitchenos.connect.ui;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RecipeSearchTextTest {
    @Test public void entireSharedDictionaryIsSearchable() {
        for (String[] group : FoodSearchDictionary.GROUPS)
            for (String term : group) matches(recipe(group[0]), term);
    }

    @Test public void phrasesAndMisspelledAliasesWork() {
        matches(recipe("Suppe", "Randen"), "Rote Bete");
        matches(recipe("Suppe", "Rote Bete"), "Randen");
        matches(recipe("Salat", "Frühlingszwiebeln"), "Spring Onion");
        matches(recipe("Hähnchenbrust"), "Chikcen");
    }

    @Test public void differentKnownFoodsAreNotMatchedAsCompoundFragments() {
        assertEquals(-1, score(recipe("Rührei", "Hühnerei (Größe M)"), "Hähnchen"));
        assertEquals(-1, score(recipe("Flammkuchen"), "Lamm"));
        assertEquals(-1, score(recipe("Preiselbeeren"), "Reis"));
        assertEquals(-1, score(recipe("Kreuzkümmel"), "Kümmel"));
        assertEquals(-1, score(recipe("Süßkartoffelsuppe"), "Kartoffel"));
        assertEquals(-1, score(recipe("Muskatnuss"), "Nüsse"));
        assertEquals(-1, score(recipe("Paprika"), "Pfeffer"));
    }
    private RecipeSearchText recipe(String title, String... ingredients) {
        return new RecipeSearchText(title, Arrays.asList(ingredients));
    }
    private int score(RecipeSearchText recipe, String query) {
        return recipe.score(new RecipeSearchText.Query(query));
    }
    private void matches(RecipeSearchText recipe, String... queries) {
        for (String query : queries) assertTrue("Missing: " + query, score(recipe, query) >= 0);
    }

    @Test public void germanSpellingsAreEquivalent() {
        RecipeSearchText chicken = recipe("Hähnchen-Curry", "Hähnchenbrust", "Reis");
        matches(chicken, "Hähnchen", "HAEHNCHEN", "Hahnchen", "  haehnchen  ");
        assertEquals(score(chicken, "Hähnchen"), score(chicken, "Haehnchen"));
        matches(recipe("Süße Möhren"), "suesse moehren", "susse mohren", "su\u0308ße mo\u0308hren");
        matches(recipe("Crème brûlée"), "creme brulee");
    }

    @Test public void missingHAndTransposedLettersMatch() {
        matches(recipe("Hähnchen Curry"), "Hänchen", "Ähnchen", "Haenchen", "Hähncehn");
        matches(recipe("Brokkoli Salat"), "Brokoli", "Brokkloi");
    }

    @Test public void foodComponentsAllowTyposInCompounds() {
        matches(recipe("Schnelle Bowl", "Hähnchenbrust"), "Hänchen", "Huhn", "Chicken");
        matches(recipe("Brokkolisalat"), "Brokoli");
    }

    @Test public void foodAliasesWorkInBothDirections() {
        matches(recipe("Hähnchen Curry"), "Huhn", "Hühnchen", "Chicken", "Poulet");
        matches(recipe("Chicken Curry"), "Hähnchen");
        matches(recipe("Möhrensuppe"), "Karotte", "Karotten");
        matches(recipe("Karottensuppe"), "Möhren");
        matches(recipe("Käsekuchen", "Topfen"), "Quark");
        matches(recipe("Spaghetti Bolognese"), "Nudeln", "Pasta");
    }

    @Test public void broadCategoriesDoNotExpandSpecificFood() {
        matches(recipe("Entenbrust"), "Geflügel");
        assertEquals(-1, score(recipe("Entenbrust"), "Hähnchen"));
        assertEquals(-1, score(recipe("Putenbrust"), "Hähnchen"));
        matches(recipe("Steinpilzrisotto"), "Pilze");
        assertEquals(-1, score(recipe("Steinpilzrisotto"), "Champignon"));
    }

    @Test public void allWordsMustMatchAcrossFieldsInAnyOrder() {
        RecipeSearchText chicken = recipe("Hähnchen Curry", "Basmatireis", "Kokosmilch");
        matches(chicken, "Hähnchen Reis", "Reis Hähnchen", "Chicken mit Reis", "Huhn und Kokosmilch");
        assertEquals(-1, score(chicken, "Hähnchen Erdbeere"));
    }

    @Test public void exactThenSynonymThenTypo() {
        assertTrue(score(recipe("Hähnchen Curry"), "Hähnchen") < score(recipe("Chicken Curry"), "Hähnchen"));
        assertTrue(score(recipe("Chicken Curry"), "Hähnchen") < score(recipe("Hänchen Curry"), "Hähnchen"));
        assertTrue(score(recipe("Hähnchen"), "Hähnchen") < score(recipe("Hähnchen Curry"), "Hähnchen"));
        assertTrue(score(recipe("Hähnchen Curry"), "Hähnchen") < score(recipe("Bowl", "Hähnchen"), "Hähnchen"));
    }

    @Test public void shortInputHasNoFuzzyOrInfixMatches() {
        matches(recipe("Brokkolisalat"), "b", "br", "bro");
        assertEquals(-1, score(recipe("Reis"), "Eis"));
        assertEquals(-1, score(recipe("Mais"), "Eis"));
        assertEquals(-1, score(recipe("Ei"), "Eis"));
        matches(recipe("Eiscreme"), "Eis");
    }

    @Test public void unrelatedFoodAndMultipleTyposDoNotMatch() {
        assertEquals(-1, score(recipe("Hähnchen"), "Hnchxn"));
        assertEquals(-1, score(recipe("Schokoladenkuchen"), "Kartoffel"));
        assertEquals(-1, score(recipe("Nudeln", "Nüsse"), "ohne Nüsse"));
    }

    @Test public void emptyQueryPreservesOrderAndInputIsBounded() {
        assertEquals(0, score(recipe("Suppe"), ""));
        assertEquals(0, score(recipe("Kuchen"), "   "));
        assertEquals(-1, score(recipe("a"), String.join(" ", Collections.nCopies(17, "a"))));
        assertEquals(-1, score(recipe("a"), String.join("", Collections.nCopies(121, "a"))));
    }

    @Test public void indexingAndQueryingDoNotMutatePreviouslyBuiltQueries() {
        RecipeSearchText.Query query = new RecipeSearchText.Query("Huhn Reis");
        RecipeSearchText yes = recipe("Hähnchen", "Reis");
        RecipeSearchText no = recipe("Kartoffelsuppe");
        int expected = yes.score(query);
        assertTrue(expected >= 0);
        assertEquals(-1, no.score(query));
        assertEquals(expected, yes.score(query));
    }
}
