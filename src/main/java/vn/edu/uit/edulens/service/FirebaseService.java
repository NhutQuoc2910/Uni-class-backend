package vn.edu.uit.edulens.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.edu.uit.edulens.repository.ClassMemberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseService {

    private static final int FCM_MAX_BATCH_SIZE = 500;

    private final ClassMemberRepository classMemberRepository;

    @Async
    public void sendMulticastNotification(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Skipping notification: {}", title);
            return;
        }

        if (fcmTokens == null || fcmTokens.isEmpty()) {
            return;
        }

        List<String> validTokens = fcmTokens.stream()
                .filter(t -> t != null && !t.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (validTokens.isEmpty()) {
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            int totalSuccess = 0;
            int totalFailure = 0;

            for (int i = 0; i < validTokens.size(); i += FCM_MAX_BATCH_SIZE) {
                List<String> batch = validTokens.subList(i, Math.min(i + FCM_MAX_BATCH_SIZE, validTokens.size()));

                MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                        .setNotification(notification)
                        .addAllTokens(batch);

                if (data != null && !data.isEmpty()) {
                    messageBuilder.putAllData(data);
                }

                MulticastMessage message = messageBuilder.build();
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                totalSuccess += response.getSuccessCount();
                totalFailure += response.getFailureCount();
            }

            log.info("Sent Firebase multicast notification '{}' to {} devices. Success: {}, Failure: {}",
                    title, validTokens.size(), totalSuccess, totalFailure);
        } catch (Exception e) {
            log.error("Failed to send Firebase multicast notification '{}': {}", title, e.getMessage());
        }
    }

    @Async
    public void notifyClassStudents(UUID classId, String title, String body, Map<String, String> data) {
        if (classId == null) {
            return;
        }

        try {
            List<String> rawTokens = classMemberRepository.findFcmTokensByCourseClassId(classId);
            if (rawTokens == null || rawTokens.isEmpty()) {
                log.debug("No valid FCM tokens found for classId: {}", classId);
                return;
            }

            List<String> tokens = rawTokens.stream()
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());

            if (!tokens.isEmpty()) {
                sendMulticastNotification(tokens, title, body, data);
            } else {
                log.debug("No valid FCM tokens found after sanitization for classId: {}", classId);
            }
        } catch (Exception e) {
            log.error("Failed to collect FCM tokens for class {}: {}", classId, e.getMessage());
        }
    }
}
