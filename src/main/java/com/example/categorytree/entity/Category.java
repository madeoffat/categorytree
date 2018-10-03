package com.example.categorytree.entity;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.valueextraction.ExtractedValue;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "categories")
@NoArgsConstructor
public class Category {
	@Id
	private int genreId;
	private String genreName;
	private int genreLevel;
	@Transient	
	private List<Category> children;
}
