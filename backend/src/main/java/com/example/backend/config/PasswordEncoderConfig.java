package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// @Configuration: このクラスがSpringの設定クラスであることを示す。@Bean メソッドを定義できる
@Configuration
public class PasswordEncoderConfig {

    // @Bean: このメソッドの戻り値をSpringコンテナに登録する
    // → AuthService で BCryptPasswordEncoder をフィールドに宣言するだけで自動注入される
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
