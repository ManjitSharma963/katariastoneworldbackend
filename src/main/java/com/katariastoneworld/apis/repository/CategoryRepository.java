package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByCategoryTypeOrderByDisplayOrderAsc(String categoryType);
    List<Category> findAllByOrderByDisplayOrderAsc();
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<Category> findByCategoryTypeAndIsActiveTrueOrderByDisplayOrderAsc(String categoryType);
    Category findByNameAndCategoryType(String name, String categoryType);
}

