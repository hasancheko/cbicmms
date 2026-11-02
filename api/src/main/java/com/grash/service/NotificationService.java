package com.grash.service;

import com.grash.advancedsearch.FilterField;
import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.dto.NotificationPatchDTO;
import com.grash.dto.PushTokenPayload;
import com.grash.exception.CustomException;
import com.grash.mapper.NotificationMapper;
import com.grash.model.Notification;
import com.grash.model.PushNotificationToken;
import com.grash.model.User;
import com.grash.model.enums.RoleType;
import com.grash.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final PushNotificationTokenService pushNotificationTokenService;
    private final SimpMessageSendingOperations messagingTemplate;

    @Async
    public void create(Notification notification) {
        Notification savedNotification = notificationRepository.save(notification);
        dispatchLiveNotification(savedNotification.getUser(), savedNotification);
    }

    @Async
    public void createMultiple(List<Notification> notifications, boolean mobile, String title) {
        if (notifications == null || notifications.isEmpty()) return;
        List<Notification> notificationsToSend = excludeCurrentUser(notifications);
        if (notificationsToSend.isEmpty()) return;

        List<Notification> savedNotifications = notificationRepository.saveAll(notificationsToSend);
        savedNotifications.forEach(notification ->
                dispatchLiveNotification(notification.getUser(), notification));

        if (mobile && !savedNotifications.isEmpty()) {
            try {
                Map<String, Object> pushData = new HashMap<>();
                pushData.put("type", savedNotifications.get(0).getNotificationType());
                pushData.put("id", savedNotifications.get(0).getResourceId());

                sendPushNotifications(
                        savedNotifications.stream().map(Notification::getUser).collect(Collectors.toList()),
                        title,
                        savedNotifications.get(0).getMessage(),
                        pushData
                );
            } catch (Exception e) {
                log.error("Push notification dispatch error: {}", e.getMessage());
            }
        }
    }

    private void dispatchLiveNotification(User user, Notification notification) {
        if (user == null || notification == null) return;
        try {
            String email = user.getEmail();
            String userId = user.getId() != null ? String.valueOf(user.getId()) : null;
            String username = user.getUsername();

            // 1. Dispatch to user email principal (default web STOMP subscription)
            if (email != null && !email.isEmpty()) {
                messagingTemplate.convertAndSendToUser(email, "/notifications", notification);
                messagingTemplate.convertAndSend("/topic/notifications/" + email, notification);
            }

            // 2. Dispatch to user ID principal (mobile/alternative STOMP subscription)
            if (userId != null) {
                messagingTemplate.convertAndSendToUser(userId, "/notifications", notification);
                messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
            }

            // 3. Dispatch to username if distinct from email
            if (username != null && !username.isEmpty() && !username.equals(email)) {
                messagingTemplate.convertAndSendToUser(username, "/notifications", notification);
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    public Collection<Notification> getAll(User user) {
        if (user.getRole().getRoleType().equals(RoleType.ROLE_CLIENT)) {
            return findByUser(user.getId());
        } else {
            return notificationRepository.findAll();
        }
    }

    public SearchCriteria getSearchCriteria(User user, SearchCriteria searchCriteria) {
        if (user.getRole().getRoleType().equals(RoleType.ROLE_CLIENT)) {
            searchCriteria.getFilterFields().add(FilterField.builder()
                    .field("user")
                    .value(user.getId())
                    .operation("eq")
                    .values(new ArrayList<>())
                    .build());
        }
        return searchCriteria;
    }

    private List<Notification> excludeCurrentUser(List<Notification> notifications) {
        // Keep all notifications so active users still receive alerts during testing/self-assignment
        return notifications;
    }

    public Notification getById(Long id, User user) {
        Optional<Notification> optionalNotification = notificationRepository.findById(id);
        if (optionalNotification.isPresent()) {
            Notification savedNotification = optionalNotification.get();
            checkAccessToNotification(savedNotification, user);
            return savedNotification;
        } else {
            throw new CustomException("Not found", HttpStatus.NOT_FOUND);
        }
    }

    public Notification patch(Long id, NotificationPatchDTO notificationsPatchDTO, User user) {
        Optional<Notification> optionalNotification = notificationRepository.findById(id);
        if (optionalNotification.isPresent()) {
            Notification savedNotification = optionalNotification.get();
            checkAccessToNotification(savedNotification, user);
            return notificationRepository.save(notificationMapper.updateNotification(savedNotification,
                    notificationsPatchDTO));
        } else {
            throw new CustomException("Notification not found", HttpStatus.NOT_FOUND);
        }
    }

    public void savePushToken(User user, PushTokenPayload tokenPayload) {
        String token = tokenPayload.getToken();
        PushNotificationToken pushNotificationToken;
        Optional<PushNotificationToken> optionalPushNotificationToken =
                pushNotificationTokenService.findByUser(user.getId());
        if (optionalPushNotificationToken.isPresent()) {
            pushNotificationToken = optionalPushNotificationToken.get();
            pushNotificationToken.setToken(token);
        } else {
            pushNotificationToken = PushNotificationToken.builder()
                    .user(user)
                    .token(token)
                    .build();
        }
        pushNotificationTokenService.save(pushNotificationToken);
    }

    private void checkAccessToNotification(Notification notification, User user) {
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }

    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }

    public Collection<Notification> findByUser(Long id) {
        return notificationRepository.findByUser_Id(id);
    }

    public Page<Notification> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<Notification> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return notificationRepository.findAll(builder.build(), page);
    }

    public void sendPushNotifications(Collection<User> users, String title, String message,
                                      Map<String, Object> data) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<String> tokens = new ArrayList<>();
        users.forEach(user -> {
            if (user != null && user.getId() != null) {
                Optional<PushNotificationToken> optionalPushNotificationToken =
                        pushNotificationTokenService.findByUser(user.getId());
                if (optionalPushNotificationToken.isPresent()) {
                    String token = optionalPushNotificationToken.get().getToken();
                    if (token != null && (token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken"))) {
                        tokens.add(token);
                    }
                }
            }
        });

        // Exit cleanly if no valid Expo push tokens are registered
        if (tokens.isEmpty()) {
            return;
        }

        List> payloads = new ArrayList<>();
        for (String token : tokens) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", token);
            payload.put("title", title != null ? title : "Atlas CMMS");
            payload.put("body", message != null ? message : "");
            payload.put("data", data != null ? data : Collections.emptyMap());
            payload.put("sound", "default");
            payload.put("priority", "high");
            payload.put("channelId", "default");
            payloads.add(payload);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            String expoAccessToken = System.getenv("EXPO_ACCESS_TOKEN");
            if (expoAccessToken != null && !expoAccessToken.trim().isEmpty()) {
                headers.setBearerAuth(expoAccessToken.trim());
            }

            HttpEntity<List>> request = new HttpEntity<>(payloads, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://exp.host/--/api/v2/push/send",
                    request,
                    String.class
            );

            log.info("Expo Push Server Response ({}): {}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            log.warn("Expo push relay skipped/failed: {}", e.getMessage());
        }
    }

    public void readAll(Long userId) {
        notificationRepository.readAll(userId);
    }
}
