package com.Proyect.Ecommerce.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.Proyect.Ecommerce.repository.ProductRepository;

/** Search as you type: accents, typos, prefixes and the order of the 5 suggestions. */
class ProductSearchIndexTest {

    private record View(Long getId, String getName, BigDecimal getPrice, String getImageUrl)
            implements ProductRepository.SearchView {
    }

    private final ProductRepository repository = mock(ProductRepository.class);
    private final List<ProductRepository.SearchView> catalog = new ArrayList<>();
    private ProductSearchIndex index;

    @BeforeEach
    void catalog() {
        add("Sartén de hierro fundido 26 cm");
        add("Olla de acero inoxidable 24 cm con tapa");
        add("Manta tejida de lana");
        add("Mantel de lino rústico 2,5 m");
        add("Set de 3 cuchillos de cocina");
        when(repository.findByActiveTrue()).thenReturn(catalog);
        index = new ProductSearchIndex(repository, Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ignoresAccentsAndCapitals() {
        assertThat(names("sarten")).containsExactly("Sartén de hierro fundido 26 cm");
        assertThat(names("SARTÉN")).containsExactly("Sartén de hierro fundido 26 cm");
        assertThat(names("rustico")).containsExactly("Mantel de lino rústico 2,5 m");
    }

    @Test
    void suggestsFromTheThirdLetter() {
        assertThat(names("sa")).isEmpty();
        assertThat(names("sar")).containsExactly("Sartén de hierro fundido 26 cm");
    }

    @Test
    void forgivesATypoInAWordOfFourLettersOrMore() {
        assertThat(names("sarteb")).containsExactly("Sartén de hierro fundido 26 cm");
        assertThat(names("satren")).containsExactly("Sartén de hierro fundido 26 cm");
        assertThat(names("sartn")).containsExactly("Sartén de hierro fundido 26 cm");
        assertThat(names("cuchilos")).containsExactly("Set de 3 cuchillos de cocina");
        assertThat(names("sxr")).isEmpty();
    }

    @Test
    void everyWordHasToMatchAndShortOnesAreSkipped() {
        assertThat(names("olla acero")).containsExactly("Olla de acero inoxidable 24 cm con tapa");
        assertThat(names("olla de acero")).containsExactly("Olla de acero inoxidable 24 cm con tapa");
        assertThat(names("olla hierro")).isEmpty();
    }

    @Test
    void exactMatchesComeBeforeTyposAndShorterNamesFirst() {
        assertThat(names("mant")).containsExactly("Manta tejida de lana", "Mantel de lino rústico 2,5 m");
        assertThat(names("manta")).first().isEqualTo("Manta tejida de lana");
    }

    @Test
    void returnsAtMostTheRequestedNumber() {
        for (int i = 1; i <= 8; i++) {
            add("Vaso " + i);
        }
        ProductSearchIndex.productsChanged();

        assertThat(index.suggest("vaso", 5)).hasSize(5);
        assertThat(index.matchingIds("vaso")).hasSize(8);
    }

    @Test
    void seesProductsAddedAfterItWasBuilt() {
        assertThat(names("pava")).isEmpty();

        add("Pava eléctrica de acero 1,7 L");
        ProductSearchIndex.productsChanged();

        assertThat(names("pava")).containsExactly("Pava eléctrica de acero 1,7 L");
    }

    @Test
    void typoToleranceGrowsWithTheWord() {
        assertThat(ProductSearchIndex.maxEdits("sar")).isZero();
        assertThat(ProductSearchIndex.maxEdits("sarten")).isEqualTo(1);
        assertThat(ProductSearchIndex.maxEdits("cuchillos")).isEqualTo(2);
    }

    private void add(String name) {
        catalog.add(new View((long) catalog.size() + 1, name, BigDecimal.TEN, null));
    }

    private List<String> names(String query) {
        return index.suggest(query, 5).stream().map(ProductSearchIndex.Suggestion::name).toList();
    }
}
