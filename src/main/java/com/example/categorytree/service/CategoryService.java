package com.example.categorytree.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.categorytree.entity.Category;
import com.example.categorytree.entity.Child;
import com.example.categorytree.repository.CategoryRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CategoryService {
	
	@Autowired
	CategoryRepository categoryRepository;
	
	private String url = "https://app.rakuten.co.jp/services/api/IchibaGenre/Search/20140222?format=json&formatVersion=2&"
			+ "applicationId=1026301013779899297&genrePath=0&genreId={genreId}";
    private RestTemplate restTemplate = new RestTemplate();

    private static int RETRY_LIMIT = 3;
    private static int ROOT = 0;
	private static int GRAND_CHILD = 2;
	
	private List<Category> get(int genreId) {
		if (genreId != ROOT) {
			sleep();
		}
		Map<String,Object> map = callApi(genreId);
		List<Map<String,Object>> parents = (List<Map<String, Object>>) map.get("children");
		return parents.stream()
				.map(this::convert)
				.collect(Collectors.toList());
	}
	
	private Map<String, Object> callApi(int genreId) {
		Map<String,Object> response = new HashMap<String,Object>();
		int count = 0;
		while (count < RETRY_LIMIT) {
		  try {
		    response = restTemplate.getForObject(url, Map.class, genreId);
		    break;
		  } catch (RestClientException e) {
		    count++;
		    log.error("RestClientException. try to sleep.",e);
		    sleep();
		  }
		}
		return response;
	}
	
	private void sleep() {
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			log.error("faild to sleep", e);
		}
	}
	
	public List<Category> list() throws InterruptedException {
		List<Category> categories = get(ROOT);
		create(categories);
        return categories;
	}
	
	private Category convert(Map<String,Object> map) {
		Category category = new Category();
		category.setGenreId((int)(map.get("genreId")));
		category.setGenreLevel((int)(map.get("genreLevel")));
		category.setGenreName((String)(map.get("genreName")));
		category.setChildren(findChildren(category));
		log.info("converted genre: {}", category.getGenreName());
		return category;
	}
	
	private List<Category> findChildren(Category category) {
		if (category.getGenreLevel() > GRAND_CHILD) {
			return Collections.emptyList();
		}
	   	List<Category> children = get(category.getGenreId());
       	return children;
	}
	
    public void create(List<Category> categories) {
        categoryRepository.saveAll(toFlat(categories));
        List<Child> children =  new ArrayList<>();
        categories.forEach(g -> {
        		children.addAll(
        				g.getChildren().stream()
        				.map(c -> new Child(g.getGenreId(), c.getGenreId()))
        				.collect(Collectors.toList())
        		);
        });
    }
    
    private List<Category> toFlat(List<Category> categories) {
    		List<Category> flatList = new ArrayList<>();
    		List<Category> children = extractChildren(categories);
    		List<Category> grandChildren = extractChildren(children);
    		flatList.addAll(categories);
		flatList.addAll(children);
		flatList.addAll(grandChildren);
		return flatList;
	}
    
    private List<Category> extractChildren(List<Category> categories) {
    		List<Category> children = new ArrayList<>();
    		categories.forEach(c -> {
			if (!CollectionUtils.isEmpty(c.getChildren())) {
				children.addAll(c.getChildren());
			}
		});
    		return children;
    }
}
