package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface ImagePlatforms {
    record Platform(String os,String architecture) {}
    record Result(List<Platform> platforms,boolean complete,String note) {
        public static Result unknown(){return new Result(List.of(),false,"manifest 조회·형식 확인 실패 · Registry 읽기 권한과 연결을 확인하세요.");}
    }
    Result read(Connection connection,String region,String image);
}
