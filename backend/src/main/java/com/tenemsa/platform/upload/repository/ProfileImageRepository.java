package com.tenemsa.platform.upload.repository;

import com.tenemsa.platform.upload.model.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, String> {
    @Modifying
    @Query("delete from ProfileImage p where p.companyId = :companyId and p.ownerKey = :ownerKey")
    void deleteForOwner(@Param("companyId") Long companyId, @Param("ownerKey") String ownerKey);
}
