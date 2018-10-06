package com.example.categorytree.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.categorytree.entity.Item;

@Repository
@Transactional
public interface ItemRepository extends JpaRepository<Item, Integer>{

}
