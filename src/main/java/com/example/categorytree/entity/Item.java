package com.example.categorytree.entity;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "itmes")
@NoArgsConstructor
public class Item implements Serializable{
	@Id
	private int genreId;
	private int rank;
	private String genreName;
	private String itemName;
}
