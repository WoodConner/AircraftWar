package edu.hitsz.network;

import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;

/**
 * 在线排行榜 Retrofit API 接口
 * 任务书要求：Retrofit+OkHttp实现HTTP POST/GET，服务端采用SpringBoot+MySQL
 * 服务器端RESTful接口预留，对接实际后端时填写 BASE_URL
 */
public interface ScoreApiService {

    /** 获取全球Top排行榜 */
    @GET("api/scores")
    Call<List<OnlineScoreDTO>> getTopScores(
            @Query("limit") int limit,
            @Header("Authorization") String token
    );

    /** 上传本局分数 */
    @POST("api/scores")
    Call<ApiResponse> uploadScore(
            @Body UploadScoreRequest request,
            @Header("Authorization") String token
    );

    /** 用户登录（获取JWT Token） */
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    // ===== 数据传输对象 =====

    class OnlineScoreDTO {
        public int rank;
        public String playerName;
        public int score;
        public String difficulty;
        public long timestamp;
    }

    class UploadScoreRequest {
        public String playerName;
        public int score;
        public String difficulty;
        public UploadScoreRequest(String name, int score, String diff) {
            this.playerName = name; this.score = score; this.difficulty = diff;
        }
    }

    class LoginRequest {
        public String username, password;
        public LoginRequest(String u, String p) { username = u; password = p; }
    }

    class LoginResponse {
        public String token;
        public String username;
    }

    class ApiResponse {
        public boolean success;
        public String message;
    }
}
