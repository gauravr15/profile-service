package com.odin.profileservice.repo;

import com.odin.profileservice.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {

    @Query("select distinct g from Group g left join fetch g.members left join fetch g.admins where g.groupId = :groupId")
    Optional<Group> findByIdWithMembers(@Param("groupId") String groupId);

    @Query("select distinct g from Group g join g.members m where m = :memberId")
    List<Group> findByMemberId(@Param("memberId") String memberId);

    @Query("select case when count(g) > 0 then true else false end from Group g join g.members m where g.groupId = :groupId and m = :memberId")
    boolean existsByGroupIdAndMemberId(@Param("groupId") String groupId, @Param("memberId") String memberId);
}
