package com.landverification.repository;

import com.landverification.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByNationalId(String nationalId);
    Optional<User> findByInvitationCode(String invitationCode);
    boolean existsByEmail(String email);
    boolean existsByNationalId(String nationalId);
    boolean existsByInvitationCode(String invitationCode);
    List<User> findByRole(User.Role role);
    List<User> findByRoleAndEmailContainingIgnoreCaseOrRoleAndFullNameContainingIgnoreCaseOrRoleAndNationalIdContainingIgnoreCase(
            User.Role role1,
            String email,
            User.Role role2,
            String fullName,
            User.Role role3,
            String nationalId);
}
