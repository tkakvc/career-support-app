package com.example.backend;

import com.example.backend.entity.Tag;
import com.example.backend.entity.User;
import com.example.backend.repository.TagRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("test@example.com").isPresent()) {
            return;
        }

        User user = User.builder()
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("テストユーザー")
                .build();
        userRepository.save(user);

        List.of("Java", "Spring Boot", "React", "Docker", "AWS").forEach(name ->
                tagRepository.save(Tag.builder()
                        .name(name)
                        .type("default")
                        .build())
        );
    }
}
