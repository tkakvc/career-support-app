package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

// 本番用のストレージ実装。application.yaml の storage.type=s3 のときだけ Bean 登録される
// （LocalStorageService は storage.type=local のときだけ登録されるので、2つが同時に生きることはない）。
//
// ECS Fargate のコンテナはデプロイ・スケールのたびに作り直される使い捨て（ephemeral）で、
// ローカルディスクに書いたファイルは永続化されない。S3 に保存することで、
// コンテナが何度作り直されても・複数タスクに増えても、ファイルの実体は失われない。
//
// 認証情報はコード上に一切書かない。S3Client は「デフォルト認証プロバイダーチェーン」を使い、
// ECS 上では ECS タスクロール（infra/iam.tf の aws_iam_role.ecs_task）が持つ一時的な認証情報を
// 自動で拾ってくる。ローカルで直接このクラスを動かす場合は `aws configure` 済みの認証情報を使う。
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(
            @Value("${storage.s3.bucket-name}") String bucketName,
            @Value("${storage.s3.region:ap-northeast-1}") String region) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public void upload(String storageKey, MultipartFile file) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(file.getContentType())
                .build();

        try {
            // MultipartFile の中身をそのままアップロードする。
            // fromInputStream にはあらかじめサイズを渡す必要がある
            // （S3側がアップロード前に Content-Length を知りたいため。ローカル実装の Files.write と違い、
            // ここではファイル全体を一度 byte[] に読み込まず、ストリームのまま転送できる）。
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (S3Exception e) {
            throw new IOException("S3へのアップロードに失敗しました: " + storageKey, e);
        }
    }

    @Override
    public byte[] download(String storageKey) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new IOException("ファイルが見つかりません: " + storageKey, e);
        } catch (S3Exception e) {
            throw new IOException("S3からのダウンロードに失敗しました: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        try {
            // S3 の DeleteObject は対象キーが存在しなくてもエラーにならない（冪等な操作）。
            // ローカル実装の Files.deleteIfExists と同じ「無ければ何もしない」挙動になる。
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            throw new IOException("S3からの削除に失敗しました: " + storageKey, e);
        }
    }
}
