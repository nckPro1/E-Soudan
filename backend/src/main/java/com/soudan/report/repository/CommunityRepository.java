package com.soudan.report.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soudan.report.domain.Community;

public interface CommunityRepository extends JpaRepository<Community, Long> {

	@Query("select c from Community c where c.id = :id and c.deletedAt is null")
	Optional<Community> findLive(@Param("id") Long id);

	@Query("select c from Community c where c.ownerId = :ownerId and c.deletedAt is null order by c.name")
	List<Community> findOwnedBy(@Param("ownerId") Long ownerId);

	@Query("""
			select c from Community c
			where c.deletedAt is null
			  and c.id in (select m.communityId from CommunityMember m where m.userId = :userId)
			order by c.name""")
	List<Community> findJoinedBy(@Param("userId") Long userId);
}
