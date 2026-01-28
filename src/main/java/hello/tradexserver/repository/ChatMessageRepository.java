package hello.tradexserver.repository;

import hello.tradexserver.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query(value = "SELECT c FROM ChatMessage c " +
            "WHERE c.user.id = :userId " +
            "ORDER BY c.createdAt DESC " +
            "LIMIT :limit")
    List<ChatMessage> findRecentHistory(@Param("userId") Long userId, @Param("limit") int limit);
}
