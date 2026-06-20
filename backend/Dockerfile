# ── ビルド段階 ──────────────────────────────────────────────────────────────
# この段階では「.jar ファイルを作ること」だけが目的。
# 最終的なイメージにはこの段階のファイルは含まれない。

FROM eclipse-temurin:21-jdk AS builder
# eclipse-temurin:21-jdk = Java 21 の JDK（コンパイルやビルドに必要なフルセット）が入ったベースイメージ。
# `AS builder` = この段階に「builder」という名前をつける。後の段階で参照するために使う。

WORKDIR /app
# コンテナ内の作業ディレクトリを /app に設定する。
# 以降の COPY や RUN コマンドはすべて /app を起点として動く。

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
# 手元の PC にあるファイルをコンテナ内の /app にコピーする。
# Gradle でビルドするために必要なファイルを全部揃えている。

RUN chmod +x ./gradlew
# gradlew（Gradle のラッパースクリプト）に実行権限を付ける。
# Git でチェックアウトしたファイルは実行権限が外れていることがあるため、明示的に付与する。

RUN ./gradlew bootJar --no-daemon
# bootJar タスクを実行して .jar ファイルを生成する。
# 生成された .jar は build/libs/ ディレクトリに置かれる。
# --no-daemon = バックグラウンドプロセス（Gradle Daemon）を使わない指定。
#               Docker ビルド時はプロセスが終わった後にコンテナが破棄されるため、
#               Daemon を起動しても意味がないので切っておくのが定番。

# ── 実行段階 ──────────────────────────────────────────────────────────────
# builder 段階で作った .jar だけをこちらのイメージにコピーして、
# ビルドツール（JDK・Gradle）は捨てる。イメージを小さくするための分割。

FROM eclipse-temurin:21-jre-alpine
# eclipse-temurin:21-jre-alpine = Java 21 の JRE（実行専用・JDK より軽量）が入ったイメージ。
# alpine = 軽量 Linux ディストリビューション。イメージサイズを小さくできる。

WORKDIR /app
# 実行段階でも作業ディレクトリを /app に設定する。

COPY --from=builder /app/build/libs/*.jar app.jar
# builder 段階で生成された .jar ファイルをこのイメージの /app/app.jar としてコピーする。
# --from=builder = 「builder と名付けた段階からコピーする」という指定。

ENTRYPOINT ["java", "-jar", "app.jar"]
# コンテナ起動時に実行するコマンドを指定する。
# `java -jar app.jar` = Spring Boot アプリを起動するコマンド。
