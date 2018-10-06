package com.example.categorytree.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.categorytree.entity.Category;

@Repository
@Transactional
public interface CategoryRepository extends JpaRepository<Category, Integer> {
	
    @Query("SELECT x FROM Category x ORDER BY x.genreId")
    List<Category> findAllOrderById();
    
    List<Category> findByGenrePath(int genrePath);

}