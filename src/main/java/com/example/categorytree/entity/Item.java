package com.example.categorytree.entity;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "items", indexes = @Index(columnList = "genreId, rank"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	private int genreId;
	private int rank;
	private String genreName;
	private String itemName;
}
