package com.patex.entities;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Created by Alexey on 12.03.2016.
 */
@Repository
public interface AuthorRepository extends CrudRepository<Author, Long> {

    Page<Author> findAllOrderByName(Pageable pageable);

    List<Author> findByNameStartingWithIgnoreCaseOrderByName(String name);


    @Query("SELECT NEW com.patex.entities.AggrResult(substring(a.name,0, :prefixLenth) as  id, count(*) as result)" +
            " FROM Author a where name like :prefix% group by col_0_0_")//TODO FIX THAT
    List<AggrResult> getAuthorsCount(@Param("prefixLenth")int length, @Param("prefix") String name);

    Page<Author> findByNameStartingWithIgnoreCase(String name, Pageable pageable);


    @Query("SELECT NEW com.patex.entities.Author(a.id, a.name)" +
            " FROM Author a where name like :prefix% order by name")
    Page<Author> getAuthorsByName(Pageable pageable, @Param("prefix") String prefix);
}
