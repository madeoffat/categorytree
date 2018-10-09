package com.example.categorytree.entity;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "categories")
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {
	@Id
	private int genreId;
	private String genreName;
	private int genrePath;
	// private Set<Integer> childIds;
	@Transient
	private List<Category> children;
}
