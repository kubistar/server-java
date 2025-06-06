package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String userId);
    User save(User user);
    Optional<User> findByIdForUpdate(String userId); // 비관적 락
}