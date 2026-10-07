package com.bernardo.transcricao.repository;

import com.bernardo.transcricao.model.Transcricao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TranscricaoRepository extends JpaRepository<Transcricao, UUID> {
}