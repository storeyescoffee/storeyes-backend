package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.UpdateArticleRequest;
import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.repositories.ArticleRepository;
import io.storeyes.storeyes_coffee.stock.repositories.RecipeIngredientRepository;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @InjectMocks
    private ArticleService articleService;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void updateArticle_rejectsDisablingNestedFlagWhenReferencedByOtherRecipes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentStoreContext.REQUEST_ATTR_STORE_ID, 1L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Article article = Article.builder()
                .id(22L)
                .name("Sauce")
                .allowAsSubRecipeArticle(true)
                .build();

        when(articleRepository.findByIdAndStoreId(22L, 1L)).thenReturn(Optional.of(article));
        when(recipeIngredientRepository.countByIngredientArticle_Id(22L)).thenReturn(2L);

        UpdateArticleRequest requestBody = UpdateArticleRequest.builder()
                .allowAsSubRecipeArticle(false)
                .build();

        assertThatThrownBy(() -> articleService.updateArticle(22L, requestBody))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cannot disable nested-ingredient flag: this article is referenced by other recipes");

        verify(articleRepository, never()).save(article);
    }
}
