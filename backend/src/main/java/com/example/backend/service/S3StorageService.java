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

// ============================================================
// 【このファイルの使い方】
//   直接 new しない。呼び出し側（AttachmentService）は StorageService インターフェースに
//   依存し、upload / download / delete だけを呼ぶ。S3 版とローカル版どちらが動くかは
//   application.yaml の storage.type が決める（下の @ConditionalOnProperty）。
//   → このファイルは「storage.type=s3 のときだけ選ばれる実装の中身」。
//     S3 の API を細かく覚える必要はなく、下の3つの作法だけ押さえればよい。
//
// 【押さえる作法】
//   1. AWS SDK v2 の共通形：XxxRequest.builder().～.build() で引数オブジェクトを組み立て、
//      s3Client.xxx(request) に渡す。putObject / getObject / deleteObject 全部この形。
//      S3 以外の AWS サービスも SDK v2 は全部この形なので、1回覚えれば他も読める。
//   2. 認証情報はコードに書かない。S3Client は「デフォルト認証プロバイダーチェーン」で
//      実行環境から取得する（ECS = タスクロール infra/iam.tf の aws_iam_role.ecs_task、
//      ローカル = `aws configure` した ~/.aws/ の認証情報）。コードは「どこで動くか」を知らない。
//   3. AWS 固有の例外（S3Exception / NoSuchKeyException）は必ず catch して IOException に
//      詰め替える。AWS の型を呼び出し側に漏らさないための「境界」がこのクラスの役割。
//
// 【storageKey は呼び出し側が決める】
//   引数の storageKey をそのまま S3 のキー（バケット内のパス）として使う。
//   命名規則 attachments/{userId}/{attachmentId}/{fileName} は
//   AttachmentService.buildStorageKey() と docs/attachments/storage.md 側の責務。
//
// 【なぜ S3 なのか】
//   ECS Fargate のコンテナはデプロイ・スケールのたびに作り直される使い捨て（ephemeral）で、
//   ローカルディスクに書いたファイルは残らない。S3 に置けばコンテナが増減・再作成されても
//   ファイルの実体は失われない。
//
// 【@ConditionalOnProperty(name = "storage.type", havingValue = "s3")】
//   storage.type の値が "s3" のときだけこのクラスを Bean 登録する条件。
//   LocalStorageService は "local"（または未設定）のときだけ登録されるので、
//   StorageService 型の Bean は常にどちらか1つだけになり、注入時に衝突しない。
// ============================================================
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    // ▼ s3Client は起動時に1個だけ作り、全リクエストで共有する（private final = 差し替えない）
    //   ・Web サーバーは複数リクエストを同時処理する＝複数スレッドが同時にこの s3Client を呼ぶ
    //   ・S3Client はスレッドセーフ（同時に呼ばれても内部で壊れない）なので、スレッドごとに
    //     分ける必要がなく、1個の共有で安全に動く
    //   ・S3Client は生成時にコネクションプールを張る重いオブジェクト。共有できるなら
    //     リクエストごとに new せず使い回す方が無駄がない
    private final S3Client s3Client;
    private final String bucketName;

    // @Value("${キー:デフォルト値}") は application.yaml の値をコンストラクタ引数に流し込む。
    //   storage.s3.bucket-name        → 必須（デフォルトなし。未設定だと起動失敗）
    //   storage.s3.region:ap-northeast-1 → 未設定なら東京リージョンを使う
    public S3StorageService(
            @Value("${storage.s3.bucket-name}") String bucketName,
            @Value("${storage.s3.region:ap-northeast-1}") String region) {
        this.bucketName = bucketName;
        // ここでも認証情報は渡していない。region だけ指定して build() すると、
        // 認証はデフォルト認証プロバイダーチェーンが実行環境から自動で解決する。
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public void upload(String storageKey, MultipartFile file) throws IOException {
        // 作法1：まず「何を・どこに置くか」を Request オブジェクトに組み立てる。
        //   bucket = どのバケットか / key = バケット内のパス（= storageKey をそのまま使う）
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(file.getContentType())
                .build();

        try {
            // 組み立てた request に「中身（本体）」を添えて putObject に渡す。
            // RequestBody.fromInputStream にはサイズを一緒に渡す必要がある
            // （S3 はアップロード開始前に Content-Length を知りたいため）。
            // ストリームのまま送るので、ファイル全体を byte[] に読み込まずに済む。
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (S3Exception e) {
            // 作法3：AWS 固有の S3Exception を、インターフェースが約束した IOException に詰め替える。
            // 第2引数に e を渡して元の例外を原因（cause）として保持する。
            throw new IOException("S3へのアップロードに失敗しました: " + storageKey, e);
        }
    }

    @Override
    public byte[] download(String storageKey) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        // try-with-resources：() 内で開いたストリームは try を抜けるとき自動で close される。
        // getObject が返す ResponseInputStream は S3 との接続を握っているので閉じ忘れると
        // コネクションが枯渇する。
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            // 全部メモリに読み込んで byte[] で返す。添付は上流（AttachmentService）で
            // 10MB 上限をかけているので全読みして問題ない。
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            // 作法3：キーが無いケースは NoSuchKeyException（S3Exception のサブクラス）で先に捕まえ、
            // 「見つからない」と分かるメッセージにしてから IOException に詰め替える。
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
            // S3 の DeleteObject は対象キーが存在しなくてもエラーにならない（冪等 = 何回呼んでも結果が同じ）。
            // ローカル実装の Files.deleteIfExists と同じ「無ければ何もしない」挙動になるので、
            // 呼び出し側は「消す前に存在チェック」を書かなくていい。
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            throw new IOException("S3からの削除に失敗しました: " + storageKey, e);
        }
    }
}
