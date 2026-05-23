package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.entities.RecipeIngredient;
import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.stock.repositories.RecipeIngredientRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockMovementRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockConsumptionServiceTest {

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private StockProductRepository stockProductRepository;

    @InjectMocks
    private StockConsumptionService stockConsumptionService;

    @Test
    void createConsumptionForArticleSale_createsNegativeMovementsForEachIngredient() {
        Store store = Store.builder().id(1L).build();
        Article article = Article.builder().id(10L).store(store).name("Café").build();
        StockProduct product1 = StockProduct.builder().id(1L).unit("g").store(store).build();
        StockProduct product2 = StockProduct.builder().id(2L).unit("cl").store(store).build();

        RecipeIngredient ri1 = RecipeIngredient.builder()
                .article(article).product(product1).quantity(new BigDecimal("18"))
                .build();
        RecipeIngredient ri2 = RecipeIngredient.builder()
                .article(article).product(product2).quantity(new BigDecimal("30"))
                .build();

        when(recipeIngredientRepository.findByArticleIdOrderByProductName(10L))
                .thenReturn(List.of(ri1, ri2));
        when(stockProductRepository.findById(1L)).thenReturn(Optional.of(product1));
        when(stockProductRepository.findById(2L)).thenReturn(Optional.of(product2));

        stockConsumptionService.createConsumptionForArticleSale(
                1L, 10L, new BigDecimal("2"), LocalDate.of(2025, 3, 5), 100L);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());

        List<StockMovement> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);

        StockMovement m1 = saved.get(0);
        assertThat(m1.getType()).isEqualTo(StockMovementType.CONSUMPTION);
        assertThat(m1.getQuantity()).isEqualByComparingTo(new BigDecimal("-36")); // 18 * 2
        assertThat(m1.getProduct().getId()).isEqualTo(1L);
        assertThat(m1.getReferenceType()).isEqualTo("ARTICLE_SALE");
        assertThat(m1.getReferenceId()).isEqualTo(100L);

        StockMovement m2 = saved.get(1);
        assertThat(m2.getQuantity()).isEqualByComparingTo(new BigDecimal("-60")); // 30 * 2
    }
}
