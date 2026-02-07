package com.joblens.api.jobposting.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface JobPostingNotificationRepository extends JpaRepository<JobPostingNotification, Long> {

    Optional<JobPostingNotification> findByPostingId(String postingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM JobPostingNotification n WHERE n.postingId = :postingId")
    Optional<JobPostingNotification> findByPostingIdForUpdate(@Param("postingId") String postingId);

    /**
     * digest 대상: 즉시 발송 안 함 + 오늘 digest 아직 안 보냄 (digest_sent_at이 null이거나 오늘 09:00 이전).
     * 실제 필터는 서비스에서 digest_sent_at == null로 조회 후, 오늘 이미 보낸 digest에 포함된 건 제외.
     */
    @Query("SELECT n FROM JobPostingNotification n WHERE n.immediateSentAt IS NULL AND n.digestSentAt IS NULL AND n.totalScoreSnapshot >= :minScore")
    List<JobPostingNotification> findEligibleForDigest(@Param("minScore") int minScore);
}
