package com.soudan.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soudan.chat.domain.ChatRead;

public interface ChatReadRepository extends JpaRepository<ChatRead, ChatRead.Key> {
}
