package hello.tradexserver.repository;

import hello.tradexserver.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT c FROM ChatMessage c WHERE c.chatSession.id = :sessionId ORDER BY c.createdAt DESC LIMIT :limit")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    List<ChatMessage> findAllByChatSessionIdOrderByCreatedAtAsc(Long sessionId);

    void deleteAllByChatSessionId(Long sessionId);
}