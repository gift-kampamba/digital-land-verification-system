package com.landverification.repository;

import com.landverification.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Integer> {
    List<Document> findByRelatedTypeAndRelatedId(Document.RelatedType relatedType, Integer relatedId);
    List<Document> findByUploadedBy_UserIdOrderByUploadedAtDesc(Integer userId);
    @Query("SELECT d FROM Document d " +
           "WHERE (:docType IS NULL OR d.documentType = :docType) " +
           "AND (:search IS NULL OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :search, '%'))) ")
    List<Document> searchByTypeAndFileName(@Param("docType") Document.DocumentType docType,
                                           @Param("search") String search);
}