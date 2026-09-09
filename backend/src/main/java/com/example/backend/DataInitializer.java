package com.example.backend;

// ============================================================
// 【このファイル全体の方針】
// 【書き方は覚えなくてOK】ここに出てくる CommandLineRunner の実装パターンは
// Springの決まり文句(ボイラープレート)。書き方自体を暗記する必要はない。
//   ボイラープレートとは: 元々は「ボイラー(boiler=蒸気釜)の外壁に使う、
//   同じ規格で大量生産された鉄板(plate)」のこと。どの現場に運んでも形が同じで
//   使い回せることから、印刷業界で「新聞社に配布する、内容を変えずにそのまま
//   使い回す定型文」を指すようになった。転じてプログラミングでは「毎回ほぼ同じ形で
//   書かなければならない、決まりきったコード」を意味する。
// ただし「なぜこのファイルがあるか」は理解しておく。
//   → CommandLineRunner を実装すると Spring Boot 起動直後に run() が自動で呼ばれる。
//   → ここで作るテストユーザー/タグはローカル開発用のダミーデータ。
//     ・OpenAPI(Swagger)の「仕様書を自動生成する仕組み」とは無関係(DBにデータを入れるだけの処理)。
//     ・ただし Swagger UI の「Try it out」で実際に GET /api/tags を叩けば、
//       ここで入れた default タグ(Java, Spring Boot, React, Docker, AWS)は実データとして返ってくる。
//   → 本番環境では Flyway などのマイグレーションツールで代替することが多い。
// ============================================================
import com.example.backend.entity.Tag;
import com.example.backend.entity.User;
import com.example.backend.repository.TagRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

// DataInitializer = data(データ) + initializer(初期化するもの)。
//
// @Component: このクラスを Spring の DI コンテナ(アプリ起動時に必要なオブジェクトを
// まとめて管理する仕組み)に登録するアノテーション。登録されたクラスは Spring が
// 自動でインスタンス化(newして使える状態に)してくれる。
//
// implements CommandLineRunner: Spring Boot が用意しているインターフェース。
// これを実装したクラスは、アプリの起動処理が全部終わった直後に run() が自動で呼ばれる。
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
