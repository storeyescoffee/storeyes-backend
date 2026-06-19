package io.storeyes.storeyes_coffee.notification.services;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseApp.class)
public class FcmNotificationService {

    private final FirebaseApp firebaseApp;

    /**
     * Direct port of send_alert_success_notification() from the Python notif-test project.
     * Sends to all provided tokens with the alert_success payload.
     *
     * @return number of tokens notified successfully
     */
    public int sendAlertSuccessNotification(List<String> tokens, String boardId, String deviceName) {
        return sendToTokens(
                tokens,
                "Alert",
                "An alert has been processed successfully",
                Map.of(
                        "board_id", boardId,
                        "device_name", deviceName,
                        "type", "alert_success"
                )
        );
    }

    /**
     * Send the same notification to multiple device tokens.
     *
     * @return number of tokens that received the notification successfully
     */
    public int sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        int successCount = 0;
        for (String token : tokens) {
            if (token == null || token.isBlank()) continue;
            try {
                sendToToken(token.strip(), title, body, data);
                successCount++;
            } catch (FirebaseMessagingException e) {
                log.error("FCM send failed for token ending in ...{}: {} ({})",
                        token.length() > 8 ? token.substring(token.length() - 8) : token,
                        e.getMessagingErrorCode(), e.getMessage());
            }
        }
        log.debug("FCM batch done — {}/{} tokens notified", successCount, tokens.size());
        return successCount;
    }

    /**
     * Send a notification to a single device token.
     * Mirrors the per-token HTTP POST in firebase-notifier.py, including the APNS priority header.
     */
    public void sendToToken(String token, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .build())
                .build();

        String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
        log.debug("FCM message sent, id={}", messageId);
    }
}
