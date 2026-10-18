package com.openmarket.member.infra.persistence;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** {@link MemberRepository}의 Spring Data JPA 구현. 메서드는 도메인 인터페이스에서 이름으로 만든다. */
public interface JpaMemberRepository extends Repository<Member, UUID>, MemberRepository {

    /** {@code SELECT ... FOR UPDATE}. 배송지를 동시에 더해도 개수 제한을 넘지 않는다. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findForUpdateById(UUID id);
}
