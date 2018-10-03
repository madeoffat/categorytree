package com.example.categorytree.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.categorytree.entity.Category;
import com.example.categorytree.service.CategoryService;

@Controller
@RequestMapping("category")
public class CategoryController {
	
	@Autowired
	CategoryService categoryService;
	
    @GetMapping("list")
    public String list(Model model) throws InterruptedException {
    		List<Category> genres = categoryService.list();
    		System.out.println(genres);
    		model.addAttribute("categories", genres);
    		return "categories/list";
    }
}
