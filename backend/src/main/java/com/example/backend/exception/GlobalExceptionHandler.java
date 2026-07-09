package com.example.backend.exception;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ例外処理を各 Controller に書かず一か所にまとめるか
//   → 各 Controller で try-catch を書くと、同じエラー処理が何十か所にも重複する。
//     @RestControllerAdvice で全 Controller の例外をここで一括処理することで、
//     エラーレスポンスの形式を統一でき、修正も1箇所で済む（DRY原則）。
// 【面接で説明できるようにする】なぜ 500 エラーで ex.getMessage() を返さないか
//   → 内部エラーの詳細（スタックトレース・クラス名・DBのテーブル名など）を返すと
//     攻撃者にシステム内部の情報を与えることになる（セキュリティリスク）。
//     詳細はサーバーのログに残し、外部には汎用メッセージだけ返すのがセキュリティの原則。
// 【AI任せでOK】@RestControllerAdvice / @ExceptionHandler / @ResponseStatus の書き方
// ============================================================
import com.example.backend.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

// ============================================================
// GlobalExceptionHandler とは何か
// ============================================================
//
// 【何もしないと何が起きるか】
//   例外を誰も受け取らないと、Spring が自動でエラーレスポンスを作る。
//   しかしそのレスポンスはこんな形で、フロントエンドには扱いにくい：
//
//   {
//     "timestamp": "2026-06-01T10:00:00.000+00:00",
//     "status": 400,
//     "error": "Bad Request",
//     "path": "/api/auth/signup"
//   }
//
//   エラーメッセージ（"パスワードは8文字以上"）がどこにも入っていない。
//
// 【このクラスの役割】
//   アプリ全体で throw された例外を「ここで一括して受け取り」、
//   フロントエンドが扱いやすい統一フォーマットの JSON に整えて返す係。
//
//   throw された例外
//         ↓
//   GlobalExceptionHandler が受け取る
//         ↓
//   { "status": 400, "message": "バリデーションエラーが発生しました", "errors": [...] }
//
// 【@RestControllerAdvice とは】
//   「全 Controller の例外をここで受け取る」とSpringに伝えるアノテーション。
//   これがないと、例外はここに来ず Spring のデフォルト処理に流れてしまう。
//
// ============================================================

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ============================================================
    // ケース① バリデーションエラー（400 Bad Request）
    // ============================================================
    //
    // 【いつ呼ばれるか】
    //   Controller の引数に @Valid を付けたとき、リクエストの内容が
    //   @NotBlank / @Email / @Size などの制約を破っていた場合。
    //   Spring が自動で MethodArgumentNotValidException を throw するのでここに来る。
    //
    // 【@ExceptionHandler(MethodArgumentNotValidException.class) とは】
    //   「MethodArgumentNotValidException が throw されたらこのメソッドを呼べ」とSpringに伝えるアノテーション。
    //   () の中に「どの例外クラスか」を書く。
    //
    // 【@ResponseStatus(HttpStatus.BAD_REQUEST) とは】
    //   このメソッドが return するときの HTTP ステータスコードを 400 に固定するアノテーション。
    //   HttpStatus.BAD_REQUEST は 400 の定数。
    //
    // 【中で何をやっているか】
    //   ex.getBindingResult().getFieldErrors()
    //     → バリデーションに引っかかったフィールドのエラー一覧を取得
    //        例）[{field: "email", message: "メールアドレスは必須です"},
    //             {field: "password", message: "パスワードは8文字以上で入力してください"}]
    //   .stream().map(FieldError::getDefaultMessage).collect(Collectors.toList())
    //     → エラーオブジェクトからメッセージ文字列だけを取り出してリスト化
    //        例）["メールアドレスは必須です", "パスワードは8文字以上で入力してください"]
    //
    // 【返すJSON】
    //   {
    //     "status": 400,
    //     "message": "バリデーションエラーが発生しました",
    //     "errors": ["メールアドレスは必須です", "パスワードは8文字以上で入力してください"]
    //   }
    //
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "バリデーションエラーが発生しました", errors);
    }

    // ============================================================
    // ケース② リソース未存在エラー（404 Not Found）
    // ============================================================
    //
    // 【いつ呼ばれるか】
    //   LearningRecordService などで存在しない tagId が指定されたとき。
    //   throw new ResourceNotFoundException("...") したときにここに来る。
    //
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    // ============================================================
    // ケース③ 業務エラー（401 Unauthorized / 409 Conflict など）
    // ============================================================
    //
    // 【いつ呼ばれるか】
    //   AuthService で throw new ResponseStatusException(...) したとき。
    //   例）メールアドレスが既に登録済み → throw new ResponseStatusException(409, "このメールアドレスは...")
    //       パスワードが間違い        → throw new ResponseStatusException(401, "メールアドレスまたは...")
    //
    // 【ResponseEntity<ErrorResponse> とは】
    //   HTTP レスポンスのステータス・ヘッダー・ボディをまとめて組み立てられるラッパークラス。
    //   <ErrorResponse> は「ボディの型」を表す。
    //
    // 【なぜここだけ @ResponseStatus ではなく ResponseEntity を使うのか】
    //   @ResponseStatus はアノテーションに 401 や 409 など固定値しか書けない。
    //   でもこのメソッドは 401 にも 409 にもなりうる（例外が持つステータスによって変わる）。
    //   だから ResponseEntity を使って、例外から取り出したステータスをそのまま使う：
    //     ResponseEntity.status(ex.getStatusCode()).body(body)
    //     → ex.getStatusCode() が 409 なら 409 を、401 なら 401 を返す
    //
    // 【返すJSON（409の例）】
    //   {
    //     "status": 409,
    //     "message": "このメールアドレスはすでに登録されています"
    //   }
    //
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse body = new ErrorResponse(ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    // ============================================================
    // ケース③ 予期しない例外（500 Internal Server Error）
    // ============================================================
    //
    // 【いつ呼ばれるか】
    //   上の2つのケースに当てはまらない、想定外の例外が throw されたとき。
    //   例）DBに接続できない、コードにバグがあって NullPointerException が起きた、など。
    //
    // 【Exception.class を指定する意味】
    //   Exception はすべての例外クラスの親クラス（基底クラス）。
    //   つまり「どんな例外でも受け取る」という意味になる。
    //   ただし Spring は「より具体的な @ExceptionHandler を優先」するので、
    //   ケース①②に当てはまるものはそちらが先に処理される。
    //   ここはあくまで「誰も受け取らなかった例外の最後の受け皿」。
    //
    // 【なぜ "予期しないエラーが発生しました" しか返さないのか】
    //   ex.getMessage() などでエラーの詳細をそのまま返すと、
    //   DBのテーブル名・内部のクラス名・ファイルパスなどが漏れてしまい、
    //   攻撃者にシステム内部の情報を与えることになる（セキュリティリスク）。
    //   詳細はサーバー側のログに残し、外部には汎用メッセージだけ返すのが原則。
    //
    // 【返すJSON】
    //   {
    //     "status": 500,
    //     "message": "予期しないエラーが発生しました"
    //   }
    //
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception ex) {
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "予期しないエラーが発生しました");
    }
}
