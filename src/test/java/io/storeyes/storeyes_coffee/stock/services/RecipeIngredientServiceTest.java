package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.RecipeIngredientResponse;
import io.storeyes.storeyes_coffee.stock.dto.RecipeIngredientType;
import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.entities.RecipeIngredient;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.repositories.RecipeIngredientRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeIngredientServiceTest {

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private StockProductRepository stockProductRepository;

    @Mock
    private ArticleService articleService;

    @Mock
    private DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    @InjectMocks
    private RecipeIngredientService recipeIngredientService;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getRecipeByArticleId_sortsMixedStockAndNestedIngredientsByDisplayName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentStoreContext.REQUEST_ATTR_STORE_ID, 1L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Article root = Article.builder().id(10L).name("Crepe").build();
        Article nested = Article.builder().id(30L).name("Chocolate Sauce").build();
        StockProduct almonds = StockProduct.builder()
                .id(20L)
                .name("Almonds")
                .unit("g")
                .unitPrice(new BigDecimal("1.50"))
                .build();

        RecipeIngredient nestedLine = RecipeIngredient.builder()
                .id(2L)
                .article(root)
                .ingredientArticle(nested)
                .quantity(BigDecimal.ONE)
                .build();
        RecipeIngredient stockLine = RecipeIngredient.builder()
                .id(1L)
                .article(root)
                .product(almonds)
                .quantity(new BigDecimal("2.0000"))
                .build();

        when(demoStoreDataSourceResolver.resolveStockDataStoreId(1L)).thenReturn(1L);
        when(articleService.getArticleEntity(10L, 1L)).thenReturn(root);
        when(recipeIngredientRepository.findByArticleIdOrderByProductName(10L))
                .thenReturn(List.of(nestedLine, stockLine));
        when(recipeIngredientRepository.findByArticleIdOrderByProductName(30L))
                .thenReturn(List.of());

        List<RecipeIngredientResponse> result = recipeIngredientService.getRecipeByArticleId(10L);

        assertThat(result).extracting(RecipeIngredientResponse::getIngredientType)
                .containsExactly(RecipeIngredientType.STOCK, RecipeIngredientType.ARTICLE);
        assertThat(result).extracting(RecipeIngredientResponse::getProductName)
                .containsExactly("Almonds", null);
        assertThat(result).extracting(RecipeIngredientResponse::getIngredientArticleName)
                .containsExactly(null, "Chocolate Sauce");
    }
}
