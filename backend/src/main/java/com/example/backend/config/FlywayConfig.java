package com.example.backend.config;

// 【このファイルを作った経緯】
// 本番のRDSにはFlyway導入前（Hibernateのddl-auto: update時代）から存在していた
// テーブルがあり、V1__baseline_schema.sqlはその実体を調査しながら少しずつ正確にする
// 必要があった（本番を実際に起動してみるまで、tags.created_at等の抜けが分からなかった）。
//
// V1の内容を1度適用が成功した後にもう一度修正すると、Flywayは「このファイルの中身の
// ハッシュ値（チェックサム）が、前回成功した時に記録した値と違う」と判断し、
// 以後の起動を全てValidateErrorで止めてしまう（改ざん検知の仕組みなので、
// 本来は正しい挙動）。
//
// repair()は「flyway_schema_historyに記録されたチェックサムを、今のファイルの
// 内容で記録し直す」操作。migrate()の前に毎回repair()を挟むことで、
// 「ファイルの中身が正＝DB側の記録をファイルに合わせる」という運用に統一し、
// 今後V1の中身を直すことになっても手動復旧が要らないようにしている。
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return this::repairThenMigrate;
    }

    private void repairThenMigrate(Flyway flyway) {
        flyway.repair();
        MigrateResult result = flyway.migrate();
        if (!result.success) {
            throw new IllegalStateException("Flyway migration failed: " + result.migrationsExecuted);
        }
    }
}
