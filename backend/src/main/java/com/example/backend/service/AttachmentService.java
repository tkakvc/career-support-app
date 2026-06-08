package com.example.backend.service;

import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.entity.Attachment;
import com.example.backend.entity.LearningRecord;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AttachmentRepository;
import com.example.backend.repository.LearningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    // サーバーのメモリ圧迫を防ぐため 10MB を上限とする
    // 10 * 1024 * 1024 = 10,485,760 バイト = 10MB
    // long 型にするのは int の最大値（約2.1億）を超えないが、明示的に long にすることで意図を伝えるため
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    // ストレージの無制限消費を防ぐため学習記録1件あたり 10 ファイルを上限とする
    private static final int MAX_ATTACHMENT_COUNT = 10;

    private final AttachmentRepository attachmentRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final StorageService storageService;

    // ============================================================
    // 添付ファイル一覧取得
    // ============================================================
    //
    // 【処理の流れ】
    //   1. verifyOwnership で学習記録の存在確認・所有者チェックを行う
    //   2. その学習記録に紐づく Attachment を全件取得する
    //   3. Attachment → AttachmentResponse に変換して返す
    //
    // 【readOnly = true とは】
    //   このメソッドはDBを読み取るだけで書き込まないため readOnly = true を付ける。
    //   readOnly を付けると Hibernate が最適化を行い、パフォーマンスが上がる。
    //   また誤って書き込み処理が混入したときにエラーで気づける。
    //
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getList(UUID userId, UUID learningRecordId) {
        verifyOwnership(userId, learningRecordId);
        return attachmentRepository.findByLearningRecordId(learningRecordId)
                .stream().map(AttachmentResponse::new).toList();
    }

    // ============================================================
    // ファイルアップロード
    // ============================================================
    //
    // 【処理の流れ】
    //   1. 所有者チェック
    //   2. バリデーション（ファイル未添付・サイズ超過・添付数上限）
    //   3. UUID を先に生成して storageKey を組み立てる（DB保存を1回にするため）
    //   4. Attachment を DB に保存
    //   5. StorageService でファイルをストレージに保存
    //   6. ストレージ保存が失敗した場合、@Transactional が自動ロールバックして DB の保存も取り消される
    //
    // 【@Transactional の自動ロールバックとは】
    //   @Transactional が付いたメソッドの中で RuntimeException が throw されると、
    //   Spring がそのトランザクション（一連のDB操作）を全部なかったことにする。
    //   ResponseStatusException は RuntimeException の一種なので、
    //   storageService.upload() が失敗して throw したとき、直前の save() も取り消される。
    //
    @Transactional
    public AttachmentResponse upload(UUID userId, UUID learningRecordId, MultipartFile file) {
        verifyOwnership(userId, learningRecordId);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルが添付されていません");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルサイズは10MB以下にしてください");
        }
        if (attachmentRepository.countByLearningRecordId(learningRecordId) >= MAX_ATTACHMENT_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "添付ファイルは1件の学習記録につき10ファイルまでです");
        }

        // DB 保存より前に attachmentId を確定させて storageKey を組み立てる（DB 書き込みを1回で済ませるため）
        UUID attachmentId = UUID.randomUUID();
        String storageKey = buildStorageKey(userId, attachmentId, file.getOriginalFilename());

        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .learningRecordId(learningRecordId)
                .fileName(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build();

        attachmentRepository.save(attachment);

        try {
            storageService.upload(storageKey, file);
        } catch (IOException e) {
            // RuntimeException を throw すると @Transactional がロールバックするため save() も取り消される
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルの保存に失敗しました");
        }

        return new AttachmentResponse(attachment);
    }

    // ============================================================
    // ファイルダウンロード
    // ============================================================
    //
    // 【AttachmentDownload とは】
    //   ダウンロード時は byte[]（ファイルの中身）だけでなく、
    //   Content-Type や fileName もレスポンスヘッダーに必要。
    //   これらをまとめて Controller に渡すために AttachmentDownload という record を使っている。
    //   record は Java 16 から使えるクラスで、フィールドとコンストラクタを1行で定義できる。
    //
    // 【learningRecordId との一致確認】
    //   attachmentId だけで Attachment を取得できるが、
    //   URLに指定した learningRecordId と実際の learningRecordId が一致するか確認する。
    //   これをしないと「/learning-records/他人のID/attachments/自分のファイルID」で
    //   他人のレコードのURLを通じて自分のファイルにアクセスできてしまう。
    //
    public AttachmentDownload download(UUID userId, UUID learningRecordId, UUID attachmentId) {
        verifyOwnership(userId, learningRecordId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("添付ファイルが見つかりません"));

        // attachmentId が URL の learningRecordId に紐づいているか確認（他記録への横断アクセスを防ぐ）
        if (!attachment.getLearningRecordId().equals(learningRecordId)) {
            throw new ResourceNotFoundException("添付ファイルが見つかりません");
        }

        try {
            byte[] bytes = storageService.download(attachment.getStorageKey());
            return new AttachmentDownload(bytes, attachment.getContentType(), attachment.getFileName(), attachment.getFileSize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルのダウンロードに失敗しました");
        }
    }

    // ============================================================
    // 添付ファイル削除
    // ============================================================
    //
    // 【削除の順番：ストレージ → DB】
    //   DB を先に消すとストレージにファイルが残ったまま（孤立ファイル）になるリスクがある。
    //   ストレージを先に消してから DB を消す順番にすることで、
    //   万が一 DB 削除が失敗してもストレージにファイルは残らない。
    //
    @Transactional
    public void delete(UUID userId, UUID learningRecordId, UUID attachmentId) {
        verifyOwnership(userId, learningRecordId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("添付ファイルが見つかりません"));

        if (!attachment.getLearningRecordId().equals(learningRecordId)) {
            throw new ResourceNotFoundException("添付ファイルが見つかりません");
        }

        // ストレージ → DB の順で削除する。DB を先に消すとストレージに孤立ファイルが残るリスクがある
        try {
            storageService.delete(attachment.getStorageKey());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルの削除に失敗しました");
        }

        attachmentRepository.delete(attachment);
    }

    // ============================================================
    // 学習記録削除時の一括削除（カスケード削除）
    // ============================================================
    //
    // 【なぜここで呼ぶか】
    //   LearningRecord を削除するとき、紐づく Attachment もストレージ・DB の両方から消す必要がある。
    //   JPA の CascadeType.ALL を使えば DB 削除は自動化できるが、
    //   ストレージ削除は Java コードで明示的に呼ばないといけない。
    //   そのため LearningRecordService.delete() からこのメソッドを呼ぶ設計にしている。
    //
    // 【ベストエフォートとは】
    //   ストレージ削除が失敗してもそのまま続ける（例外を握りつぶす）という意味。
    //   学習記録を消したいユーザーにとって、ストレージ削除の失敗でエラーになるより、
    //   多少ファイルが残ってもレコードが消える方が体験として良い。
    //   孤立ファイルは運用で定期クリーンアップすればよい。
    //
    @Transactional
    public void deleteAllByLearningRecordId(UUID learningRecordId) {
        List<Attachment> attachments = attachmentRepository.findByLearningRecordId(learningRecordId);
        for (Attachment attachment : attachments) {
            try {
                storageService.delete(attachment.getStorageKey());
            } catch (IOException ignored) {
            }
        }
        attachmentRepository.deleteAll(attachments);
    }

    // ============================================================
    // 所有者チェック（private）
    // ============================================================
    //
    // 【なぜ private メソッドに切り出すか】
    //   getList・upload・download・delete の全メソッドで同じチェックが必要。
    //   同じコードを4回書くと、修正が必要なとき4箇所直さないといけない。
    //   private メソッドに切り出すことで1箇所だけ直せばよくなる。
    //   これを「DRY原則（Don't Repeat Yourself）」という。
    //
    private void verifyOwnership(UUID userId, UUID learningRecordId) {
        LearningRecord record = learningRecordRepository.findById(learningRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        if (!record.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの学習記録にはアクセスできません");
        }
    }

    // ============================================================
    // storageKey の組み立て（private）
    // ============================================================
    //
    // 【なぜパスに attachmentId（UUID）を含めるか】
    //   同じファイル名（例: memo.pdf）を複数アップロードしたとき、
    //   パスが被って上書きされてしまうのを防ぐため。
    //   UUID はランダムに生成されるため、必ず一意なパスになる。
    //
    // 【String.format とは】
    //   文字列の中に変数を埋め込むメソッド。
    //   %s が変数の埋め込み位置を表す。
    //   例: String.format("attachments/%s/%s/%s", userId, attachmentId, fileName)
    //     → "attachments/550e.../7c9e.../memo.pdf" という文字列になる。
    //
    private String buildStorageKey(UUID userId, UUID attachmentId, String fileName) {
        return String.format("attachments/%s/%s/%s", userId, attachmentId, fileName);
    }
}
