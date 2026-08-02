package com.scheduler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.scheduler.model.DlqEntry;
// repository used for dlq replay
@Repository
public interface DlqRepository extends JpaRepository<DlqEntry, String> {

    // finding tasks which are failed, in the descending order
    // so most recently failed will appear at the top
    List<DlqEntry> findAllByOrderByFailedAtDesc();

    // counting the replayed tasks
    long countByReplayed(boolean replayed);
}