package com.soudan.report.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.CommunityMember;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

	List<CommunityMember> findByCommunityIdOrderByAddedAtAscIdAsc(Long communityId);

	boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

	void deleteByCommunityIdAndUserId(Long communityId, Long userId);

	@Query("select m.communityId as communityId, count(m) as total from CommunityMember m where m.communityId in :ids group by m.communityId")
	List<MemberCount> countByCommunities(@Param("ids") Collection<Long> ids);

	interface MemberCount {
		Long getCommunityId();

		long getTotal();
	}
}
