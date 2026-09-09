package com.example.backend.config;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ BCryptPasswordEncoder を @Bean として登録するか
//   → BCryptPasswordEncoder は AuthService と DataInitializer の両方で使う。
//     @Bean にして Spring に管理させることで、どちらにも同じインスタンスが自動注入される。
//     SecurityConfig に書かず独立したクラスに切り出している理由は、
//     AuthService が SecurityConfig に依存する形になるのを避けるため。
//     SecurityConfig は認証フィルターの設定に専念させ、
//     BCryptPasswordEncoder の提供という責務を持たせない。

//     ★ なぜ「依存する形になる」と言えるか：
//       @Bean メソッドを実行するには、そのメソッドを持つクラス自体を先にSpringが組み立てる必要がある。
//       もし passwordEncoder() が SecurityConfig の中にあると、
//         「AuthServiceがBCryptPasswordEncoderを要求」
//           → 「Springがその@BeanメソッドはSecurityConfigの中にあると気づく」
//           → 「SecurityConfigを組み立てる（JwtAuthenticationFilterやCORSの許可オリジン設定も一緒に必要になる）」
//       という流れになり、パスワードのハッシュ化がしたいだけのAuthServiceが、
//       JWTフィルターやCORS設定という無関係な依存まで間接的に引きずり込むことになる。
//       独立クラスに切り出せば、この巻き込みが起きない
//       （AuthServiceの単体テストで、無関係なSecurityConfigの依存まで用意する必要が無くなる、というメリットもある）。
// 【面接で説明できるようにする】なぜ BCrypt でパスワードをハッシュ化するか
//   → 平文で保存するとDBが漏洩したとき即座にパスワードが判明してしまう。
//     BCrypt はハッシュ化に意図的に時間がかかる設計になっており、総当たり攻撃を遅らせられる。
//     また BCrypt はソルト（ランダム値）を自動で付与するため、同じパスワードでも毎回異なるハッシュになる。
// 【AI任せでOK】@Configuration / @Bean アノテーションの書き方
// ============================================================
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// @Configuration: このクラスがSpringの設定クラスであることを示す。@Bean メソッドを定義できる
@Configuration
public class PasswordEncoderConfig {

    // @Bean: このメソッドの戻り値を Spring の管理下に置くアノテーション。
    // BCryptPasswordEncoder はライブラリのクラスなので @Component を付けられない。
    // 代わりに new BCryptPasswordEncoder() で作ったインスタンスを @Bean で登録することで、
    // AuthService のフィールドに BCryptPasswordEncoder を書くだけで Spring が自動注入できるようになる。
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
