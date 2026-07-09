package com.example.backend;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】このファイルは Spring Boot のボイラープレート。覚えなくていい。
//   → @SpringBootApplication と SpringApplication.run() の2行だけで Spring Boot が起動する。
//   → Spring Boot を使う場合は必ずこのファイルが1つ存在する。
// ============================================================
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
