package com.example.db_setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<User,Integer>{

    UserProfile findByID(Integer ID);
}