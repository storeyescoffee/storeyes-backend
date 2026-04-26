package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.ArticleResponse;
import io.storeyes.storeyes_coffee.stock.dto.CreateArticleRequest;
import io.storeyes.storeyes_coffee.stock.dto.UpdateArticleRequest;
import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.repositories.ArticleRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final StoreRepository storeRepository;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    private Long getStoreId() {
        return CurrentStoreContext.requireCurrentStoreId();
    }

    public List<ArticleResponse> getArticles(String category, String search) {
        Long storeId = getStoreId();
        Long dataStoreId = demoStoreDataSourceResolver.resolveStockDataStoreId(storeId);
        List<Article> articles = articleRepository.findByStoreIdAndFilters(
                dataStoreId,
                (category != null && !category.isBlank()) ? category.trim() : null,
                (search != null && !search.isBlank()) ? search.trim() : null
        );
        return articles.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ArticleResponse getArticleById(Long id) {
        Long storeId = getStoreId();
        Long dataStoreId = demoStoreDataSourceResolver.resolveStockDataStoreId(storeId);
        Article article = articleRepository.findByIdAndStoreId(id, dataStoreId)
                .orElseThrow(() -> new RuntimeException("Article not found with id: " + id));
        return toResponse(article);
    }

    @Transactional
    public ArticleResponse createArticle(CreateArticleRequest request) {
        Long storeId = getStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
        Article article = Article.builder()
                .store(store)
                .name(request.getName().trim())
                .salePrice(request.getSalePrice())
                .category(request.getCategory() != null && !request.getCategory().isBlank() ? request.getCategory().trim() : null)
                .build();
        Article saved = articleRepository.save(article);
        return toResponse(saved);
    }

    @Transactional
    public ArticleResponse updateArticle(Long id, UpdateArticleRequest request) {
        Long storeId = getStoreId();
        Article article = articleRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new RuntimeException("Article not found with id: " + id));

        if (request.getName() != null) {
            article.setName(request.getName().trim());
        }
        if (request.getSalePrice() != null) {
            article.setSalePrice(request.getSalePrice());
        }
        if (request.getCategory() != null) {
            article.setCategory(request.getCategory().trim().isEmpty() ? null : request.getCategory().trim());
        }

        Article updated = articleRepository.save(article);
        return toResponse(updated);
    }

    @Transactional
    public void deleteArticle(Long id) {
        Long storeId = getStoreId();
        if (!articleRepository.existsByIdAndStoreId(id, storeId)) {
            throw new RuntimeException("Article not found with id: " + id);
        }
        articleRepository.deleteById(id);
    }

    /** Used by RecipeIngredientService to ensure article belongs to store. */
    public Article getArticleEntity(Long articleId, Long storeId) {
        return articleRepository.findByIdAndStoreId(articleId, storeId)
                .orElseThrow(() -> new RuntimeException("Article not found with id: " + articleId));
    }

    private ArticleResponse toResponse(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .name(article.getName())
                .salePrice(article.getSalePrice())
                .category(article.getCategory())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
