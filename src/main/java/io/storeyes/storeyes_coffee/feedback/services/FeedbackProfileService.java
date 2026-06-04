package io.storeyes.storeyes_coffee.feedback.services;

import io.storeyes.storeyes_coffee.documents.services.S3Service;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileCreateRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileUpdateRequest;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackProfile;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackProfileRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class FeedbackProfileService {

    private static final String S3_FOLDER = "feedback-profiles";
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FeedbackProfileRepository feedbackProfileRepository;
    private final StoreRepository storeRepository;
    private final S3Service s3Service;

    public FeedbackProfileDTO getByCode(String code) {
        return feedbackProfileRepository.findByCode(code)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("FeedbackProfile not found: " + code));
    }

    @Transactional
    public FeedbackProfileDTO create(FeedbackProfileCreateRequest request) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new RuntimeException("Store not found: " + request.getStoreId()));

        if (feedbackProfileRepository.findByStoreId(store.getId()).isPresent()) {
            throw new RuntimeException("A FeedbackProfile already exists for store: " + store.getId());
        }

        String logoUrl = null;
        if (request.getLogo() != null && !request.getLogo().isEmpty()) {
            logoUrl = s3Service.uploadFile(request.getLogo(), S3_FOLDER);
        }

        FeedbackProfile profile = FeedbackProfile.builder()
                .store(store)
                .code(generateCode())
                .storeName(request.getStoreName())
                .logoUrl(logoUrl)
                .googleReviewUrl(request.getGoogleReviewUrl())
                .build();

        return toDTO(feedbackProfileRepository.save(profile));
    }

    @Transactional
    public FeedbackProfileDTO update(Long id, FeedbackProfileUpdateRequest request) {
        FeedbackProfile profile = findById(id);

        if (request.getStoreName() != null && !request.getStoreName().isBlank()) {
            profile.setStoreName(request.getStoreName());
        }
        if (request.getGoogleReviewUrl() != null && !request.getGoogleReviewUrl().isBlank()) {
            profile.setGoogleReviewUrl(request.getGoogleReviewUrl());
        }
        if (request.getLogo() != null && !request.getLogo().isEmpty()) {
            if (profile.getLogoUrl() != null) {
                s3Service.deleteFile(profile.getLogoUrl());
            }
            profile.setLogoUrl(s3Service.uploadFile(request.getLogo(), S3_FOLDER));
        }

        return toDTO(feedbackProfileRepository.save(profile));
    }

    @Transactional
    public void delete(Long id) {
        FeedbackProfile profile = findById(id);
        if (profile.getLogoUrl() != null) {
            s3Service.deleteFile(profile.getLogoUrl());
        }
        feedbackProfileRepository.delete(profile);
    }

    private String generateCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (feedbackProfileRepository.findByCode(code).isPresent());
        return code;
    }

    private FeedbackProfile findById(Long id) {
        return feedbackProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FeedbackProfile not found: " + id));
    }

    private FeedbackProfileDTO toDTO(FeedbackProfile p) {
        return FeedbackProfileDTO.builder()
                .id(p.getId())
                .storeId(p.getStore().getId())
                .code(p.getCode())
                .storeName(p.getStoreName())
                .logoUrl(p.getLogoUrl())
                .googleReviewUrl(p.getGoogleReviewUrl())
                .build();
    }
}
